import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import '../../../core/theme/app_theme.dart';
import '../../providers/ride_provider.dart';

class RideTrackingScreen extends ConsumerWidget {
  const RideTrackingScreen({super.key});

  String _formatDuration(int totalSeconds) {
    final int hours = totalSeconds ~/ 3600;
    final int minutes = (totalSeconds % 3600) ~/ 60;
    final int seconds = totalSeconds % 60;
    return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rideState = ref.watch(rideStateProvider);
    final activeRide = rideState.activeRide;

    if (activeRide == null) {
      // If no active ride is found, redirect to home
      WidgetsBinding.instance.addPostFrameCallback((_) {
        context.go('/home');
      });
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    // Convert path history array to LatLng list
    final List<LatLng> pathPoints = rideState.pathHistory.map((pt) {
      return LatLng(pt[0] as double, pt[1] as double);
    }).toList();

    LatLng mapCenter = LatLng(rideState.currentLatitude, rideState.currentLongitude);

    return Scaffold(
      backgroundColor: AppTheme.darkBackground,
      body: Stack(
        children: [
          // Background Map showing live bike tracking
          FlutterMap(
            options: MapOptions(
              initialCenter: mapCenter,
              initialZoom: 17.0,
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                subdomains: const ['a', 'b', 'c'],
              ),
              PolylineLayer(
                polylines: [
                  Polyline(
                    points: pathPoints,
                    strokeWidth: 4.0,
                    color: AppTheme.neonGreen,
                  ),
                ],
              ),
              MarkerLayer(
                markers: [
                  // Start point marker
                  Marker(
                    point: LatLng(activeRide.startLatitude, activeRide.startLongitude),
                    width: 30,
                    height: 30,
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        border: Border.all(color: Colors.black, width: 2),
                        shape: BoxShape.circle,
                      ),
                      child: const Center(
                        child: Text(
                          'S',
                          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 11, color: Colors.black),
                        ),
                      ),
                    ),
                  ),
                  // Current location marker (pulsing)
                  Marker(
                    point: mapCenter,
                    width: 44,
                    height: 44,
                    child: Stack(
                      alignment: Alignment.center,
                      children: [
                        const CurrentLocationPulse(),
                        Container(
                          width: 20,
                          height: 20,
                          decoration: const BoxDecoration(
                            color: AppTheme.cyberCyan,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),

          // Live Metrics & End Ride UI (Float sheets)
          Positioned(
            top: 50,
            left: 20,
            right: 20,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: AppTheme.darkCard.withOpacity(0.9),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppTheme.neonGreen.withOpacity(0.2)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.flash_on, color: AppTheme.neonGreen),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'ACTIVE RIDE: ${activeRide.bikeQrCode}',
                      style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 13),
                    ),
                  ),
                  Container(
                    width: 10,
                    height: 10,
                    decoration: const BoxDecoration(color: AppTheme.neonGreen, shape: BoxShape.circle),
                  ),
                  const SizedBox(width: 6),
                  const Text('GPS connected', style: TextStyle(color: Colors.grey, fontSize: 11)),
                ],
              ),
            ),
          ),

          Positioned(
            bottom: 30,
            left: 20,
            right: 20,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Metrics HUD
                Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    color: AppTheme.darkCard.withOpacity(0.9),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: AppTheme.cyberCyan.withOpacity(0.2)),
                  ),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceAround,
                        children: [
                          _buildMetric('DURATION', _formatDuration(rideState.elapsedSeconds), AppTheme.neonGreen),
                          _buildMetric('DISTANCE', '${(rideState.elapsedSeconds * 0.002).toStringAsFixed(3)} km', Colors.white), // mock incrementing distance
                          _buildMetric('EST. FARE', '${(5.0 + (rideState.elapsedSeconds ~/ 60) * 0.5 + (rideState.elapsedSeconds * 0.002) * 2.0).toStringAsFixed(2)} ETB', AppTheme.cyberCyan),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 15),

                // Custom Slide to Latch Lock Button
                SlideToLockAction(
                  onConfirm: () async {
                    try {
                      await ref.read(rideStateProvider.notifier).endRide();
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Ride completed successfully. Payment deducted.'),
                            backgroundColor: AppTheme.neonGreen,
                            foregroundColor: Colors.black,
                          ),
                        );
                        context.go('/home');
                      }
                    } catch (e) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Failed to end ride: $e'),
                          backgroundColor: AppTheme.hotPink,
                        ),
                      );
                    }
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMetric(String label, String value, Color color) {
    return Column(
      children: [
        Text(label, style: const TextStyle(color: Colors.grey, fontSize: 10, letterSpacing: 1)),
        const SizedBox(height: 6),
        Text(
          value,
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color),
        ),
      ],
    );
  }
}

class CurrentLocationPulse extends StatefulWidget {
  const CurrentLocationPulse({super.key});

  @override
  State<CurrentLocationPulse> createState() => _CurrentLocationPulseState();
}

class _CurrentLocationPulseState extends State<CurrentLocationPulse> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();
    _animation = Tween<double>(begin: 12, end: 40).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return Container(
          width: _animation.value,
          height: _animation.value,
          decoration: BoxDecoration(
            color: AppTheme.cyberCyan.withOpacity(1.0 - (_controller.value)),
            shape: BoxShape.circle,
          ),
        );
      },
    );
  }
}

class SlideToLockAction extends StatefulWidget {
  final Future<void> Function() onConfirm;

  const SlideToLockAction({required this.onConfirm, super.key});

  @override
  State<SlideToLockAction> createState() => _SlideToLockActionState();
}

class _SlideToLockActionState extends State<SlideToLockAction> {
  double _dragOffset = 0.0;
  bool _isConfirming = false;

  @override
  Widget build(BuildContext context) {
    const double sliderWidth = 60.0;
    
    return LayoutBuilder(
      builder: (context, constraints) {
        final double maxOffset = constraints.maxWidth - sliderWidth - 8.0;

        return Container(
          height: 68,
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            color: Colors.red.withOpacity(0.15),
            borderRadius: BorderRadius.circular(34),
            border: Border.all(color: Colors.red.withOpacity(0.3)),
          ),
          child: Stack(
            children: [
              const Center(
                child: Text(
                  'SLIDE TO LOCK & END RIDE',
                  style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold, fontSize: 13, letterSpacing: 1),
                ),
              ),
              AnimatedPositioned(
                duration: const Duration(milliseconds: 50),
                left: _dragOffset,
                top: 0,
                bottom: 0,
                child: GestureDetector(
                  onHorizontalDragUpdate: (details) {
                    if (_isConfirming) return;
                    setState(() {
                      _dragOffset += details.primaryDelta!;
                      if (_dragOffset < 0) _dragOffset = 0;
                      if (_dragOffset > maxOffset) _dragOffset = maxOffset;
                    });
                  },
                  onHorizontalDragEnd: (details) async {
                    if (_isConfirming) return;
                    if (_dragOffset >= maxOffset * 0.85) {
                      // Trigger callback
                      setState(() {
                        _dragOffset = maxOffset;
                        _isConfirming = true;
                      });
                      try {
                        await widget.onConfirm();
                      } catch (_) {
                        setState(() {
                          _dragOffset = 0.0;
                          _isConfirming = false;
                        });
                      }
                    } else {
                      // Snap back
                      setState(() {
                        _dragOffset = 0.0;
                      });
                    }
                  },
                  child: Container(
                    width: sliderWidth,
                    decoration: const BoxDecoration(
                      color: Colors.red,
                      shape: BoxShape.circle,
                    ),
                    child: Center(
                      child: _isConfirming
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                            )
                          : const Icon(Icons.arrow_forward_ios_rounded, color: Colors.white),
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
