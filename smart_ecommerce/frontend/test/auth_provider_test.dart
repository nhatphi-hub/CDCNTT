import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/models/auth_session.dart';
import 'package:frontend/models/auth_user.dart';
import 'package:frontend/providers/auth_provider.dart';
import 'package:frontend/repositories/auth_repository.dart';

class _FakeAuthRepository extends Fake implements AuthRepository {
  _FakeAuthRepository(this.session);
  AuthSession? session;

  @override
  Future<AuthSession?> restoreSession() async => session;

  @override
  Future<AuthUser> refreshCurrentUser() async => session!.user;
}

const _customer = AuthUser(
  id: 'customer-1',
  fullName: 'Khách demo',
  email: 'customer@example.test',
  roles: {'CUSTOMER'},
);

void main() {
  test('no saved session means unauthenticated', () async {
    final provider = AuthProvider(_FakeAuthRepository(null));
    addTearDown(provider.dispose);
    await provider.initialize();
    expect(provider.status, AuthStatus.unauthenticated);
    expect(provider.isAuthenticated, isFalse);
  });

  test('verified session survives a Flutter Web reload', () async {
    final repo = _FakeAuthRepository(const AuthSession(
      accessToken: 'fixture-token',
      tokenType: 'Bearer',
      user: _customer,
    ));
    final provider = AuthProvider(repo);
    addTearDown(provider.dispose);
    await provider.initialize();
    expect(provider.status, AuthStatus.authenticated);
    expect(provider.isCustomer, isTrue);

    repo.session = const AuthSession(
      accessToken: 'fixture-token',
      tokenType: 'Bearer',
      user: AuthUser(
        id: 'customer-1',
        fullName: 'Khách demo',
        email: 'customer@example.test',
        roles: {'CUSTOMER', 'SELLER'},
      ),
    );
    expect(await provider.refreshCurrentUser(), isTrue);
    expect(provider.user!.roles.contains('SELLER'), isTrue);
  });
}
