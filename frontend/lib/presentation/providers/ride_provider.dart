import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/ride_model.dart';
import '../../data/repositories/ride_repository.dart';
import '../../core/websocket/websocket_service.dart';
import 'auth_provider.dart';

final rideStateProvider = StateNotifierProvider<RideNotifier, RideState>((ref) {
  final rideRepo = ref.read(rideRepositoryProvider);
  final wsService = ref.read(webSocketServiceProvider);
  return RideNotifier(rideRepo, wsService, ref);
});

class RideState {
  final RideModel? activeRide;
  final bool isLoading;
  final String? error;
  final int elapsedSeconds;
  final double currentLatitude;
  final double currentLongitude;
  final List<dynamic> pathHistory;

  RideState({
    this.activeRide,
    this.isLoading = false,
    this.error,
    this.elapsedSeconds = 0,
    this.currentLatitude = 0.0,
    this.currentLongitude = 0.0,
    this.pathHistory = const [],
  });

  RideState copyWith({
    RideModel? Function()? activeRide,
    bool? isLoading,
    String? Function()? error,
    int? elapsedSeconds,
    double? currentLatitude,
    double? currentLongitude,
    List<dynamic>? pathHistory,
  }) {
    return RideState(
      activeRide: activeRide != null ? activeRide() : this.activeRide,
      isLoading: isLoading ?? this.isLoading,
      error: error != null ? error() : this.error,
      elapsedSeconds: elapsedSeconds ?? this.elapsedSeconds,
      currentLatitude: currentLatitude ?? this.currentLatitude,
      currentLongitude: currentLongitude ?? this.currentLongitude,
      pathHistory: pathHistory ?? this.pathHistory,
    );
  }
}

class RideNotifier extends StateNotifier<RideState> {
  final RideRepository _rideRepository;
  final WebSocketService _wsService;
  final Ref _ref;
  
  Timer? _timer;
  StreamSubscription? _wsSubscription;

  RideNotifier(this._rideRepository, this._wsService, this._ref) : super(RideState()) {
    checkActiveRide();
    _listenToWebSocket();
  }

  Future<void> checkActiveRide() async {
    state = state.copyWith(isLoading: true);
    try {
      final ride = await _rideRepository.getActiveRide();
      if (ride != null) {
        state = state.copyWith(
          activeRide: () => ride,
          isLoading: false,
          currentLatitude: ride.startLatitude,
          currentLongitude: ride.startLongitude,
          pathHistory: [ [ride.startLatitude, ride.startLongitude] ],
        );
        _startTimer();
      } else {
        state = state.copyWith(isLoading: false);
      }
    } catch (e) {
      state = state.copyWith(isLoading: false, error: () => e.toString());
    }
  }

  Future<void> startRide(String qrCode) async {
    state = state.copyWith(isLoading: true, error: () => null);
    try {
      final ride = await _rideRepository.startRide(qrCode);
      state = state.copyWith(
        activeRide: () => ride,
        isLoading: false,
        elapsedSeconds: 0,
        currentLatitude: ride.startLatitude,
        currentLongitude: ride.startLongitude,
        pathHistory: [ [ride.startLatitude, ride.startLongitude] ],
      );
      _startTimer();
    } catch (e) {
      state = state.copyWith(isLoading: false, error: () => e.toString());
      rethrow;
    }
  }

  Future<void> endRide() async {
    if (state.activeRide == null) return;
    state = state.copyWith(isLoading: true);
    try {
      final finishedRide = await _rideRepository.endRide(
        state.activeRide!.id,
        state.currentLatitude,
        state.currentLongitude,
      );
      
      _timer?.cancel();
      _timer = null;

      final authUser = _ref.read(authStateProvider).value;
      if (authUser != null) {
        _ref.read(authStateProvider.notifier).refreshUserBalance(
          authUser.walletBalance - finishedRide.cost
        );
      }

      state = RideState();
    } catch (e) {
      state = state.copyWith(isLoading: false, error: () => e.toString());
      rethrow;
    }
  }

  void _startTimer() {
    _timer?.cancel();
    if (state.activeRide == null) return;
    
    final start = state.activeRide!.startTime;
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final diff = DateTime.now().difference(start).inSeconds;
      state = state.copyWith(elapsedSeconds: diff);
    });
  }

  void _listenToWebSocket() {
    _wsSubscription = _wsService.eventStream.listen((event) {
      final eventName = event['event'];
      final bikeId = event['bikeId'];

      if (state.activeRide == null) return;

      if (bikeId == state.activeRide!.bikeId) {
        if (eventName == 'BIKE_GPS') {
          final lat = (event['latitude'] as num).toDouble();
          final lon = (event['longitude'] as num).toDouble();
          
          final newHistory = List<dynamic>.from(state.pathHistory)..add([lat, lon]);
          state = state.copyWith(
            currentLatitude: lat,
            currentLongitude: lon,
            pathHistory: newHistory,
          );
        } else if (eventName == 'BIKE_STATUS') {
          final isLocked = event['locked'] as bool;
          if (isLocked) {
            checkActiveRide();
          }
        }
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _wsSubscription?.cancel();
    super.dispose();
  }
}
