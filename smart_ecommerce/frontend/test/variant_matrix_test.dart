import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/catalog/variant_matrix.dart';

void main() {
  test('2 real photos x 4 sizes generates 8 unique SKUs, no dummy products', () {
    const images = [
      '3b07ceaa-3d3b-425f-9d85-9b570abe1a15',
      '8c5dc2e3-70ac-427f-a041-786071e427d2',
    ];
    final variants = VariantMatrix.make(
      productId: 'real-product-uuid', productSku: 'AO-SO-MI-01',
      selectedImageIds: images, galleryImageIds: images,
      sizes: ['M', 'L', 'XL', 'XXL'], quantity: 12,
    );
    expect(variants.length, 8);
    expect(variants.map((v) => v['sku']).toSet().length, 8);
    expect(variants.map((v) => v['imageId']).toSet(), images.toSet());
    expect(variants.every((v) => v['quantity'] == 12), isTrue);
    expect(variants.every((v) => v['status'] == 'ACTIVE'), isTrue);
  });

  test('SKU stays fixed when photo display order changes', () {
    const image = '3b07ceaa-3d3b-425f-9d85-9b570abe1a15';
    final a = VariantMatrix.sku('AO-SO-MI-01', image, 'XL');
    final b = VariantMatrix.make(
      productId: 'real-id', productSku: 'AO-SO-MI-01',
      selectedImageIds: [image], galleryImageIds: ['other-id', image],
      sizes: ['XL'], quantity: 2,
    ).single['sku'];
    expect(a, b);
    expect(a.length, lessThanOrEqualTo(100));
  });
}
