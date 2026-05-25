import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/bike_model.dart';
import '../../data/repositories/bike_repository.dart';
import '../../core/websocket/websocket_service.dart';

final mapStateProvider = StateNotifierProvider<MapNotifier, AsyncValue<List<BikeModel>>>((ref) {
  final bikeRepo = ref.read(bikeRepositoryProvider);
  final wsService = ref.read(webSocketServiceProvider);
  return MapNotifier(bikeRepo, wsService);
});

class MapNotifier extends StateNotifier<AsyncValue<List<BikeModel>>> {
  final BikeRepository _bikeRepository;
  final WebSocketService _wsService;
  StreamSubscription? _wsSubscription;

  MapNotifier(this._bikeRepository, this._wsService) : super(const AsyncValue.loading()) {
    loadBikes();
    _listenToWebSocket();
  }

  Future<void> loadBikes() async {
    try {
      final bikes = await _bikeRepository.getBikes(availableOnly: false);
      state = AsyncValue.data(bikes);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  void _listenToWebSocket() {
    _wsService.connect();
    _wsSubscription = _wsService.eventStream.listen((event) {
      final eventName = event['event'];
      final bikeId = event['bikeId'];

      if (state.value == null) return;
      final currentList = List<BikeModel>.from(state.value!);

      if (eventName == 'BIKE_GPS') {
        final lat = (event['latitude'] as num).toDouble();
        final lon = (event['longitude'] as num).toDouble();
        
        final updatedList = currentList.map((bike) {
          if (bike.id == bikeId) {
            return BikeModel(
              id: bike.id,
              qrCode: bike.qrCode,
              latitude: lat,
              longitude: lon,
              locked: bike.locked,
              batteryLevel: bike.batteryLevel,
              state: bike.state,
            );
          }
          return bike;
        }).toList();
        state = AsyncValue.data(updatedList);
      } else if (eventName == 'BIKE_STATUS') {
        final locked = event['locked'] as bool;
        final battery = (event['batteryLevel'] as num).toInt();
        final stateStr = event['state'] as String;
        
        BikeState parsedState = BikeState.AVAILABLE;
        try {
          parsedState = BikeState.values.firstWhere((e) => e.name == stateStr);
        } catch (_) {}

        final updatedList = currentList.map((bike) {
          if (bike.id == bikeId) {
            return BikeModel(
              id: bike.id,
              qrCode: bike.qrCode,
              latitude: bike.latitude,
              longitude: bike.longitude,
              locked: locked,
              batteryLevel: battery,
              state: parsedState,
            );
          }
          return bike;
        }).toList();
        state = AsyncValue.data(updatedList);
      }
    });
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
    super.dispose();
  }
}
