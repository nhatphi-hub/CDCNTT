import 'package:flutter/foundation.dart';

import '../core/network/api_exception.dart';
import '../models/cart.dart';
import '../repositories/cart_repository.dart';

class CartProvider extends ChangeNotifier {
  CartProvider(this._repo);
  final CartRepository _repo;

  CartModel? cart;
  bool loading = false;
  String? error;
  String? _userId;
  // Null means the first cart load selects all items (Shopee-style checkout).
  Set<String>? _selectedIds;

  Set<String> get selectedIds => _selectedIds ?? const <String>{};
  List<CartItemModel> get selectedItems => cart?.items
      .where((item) => selectedIds.contains(item.id)).toList() ?? const [];
  double get selectedTotal => selectedItems.fold(0,
      (total, item) => total + item.subtotal);
  int get selectedQuantity => selectedItems.fold(0,
      (total, item) => total + item.quantity);
  bool get isAllSelected => cart != null && cart!.items.isNotEmpty
      && cart!.items.every((item) => selectedIds.contains(item.id));

  void setSelected(String id, bool value) {
    final valid = cart?.items.any((item) => item.id == id) ?? false;
    if (!valid) return;
    final updated = {...selectedIds};
    value ? updated.add(id) : updated.remove(id);
    _selectedIds = updated;
    notifyListeners();
  }

  void selectAll(bool selected) {
    _selectedIds = selected
      ? cart?.items.map((item) => item.id).toSet() ?? <String>{}
      : <String>{};
    notifyListeners();
  }

  void _syncSelection() {
    final current = cart?.items.map((item) => item.id).toSet() ?? <String>{};
    _selectedIds = _selectedIds == null ? current : _selectedIds!.intersection(current);
  }

  void syncUser(String? userId) {
    if (_userId == userId) return;
    _userId = userId;
    cart = null;
    _selectedIds = null;
    loading = false;
    error = null;
    notifyListeners();
  }

  Future<void> load() async {
    loading = true;
    error = null;
    notifyListeners();
    try {
      cart = await _repo.getCart();
      _syncSelection();
    } on ApiException catch (e) {
      error = e.message;
    } catch (_) {
      error = 'Không thể tải giỏ hàng.';
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<bool> add(String productId, int qty, {String? variantId}) =>
      _mutate(() => _repo.add(productId, qty, variantId: variantId));

  Future<bool> update(String itemId, int qty) =>
      _mutate(() => _repo.update(itemId, qty));

  Future<bool> remove(String itemId) => _mutate(() => _repo.remove(itemId));

  Future<bool> _mutate(Future<CartModel> Function() operation) async {
    if (loading) return false;
    loading = true;
    error = null;
    notifyListeners();
    try {
      cart = await operation();
      _syncSelection();
      return true;
    } on ApiException catch (e) {
      error = e.message;
      return false;
    } catch (_) {
      error = 'Không thể cập nhật giỏ hàng.';
      return false;
    } finally {
      loading = false;
      notifyListeners();
    }
  }
}
