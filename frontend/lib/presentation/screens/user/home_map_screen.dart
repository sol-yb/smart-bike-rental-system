import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import 'package:intl/intl.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/websocket/websocket_service.dart';
import '../../providers/auth_provider.dart';
import '../../providers/map_provider.dart';
import '../../providers/ride_provider.dart';
import '../../../data/models/bike_model.dart';
import '../../../data/repositories/notification_repository.dart';
import '../../../data/models/notification_model.dart';

class HomeMapScreen extends ConsumerStatefulWidget {
  const HomeMapScreen({super.key});

  @override
  ConsumerState<HomeMapScreen> createState() => _HomeMapScreenState();
}

class _HomeMapScreenState extends ConsumerState<HomeMapScreen> {
  int _currentTab = 0;
  final MapController _mapController = MapController();
  StreamSubscription? _wsSubscription;
  List<NotificationModel> _alerts = [];
  bool _isLoadingAlerts = false;

  @override
  void initState() {
    super.initState();
    _listenToWSNotifications();
    _fetchAlerts();
  }

  @override
  void dispose() {
    _wsSubscription?.cancel();
    super.dispose();
  }

  void _listenToWSNotifications() {
    final ws = ref.read(webSocketServiceProvider);
    _wsSubscription = ws.eventStream.listen((event) {
      final eventName = event['event'];
      if (eventName == 'NOTIFICATION') {
        final title = event['title'] ?? 'System Alert';
        final msg = event['message'] ?? '';
        final type = event['type'] ?? 'SYSTEM_ALERT';

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                Icon(
                  type == 'THEFT_ALERT' ? Icons.warning_amber_rounded : Icons.info_outline,
                  color: type == 'THEFT_ALERT' ? Colors.red : AppTheme.neonGreen,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
                      Text(msg, style: const TextStyle(fontSize: 12)),
                    ],
                  ),
                ),
              ],
            ),
            backgroundColor: AppTheme.darkCard,
            duration: const Duration(seconds: 5),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          ),
        );
        _fetchAlerts();
      }
    });
  }

  Future<void> _fetchAlerts() async {
    setState(() => _isLoadingAlerts = true);
    try {
      final repo = ref.read(notificationRepositoryProvider);
      final list = await repo.getNotifications();
      setState(() {
        _alerts = list;
        _isLoadingAlerts = false;
      });
    } catch (_) {
      setState(() => _isLoadingAlerts = false);
    }
  }

  Future<void> _triggerTopUp(double amount) async {
    try {
      await ref.read(authStateProvider.notifier).topUp(amount, 'CHAPA_SIMULATED');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Simulated Chapa Payment successful! Credited $amount Birr.'),
          backgroundColor: AppTheme.neonGreen,
          foregroundColor: Colors.black,
        ),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Payment failed: $e'),
          backgroundColor: AppTheme.hotPink,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    // Auto redirect to Active Ride Screen if user has an active ride
    final rideState = ref.watch(rideStateProvider);
    if (rideState.activeRide != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        context.go('/ride');
      });
    }

    final authUser = ref.watch(authStateProvider).value;
    if (authUser == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      backgroundColor: AppTheme.darkBackground,
      appBar: AppBar(
        backgroundColor: AppTheme.darkBackground,
        elevation: 0,
        title: Row(
          children: [
            const Icon(Icons.pedal_bike_rounded, color: AppTheme.neonGreen),
            const SizedBox(width: 8),
            Text(
              _currentTab == 0 ? 'NEARBY BIKES' : _currentTab == 1 ? 'WALLET & TOPUP' : 'ALERTS & ACCOUNT',
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white),
            ),
          ],
        ),
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 16),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: AppTheme.neonGreen.withOpacity(0.1),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: AppTheme.neonGreen.withOpacity(0.3)),
            ),
            child: Row(
              children: [
                const Icon(Icons.account_balance_wallet_outlined, size: 16, color: AppTheme.neonGreen),
                const SizedBox(width: 6),
                Text(
                  '${authUser.walletBalance.toStringAsFixed(2)} ETB',
                  style: const TextStyle(fontWeight: FontWeight.bold, color: AppTheme.neonGreen, fontSize: 13),
                ),
              ],
            ),
          )
        ],
      ),
      body: IndexedStack(
        index: _currentTab,
        children: [
          _buildMapTab(),
          _buildWalletTab(authUser),
          _buildAccountTab(authUser),
        ],
      ),
      floatingActionButton: _currentTab == 0
          ? FloatingActionButton.extended(
              onPressed: () => context.go('/scan'),
              backgroundColor: AppTheme.neonGreen,
              foregroundColor: Colors.black,
              icon: const Icon(Icons.qr_code_scanner_rounded),
              label: const Text('SCAN TO RIDE', style: TextStyle(fontWeight: FontWeight.bold)),
            )
          : null,
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentTab,
        onTap: (index) => setState(() => _currentTab = index),
        backgroundColor: AppTheme.darkCard,
        selectedItemColor: AppTheme.neonGreen,
        unselectedItemColor: Colors.grey,
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.map_outlined), activeIcon: Icon(Icons.map), label: 'Map'),
          BottomNavigationBarItem(icon: Icon(Icons.wallet_outlined), activeIcon: Icon(Icons.wallet), label: 'Wallet'),
          BottomNavigationBarItem(icon: Icon(Icons.notifications_outlined), activeIcon: Icon(Icons.notifications), label: 'Alerts'),
        ],
      ),
    );
  }

  Widget _buildMapTab() {
    final bikesState = ref.watch(mapStateProvider);

    return bikesState.when(
      loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.neonGreen)),
      error: (err, _) => Center(child: Text('Error loading map: $err', style: const TextStyle(color: Colors.red))),
      data: (bikes) {
        // Find center coordinate or default
        LatLng mapCenter = const LatLng(9.035, 38.752);
        if (bikes.isNotEmpty) {
          mapCenter = LatLng(bikes.first.latitude, bikes.first.longitude);
        }

        final markers = bikes.map((bike) {
          final isAvailable = bike.state == BikeState.AVAILABLE;
          return Marker(
            point: LatLng(bike.latitude, bike.longitude),
            width: 50,
            height: 50,
            child: GestureDetector(
              onTap: () => _showBikeDetailsBottomSheet(bike),
              child: Stack(
                alignment: Alignment.center,
                children: [
                  Container(
                    width: 34,
                    height: 34,
                    decoration: BoxDecoration(
                      color: isAvailable ? AppTheme.neonGreen.withOpacity(0.2) : Colors.red.withOpacity(0.2),
                      shape: BoxShape.circle,
                    ),
                  ),
                  Icon(
                    Icons.pedal_bike_rounded,
                    color: isAvailable ? AppTheme.neonGreen : Colors.red,
                    size: 22,
                  ),
                ],
              ),
            ),
          );
        }).toList();

        return FlutterMap(
          mapController: _mapController,
          options: MapOptions(
            initialCenter: mapCenter,
            initialZoom: 16.0,
          ),
          children: [
            TileLayer(
              urlTemplate: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
              subdomains: const ['a', 'b', 'c'],
            ),
            MarkerLayer(markers: markers),
          ],
        );
      },
    );
  }

  void _showBikeDetailsBottomSheet(BikeModel bike) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.darkCard,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) {
        final isAvailable = bike.state == BikeState.AVAILABLE;
        return Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    bike.qrCode,
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: isAvailable ? AppTheme.neonGreen.withOpacity(0.1) : Colors.amber.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      bike.state.name,
                      style: TextStyle(
                        color: isAvailable ? AppTheme.neonGreen : Colors.amber,
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _buildStatItem(Icons.battery_charging_full_rounded, '${bike.batteryLevel}%', 'Battery'),
                  _buildStatItem(Icons.lock_outline_rounded, bike.locked ? 'Locked' : 'Unlocked', 'Status'),
                  _buildStatItem(Icons.map_outlined, 'Campus Area', 'Geofence'),
                ],
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: isAvailable
                    ? () {
                        Navigator.pop(context);
                        context.go('/scan');
                      }
                    : null,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.neonGreen,
                  foregroundColor: Colors.black,
                  disabledBackgroundColor: Colors.grey.shade800,
                  disabledForegroundColor: Colors.grey,
                ),
                child: Text(isAvailable ? 'SCAN AND UNLOCK' : 'BIKE UNAVAILABLE'),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildStatItem(IconData icon, String value, String label) {
    return Column(
      children: [
        Icon(icon, color: AppTheme.cyberCyan, size: 28),
        const SizedBox(height: 6),
        Text(value, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 15)),
        Text(label, style: const TextStyle(color: Colors.grey, fontSize: 11)),
      ],
    );
  }

  Widget _buildWalletTab(dynamic user) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [AppTheme.darkCard, Color(0xFF0F172A)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: AppTheme.cyberCyan.withOpacity(0.2)),
              boxShadow: [
                BoxShadow(color: AppTheme.cyberCyan.withOpacity(0.05), blurRadius: 20, spreadRadius: 5)
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('WALLET BALANCE', style: TextStyle(color: Colors.grey, fontSize: 12, letterSpacing: 1.5)),
                const SizedBox(height: 10),
                Text(
                  '${user.walletBalance.toStringAsFixed(2)} ETB',
                  style: const TextStyle(fontSize: 36, fontWeight: FontWeight.bold, color: Colors.white),
                ),
                const SizedBox(height: 8),
                const Text('Simulated payment processor active', style: TextStyle(color: AppTheme.cyberCyan, fontSize: 11)),
              ],
            ),
          ),
          const SizedBox(height: 30),
          const Text(
            'QUICK TOPUP (SIMULATED CHAPA)',
            style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 14),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(child: _buildTopUpButton(50)),
              const SizedBox(width: 10),
              Expanded(child: _buildTopUpButton(100)),
              const SizedBox(width: 10),
              Expanded(child: _buildTopUpButton(250)),
            ],
          ),
          const SizedBox(height: 40),
          const Text(
            'PRICING POLICY',
            style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 14),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppTheme.darkCard,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Column(
              children: [
                _buildPolicyRow('Unlock Base Fare', '5.00 ETB'),
                Divider(color: Colors.grey),
                _buildPolicyRow('Time Fare (per minute)', '0.50 ETB'),
                Divider(color: Colors.grey),
                _buildPolicyRow('Distance Fare (per km)', '2.00 ETB'),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTopUpButton(double amount) {
    return ElevatedButton(
      onPressed: () => _triggerTopUp(amount),
      style: ElevatedButton.styleFrom(
        backgroundColor: AppTheme.darkCard,
        foregroundColor: AppTheme.neonGreen,
        side: BorderSide(color: AppTheme.neonGreen.withOpacity(0.5)),
        padding: const EdgeInsets.symmetric(vertical: 16),
      ),
      child: Text('+$amount ETB', style: const TextStyle(fontWeight: FontWeight.bold)),
    );
  }

  static Widget _buildPolicyRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
        ],
      ),
    );
  }

  Widget _buildAccountTab(dynamic user) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: AppTheme.darkCard, borderRadius: BorderRadius.circular(16)),
            child: Row(
              children: [
                CircleAvatar(
                  backgroundColor: AppTheme.neonGreen.withOpacity(0.2),
                  radius: 30,
                  child: Text(
                    user.firstName[0].toUpperCase(),
                    style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: AppTheme.neonGreen),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${user.firstName} ${user.lastName}',
                        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                      ),
                      Text(user.email, style: const TextStyle(color: Colors.grey)),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 30),
          const Text('SYSTEM ALERTS HISTORY', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 14)),
          const SizedBox(height: 10),
          _isLoadingAlerts
              ? const Center(child: CircularProgressIndicator())
              : _alerts.isEmpty
                  ? const Card(
                      child: Padding(
                        padding: EdgeInsets.all(16.0),
                        child: Center(child: Text('No system alerts or theft warnings.')),
                      ),
                    )
                  : ListView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: _alerts.length > 5 ? 5 : _alerts.length,
                      itemBuilder: (context, index) {
                        final alert = _alerts[index];
                        final isTheft = alert.type == 'THEFT_ALERT';
                        return Card(
                          margin: const EdgeInsets.only(bottom: 10),
                          color: AppTheme.darkCard,
                          child: ListTile(
                            leading: Icon(
                              isTheft ? Icons.warning_amber_rounded : Icons.info_outline,
                              color: isTheft ? Colors.red : AppTheme.cyberCyan,
                            ),
                            title: Text(alert.title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                            subtitle: Text(alert.message, style: const TextStyle(fontSize: 11, color: Colors.grey)),
                            trailing: Text(
                              DateFormat('hh:mm a').format(alert.createdAt),
                              style: const TextStyle(fontSize: 10, color: Colors.grey),
                            ),
                          ),
                        );
                      },
                    ),
          const SizedBox(height: 40),
          ElevatedButton(
            onPressed: () {
              ref.read(authStateProvider.notifier).logout();
              context.go('/login');
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.transparent,
              foregroundColor: Colors.red,
              shadowColor: Colors.transparent,
              side: const BorderSide(color: Colors.red),
            ),
            child: const Text('LOG OUT'),
          ),
        ],
      ),
    );
  }
}
