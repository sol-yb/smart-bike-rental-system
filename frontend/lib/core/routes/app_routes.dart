import 'package:go_router/go_router.dart';
import '../../presentation/screens/auth/splash_screen.dart';
import '../../presentation/screens/auth/login_screen.dart';
import '../../presentation/screens/auth/register_screen.dart';
import '../../presentation/screens/user/home_map_screen.dart';
import '../../presentation/screens/user/scan_qr_screen.dart';
import '../../presentation/screens/user/ride_tracking_screen.dart';
import '../../presentation/screens/admin/admin_dashboard_screen.dart';

final appRouter = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const SplashScreen(),
    ),
    GoRoute(
      path: '/login',
      builder: (context, state) => const LoginScreen(),
    ),
    GoRoute(
      path: '/register',
      builder: (context, state) => const RegisterScreen(),
    ),
    GoRoute(
      path: '/home',
      builder: (context, state) => const HomeMapScreen(),
    ),
    GoRoute(
      path: '/scan',
      builder: (context, state) => const ScanQrScreen(),
    ),
    GoRoute(
      path: '/ride',
      builder: (context, state) => const RideTrackingScreen(),
    ),
    GoRoute(
      path: '/admin',
      builder: (context, state) => const AdminDashboardScreen(),
    ),
  ],
);
