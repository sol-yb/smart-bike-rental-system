class ApiEndpoints {
  // For Windows / iOS / Web, localhost works out of the box.
  // For Android Emulators, you can change this to http://10.0.2.2:8080/api
  static const String baseUrl = 'http://localhost:8080/api';
  static const String wsUrl = 'ws://localhost:8080/ws';
  static const String mqttBroker = 'localhost';
}
