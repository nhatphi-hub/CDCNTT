import 'package:flutter/foundation.dart';
import '../models/product_variant.dart';
import '../repositories/product_variant_repository.dart';
class ProductVariantProvider extends ChangeNotifier { ProductVariantProvider(this._repo); final ProductVariantRepository _repo; Future<List<ProductVariantModel>> byProduct(String id)=>_repo.byProduct(id); }
