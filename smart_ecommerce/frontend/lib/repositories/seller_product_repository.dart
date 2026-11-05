import '../core/network/api_client.dart';
import 'dart:typed_data';
import 'package:http/http.dart' as http;

class SellerProductRepository {
  SellerProductRepository(this._api);
  final ApiClient _api;
  static const root = '/api/seller/products';
  Future<List<Map<String, dynamic>>> list() async =>
      (await _api.get(root) as List).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  Future<Map<String, dynamic>> create(Map<String, dynamic> data) async =>
      Map<String, dynamic>.from(await _api.post(root, body: data) as Map);
  Future<Map<String, dynamic>> update(String id, Map<String, dynamic> data) async =>
      Map<String, dynamic>.from(await _api.put('$root/$id', body: data) as Map);
  Future<Map<String, dynamic>> visibility(String id, bool visible) async =>
      Map<String, dynamic>.from(await _api.patch('$root/$id/visibility', body: {'visible': visible}) as Map);
  /// Remove product from public sale; server preserves order/review history.
  Future<void> removeProductFromSale(String id) async {
    await _api.delete('$root/$id');
  }
  Future<List<Map<String, dynamic>>> variants(String productId) async =>
      (await _api.get('$root/$productId/variants') as List).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  Future<Map<String, dynamic>> saveVariant(String productId, Map<String, dynamic> data, {String? id}) async =>
      Map<String, dynamic>.from((id == null ? await _api.post('$root/$productId/variants', body: data) :
          await _api.put('$root/$productId/variants/$id', body: data)) as Map);
  /// true = deleted from DB; false = deactivated because there are references.
  Future<bool> deleteVariant(String productId, String variantId) async {
    final response = await _api.delete('$root/$productId/variants/$variantId');
    return (response as Map)['hardDeleted'] == true;
  }
  Future<List<Map<String, dynamic>>> saveVariantsBulk(
      String productId, List<Map<String, dynamic>> data) async =>
      (await _api.post('$root/$productId/variants/bulk', body: data) as List)
          .map((e) => Map<String, dynamic>.from(e as Map)).toList();
  Future<Map<String, dynamic>> stock(String productId, String variantId, int quantity) async =>
      Map<String, dynamic>.from(await _api.patch('$root/$productId/variants/$variantId/stock', body: {'quantity': quantity}) as Map);
}

// Image management uses seller-specific routes; never ADMIN product-image endpoints.
extension SellerProductImages on SellerProductRepository {
  Future<Map<String,dynamic>> uploadImage(String productId,Uint8List bytes,String filename,{bool primary=false}) async =>
    Map<String,dynamic>.from(await _api.multipart('POST',
      '${SellerProductRepository.root}/$productId/images/upload',
      fields:{'primary':primary.toString()},
      files:[http.MultipartFile.fromBytes('image',bytes,filename:filename)]) as Map);

  Future<List<Map<String, dynamic>>> images(String productId) async =>
      (await _api.get('${SellerProductRepository.root}/$productId/images') as List)
          .map((e) => Map<String, dynamic>.from(e as Map)).toList();

  Future<Map<String, dynamic>> saveImage(String productId, Map<String, dynamic> data, {String? imageId}) async =>
      Map<String, dynamic>.from((imageId == null
        ? await _api.post('${SellerProductRepository.root}/$productId/images', body: data)
        : await _api.put('${SellerProductRepository.root}/$productId/images/$imageId', body: data)) as Map);

  Future<void> primaryImage(String productId, String imageId) async {
    await _api.put('${SellerProductRepository.root}/$productId/images/$imageId/primary');
  }

  Future<void> deleteImage(String productId, String imageId) async {
    await _api.delete('${SellerProductRepository.root}/$productId/images/$imageId');
  }
}
