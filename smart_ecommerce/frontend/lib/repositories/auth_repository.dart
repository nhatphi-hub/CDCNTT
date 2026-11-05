import '../core/network/api_client.dart';
import '../core/network/api_exception.dart';
import '../models/auth_session.dart';
import '../models/auth_user.dart';
import '../services/auth_service.dart';
import '../services/jwt_utils.dart';
import '../services/token_storage.dart';
import '../services/google_sign_in_bridge.dart';

class AuthRepository {
  AuthRepository({
    required AuthService authService,
    required TokenStorage tokenStorage,
    required ApiClient apiClient,
  })  : _authService = authService,
        _tokenStorage = tokenStorage,
        _apiClient = apiClient;

  final AuthService _authService;
  final TokenStorage _tokenStorage;
  final ApiClient _apiClient;

  Future<AuthSession?> restoreSession() async {
    final session = await _tokenStorage.read();
    if (session == null) return null;
    if (JwtUtils.isExpired(session.accessToken)) {
      await logout();
      return null;
    }
    _apiClient.setAuthorization(token: session.accessToken, tokenType: session.tokenType);
    try {
      // A saved token alone does not prove the user is active or still has a role.
      final user = await _authService.currentUser();
      final refreshed = AuthSession(
        accessToken: session.accessToken,
        tokenType: session.tokenType,
        user: user,
      );
      await _tokenStorage.save(refreshed);
      return refreshed;
    } on ApiException catch (error) {
      if (error.statusCode == 401 || error.statusCode == 403) {
        await logout();
        return null;
      }
      _apiClient.clearAuthorization();
      rethrow;
    } catch (_) {
      _apiClient.clearAuthorization();
      rethrow;
    }
  }

  /// Refresh an already-authenticated user's roles without replacing the valid JWT
  /// when a transient network error occurs. A 401 is handled by ApiClient.
  Future<AuthUser> refreshCurrentUser() async {
    final user = await _authService.currentUser();
    final session = await _tokenStorage.read();
    if (session != null && !JwtUtils.isExpired(session.accessToken)) {
      await _tokenStorage.save(AuthSession(
        accessToken: session.accessToken,
        tokenType: session.tokenType,
        user: user,
      ));
    }
    return user;
  }

  Future<AuthSession> login({required String email, required String password}) async {
    final session = await _authService.login(email: email, password: password);
    _apiClient.setAuthorization(token: session.accessToken, tokenType: session.tokenType);
    await _tokenStorage.save(session);
    return session;
  }

  Future<AuthSession> loginWithGoogle(String idToken) async {
    final session = await _authService.loginWithGoogle(idToken);
    _apiClient.setAuthorization(token: session.accessToken, tokenType: session.tokenType);
    await _tokenStorage.save(session);
    return session;
  }

  Future<bool> googleLinkStatus() => _authService.googleLinkStatus();

  Future<void> linkGoogle(String idToken) => _authService.linkGoogle(idToken);

  Future<AuthUser> register({required String fullName, required String email, required String password}) {
    return _authService.register(fullName: fullName, email: email, password: password);
  }

  Future<void> logout() async {
    _apiClient.clearAuthorization();
    await _tokenStorage.clear();
    await GoogleSignInBridge.signOutIfInitialized();
  }
}
