import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/user_model.dart';
import '../../data/repositories/auth_repository.dart';
import '../../core/network/dio_client.dart';

final authStateProvider = StateNotifierProvider<AuthNotifier, AsyncValue<UserModel?>>((ref) {
  return AuthNotifier(ref.read(authRepositoryProvider), ref);
});

class AuthNotifier extends StateNotifier<AsyncValue<UserModel?>> {
  final AuthRepository _repository;
  final Ref _ref;

  AuthNotifier(this._repository, this._ref) : super(const AsyncValue.data(null));

  Future<void> login(String email, String password) async {
    state = const AsyncValue.loading();
    try {
      final user = await _repository.login(email, password);
      state = AsyncValue.data(user);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  Future<void> register(String email, String password, String firstName, String lastName) async {
    state = const AsyncValue.loading();
    try {
      final user = await _repository.register(email, password, firstName, lastName);
      state = AsyncValue.data(user);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  Future<void> topUp(double amount, String method) async {
    if (state.value == null) return;
    try {
      final user = await _repository.topUp(amount, method);
      state = AsyncValue.data(user);
    } catch (e) {
      rethrow;
    }
  }

  void logout() {
    _ref.read(tokenProvider.notifier).state = null;
    state = const AsyncValue.data(null);
  }

  void refreshUserBalance(double newBalance) {
    if (state.value != null) {
      final current = state.value!;
      state = AsyncValue.data(UserModel(
        id: current.id,
        email: current.email,
        firstName: current.firstName,
        lastName: current.lastName,
        role: current.role,
        walletBalance: newBalance,
      ));
    }
  }
}
