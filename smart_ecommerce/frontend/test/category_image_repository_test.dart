import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/repositories/category_repository.dart';

void main() {
  test('Admin updates a category image without changing its id', () async {
    final client = MockClient((request) async {
      expect(request.method, 'PUT');
      expect(request.url.path, '/api/categories/root-1');
      expect(request.headers['Authorization'], 'Bearer admin-token');
      final body = jsonDecode(request.body) as Map<String, dynamic>;
      expect(body['image'], 'icon:fashion');
      expect(body['name'], 'Thời trang nam');
      return http.Response(jsonEncode({
        'id': 'root-1', 'name': body['name'], 'slug': body['slug'],
        'isActive': true, 'parentId': null, 'image': body['image'],
      }), 200, headers: {'content-type':'application/json'});
    });
    final api = ApiClient(client: client)..setAuthorization(token: 'admin-token');
    final category = await CategoryRepository(api).update('root-1',
      name: 'Thời trang nam', slug: 'thoi-trang-nam',
      isActive: true, image: 'icon:fashion');
    expect(category.image, 'icon:fashion');
  });

  test('Uploading a category thumbnail sends multipart image to admin endpoint', () async {
    final client = MockClient((request) async {
      expect(request.method, 'POST');
      expect(request.url.path, '/api/categories/media/upload');
      expect(request.headers['Authorization'], 'Bearer admin-token');
      expect(request.headers['content-type']?.toLowerCase(), contains('multipart/form-data'));
      return http.Response(jsonEncode({'image':'/api/media/products/example.png'}),
        200, headers: {'content-type':'application/json'});
    });
    final api = ApiClient(client: client)..setAuthorization(token: 'admin-token');
    final result = await CategoryRepository(api).uploadImage(Uint8List.fromList([137,80,78,71]), 'icon.png');
    expect(result, '/api/media/products/example.png');
  });
}
