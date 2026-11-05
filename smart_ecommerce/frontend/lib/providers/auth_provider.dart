import 'package:flutter/foundation.dart' show ChangeNotifier;

import '../core/network/api_exception.dart';
import '../models/auth_user.dart';
import '../repositories/auth_repository.dart';

enum AuthStatus { initializing, unauthenticated, authenticating, authenticated, error }

class AuthProvider extends ChangeNotifier {
  AuthProvider(this._repository);

  final AuthRepository _repository;
  AuthStatus _status = AuthStatus.initializing;
  AuthUser? _user;
  String? _errorMessage;

  AuthStatus get status => _status;
  AuthUser? get user => _user;
  String? get errorMessage => _errorMessage;
  bool get isAuthenticated => _status == AuthStatus.authenticated && _user != null;
  bool get isBusy => _status == AuthStatus.authenticating;
  bool get isCustomer => isAuthenticated && (_user?.roles.contains('CUSTOMER') ?? false);

  Future<void> initialize() async {
    try {
      // Resume only when the token has not expired AND /api/users/me accepts it.
      // This also fetches freshly approved/revoked SELLER privileges.
      final session = await _repository.restoreSession();
      _user = session?.user;
      _status = session == null ? AuthStatus.unauthenticated : AuthStatus.authenticated;
      _errorMessage = null;
    } catch (_) {
      // Offline/server error: never treat unverified saved credentials as signed in.
      _user = null;
      _status = AuthStatus.unauthenticated;
      // Do not show a frightening login error before the user has submitted a form.
      // Verification failure still leaves the user unauthenticated.
      _errorMessage = null;
    }
    notifyListeners();
  }

  Future<bool> refreshCurrentUser() async {
    if (!isAuthenticated) return false;
    try {
      _user = await _repository.refreshCurrentUser();
      _status = AuthStatus.authenticated;
      _errorMessage = null;
      notifyListeners();
      return true;
    } catch (_) {
      // A transient error does not erase a previously verified session.
      // An HTTP 401 independently triggers the API client's logout callback.
      _errorMessage = 'Không thể làm mới quyền tài khoản. Kiểm tra kết nối mạng.';
      notifyListeners();
      return false;
    }
  }

  Future<bool> login({required String email, required String password}) async {
    _status = AuthStatus.authenticating;
    _errorMessage = null;
    notifyListeners();
    try {
      final session = await _repository.login(email: email, password: password);
      _user = session.user;
      _status = AuthStatus.authenticated;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _status = AuthStatus.error;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'Đã xảy ra lỗi không mong muốn.';
      _status = AuthStatus.error;
      notifyListeners();
      return false;
    }
  }

  Future<bool> loginWithGoogle({required String idToken}) async {
    _status = AuthStatus.authenticating;
    _errorMessage = null;
    notifyListeners();
    try {
      final session = await _repository.loginWithGoogle(idToken);
      _user = session.user;
      _status = AuthStatus.authenticated;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _status = AuthStatus.error;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'Không thể kết nối PPee để đăng nhập Google.';
      _status = AuthStatus.error;
      notifyListeners();
      return false;
    }
  }

  Future<bool> isGoogleLinked() => _repository.googleLinkStatus();

  Future<void> linkGoogle(String idToken) => _repository.linkGoogle(idToken);

  void showGoogleError(String message) {
    _errorMessage = message;
    _status = _user == null ? AuthStatus.error : AuthStatus.authenticated;
    notifyListeners();
  }

  Future<bool> register({required String fullName, required String email, required String password}) async {
    _status = AuthStatus.authenticating;
    _errorMessage = null;
    notifyListeners();
    try {
      await _repository.register(fullName: fullName, email: email, password: password);
      _status = AuthStatus.unauthenticated;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _status = AuthStatus.error;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'Đã xảy ra lỗi không mong muốn.';
      _status = AuthStatus.error;
      notifyListeners();
      return false;
    }
  }

  Future<void> logout() async {
    await _repository.logout();
    _user = null;
    _errorMessage = null;
    _status = AuthStatus.unauthenticated;
    notifyListeners();
  }

  Future<void> handleUnauthorized() async {
    if (_status == AuthStatus.unauthenticated) return;
    await logout();
  }

  void clearError() {
    _errorMessage = null;
    if (_status == AuthStatus.error) {
      _status = _user == null ? AuthStatus.unauthenticated : AuthStatus.authenticated;
    }
    notifyListeners();
  }
}
