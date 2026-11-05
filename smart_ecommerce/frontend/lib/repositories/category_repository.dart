import 'dart:typed_data';
import 'package:http/http.dart' as http;
import '../core/config/api_config.dart';
import '../core/network/api_client.dart';
import '../models/category.dart';

class CategoryRepository {
  CategoryRepository(this._apiClient);
  final ApiClient _apiClient;

  /// ADMIN-only endpoint. Backend stores the row in the existing categories table.
  Future<Category> create({
    required String name,
    required String slug,
    String? description,
    String? parentId,
    bool isActive = true,
    String? image,
  }) async {
    final raw = await _apiClient.post(ApiConfig.categories, body: {
      'name': name.trim(),
      'slug': slug.trim(),
      'description': (description?.trim().isEmpty ?? true) ? null : description!.trim(),
      'parentId': parentId,
      'isActive': isActive,
      'image': image,
    });
    return Category.fromJson(Map<String, dynamic>.from(raw as Map));
  }

  /// ADMIN only. Updates existing row including its cover/icon metadata.
  Future<Category> update(String id, {
    required String name,
    required String slug,
    String? description,
    String? parentId,
    required bool isActive,
    String? image,
  }) async {
    final data = await _apiClient.put(ApiConfig.category(id), body: {
      'name': name.trim(),
      'slug': slug.trim(),
      'description': description?.trim(),
      'parentId': parentId,
      'isActive': isActive,
      'image': image,
    });
    return Category.fromJson(Map<String, dynamic>.from(data as Map));
  }

  /// Uploads JPEG/PNG/WebP to the already secured PPee server; the returned
  /// relative URL can be saved in the existing categories.image column.
  Future<String> uploadImage(Uint8List bytes, String filename) async {
    final data = await _apiClient.multipart('POST',
      '/api/categories/media/upload',
      files: [http.MultipartFile.fromBytes('image', bytes, filename: filename)],
    );
    return (data as Map)['image']?.toString() ?? '';
  }

  Future<List<Category>> getAll() async {
    final data = await _apiClient.get(ApiConfig.categories, authorized: false);
    if (data is! List) return const [];
    return data.map((e) => Category.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<List<Category>> getRoots() async {
    final data = await _apiClient.get('${ApiConfig.categories}/root', authorized: false);
    if (data is! List) return const [];
    return data.map((e) => Category.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<Category> getById(String id) async {
    final data = await _apiClient.get(ApiConfig.category(id), authorized: false);
    return Category.fromJson(Map<String, dynamic>.from(data as Map));
  }

  Future<List<Category>> getChildren(String id) async {
    final data = await _apiClient.get(ApiConfig.categoryChildren(id), authorized: false);
    if (data is! List) return const [];
    return data.map((e) => Category.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }
}
