class RideModel {
  final String id;
  final String userId;
  final String bikeId;
  final String bikeQrCode;
  final DateTime startTime;
  final DateTime? endTime;
  final double startLatitude;
  final double startLongitude;
  final double? endLatitude;
  final double? endLongitude;
  final double distance;
  final double cost;
  final bool active;

  RideModel({
    required this.id,
    required this.userId,
    required this.bikeId,
    required this.bikeQrCode,
    required this.startTime,
    this.endTime,
    required this.startLatitude,
    required this.startLongitude,
    this.endLatitude,
    this.endLongitude,
    required this.distance,
    required this.cost,
    required this.active,
  });

  factory RideModel.fromJson(Map<String, dynamic> json) {
    return RideModel(
      id: json['id'] ?? '',
      userId: json['userId'] ?? '',
      bikeId: json['bikeId'] ?? '',
      bikeQrCode: json['bikeQrCode'] ?? '',
      startTime: json['startTime'] != null ? DateTime.parse(json['startTime']) : DateTime.now(),
      endTime: json['endTime'] != null ? DateTime.parse(json['endTime']) : null,
      startLatitude: (json['startLatitude'] as num?)?.toDouble() ?? 0.0,
      startLongitude: (json['startLongitude'] as num?)?.toDouble() ?? 0.0,
      endLatitude: (json['endLatitude'] as num?)?.toDouble(),
      endLongitude: (json['endLongitude'] as num?)?.toDouble(),
      distance: (json['distance'] as num?)?.toDouble() ?? 0.0,
      cost: (json['cost'] as num?)?.toDouble() ?? 0.0,
      active: json['active'] ?? false,
    );
  }
}
