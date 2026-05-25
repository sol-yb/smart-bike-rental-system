import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/network/dio_client.dart';
import '../../providers/auth_provider.dart';
import '../../../data/models/bike_model.dart';
import '../../../data/repositories/bike_repository.dart';

class AdminDashboardScreen extends ConsumerStatefulWidget {
  const AdminDashboardScreen({super.key});

  @override
  ConsumerState<AdminDashboardScreen> createState() => _AdminDashboardScreenState();
}

class _AdminDashboardScreenState extends ConsumerState<AdminDashboardScreen> {
  bool _isLoading = false;
  long _totalBikes = 0;
  long _activeRides = 0;
  long _totalUsers = 0;
  double _totalRevenue = 0.0;
  long _theftAlertsCount = 0;

  List<dynamic> _theftAlerts = [];
  List<BikeModel> _bikes = [];
  final MapController _adminMapController = MapController();

  @override
  void initState() {
    super.initState();
    _fetchAdminData();
  }

  Future<void> _fetchAdminData() async {
    setState(() => _isLoading = true);
    try {
      final dio = ref.read(dioProvider);
      
      // 1. Fetch Analytics
      final response = await dio.get('/admin/analytics');
      final data = response.data;
      
      // 2. Fetch Theft alerts
      final alertResponse = await dio.get('/admin/theft-alerts');
      final alertData = alertResponse.data as List;

      // 3. Fetch all bikes
      final bikeRepo = ref.read(bikeRepositoryProvider);
      final bikesList = await bikeRepo.getBikes();

      setState(() {
        _totalBikes = (data['totalBikes'] as num?)?.toInt() ?? 0;
        _activeRides = (data['activeRides'] as num?)?.toInt() ?? 0;
        _totalUsers = (data['totalUsers'] as num?)?.toInt() ?? 0;
        _totalRevenue = (data['totalRevenue'] as num?)?.toDouble() ?? 0.0;
        _theftAlertsCount = (data['theftAlertsCount'] as num?)?.toInt() ?? 0;
        
        _theftAlerts = alertData;
        _bikes = bikesList;
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to load admin stats: $e'), backgroundColor: Colors.red),
      );
    }
  }

  Future<void> _deleteBike(String bikeId) async {
    try {
      final repo = ref.read(bikeRepositoryProvider);
      await repo.deleteBike(bikeId);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Bike deleted successfully.')),
      );
      _fetchAdminData();
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to delete bike: $e'), backgroundColor: Colors.red),
      );
    }
  }

  Future<void> _addNewBike() async {
    final TextEditingController qrController = TextEditingController();
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          backgroundColor: AppTheme.darkCard,
          title: const Text('Add New Smart Bike', style: TextStyle(color: Colors.white)),
          content: TextField(
            controller: qrController,
            decoration: const InputDecoration(labelText: 'Bike QR Code ID (e.g. BIKE-002)'),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
            ElevatedButton(
              onPressed: () async {
                final qr = qrController.text.trim();
                if (qr.isNotEmpty) {
                  try {
                    final repo = ref.read(bikeRepositoryProvider);
                    // Add at default campus coordinates
                    await repo.addBike(qr, 9.0350, 38.7520);
                    Navigator.pop(context);
                    _fetchAdminData();
                  } catch (e) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Failed: $e'), backgroundColor: Colors.red),
                    );
                  }
                }
              },
              child: const Text('ADD'),
            )
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.darkBackground,
      appBar: AppBar(
        backgroundColor: AppTheme.darkBackground,
        title: const Text('ADMIN CONTROL CENTER', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 16)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: AppTheme.neonGreen),
            onPressed: _fetchAdminData,
          ),
          IconButton(
            icon: const Icon(Icons.logout, color: Colors.red),
            onPressed: () {
              ref.read(authStateProvider.notifier).logout();
              context.go('/login');
            },
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.neonGreen))
          : SingleChildScrollView(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // KPI Grid
                  GridView.count(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    crossAxisCount: 2,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.5,
                    children: [
                      _buildKpiCard('TOTAL BIKES', '$_totalBikes', Icons.pedal_bike_rounded, AppTheme.neonGreen),
                      _buildKpiCard('ACTIVE RIDES', '$_activeRides', Icons.navigation_outlined, AppTheme.cyberCyan),
                      _buildKpiCard('REVENUE', '${_totalRevenue.toStringAsFixed(1)} ETB', Icons.attach_money_rounded, Colors.greenAccent),
                      _buildKpiCard('THEFT ALARMS', '$_theftAlertsCount', Icons.warning_rounded, Colors.redAccent),
                    ],
                  ),
                  const SizedBox(height: 25),

                  // FLEET MAP OVERVIEW
                  const Text('FLEET REAL-TIME MAP', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 14)),
                  const SizedBox(height: 8),
                  Container(
                    height: 240,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: Colors.white.withOpacity(0.1)),
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: FlutterMap(
                        mapController: _adminMapController,
                        options: MapOptions(
                          initialCenter: const LatLng(9.035, 38.752),
                          initialZoom: 15.0,
                        ),
                        children: [
                          TileLayer(
                            urlTemplate: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                            subdomains: const ['a', 'b', 'c'],
                          ),
                          MarkerLayer(
                            markers: _bikes.map((bike) {
                              return Marker(
                                point: LatLng(bike.latitude, bike.longitude),
                                width: 34,
                                height: 34,
                                child: Container(
                                  decoration: BoxDecoration(
                                    color: bike.state == BikeState.AVAILABLE
                                        ? AppTheme.neonGreen.withOpacity(0.2)
                                        : Colors.red.withOpacity(0.2),
                                    shape: BoxShape.circle,
                                  ),
                                  child: Icon(
                                    Icons.pedal_bike_rounded,
                                    size: 16,
                                    color: bike.state == BikeState.AVAILABLE ? AppTheme.neonGreen : Colors.red,
                                  ),
                                ),
                              );
                            }).toList(),
                          )
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 25),

                  // BIKE LIST & CRUD
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('FLEET MANAGEMENT', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 14)),
                      ElevatedButton.icon(
                        onPressed: _addNewBike,
                        style: ElevatedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                          textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                        ),
                        icon: const Icon(Icons.add, size: 14),
                        label: const Text('NEW BIKE'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  ListView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: _bikes.length,
                    itemBuilder: (context, index) {
                      final bike = _bikes[index];
                      return Card(
                        color: AppTheme.darkCard,
                        child: ListTile(
                          leading: Icon(
                            Icons.pedal_bike,
                            color: bike.state == BikeState.AVAILABLE ? AppTheme.neonGreen : Colors.grey,
                          ),
                          title: Text(bike.qrCode, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
                          subtitle: Text('Battery: ${bike.batteryLevel}% | Status: ${bike.state.name}', style: const TextStyle(fontSize: 11)),
                          trailing: IconButton(
                            icon: const Icon(Icons.delete_outline, color: Colors.red),
                            onPressed: () => _deleteBike(bike.id),
                          ),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 25),

                  // THEFT WARNING ALERTS
                  const Text('RECENT SECURITY ALERTS', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 14)),
                  const SizedBox(height: 10),
                  _theftAlerts.isEmpty
                      ? const Card(
                          child: Padding(
                            padding: EdgeInsets.all(16.0),
                            child: Center(child: Text('No active security violations.')),
                          ),
                        )
                      : ListView.builder(
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          itemCount: _theftAlerts.length,
                          itemBuilder: (context, index) {
                            final alert = _theftAlerts[index];
                            return Card(
                              color: Colors.red.withOpacity(0.08),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: Colors.red.withOpacity(0.2)),
                              ),
                              child: ListTile(
                                leading: const Icon(Icons.error_outline_rounded, color: Colors.red),
                                title: Text(alert['title'] ?? 'Theft Alarm', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 13)),
                                subtitle: Text(alert['message'] ?? '', style: const TextStyle(color: Colors.grey, fontSize: 11)),
                              ),
                            );
                          },
                        ),
                ],
              ),
            ),
    );
  }

  Widget _buildKpiCard(String label, String value, IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.darkCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withOpacity(0.05)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(label, style: const TextStyle(fontSize: 10, color: Colors.grey, letterSpacing: 1)),
              Icon(icon, color: color, size: 16),
            ],
          ),
          Text(
            value,
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: color),
          ),
        ],
      ),
    );
  }
}
