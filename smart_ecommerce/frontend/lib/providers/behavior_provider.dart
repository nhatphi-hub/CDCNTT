import 'package:flutter/foundation.dart';

import '../core/network/api_exception.dart';
import '../models/product_view.dart';
import '../models/search_history.dart';
import '../repositories/behavior_repository.dart';

/// Customer-scoped activity; backend resolves the owner from JWT.
class BehaviorProvider extends ChangeNotifier {
  BehaviorProvider(this._repo);
  final BehaviorRepository _repo;

  List<ProductViewModel> views = const [];
  List<SearchHistoryItem> searchHistory = const [];
  bool loading = false;
  bool searchesLoading = false;
  String? error;
  String? searchesError;
  // Debug-only diagnostics for failed auxiliary tracking; shopping continues.
  String? lastTrackingError;
  String? _userId;
  int _revision = 0;
  int _searchRequest = 0;

  void syncUser(String? userId) {
    if (_userId == userId) return;
    _userId = userId;
    _revision++;
    _searchRequest++;
    views = const [];
    searchHistory = const [];
    loading = false;
    searchesLoading = false;
    error = null;
    searchesError = null;
    lastTrackingError = null;
    notifyListeners();
  }

  Future<void> load() async {
    if (_userId == null) return;
    final revision = _revision;
    final searchRequest = ++_searchRequest;
    loading = true;
    error = null;
    notifyListeners();
    try {
      final results = await Future.wait([_repo.myViews(), _repo.searches()]);
      if (revision != _revision) return;
      views = results[0] as List<ProductViewModel>;
      if (searchRequest == _searchRequest) {
        searchHistory = results[1] as List<SearchHistoryItem>;
        searchesError = null;
      }
    } on ApiException catch (e) {
      if (revision == _revision) error = e.message;
    } catch (_) {
      if (revision == _revision) error = 'Không thể tải lịch sử hoạt động.';
    } finally {
      if (revision == _revision) {
        loading = false;
        notifyListeners();
      }
    }
  }

  /// Refresh only search suggestions without reloading the viewed-products list.
  Future<void> loadSearches() async {
    if (_userId == null) return;
    final revision = _revision;
    final searchRequest = ++_searchRequest;
    searchesLoading = true;
    searchesError = null;
    notifyListeners();
    try {
      final history = await _repo.searches();
      if (revision != _revision || searchRequest != _searchRequest) return;
      searchHistory = history;
    } on ApiException catch (e) {
      if (revision == _revision && searchRequest == _searchRequest) searchesError = e.message;
    } catch (_) {
      if (revision == _revision && searchRequest == _searchRequest) searchesError = 'Không thể tải lịch sử tìm kiếm.';
    } finally {
      if (revision == _revision && searchRequest == _searchRequest) {
        searchesLoading = false;
        notifyListeners();
      }
    }
  }

  Future<void> recordView(String id) async {
    if (_userId == null) return;
    try {
      await _repo.recordProductView(id);
      lastTrackingError = null;
    } catch (e) {
      // Do not interrupt shopping, but expose diagnostic details in debug mode.
      lastTrackingError = 'Không ghi được lượt xem $id: $e';
      debugPrint(lastTrackingError);
    }
  }

  Future<void> recordSearch(String keyword) async {
    final value = keyword.trim();
    if (_userId == null || value.isEmpty) return;
    final revision = _revision;
    try {
      await _repo.recordSearch(value);
      if (revision == _revision) await loadSearches();
    } catch (e) {
      lastTrackingError = 'Không ghi được tìm kiếm: $e';
      debugPrint(lastTrackingError);
    }
  }

  Future<bool> deleteSearch(String id) async {
    if (_userId == null) return false;
    final revision = _revision;
    try {
      await _repo.deleteSearch(id);
      if (revision != _revision) return false;
      _searchRequest++;
      searchesLoading = false;
      searchHistory = searchHistory.where((entry) => entry.id != id).toList();
      searchesError = null;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      if (revision == _revision) searchesError = e.message;
    } catch (_) {
      if (revision == _revision) searchesError = 'Không thể xóa mục tìm kiếm.';
    }
    if (revision == _revision) notifyListeners();
    return false;
  }

  Future<bool> clearViews() async {
    if (_userId == null) return false;
    final revision = _revision;
    try {
      await _repo.clearViews();
      if (revision != _revision) return false;
      views = const [];
      error = null;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      if (revision == _revision) error = e.message;
    } catch (_) {
      if (revision == _revision) error = 'Không thể xóa sản phẩm đã xem.';
    }
    if (revision == _revision) notifyListeners();
    return false;
  }

  Future<bool> clearSearches() async {
    if (_userId == null) return false;
    final revision = _revision;
    try {
      await _repo.clearSearches();
      if (revision != _revision) return false;
      _searchRequest++;
      searchHistory = const [];
      searchesLoading = false;
      searchesError = null;
      error = null;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      if (revision == _revision) {
        searchesError = e.message;
        error = e.message;
      }
    } catch (_) {
      if (revision == _revision) {
        searchesError = 'Không thể xóa lịch sử tìm kiếm.';
        error = searchesError;
      }
    }
    if (revision == _revision) notifyListeners();
    return false;
  }
}
