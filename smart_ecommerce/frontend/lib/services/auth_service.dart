import '../core/config/api_config.dart';
import '../core/network/api_client.dart';
import '../models/auth_session.dart';
import '../models/auth_user.dart';

class AuthService {
  AuthService(this._apiClient);
  final ApiClient _apiClient;

  Future<AuthSession> login({required String email, required String password}) async {
    final data = await _apiClient.post(
      ApiConfig.login,
      body: {'email': email.trim(), 'password': password},
      authorized: false,
    );
    return AuthSession.fromLoginJson(data as Map<String, dynamic>);
  }

  Future<AuthSession> loginWithGoogle(String idToken) async {
    final data = await _apiClient.post(
      ApiConfig.googleLogin,
      body: {'idToken': idToken},
      authorized: false,
    );
    return AuthSession.fromLoginJson(data as Map<String, dynamic>);
  }

  /// Refreshes roles and account state from the server (JWT is checked on every call).
  Future<AuthUser> currentUser() async {
    final json = await _apiClient.get(ApiConfig.currentUser);
    return AuthUser.fromJson(json as Map<String, dynamic>);
  }

  Future<bool> googleLinkStatus() async {
    final data = await _apiClient.get(ApiConfig.googleLink);
    return (data as Map<String, dynamic>)['linked'] == true;
  }

  Future<void> linkGoogle(String idToken) async {
    await _apiClient.post(ApiConfig.googleLink, body: {'idToken': idToken});
  }

  Future<AuthUser> register({
    required String fullName,
    required String email,
    required String password,
  }) async {
    final data = await _apiClient.post(
      ApiConfig.register,
      body: {
        'fullName': fullName.trim(),
        'email': email.trim(),
        'password': password,
      },
      authorized: false,
    );
    return AuthUser.fromJson(data as Map<String, dynamic>);
  }
}
