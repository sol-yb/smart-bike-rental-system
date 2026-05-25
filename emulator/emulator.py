import sys
import time
import json
import random
import threading
import subprocess

# Auto-install paho-mqtt if not present
try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("Installing required 'paho-mqtt' library...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "paho-mqtt"])
    import paho.mqtt.client as mqtt

# Default Configuration
DEFAULT_BROKER = "localhost"  # Fallback: "broker.emqx.io"
PORT = 1883
BIKE_ID = "BIKE-001"

# State variables
state = {
    "locked": True,
    "battery_level": 98,
    "state": "AVAILABLE",
    "latitude": 9.0350,   # Centered inside the campus boundary
    "longitude": 38.7520,
    "is_riding": False,
    "theft_simulation": False
}

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"Connected to MQTT broker: {DEFAULT_BROKER}")
        # Subscribe to lock/unlock topics
        lock_topic = f"bike/{BIKE_ID}/lock"
        unlock_topic = f"bike/{BIKE_ID}/unlock"
        client.subscribe(lock_topic)
        client.subscribe(unlock_topic)
        print(f"Subscribed to commands: \n  - {lock_topic}\n  - {unlock_topic}")
    else:
        print(f"Connection failed with code {rc}")

def on_message(client, userdata, msg):
    payload_str = msg.payload.decode()
    print(f"\n[Command Received] Topic: {msg.topic} -> {payload_str}")
    try:
        data = json.loads(payload_str)
        # Check command
        if "lock" in msg.topic:
            state["locked"] = True
            state["state"] = "AVAILABLE"
            state["is_riding"] = False
            print(">>> System LOCKED. Stopping ride simulation.")
            publish_status(client)
        elif "unlock" in msg.topic:
            state["locked"] = False
            state["state"] = "IN_USE"
            state["is_riding"] = True
            print(">>> System UNLOCKED. Starting ride simulation.")
            publish_status(client)
    except Exception as e:
        print(f"Error parsing command: {e}")

def publish_status(client):
    status_topic = f"bike/{BIKE_ID}/status"
    payload = {
        "locked": state["locked"],
        "batteryLevel": state["battery_level"],
        "state": state["state"]
    }
    client.publish(status_topic, json.dumps(payload))
    print(f"[Telemetry Sent] {status_topic} -> {payload}")

def publish_gps(client):
    gps_topic = f"bike/{BIKE_ID}/gps"
    payload = {
        "latitude": state["latitude"],
        "longitude": state["longitude"]
    }
    client.publish(gps_topic, json.dumps(payload))
    print(f"[GPS Sent] {gps_topic} -> {payload}")

def telemetry_loop(client):
    last_status_time = 0
    last_gps_time = 0
    
    while True:
        now = time.time()
        
        # Simulating battery discharge slowly
        if state["is_riding"] and random.random() < 0.1:
            state["battery_level"] = max(0, state["battery_level"] - 1)

        # Simulate movement if riding or simulating theft
        if state["is_riding"]:
            # Move slightly north-east
            state["latitude"] += 0.0002 + random.uniform(-0.00005, 0.00005)
            state["longitude"] += 0.0002 + random.uniform(-0.00005, 0.00005)
        elif state["theft_simulation"]:
            # Move slightly while locked
            state["latitude"] += 0.0001
            state["longitude"] += 0.0001
            # Send theft alerts
            client.publish(f"bike/{BIKE_ID}/theft-alert", json.dumps({"alert": "vibration_detected"}))
            print(f"[THEFT ALERT Sent] bike/{BIKE_ID}/theft-alert -> vibration_detected")

        # Stream GPS every 4 seconds
        if now - last_gps_time >= 4:
            publish_gps(client)
            last_gps_time = now

        # Stream status every 8 seconds
        if now - last_status_time >= 8:
            publish_status(client)
            last_status_time = now

        time.sleep(1)

def input_loop():
    print("\n-----------------------------------------------------")
    print("ESP32 IoT BIKE SIMULATOR CONSOLE")
    print("Commands:")
    print("  'theft' - Toggle theft simulation (moves bike while locked)")
    print("  'out'   - Force GPS coordinates outside the campus geofence boundary")
    print("  'in'    - Teleport GPS coordinates back inside campus boundary")
    print("  'status'- Manually trigger telemetry publish")
    print("  'exit'  - Terminate simulator")
    print("-----------------------------------------------------\n")
    
    while True:
        cmd = input().strip().lower()
        if cmd == "theft":
            state["theft_simulation"] = not state["theft_simulation"]
            print(f"Theft Simulation active: {state['theft_simulation']}")
        elif cmd == "out":
            state["latitude"] = 9.0600
            state["longitude"] = 38.7800
            print(f"Teleported OUTSIDE geofence: {state['latitude']}, {state['longitude']}")
        elif cmd == "in":
            state["latitude"] = 9.0350
            state["longitude"] = 38.7520
            print(f"Teleported INSIDE geofence: {state['latitude']}, {state['longitude']}")
        elif cmd == "status":
            print(f"Current internal state: {state}")
        elif cmd == "exit":
            print("Exiting...")
            sys.exit(0)

if __name__ == "__main__":
    # Allow passing broker and bike id as args
    import argparse
    parser = argparse.ArgumentParser(description="ESP32 Smart Lock Simulator")
    parser.add_argument("--broker", default=DEFAULT_BROKER, help="MQTT Broker URL")
    parser.add_argument("--bike-id", default=BIKE_ID, help="Bike QR/ID code")
    args = parser.parse_args()

    DEFAULT_BROKER = args.broker
    BIKE_ID = args.bike_id

    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = on_message

    try:
        client.connect(DEFAULT_BROKER, PORT, 60)
    except Exception as e:
        print(f"Could not connect to MQTT broker '{DEFAULT_BROKER}'. Trying EMQX public broker...")
        DEFAULT_BROKER = "broker.emqx.io"
        try:
            client.connect(DEFAULT_BROKER, PORT, 60)
            print(f"Connected to public EMQX broker: {DEFAULT_BROKER}")
        except Exception as ex:
            print("Fatal: Could not connect to any MQTT Broker. Ensure broker is running.")
            sys.exit(1)

    # Start network loop thread
    client.loop_start()

    # Start telemetry loop thread
    telemetry_thread = threading.Thread(target=telemetry_loop, args=(client,), daemon=True)
    telemetry_thread.start()

    # Start input loop in main thread
    input_loop()
