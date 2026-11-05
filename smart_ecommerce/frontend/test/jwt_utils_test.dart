import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/services/jwt_utils.dart';

String sampleJwt(int expiresAtEpochSeconds) {
  final payload = base64Url.encode(
    utf8.encode(jsonEncode({'exp': expiresAtEpochSeconds})),
  );
  return 'header.$payload.signature';
}

void main() {
  test('rejects malformed JWT instead of restoring an invalid session', () {
    expect(JwtUtils.isExpired('invalid'), isTrue);
    expect(JwtUtils.isExpired('a.b.c'), isTrue);
  });

  test('rejects expired JWT and keeps a still-valid JWT', () {
    final now = DateTime.now().toUtc().millisecondsSinceEpoch ~/ 1000;
    expect(JwtUtils.isExpired(sampleJwt(now - 3600)), isTrue);
    expect(JwtUtils.isExpired(sampleJwt(now + 3600)), isFalse);
  });
}
