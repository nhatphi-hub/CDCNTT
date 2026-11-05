import '../core/network/api_client.dart';
class ShopReturnRepository {
  ShopReturnRepository(this.api); final ApiClient api;
  Future<List<Map<String,dynamic>>> seller() async =>
    (await api.get('/api/seller/returns') as List).map((x)=>Map<String,dynamic>.from(x as Map)).toList();
  Future<List<Map<String,dynamic>>> customer() async =>
    (await api.get('/api/returns/mine') as List).map((x)=>Map<String,dynamic>.from(x as Map)).toList();
  Future<void> request(String orderId,String productId,String reason) async {
    await api.post('/api/orders/$orderId/returns',body:{'productId':productId,'reason':reason});
  }
  Future<void> update(String id,String status) async {
    await api.patch('/api/seller/returns/$id',body:{'status':status});
  }
}
