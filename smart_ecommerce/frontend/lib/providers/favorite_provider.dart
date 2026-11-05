import 'package:flutter/foundation.dart';

import '../core/network/api_exception.dart';
import '../models/favorite.dart';
import '../repositories/favorite_repository.dart';

class FavoriteProvider extends ChangeNotifier {
  FavoriteProvider(this._repository);
  final FavoriteRepository _repository;

  final Map<String, bool> _states = {};
  final Set<String> _loading = {};
  List<FavoriteModel> favorites = const [];
  bool listLoading = false;
  String? error;
  String? _userId;

  bool isFavorite(String productId) => _states[productId] == true;
  bool isLoading(String productId) => _loading.contains(productId);

  void syncUser(String? userId) {
    if (_userId == userId) return;
    _userId = userId;
    _states.clear();
    _loading.clear();
    favorites = const [];
    error = null;
    notifyListeners();
  }

  Future<void> loadAll() async {
    listLoading = true;
    error = null;
    notifyListeners();
    try {
      favorites = await _repository.all();
      for (final favorite in favorites) {
        _states[favorite.productId] = true;
      }
    } on ApiException catch (e) {
      error = e.message;
    } finally {
      listLoading = false;
      notifyListeners();
    }
  }

  Future<void> ensure(String productId) async {
    if (_states.containsKey(productId) || _loading.contains(productId)) return;
    _loading.add(productId);
    notifyListeners();
    try {
      _states[productId] = await _repository.check(productId);
    } on ApiException catch (e) {
      error = e.message;
    } finally {
      _loading.remove(productId);
      notifyListeners();
    }
  }

  Future<bool> toggle(String productId) async {
    if (_loading.contains(productId)) return false;
    _loading.add(productId);
    error = null;
    notifyListeners();
    final current = isFavorite(productId);
    try {
      if (current) {
        await _repository.remove(productId);
        favorites = favorites.where((item) => item.productId != productId).toList();
      } else {
        await _repository.add(productId);
      }
      _states[productId] = !current;
      if (!current) await loadAll();
      return true;
    } on ApiException catch (e) {
      error = e.message;
      return false;
    } finally {
      _loading.remove(productId);
      notifyListeners();
    }
  }
}
