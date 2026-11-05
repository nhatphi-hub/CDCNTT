import '../core/config/api_config.dart';
import '../core/network/api_client.dart';
import '../models/review.dart';

class ReviewRepository {
  ReviewRepository(this._apiClient);
  final ApiClient _apiClient;

  Future<List<Review>> byProduct(String productId) async {
    final data = await _apiClient.get(ApiConfig.productReviews(productId), authorized: false);
    return (data as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(Review.fromJson)
        .toList();
  }

  Future<ReviewEligibility> eligibility(String productId) async {
    final data = await _apiClient.get(ApiConfig.reviewEligibility(productId));
    return ReviewEligibility.fromJson(Map<String, dynamic>.from(data as Map));
  }

  Future<List<Review>> mine() async {
    final data = await _apiClient.get(ApiConfig.myReviews);
    return (data as List? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(Review.fromJson)
        .toList();
  }

  Future<Review> create(String productId, {required int rating, String? comment}) async {
    final data = await _apiClient.post(
      ApiConfig.productReviews(productId),
      body: {'rating': rating, 'comment': comment},
    );
    return Review.fromJson(Map<String, dynamic>.from(data as Map));
  }

  Future<Review> update(String reviewId, {required int rating, String? comment}) async {
    final data = await _apiClient.put(
      ApiConfig.review(reviewId),
      body: {'rating': rating, 'comment': comment},
    );
    return Review.fromJson(Map<String, dynamic>.from(data as Map));
  }

  Future<void> delete(String reviewId) => _apiClient.delete(ApiConfig.review(reviewId));
}
