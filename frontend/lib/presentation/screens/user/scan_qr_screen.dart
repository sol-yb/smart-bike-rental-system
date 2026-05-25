import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../../core/theme/app_theme.dart';
import '../../providers/ride_provider.dart';

class ScanQrScreen extends ConsumerStatefulWidget {
  const ScanQrScreen({super.key});

  @override
  ConsumerState<ScanQrScreen> createState() => _ScanQrScreenState();
}

class _ScanQrScreenState extends ConsumerState<ScanQrScreen> {
  final MobileScannerController _scannerController = MobileScannerController();
  bool _isProcessing = false;
  String _statusText = 'Align QR code inside the frame';

  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_isProcessing) return;

    final barcodes = capture.barcodes;
    if (barcodes.isNotEmpty && barcodes.first.rawValue != null) {
      final code = barcodes.first.rawValue!;
      debugPrint('QR Code Scanned: $code');

      setState(() {
        _isProcessing = true;
        _statusText = 'Validating code: $code';
      });

      try {
        // Freeze scanner
        _scannerController.stop();

        setState(() {
          _statusText = 'Unlocking Smart Lock...';
        });

        // Trigger start ride (sends booking API request, and issues MQTT unlock payload)
        await ref.read(rideStateProvider.notifier).startRide(code);

        if (mounted) {
          context.go('/ride');
        }
      } catch (e) {
        setState(() {
          _isProcessing = false;
          _statusText = 'Validation failed. Scanning resumed.';
        });
        
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to unlock bike: $e'),
            backgroundColor: AppTheme.hotPink,
          ),
        );

        // Resume scanner
        _scannerController.start();
      }
    }
  }

  @override
  void dispose() {
    _scannerController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        iconTheme: const IconThemeData(color: Colors.white),
        elevation: 0,
        title: const Text('SCAN QR CODE', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
      ),
      body: Stack(
        alignment: Alignment.center,
        children: [
          // Background Scanner View
          MobileScanner(
            controller: _scannerController,
            onDetect: _onDetect,
          ),
          
          // Outer overlay bounds dimming
          ColorFiltered(
            colorFilter: ColorFilter.mode(
              Colors.black.withOpacity(0.5),
              BlendMode.srcOut,
            ),
            child: Stack(
              children: [
                Container(
                  decoration: const BoxDecoration(
                    color: Colors.black,
                    backgroundBlendMode: BlendMode.dstOut,
                  ),
                ),
                Align(
                  alignment: Alignment.center,
                  child: Container(
                    width: 250,
                    height: 250,
                    decoration: BoxDecoration(
                      color: Colors.red,
                      borderRadius: BorderRadius.circular(20),
                    ),
                  ),
                ),
              ],
            ),
          ),
          
          // Pulsing Neon Border Frame
          Align(
            alignment: Alignment.center,
            child: Container(
              width: 250,
              height: 250,
              decoration: BoxDecoration(
                border: Border.all(color: AppTheme.neonGreen, width: 3),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: AppTheme.neonGreen.withOpacity(0.2),
                    blurRadius: 15,
                    spreadRadius: 2,
                  )
                ],
              ),
            ),
          ),
          
          // Laser scan line indicator
          const ScanLaserLine(),

          // Status & Progress Sheet
          Positioned(
            bottom: 60,
            left: 20,
            right: 20,
            child: Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppTheme.darkCard.withOpacity(0.9),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppTheme.cyberCyan.withOpacity(0.2)),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (_isProcessing)
                    const Padding(
                      padding: EdgeInsets.only(bottom: 12),
                      child: CircularProgressIndicator(color: AppTheme.neonGreen),
                    ),
                  Text(
                    _statusText,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Ensure the QR code is clear and well-lit.',
                    style: TextStyle(color: Colors.grey, fontSize: 12),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class ScanLaserLine extends StatefulWidget {
  const ScanLaserLine({super.key});

  @override
  State<ScanLaserLine> createState() => _ScanLaserLineState();
}

class _ScanLaserLineState extends State<ScanLaserLine> with SingleTickerProviderStateMixin {
  late AnimationController _animController;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);
    
    _animation = Tween<double>(begin: -110, end: 110).animate(_animController);
  }

  @override
  void dispose() {
    _animController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        return Transform.translate(
          offset: Offset(0, _animation.value),
          child: Container(
            width: 230,
            height: 2,
            decoration: BoxDecoration(
              color: AppTheme.neonGreen,
              boxShadow: [
                BoxShadow(
                  color: AppTheme.neonGreen.withOpacity(0.8),
                  blurRadius: 8,
                  spreadRadius: 1,
                )
              ],
            ),
          ),
        );
      },
    );
  }
}
