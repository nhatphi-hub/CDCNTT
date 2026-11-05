import 'auth_user.dart';

class AuthSession {
  const AuthSession({required this.accessToken, required this.tokenType, required this.user});

  final String accessToken;
  final String tokenType;
  final AuthUser user;

  factory AuthSession.fromLoginJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      user: AuthUser.fromLoginJson(json),
    );
  }
}
