import 'package:flutter/foundation.dart';

import '../core/network/api_exception.dart';
import '../models/product.dart';
import '../models/product_image.dart';
import '../models/review.dart';
import '../repositories/product_repository.dart';

enum LoadStatus { initial, loading, success, empty, error }

class ProductProvider extends ChangeNotifier {
  ProductProvider(this._repository);
  final ProductRepository _repository;

  LoadStatus status = LoadStatus.initial;
  List<Product> products = const [];
  String? errorMessage;
  Map<String, int> salesCounts = const {};
  final Map<String, List<ProductImageModel>> _images = {};
  final Map<String, ReviewSummary> _reviews = {};

  List<Product> get saleProducts => products.where((p) => p.isOnSale).toList();
  List<Product> get newProducts {
    final copy = [...products]..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return copy.take(8).toList();
  }

  List<ProductImageModel> imagesFor(String productId) => _images[productId] ?? const [];
  ReviewSummary? reviewFor(String productId) => _reviews[productId];

  Future<void> loadProducts({bool force = false}) async {
    if (!force && (status == LoadStatus.loading || status == LoadStatus.success)) return;
    status = LoadStatus.loading;
    if (force) _images.clear();
    errorMessage = null;
    notifyListeners();
    try {
      products = await _repository.getProducts();
      status = products.isEmpty ? LoadStatus.empty : LoadStatus.success;
      notifyListeners();
      try {
        salesCounts = await _repository.getSalesCounts();
      } catch (_) {
        salesCounts = const {}; // Sales endpoint missing: never invent sales figures.
      }
      notifyListeners();
      // The catalog now returns each product's primary image URL directly.
      // Keep the full image endpoint for detail pages and products without a thumbnail.
      await _loadImages(products.where((p) => p.imageUrl == null || p.imageUrl!.trim().isEmpty).toList());
    } on ApiException catch (e) {
      status = LoadStatus.error;
      errorMessage = e.message;
      notifyListeners();
    } catch (_) {
      status = LoadStatus.error;
      errorMessage = 'Không thể tải danh sách sản phẩm.';
      notifyListeners();
    }
  }

  /// Returns the current visible public catalog without touching provider state.
  Future<List<Product>> getCatalog() => _repository.getProducts();

  Future<List<Product>> search(String keyword) => _repository.search(keyword);
  Future<List<Product>> byCategory(String categoryId) => _repository.getByCategory(categoryId);
  Future<Product> getById(String id) => _repository.getProductById(id);

  Future<List<ProductImageModel>> ensureImages(String productId) async {
    if (_images.containsKey(productId)) return _images[productId]!;
    final images = await _repository.getImages(productId);
    _images[productId] = images;
    notifyListeners();
    return images;
  }

  Future<ReviewSummary> ensureReview(String productId) async {
    if (_reviews.containsKey(productId)) return _reviews[productId]!;
    final summary = await _repository.getReviewSummary(productId);
    _reviews[productId] = summary;
    notifyListeners();
    return summary;
  }

  Future<void> _loadImages(List<Product> items) async {
    await Future.wait(items.map((product) async {
      try {
        _images[product.id] = await _repository.getImages(product.id);
      } catch (_) {
        _images[product.id] = const [];
      }
    }));
    notifyListeners();
  }
}
