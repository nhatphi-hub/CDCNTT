import '../core/network/api_client.dart';

class SellerShopRepository {
  SellerShopRepository(this._api);
  final ApiClient _api;

  Future<Map<String, dynamic>> apply(Map<String, dynamic> data) async =>
      Map<String, dynamic>.from(await _api.post('/api/seller/shop', body: data) as Map);

  Future<Map<String, dynamic>> mine() async =>
      Map<String, dynamic>.from(await _api.get('/api/seller/shop') as Map);

  Future<Map<String, dynamic>> update(Map<String, dynamic> data) async =>
      Map<String, dynamic>.from(await _api.put('/api/seller/shop', body: data) as Map);
}
