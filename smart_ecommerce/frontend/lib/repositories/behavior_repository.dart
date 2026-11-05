import '../core/network/api_client.dart';
import '../models/product_view.dart'; import '../models/search_history.dart';
class BehaviorRepository { BehaviorRepository(this._api); final ApiClient _api;
 Future<void> recordProductView(String productId) async {await _api.post('/api/products/$productId/views',body:{'durationSeconds':0});}
 Future<List<ProductViewModel>> myViews() async {final d=await _api.get('/api/views/mine');return (d as List? ?? const []).whereType<Map>().map((e)=>ProductViewModel.fromJson(Map<String,dynamic>.from(e))).toList();}
 Future<void> clearViews()=>_api.delete('/api/views/mine').then((_){ });
 Future<void> recordSearch(String keyword) async {await _api.post('/api/search-history',body:{'keyword':keyword});}
 Future<List<SearchHistoryItem>> searches() async {final d=await _api.get('/api/search-history');return (d as List? ?? const []).whereType<Map>().map((e)=>SearchHistoryItem.fromJson(Map<String,dynamic>.from(e))).toList();}
 Future<void> deleteSearch(String id)=>_api.delete('/api/search-history/$id').then((_){ });
 Future<void> clearSearches()=>_api.delete('/api/search-history').then((_){ });
}
