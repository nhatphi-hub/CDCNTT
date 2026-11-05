import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:frontend/models/auth_session.dart';
import 'package:frontend/models/auth_user.dart';
import 'package:frontend/services/token_storage.dart';

void main() {
  test('token storage can save, restore and clear a valid session', () async {
    if (kIsWeb) return; // Web needs a real browser sessionStorage integration run.
    SharedPreferences.setMockInitialValues({});
    final storage = TokenStorage();
    const session = AuthSession(
      accessToken: 'test-jwt',
      tokenType: 'Bearer',
      user: AuthUser(
        id: 'test-user',
        fullName: 'PPee Test',
        email: 'test@example.test',
        roles: {'CUSTOMER'},
      ),
    );
    await storage.save(session);
    final restored = await TokenStorage().read();
    expect(restored?.accessToken, 'test-jwt');
    expect(restored?.user.id, 'test-user');
    await storage.clear();
    expect(await storage.read(), isNull);
  });
}
