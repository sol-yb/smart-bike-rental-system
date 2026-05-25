import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/network/dio_client.dart';
import '../models/ride_model.dart';

final rideRepositoryProvider = Provider<RideRepository>((ref) {
  return RideRepository(ref.read(dioProvider));
});

class RideRepository {
  final Dio _dio;

  RideRepository(this._dio);

  Future<RideModel> startRide(String qrCode) async {
    try {
      final response = await _dio.post('/rides/start', data: {
        'qrCode': qrCode,
      });
      return RideModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<RideModel> endRide(String rideId, double lat, double lon) async {
    try {
      final response = await _dio.post(
        '/rides/end/$rideId',
        queryParameters: {
          'latitude': lat,
          'longitude': lon,
        },
      );
      return RideModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<RideModel?> getActiveRide() async {
    try {
      final response = await _dio.get('/rides/active');
      return RideModel.fromJson(response.data);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) {
        return null;
      }
      throw _handleError(e);
    }
  }

  Future<List<RideModel>> getRideHistory() async {
    try {
      final response = await _dio.get('/rides/history');
      return (response.data as List).map((x) => RideModel.fromJson(x)).toList();
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
