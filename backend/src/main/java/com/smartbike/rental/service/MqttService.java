package com.smartbike.rental.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbike.rental.config.WebSocketHandler;
import com.smartbike.rental.model.Bike;
import com.smartbike.rental.model.BikeState;
import com.smartbike.rental.model.GpsLog;
import com.smartbike.rental.model.MqttEvent;
import com.smartbike.rental.repository.BikeRepository;
import com.smartbike.rental.repository.GpsLogRepository;
import com.smartbike.rental.repository.MqttEventRepository;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class MqttService implements MqttCallback {

    @Value("${app.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${app.mqtt.client-id}")
    private String clientId;

    @Autowired
    private BikeRepository bikeRepository;

    @Autowired
    private GpsLogRepository gpsLogRepository;

    @Autowired
    private MqttEventRepository mqttEventRepository;

    @Autowired
    private WebSocketHandler webSocketHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private GeofenceService geofenceService;

    @Autowired
    @Lazy
    private NotificationService notificationService;

    @Autowired
    @Lazy
    private GpsSimulationService gpsSimulationService;

    private MqttClient mqttClient;

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setAutomaticReconnect(true);
            
            mqttClient.setCallback(this);
            mqttClient.connect(connOpts);

            mqttClient.subscribe("bike/+/status");
            mqttClient.subscribe("bike/+/gps");
            mqttClient.subscribe("bike/+/theft-alert");

            System.out.println("Successfully connected and subscribed to MQTT Broker: " + brokerUrl);
        } catch (MqttException e) {
            System.err.println("Failed to initialize MQTT client: " + e.getMessage());
        }
    }

    public void publish(String topic, String payload) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            System.err.println("MQTT client not connected. Skipping publish.");
            return;
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            mqttClient.publish(topic, message);
            
            mqttEventRepository.save(MqttEvent.builder().topic(topic).payload(payload).build());
        } catch (MqttException e) {
            System.err.println("Failed to publish MQTT message: " + e.getMessage());
        }
    }

    public void publishLockCommand(String qrCode) {
        publish("bike/" + qrCode + "/lock", "{\"command\":\"lock\"}");
    }

    public void publishUnlockCommand(String qrCode) {
        publish("bike/" + qrCode + "/unlock", "{\"command\":\"unlock\"}");
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("MQTT Connection lost: " + cause.getMessage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());
        
        mqttEventRepository.save(MqttEvent.builder().topic(topic).payload(payload).build());

        String[] tokens = topic.split("/");
        if (tokens.length < 3) return;
        
        String qrCode = tokens[1];
        String subTopic = tokens[2];

        Optional<Bike> bikeOpt = bikeRepository.findByQrCode(qrCode);
        if (bikeOpt.isEmpty()) {
            System.out.println("MQTT message received for unknown bike QR code: " + qrCode);
            return;
        }
        Bike bike = bikeOpt.get();

        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("bikeId", bike.getId().toString());
        wsPayload.put("qrCode", qrCode);

        if ("status".equalsIgnoreCase(subTopic)) {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            if (data.containsKey("locked")) {
                bike.setLocked((Boolean) data.get("locked"));
            }
            if (data.containsKey("batteryLevel")) {
                bike.setBatteryLevel(((Number) data.get("batteryLevel")).intValue());
            }
            if (data.containsKey("state")) {
                bike.setState(BikeState.valueOf((String) data.get("state")));
            }
            bikeRepository.save(bike);

            wsPayload.put("event", "BIKE_STATUS");
            wsPayload.put("locked", bike.isLocked());
            wsPayload.put("batteryLevel", bike.getBatteryLevel());
            wsPayload.put("state", bike.getState().name());
            webSocketHandler.broadcast(objectMapper.writeValueAsString(wsPayload));

        } else if ("gps".equalsIgnoreCase(subTopic)) {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            double lat = ((Number) data.get("latitude")).doubleValue();
            double lon = ((Number) data.get("longitude")).doubleValue();

            bike.setLatitude(lat);
            bike.setLongitude(lon);
            bikeRepository.save(bike);

            gpsLogRepository.save(GpsLog.builder().bike(bike).latitude(lat).longitude(lon).build());

            gpsSimulationService.processGpsPing(bike, lat, lon);

            wsPayload.put("event", "BIKE_GPS");
            wsPayload.put("latitude", lat);
            wsPayload.put("longitude", lon);
            webSocketHandler.broadcast(objectMapper.writeValueAsString(wsPayload));

        } else if ("theft-alert".equalsIgnoreCase(subTopic)) {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String alertType = (String) data.getOrDefault("alert", "unauthorized_movement");

            notificationService.createNotification(
                    null,
                    "Theft Warning: " + qrCode,
                    "Unauthorized activity (" + alertType + ") detected on bike " + qrCode,
                    "THEFT_ALERT"
            );
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            // Ignore
        }
    }
}
