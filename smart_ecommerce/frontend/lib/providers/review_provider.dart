import 'package:flutter/foundation.dart';

import '../core/network/api_exception.dart';
import '../models/review.dart';
import '../repositories/review_repository.dart';

class ReviewProvider extends ChangeNotifier {
  ReviewProvider(this._repository);
  final ReviewRepository _repository;

  final Map<String, List<Review>> _productReviews = {};
  List<Review> myReviews = const [];
  final Set<String> _loadingProducts = {};
  final Map<String, ReviewEligibility> _eligibilities = {};
  final Set<String> _checkingEligibility = {};
  int _userGeneration = 0;

  ReviewEligibility? eligibilityFor(String productId) => _eligibilities[productId];
  bool checkingEligibility(String productId) => _checkingEligibility.contains(productId);
  bool myReviewsLoading = false;
  bool mutating = false;
  String? error;
  String? _userId;

  List<Review> forProduct(String productId) => _productReviews[productId] ?? const [];
  bool isLoadingProduct(String productId) => _loadingProducts.contains(productId);

  void syncUser(String? userId) {
    if (_userId == userId) return;
    _userId = userId;
    _userGeneration++;
    _eligibilities.clear();
    _checkingEligibility.clear();
    myReviews = const [];
    myReviewsLoading = false;
    mutating = false;
    error = null;
    notifyListeners();
  }

  Future<void> loadProduct(String productId, {bool force = false}) async {
    if (!force && _productReviews.containsKey(productId)) return;
    if (_loadingProducts.contains(productId)) return;
    _loadingProducts.add(productId);
    error = null;
    notifyListeners();
    try {
      _productReviews[productId] = await _repository.byProduct(productId);
    } on ApiException catch (e) {
      error = e.message;
    } finally {
      _loadingProducts.remove(productId);
      notifyListeners();
    }
  }

  Future<ReviewEligibility?> loadEligibility(String productId, {bool force = false}) async {
    if (_userId == null) return null;
    if (!force && _eligibilities.containsKey(productId)) return _eligibilities[productId];
    if (_checkingEligibility.contains(productId)) return null;
    final generation = _userGeneration;
    _checkingEligibility.add(productId);
    notifyListeners();
    try {
      final result = await _repository.eligibility(productId);
      if (generation != _userGeneration) return null;
      _eligibilities[productId] = result;
      return result;
    } on ApiException catch (e) {
      if (generation == _userGeneration) error = e.message;
      return null;
    } finally {
      if (generation == _userGeneration) {
        _checkingEligibility.remove(productId);
        notifyListeners();
      }
    }
  }

  Future<void> loadMine() async {
    final generation = _userGeneration;
    myReviewsLoading = true;
    error = null;
    notifyListeners();
    try {
      final result = await _repository.mine();
      if (generation == _userGeneration) myReviews = result;
    } on ApiException catch (e) {
      if (generation == _userGeneration) error = e.message;
    } finally {
      if (generation == _userGeneration) {
        myReviewsLoading = false;
        notifyListeners();
      }
    }
  }

  Future<bool> create(String productId, int rating, String? comment) async {
    if (mutating) return false;
    final generation = _userGeneration;
    mutating = true;
    error = null;
    notifyListeners();
    try {
      await _repository.create(productId, rating: rating, comment: _normalize(comment));
      if (generation != _userGeneration) return false;
      await loadProduct(productId, force: true);
      await loadEligibility(productId, force: true);
      await loadMine();
      return true;
    } on ApiException catch (e) {
      if (generation == _userGeneration) error = e.message;
      return false;
    } finally {
      if (generation == _userGeneration) {
        mutating = false;
        notifyListeners();
      }
    }
  }

  Future<bool> update(Review review, int rating, String? comment) async {
    if (mutating) return false;
    final generation = _userGeneration;
    mutating = true;
    error = null;
    notifyListeners();
    try {
      await _repository.update(review.id, rating: rating, comment: _normalize(comment));
      if (generation != _userGeneration) return false;
      await loadProduct(review.productId, force: true);
      await loadEligibility(review.productId, force: true);
      await loadMine();
      return true;
    } on ApiException catch (e) {
      if (generation == _userGeneration) error = e.message;
      return false;
    } finally {
      if (generation == _userGeneration) {
        mutating = false;
        notifyListeners();
      }
    }
  }

  Future<bool> remove(Review review) async {
    if (mutating) return false;
    final generation = _userGeneration;
    mutating = true;
    error = null;
    notifyListeners();
    try {
      await _repository.delete(review.id);
      if (generation != _userGeneration) return false;
      await loadProduct(review.productId, force: true);
      await loadEligibility(review.productId, force: true);
      await loadMine();
      return true;
    } on ApiException catch (e) {
      if (generation == _userGeneration) error = e.message;
      return false;
    } finally {
      if (generation == _userGeneration) {
        mutating = false;
        notifyListeners();
      }
    }
  }

  String? _normalize(String? value) {
    final text = value?.trim();
    return text == null || text.isEmpty ? null : text;
  }
}
