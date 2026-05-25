import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/routes/app_routes.dart';
import 'core/theme/app_theme.dart';

void main() {
  runApp(
    const ProviderScope(
      child: SmartBikeApp(),
    ),
  );
}

class SmartBikeApp extends StatelessWidget {
  const SmartBikeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Smart Bike Rental',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark, // Default to dark neon cyberpunk theme
      darkTheme: AppTheme.darkTheme,
      theme: AppTheme.lightTheme,
      routerConfig: appRouter,
    );
  }
}
