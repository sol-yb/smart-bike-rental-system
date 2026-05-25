import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/network/dio_client.dart';
import '../models/notification_model.dart';

final notificationRepositoryProvider = Provider<NotificationRepository>((ref) {
  return NotificationRepository(ref.read(dioProvider));
});

class NotificationRepository {
  final Dio _dio;

  NotificationRepository(this._dio);

  Future<List<NotificationModel>> getNotifications() async {
    try {
      final response = await _dio.get('/notifications');
      return (response.data as List).map((x) => NotificationModel.fromJson(x)).toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<void> markAsRead(String id) async {
    try {
      await _dio.post('/notifications/$id/read');
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  String _handleError(DioException e) {
    if (e.response != null && e.response!.data != null) {
      final msg = e.response!.data['message'] ?? e.response!.data['error'];
      if (msg != null) return msg.toString();
    }
    return e.message ?? 'An unexpected error occurred';
  }
}
