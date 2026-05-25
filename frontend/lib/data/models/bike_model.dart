enum BikeState { AVAILABLE, RESERVED, IN_USE, LOCKED, MAINTENANCE }

class BikeModel {
  final String id;
  final String qrCode;
  final double latitude;
  final double longitude;
  final bool locked;
  final int batteryLevel;
  final BikeState state;

  BikeModel({
    required this.id,
    required this.qrCode,
    required this.latitude,
    required this.longitude,
    required this.locked,
    required this.batteryLevel,
    required this.state,
  });

  factory BikeModel.fromJson(Map<String, dynamic> json) {
    String stateStr = json['state'] ?? 'AVAILABLE';
    BikeState parsedState = BikeState.AVAILABLE;
    try {
      parsedState = BikeState.values.firstWhere((e) => e.name == stateStr);
    } catch (_) {}

    return BikeModel(
      id: json['id'] ?? '',
      qrCode: json['qrCode'] ?? '',
      latitude: (json['latitude'] as num?)?.toDouble() ?? 9.035,
      longitude: (json['longitude'] as num?)?.toDouble() ?? 38.752,
      locked: json['locked'] ?? true,
      batteryLevel: (json['batteryLevel'] as num?)?.toInt() ?? 100,
      state: parsedState,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'qrCode': qrCode,
      'latitude': latitude,
      'longitude': longitude,
      'locked': locked,
      'batteryLevel': batteryLevel,
      'state': state.name,
    };
  }
}
