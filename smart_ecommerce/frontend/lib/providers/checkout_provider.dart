import 'package:flutter/foundation.dart';

import '../models/address.dart';

class CheckoutProvider extends ChangeNotifier {
  AddressModel? address;
  String paymentMethod = 'COD';
  String note = '';
  String couponCode = '';
  bool submitting = false;
  String? _userId;

  void syncUser(String? userId) {
    if (_userId == userId) return;
    _userId = userId;
    address = null;
    paymentMethod = 'COD';
    note = '';
    couponCode = '';
    submitting = false;
    notifyListeners();
  }

  void selectAddress(AddressModel value) {
    address = value;
    notifyListeners();
  }

  void setCouponCode(String value) {
    couponCode=value.trim().toUpperCase();
    notifyListeners();
  }

  void selectPayment(String value) {
    paymentMethod = value;
    notifyListeners();
  }

  void setSubmitting(bool value) {
    submitting = value;
    notifyListeners();
  }
}
