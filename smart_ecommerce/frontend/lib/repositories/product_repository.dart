import '../core/config/api_config.dart';
import '../core/network/api_client.dart';
import '../models/product.dart';
import '../models/product_image.dart';
import '../models/review.dart';

class ProductRepository {
  ProductRepository(this._apiClient);
  final ApiClient _apiClient;

  Future<List<Product>> getProducts() async {
    final data = await _apiClient.get(ApiConfig.products, authorized: false);
    return _parseList(data);
  }

  /// Public aggregates; only completed and paid order items count as sales.
  Future<Map<String, int>> getSalesCounts() async {
    final data = await _apiClient.get('${ApiConfig.products}/sales-counts', authorized: false);
    if (data is! Map) return const {};
    return data.map((key, value) => MapEntry(
      key.toString(), value is num ? value.toInt() : int.tryParse('$value') ?? 0));
  }

  Future<Product> getProductById(String id) async {
    final data = await _apiClient.get(ApiConfig.product(id), authorized: false);
    return Product.fromJson(Map<String, dynamic>.from(data as Map));
  }

  Future<List<Product>> getByCategory(String categoryId) async {
    final data = await _apiClient.get(ApiConfig.productsByCategory(categoryId), authorized: false);
    return _parseList(data);
  }

  Future<List<Product>> search(String keyword, {int page = 0, int size = 50}) async {
    final data = await _apiClient.get(
      ApiConfig.productSearch,
      query: {'keyword': keyword, 'page': page, 'size': size},
      authorized: false,
    );
    final map = Map<String, dynamic>.from(data as Map);
    final content = map['content'];
    if (content is! List) return const [];
    return content.map((e) => Product.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<List<ProductImageModel>> getImages(String productId) async {
    final data = await _apiClient.get(ApiConfig.productImages(productId), authorized: false);
    if (data is! List) return const [];
    final images = data.map((e) => ProductImageModel.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    images.sort((a, b) {
      if (a.isPrimary != b.isPrimary) return a.isPrimary ? -1 : 1;
      return a.sortOrder.compareTo(b.sortOrder);
    });
    return images;
  }

  Future<ReviewSummary> getReviewSummary(String productId) async {
    final data = await _apiClient.get(ApiConfig.productReviews(productId), authorized: false);
    if (data is! List) return const ReviewSummary(average: 0, count: 0);
    final reviews = data.map((e) => Review.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    return ReviewSummary.fromReviews(reviews);
  }

  List<Product> _parseList(dynamic data) {
    if (data is! List) return const [];
    return data.map((e) => Product.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }
}
