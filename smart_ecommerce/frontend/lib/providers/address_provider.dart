import 'package:flutter/foundation.dart';

import '../core/network/api_exception.dart';
import '../models/address.dart';
import '../repositories/address_repository.dart';

class AddressProvider extends ChangeNotifier {
  AddressProvider(this._repo);
  final AddressRepository _repo;

  List<AddressModel> addresses = const [];
  bool loading = false;
  String? error;
  String? _userId;

  void syncUser(String? userId) {
    if (_userId == userId) return;
    _userId = userId;
    addresses = const [];
    loading = false;
    error = null;
    notifyListeners();
  }

  Future<void> load() async {
    loading = true;
    error = null;
    notifyListeners();
    try {
      addresses = await _repo.all();
    } on ApiException catch (e) {
      error = e.message;
    } catch (_) {
      error = 'Không thể tải địa chỉ.';
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<bool> save(AddressDraft draft, {String? id}) async {
    if (loading) return false;
    loading = true;
    error = null;
    notifyListeners();
    try {
      if (id == null) {
        await _repo.create(draft);
      } else {
        await _repo.update(id, draft);
      }
      addresses = await _repo.all();
      return true;
    } on ApiException catch (e) {
      error = e.message;
      return false;
    } catch (_) {
      error = 'Không thể lưu địa chỉ.';
      return false;
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<bool> setDefault(String id) async {
    if (loading) return false;
    try {
      await _repo.setDefault(id);
      await load();
      return true;
    } on ApiException catch (e) {
      error = e.message;
      notifyListeners();
      return false;
    }
  }

  Future<bool> remove(String id) async {
    if (loading) return false;
    try {
      await _repo.delete(id);
      await load();
      return true;
    } on ApiException catch (e) {
      error = e.message;
      notifyListeners();
      return false;
    }
  }

  AddressModel? get defaultAddress {
    for (final address in addresses) {
      if (address.isDefault) return address;
    }
    return addresses.isEmpty ? null : addresses.first;
  }
}
