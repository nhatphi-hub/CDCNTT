import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/api_config.dart';
import 'api_exception.dart';

class ApiClient {
  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;
  String? _accessToken;
  String _tokenType = 'Bearer';
  void Function()? onUnauthorized;

  void setAuthorization({required String token, String tokenType = 'Bearer'}) {
    _accessToken = token;
    _tokenType = tokenType;
  }

  void clearAuthorization() => _accessToken = null;

  Map<String, String> _headers({
    bool authorized = true,
    Map<String, String>? extra,
  }) {
    final headers = <String, String>{
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    };
    if (authorized && _accessToken != null && _accessToken!.isNotEmpty) {
      headers['Authorization'] = '$_tokenType $_accessToken';
    }
    if (extra != null) headers.addAll(extra);
    return headers;
  }

  Uri _uri(String path, [Map<String, dynamic>? query]) {
    final uri = Uri.parse('${ApiConfig.baseUrl}$path');
    if (query == null || query.isEmpty) return uri;
    return uri.replace(
      queryParameters: query.map(
        (key, value) => MapEntry(key, value.toString()),
      ),
    );
  }

  Future<dynamic> get(
    String path, {
    Map<String, dynamic>? query,
    bool authorized = true,
    Map<String, String>? headers,
  }) =>
      _send(
        () => _client.get(
          _uri(path, query),
          headers: _headers(authorized: authorized, extra: headers),
        ),
      );

  Future<dynamic> post(
    String path, {
    Object? body,
    bool authorized = true,
    Map<String, String>? headers,
  }) =>
      _send(
        () => _client.post(
          _uri(path),
          headers: _headers(authorized: authorized, extra: headers),
          body: body == null ? null : jsonEncode(body),
        ),
        notifyUnauthorized: authorized,
      );

  Future<dynamic> put(
    String path, {
    Object? body,
    bool authorized = true,
    Map<String, String>? headers,
  }) =>
      _send(
        () => _client.put(
          _uri(path),
          headers: _headers(authorized: authorized, extra: headers),
          body: body == null ? null : jsonEncode(body),
        ),
      );

  Future<dynamic> patch(
    String path, {
    Object? body,
    bool authorized = true,
    Map<String, String>? headers,
  }) =>
      _send(
        () => _client.patch(
          _uri(path),
          headers: _headers(authorized: authorized, extra: headers),
          body: body == null ? null : jsonEncode(body),
        ),
      );

  Future<dynamic> delete(
    String path, {
    Object? body,
    bool authorized = true,
    Map<String, String>? headers,
  }) =>
      _send(
        () => _client.delete(
          _uri(path),
          headers: _headers(authorized: authorized, extra: headers),
          body: body == null ? null : jsonEncode(body),
        ),
      );

  Future<dynamic> multipart(
    String method,
    String path, {
    Map<String, String>? fields,
    List<http.MultipartFile>? files,
  }) async {
    try {
      final request = http.MultipartRequest(method, _uri(path));
      request.headers['Accept'] = 'application/json';
      if (_accessToken != null && _accessToken!.isNotEmpty) {
        request.headers['Authorization'] = '$_tokenType $_accessToken';
      }
      if (fields != null) request.fields.addAll(fields);
      if (files != null) request.files.addAll(files);
      final streamed = await _client.send(request).timeout(ApiConfig.requestTimeout);
      final response = await http.Response.fromStream(streamed);
      return _handleResponse(response);
    } on TimeoutException {
      throw const ApiException(message: 'Kết nối quá lâu. Vui lòng thử lại.');
    } on http.ClientException {
      throw const ApiException(message: 'Không thể kết nối đến máy chủ.');
    }
  }

  Future<dynamic> _send(Future<http.Response> Function() request, {bool notifyUnauthorized = true}) async {
    try {
      final response = await request().timeout(ApiConfig.requestTimeout);
      return _handleResponse(response, notifyUnauthorized: notifyUnauthorized);
    } on TimeoutException {
      throw const ApiException(message: 'Kết nối quá lâu. Vui lòng thử lại.');
    } on http.ClientException {
      throw const ApiException(message: 'Không thể kết nối đến máy chủ.');
    }
  }

  dynamic _handleResponse(http.Response response, {bool notifyUnauthorized = true}) {
    dynamic decoded;
    if (response.body.isNotEmpty) {
      try {
        decoded = jsonDecode(utf8.decode(response.bodyBytes));
      } on FormatException {
        decoded = null;
      }
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return decoded;
    }

    final message = decoded is Map<String, dynamic>
        ? decoded['message']?.toString()
        : null;
    final path = decoded is Map<String, dynamic>
        ? decoded['path']?.toString()
        : null;
    final exception = ApiException(
      statusCode: response.statusCode,
      path: path,
      message: message ?? _fallbackMessage(response.statusCode),
    );
    if (response.statusCode == 401 && notifyUnauthorized) onUnauthorized?.call();
    throw exception;
  }

  String _fallbackMessage(int code) => switch (code) {
        400 => 'Yêu cầu không hợp lệ.',
        401 => 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.',
        403 => 'Bạn không có quyền thực hiện thao tác này.',
        404 => 'Không tìm thấy dữ liệu.',
        409 => 'Dữ liệu đã tồn tại hoặc đang xung đột.',
        >= 500 => 'Máy chủ đang gặp lỗi. Vui lòng thử lại sau.',
        _ => 'Yêu cầu thất bại ($code).',
      };

  void close() => _client.close();
}
