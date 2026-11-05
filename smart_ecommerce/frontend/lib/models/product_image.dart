class ProductImageModel {
  const ProductImageModel({
    required this.id,
    required this.productId,
    required this.productName,
    required this.imageUrl,
    required this.isPrimary,
    required this.sortOrder,
  });

  final String id;
  final String productId;
  final String productName;
  final String imageUrl;
  final bool isPrimary;
  final int sortOrder;

  factory ProductImageModel.fromJson(Map<String, dynamic> json) => ProductImageModel(
        id: json['id'].toString(),
        productId: json['productId'].toString(),
        productName: json['productName']?.toString() ?? '',
        imageUrl: json['imageUrl']?.toString() ?? '',
        isPrimary: json['isPrimary'] == true,
        sortOrder: (json['sortOrder'] as num?)?.toInt() ?? 0,
      );
}
