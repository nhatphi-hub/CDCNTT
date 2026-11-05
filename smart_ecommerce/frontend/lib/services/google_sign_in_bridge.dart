import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:google_sign_in/google_sign_in.dart';

/// Initialize Google exactly once. Do not block PPee email/password sign-in on GIS loading.
class GoogleSignInBridge {
  GoogleSignInBridge._();

  static const String webClientId = '230900237413-4psssq06jtftg2g85lcab9d5smfr9sp4.apps.googleusercontent.com';
  static Future<void>? _initializing;

  static Future<void> ensureInitialized() {
    if (!kIsWeb) return Future<void>.value();
    return _initializing ??= GoogleSignIn.instance.initialize(clientId: webClientId);
  }

  static Future<void> signOutIfInitialized() async {
    if (!kIsWeb || _initializing == null) return;
    try {
      await _initializing;
      await GoogleSignIn.instance.signOut();
    } catch (_) {
      // PPee always clears its JWT, even when Google's SDK is unreachable.
    }
  }
}
