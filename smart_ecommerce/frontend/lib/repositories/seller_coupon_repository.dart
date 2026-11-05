import '../core/network/api_client.dart';

class SellerCouponRepository {
  SellerCouponRepository(this._api);
  final ApiClient _api;
  Future<List<Map<String, dynamic>>> mine() async =>
    (await _api.get('/api/seller/coupons') as List)
      .map((v) => Map<String, dynamic>.from(v as Map)).toList();
  Future<Map<String,dynamic>> create(Map<String,dynamic> body) async =>
    Map<String,dynamic>.from(await _api.post('/api/seller/coupons',body:body) as Map);
  Future<Map<String,dynamic>> update(String id,Map<String,dynamic> body) async =>
    Map<String,dynamic>.from(await _api.put('/api/seller/coupons/$id',body:body) as Map);
  Future<void> changeActive(String id,bool active) async {
    await _api.patch('/api/seller/coupons/$id/active',body:{'active':active});
  }
}
