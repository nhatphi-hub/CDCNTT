class ApiException implements Exception {
  const ApiException({required this.message, this.statusCode, this.path});

  final String message;
  final int? statusCode;
  final String? path;

  bool get isUnauthorized => statusCode == 401;

  @override
  String toString() => message;
}
