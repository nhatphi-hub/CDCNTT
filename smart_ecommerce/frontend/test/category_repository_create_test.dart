import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/repositories/category_repository.dart';

void main() {
  test('ADMIN category creation posts to the existing database-backed endpoint', () async {
    var called = false;
    final client = MockClient((request) async {
      called = true;
      expect(request.method, 'POST');
      expect(request.url.path, '/api/categories');
      expect(request.headers['Authorization'], 'Bearer test-admin-jwt');
      final data = jsonDecode(request.body) as Map<String, dynamic>;
      expect(data['name'], 'Áo khoác');
      expect(data['slug'], 'ao-khoac');
      expect(data['isActive'], true);
      expect(data['parentId'], null);
      return http.Response(jsonEncode({
        'id': '33333333-3333-3333-3333-333333333333',
        'name': data['name'], 'slug': data['slug'],
        'description': data['description'],
        'isActive': data['isActive'], 'parentId': null,
      }), 201, headers: {'content-type': 'application/json'});
    });
    final api = ApiClient(client: client);
    api.setAuthorization(token: 'test-admin-jwt');
    final repo = CategoryRepository(api);
    final category = await repo.create(name: ' Áo khoác ', slug: 'ao-khoac');
    expect(called, true);
    expect(category.name, 'Áo khoác');
    expect(category.isActive, true);
  });
}
