import '../core/network/api_client.dart';
class SellerActivityRepository {
  SellerActivityRepository(this.api); final ApiClient api;
  Future<List<Map<String,dynamic>>> mine() async =>
    (await api.get('/api/seller/notifications') as List)
       .map((x)=>Map<String,dynamic>.from(x as Map)).toList();
}
