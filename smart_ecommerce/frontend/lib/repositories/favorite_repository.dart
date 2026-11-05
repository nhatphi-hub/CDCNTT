import '../core/config/api_config.dart';
import '../core/network/api_client.dart';
import '../models/favorite.dart';

class FavoriteRepository {
  FavoriteRepository(this._apiClient);
  final ApiClient _apiClient;

  Future<List<FavoriteModel>> all() async {
    final data = await _apiClient.get(ApiConfig.favorites);
    return (data as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(FavoriteModel.fromJson)
        .toList();
  }

  Future<bool> check(String productId) async {
    final data = await _apiClient.get(ApiConfig.favoriteCheck(productId));
    final map = Map<String, dynamic>.from(data as Map);
    return map['favorite'] == true;
  }

  Future<void> add(String productId) async {
    await _apiClient.post(ApiConfig.favorite(productId));
  }

  Future<void> remove(String productId) async {
    await _apiClient.delete(ApiConfig.favorite(productId));
  }
}
