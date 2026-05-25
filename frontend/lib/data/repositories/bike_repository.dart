import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/network/dio_client.dart';
import '../models/bike_model.dart';

final bikeRepositoryProvider = Provider<BikeRepository>((ref) {
  return BikeRepository(ref.read(dioProvider));
});

class BikeRepository {
  final Dio _dio;

  BikeRepository(this._dio);

  Future<List<BikeModel>> getBikes({bool availableOnly = false}) async {
    try {
      final response = await _dio.get('/bikes', queryParameters: {
        'availableOnly': availableOnly,
      });
      return (response.data as List).map((x) => BikeModel.fromJson(x)).toList();
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<BikeModel> getBikeById(String id) async {
    try {
      final response = await _dio.get('/bikes/$id');
      return BikeModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<BikeModel> addBike(String qrCode, double lat, double lon) async {
    try {
      final response = await _dio.post('/admin/bikes', data: {
        'qrCode': qrCode,
        'latitude': lat,
        'longitude': lon,
      });
      return BikeModel.fromJson(response.data);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<void> deleteBike(String id) async {
    try {
      await _dio.delete('/admin/bikes/$id');
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
