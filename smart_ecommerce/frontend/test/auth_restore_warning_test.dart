import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/models/auth_session.dart';
import 'package:frontend/providers/auth_provider.dart';
import 'package:frontend/repositories/auth_repository.dart';

class _UnavailableSessionRepository extends Fake implements AuthRepository {
  @override
  Future<AuthSession?> restoreSession() async => throw StateError('offline');
}

void main() {
  test('failed silent session verification does not show a login-form error', () async {
    final provider = AuthProvider(_UnavailableSessionRepository());
    addTearDown(provider.dispose);
    await provider.initialize();
    expect(provider.status, AuthStatus.unauthenticated);
    expect(provider.errorMessage, isNull);
  });
}
