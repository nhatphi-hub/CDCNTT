import '../core/network/api_client.dart';
import '../models/recommendation.dart';
class RecommendationRepository {
  RecommendationRepository(this._api); final ApiClient _api;
  Future<List<RecommendationItem>> related(String productId,{int limit=12})=>_list('/api/recommendations/products/$productId/related',{'limit':limit},false);
  Future<List<RecommendationItem>> popular({int limit=12})=>_list('/api/recommendations/popular',{'limit':limit},false);
  Future<List<RecommendationItem>> personalized({int limit=12})=>_list('/api/recommendations/personalized',{'limit':limit},true);
  Future<List<RecommendationItem>> _list(String path,Map<String,dynamic> query,bool auth) async {final d=await _api.get(path,query:query,authorized:auth); return (d as List? ?? const []).whereType<Map>().map((e)=>RecommendationItem.fromJson(Map<String,dynamic>.from(e))).toList();}
  Future<void> track({
    required String recommendationType,
    required String productId,
    required String eventType,
    required bool authenticated,
    double? score,
    int? position,
    String? sessionId,
  }) async {
    await _api.post(
      '/api/recommendations/events',
      authorized: authenticated,
      body: {
        'recommendationType': recommendationType,
        'productId': productId,
        'eventType': eventType,
        'recommendationScore': ?score,
        'position': ?position,
        if (!authenticated && sessionId != null) 'sessionId': sessionId,
      },
    );
  }
}
