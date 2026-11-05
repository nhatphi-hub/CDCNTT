import 'package:flutter/foundation.dart';

import '../core/network/api_exception.dart';
import '../models/order.dart';
import '../repositories/order_repository.dart';

class OrderProvider extends ChangeNotifier {
  OrderProvider(this._repo);
  final OrderRepository _repo;

  List<OrderModel> orders = const [];
  bool loading = false;
  String? error;
  String? _userId;

  void syncUser(String? userId) {
    if (_userId == userId) return;
    _userId = userId;
    orders = const [];
    loading = false;
    error = null;
    notifyListeners();
  }

  Future<void> load() async {
    loading = true;
    error = null;
    notifyListeners();
    try {
      orders = await _repo.all();
    } on ApiException catch (e) {
      error = e.message;
    } catch (_) {
      error = 'Không thể tải đơn hàng.';
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<OrderModel?> create({
    required String addressId,
    required String paymentMethod,
    String? note,
    String? couponCode,
    List<String>? selectedCartItemIds,
  }) async {
    if (loading) return null;
    loading = true;
    error = null;
    notifyListeners();
    try {
      return await _repo.create(
        addressId: addressId,
        paymentMethod: paymentMethod,
        note: note,
        couponCode: couponCode,
        selectedCartItemIds: selectedCartItemIds,
      );
    } on ApiException catch (e) {
      error = e.message;
      return null;
    } catch (_) {
      error = 'Không thể tạo đơn hàng.';
      return null;
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<OrderModel> get(String id) => _repo.get(id);

  Future<bool> cancel(String id) async {
    if (loading) return false;
    try {
      await _repo.cancel(id);
      await load();
      return true;
    } on ApiException catch (e) {
      error = e.message;
      notifyListeners();
      return false;
    } catch (_) {
      error = 'Không thể hủy đơn hàng.';
      notifyListeners();
      return false;
    }
  }
}
