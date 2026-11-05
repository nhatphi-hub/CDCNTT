import '../core/network/api_client.dart';
import '../models/review.dart';

class SellerReviewRepository {
  SellerReviewRepository(this._api);
  final ApiClient _api;

  Future<void> reply(String reviewId,String message) async {
    await _api.put('/api/seller/reviews/$reviewId/reply',body:{'message':message});
  }

  Future<List<Review>> mine() async {
    final data = await _api.get('/api/seller/reviews');
    return (data as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(Review.fromJson)
        .toList();
  }
}
