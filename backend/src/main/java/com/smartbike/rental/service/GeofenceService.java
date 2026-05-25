package com.smartbike.rental.service;

import com.smartbike.rental.model.Bike;
import com.smartbike.rental.model.BikeState;
import com.smartbike.rental.model.Ride;
import com.smartbike.rental.repository.RideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GeofenceService {

    @Value("${app.geofence.boundary-points}")
    private List<String> boundaryPointsStr;

    private List<Coordinate> boundary;

    @Autowired
    @Lazy
    private MqttService mqttService;

    @Autowired
    @Lazy
    private NotificationService notificationService;

    @Autowired
    private RideRepository rideRepository;

    @PostConstruct
    public void init() {
        boundary = new ArrayList<>();
        if (boundaryPointsStr != null) {
            for (String p : boundaryPointsStr) {
                String[] parts = p.split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    boundary.add(new Coordinate(lat, lon));
                }
            }
        }
    }

    public void checkGeofence(Bike bike, double lat, double lon) {
        if (boundary.isEmpty()) return;

        boolean inside = isInside(lat, lon);

        if (!inside) {
            System.out.println("GEOFENCE BREACH: Bike " + bike.getQrCode() + " is outside campus boundary!");

            if (bike.getState() == BikeState.IN_USE) {
                Optional<Ride> activeRide = rideRepository.findByBikeIdAndActive(bike.getId(), true);
                if (activeRide.isPresent()) {
                    Ride ride = activeRide.get();
                    
                    mqttService.publishLockCommand(bike.getQrCode());
                    
                    notificationService.createNotification(
                            ride.getUser(),
                            "Geofence Breach Warning",
                            "Your ride on bike " + bike.getQrCode() + " has crossed the campus boundary. The bike has been automatically locked for safety. Please return it to the campus zone.",
                            "SYSTEM_ALERT"
                    );
                }
            } else {
                notificationService.createNotification(
                        null,
                        "Theft Alarm: Out of Bounds",
                        "Bike " + bike.getQrCode() + " was detected outside the geofenced campus zone while locked. Possible theft in progress!",
                        "THEFT_ALERT"
                );
            }
        }
    }

    private boolean isInside(double lat, double lon) {
        int numPoints = boundary.size();
        boolean inPoly = false;
        int j = numPoints - 1;
        for (int i = 0; i < numPoints; i++) {
            Coordinate vertexI = boundary.get(i);
            Coordinate vertexJ = boundary.get(j);
            if (vertexI.lon < lon && vertexJ.lon >= lon || vertexJ.lon < lon && vertexI.lon >= lon) {
                if (vertexI.lat + (lon - vertexI.lon) / (vertexJ.lon - vertexI.lon) * (vertexJ.lat - vertexI.lat) < lat) {
                    inPoly = !inPoly;
                }
            }
            j = i;
        }
        return inPoly;
    }

    public static class Coordinate {
        public double lat;
        public double lon;

        public Coordinate(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }
}
