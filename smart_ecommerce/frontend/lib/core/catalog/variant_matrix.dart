/// Pure helper for generating one real SKU per selected photograph × size.
/// No placeholder products are created here. All values must be saved by the API.
class VariantMatrix {
  const VariantMatrix._();

  static String sku(String productSku, String imageId, String size) {
    final base = productSku.toUpperCase()
        .replaceAll(RegExp(r'[^A-Z0-9]+'), '-').replaceAll(RegExp(r'^-|-$'), '');
    final option = size.toUpperCase()
        .replaceAll(RegExp(r'[^A-Z0-9]+'), '-').replaceAll(RegExp(r'^-|-$'), '');
    final root = base.isEmpty ? 'PP' : base;
    final prefix = root.length > 54 ? root.substring(0, 54) : root;
    final stableId = imageId.toUpperCase().replaceAll(RegExp(r'[^A-Z0-9]'), '');
    return '$prefix-I${stableId.substring(0, stableId.length < 12 ? stableId.length : 12)}-$option';
  }

  static List<Map<String, dynamic>> make({
    required String productId,
    required String productSku,
    required List<String> selectedImageIds,
    required List<String> sizes,
    required List<String> galleryImageIds,
    required int quantity,
    num? price,
    num? salePrice,
  }) {
    final result = <Map<String, dynamic>>[];
    for (final id in selectedImageIds) {
      final imageOrder = galleryImageIds.indexOf(id);
      if (imageOrder < 0) throw ArgumentError('Ảnh không thuộc sản phẩm');
      for (final size in sizes) {
        result.add({
          'productId': productId,
          'sku': sku(productSku, id, size),
          'size': size,
          'color': 'Mẫu ${imageOrder + 1}',
          'imageId': id,
          'quantity': quantity,
          'price': price,
          'salePrice': salePrice,
          'status': 'ACTIVE',
        });
      }
    }
    return result;
  }
}
