import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import '../constants/api_endpoints.dart';

final webSocketServiceProvider = Provider<WebSocketService>((ref) {
  return WebSocketService();
});

class WebSocketService {
  WebSocketChannel? _channel;
  bool _isConnected = false;

  final _eventController = StreamController<Map<String, dynamic>>.broadcast();

  Stream<Map<String, dynamic>> get eventStream => _eventController.stream;

  void connect() {
    if (_isConnected) return;

    try {
      _channel = WebSocketChannel.connect(Uri.parse(ApiEndpoints.wsUrl));
      _isConnected = true;
      debugPrint('WebSocket Connected to ${ApiEndpoints.wsUrl}');

      _channel!.stream.listen(
        (message) {
          try {
            final data = jsonDecode(message) as Map<String, dynamic>;
            _eventController.add(data);
          } catch (e) {
            debugPrint('Failed to parse WebSocket message: $e');
          }
        },
        onError: (error) {
          debugPrint('WebSocket Error: $error');
          _isConnected = false;
          _reconnect();
        },
        onDone: () {
          debugPrint('WebSocket Connection Closed');
          _isConnected = false;
          _reconnect();
        },
      );
    } catch (e) {
      debugPrint('Failed to connect to WebSocket: $e');
      _isConnected = false;
      _reconnect();
    }
  }

  void _reconnect() {
    Future.delayed(const Duration(seconds: 5), () {
      if (!_isConnected) {
        debugPrint('Attempting to reconnect WebSocket...');
        connect();
      }
    });
  }

  void disconnect() {
    _channel?.sink.close();
    _isConnected = false;
  }
}
