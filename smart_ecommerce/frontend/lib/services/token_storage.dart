import 'dart:convert';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:shared_preferences/shared_preferences.dart';

import '../core/constants/app_constants.dart';
import '../models/auth_session.dart';
import '../models/auth_user.dart';
import 'web_session_store_stub.dart'
    if (dart.library.js_interop) 'web_session_store_web.dart' as webSession;

/// Web: keep the JWT only for the current browser tab session, across F5/reload.
/// Closing the tab ends that session. Other platforms retain previous storage.
/// A valid saved JWT is ALWAYS revalidated by AuthRepository before login.
class TokenStorage {
  Future<void> save(AuthSession session) async {
    if (kIsWeb) {
      await _clearLegacyWebTokens();
      webSession.write(AppConstants.tokenKey, session.accessToken);
      webSession.write(AppConstants.tokenTypeKey, session.tokenType);
      webSession.write(AppConstants.userKey, jsonEncode(session.user.toJson()));
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(AppConstants.tokenKey, session.accessToken);
    await prefs.setString(AppConstants.tokenTypeKey, session.tokenType);
    await prefs.setString(AppConstants.userKey, jsonEncode(session.user.toJson()));
  }

  Future<AuthSession?> read() async {
    String? token, tokenType, userJson;
    if (kIsWeb) {
      // Drop the old localStorage JWT on upgrades; it must not outlive the tab.
      await _clearLegacyWebTokens();
      token = webSession.read(AppConstants.tokenKey);
      tokenType = webSession.read(AppConstants.tokenTypeKey);
      userJson = webSession.read(AppConstants.userKey);
    } else {
      final prefs = await SharedPreferences.getInstance();
      token = prefs.getString(AppConstants.tokenKey);
      tokenType = prefs.getString(AppConstants.tokenTypeKey);
      userJson = prefs.getString(AppConstants.userKey);
    }
    if (token == null || tokenType == null || userJson == null) return null;
    try {
      return AuthSession(
        accessToken: token,
        tokenType: tokenType,
        user: AuthUser.fromJson(jsonDecode(userJson) as Map<String, dynamic>),
      );
    } catch (_) {
      await clear();
      return null;
    }
  }

  Future<void> clear() async {
    if (kIsWeb) {
      webSession.remove(AppConstants.tokenKey);
      webSession.remove(AppConstants.tokenTypeKey);
      webSession.remove(AppConstants.userKey);
      await _clearLegacyWebTokens();
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(AppConstants.tokenKey);
    await prefs.remove(AppConstants.tokenTypeKey);
    await prefs.remove(AppConstants.userKey);
  }

  Future<void> _clearLegacyWebTokens() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(AppConstants.tokenKey);
      await prefs.remove(AppConstants.tokenTypeKey);
      await prefs.remove(AppConstants.userKey);
    } catch (_) {
      // Browsers can block persistent storage; do not block session login.
    }
  }
}
