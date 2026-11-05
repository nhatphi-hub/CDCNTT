import 'package:flutter/foundation.dart' show ChangeNotifier;

import '../core/network/api_exception.dart';
import '../models/category.dart';
import '../repositories/category_repository.dart';
import 'product_provider.dart';

class CategoryProvider extends ChangeNotifier {
  CategoryProvider(this._repository);
  final CategoryRepository _repository;

  LoadStatus status = LoadStatus.initial;
  List<Category> categories = const [];
  String? errorMessage;

  List<Category> get activeCategories => categories.where((c) => c.isActive).toList();

  Future<void> load({bool force = false}) async {
    if (!force && (status == LoadStatus.loading || status == LoadStatus.success)) return;
    status = LoadStatus.loading;
    errorMessage = null;
    notifyListeners();
    try {
      categories = await _repository.getAll();
      status = categories.isEmpty ? LoadStatus.empty : LoadStatus.success;
    } on ApiException catch (e) {
      status = LoadStatus.error;
      errorMessage = e.message;
    } catch (_) {
      status = LoadStatus.error;
      errorMessage = 'Không thể tải danh mục.';
    }
    notifyListeners();
  }
}
