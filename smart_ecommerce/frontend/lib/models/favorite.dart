class FavoriteModel {
  const FavoriteModel({
    required this.id,
    required this.productId,
    required this.productName,
    required this.slug,
    this.brand,
    required this.price,
    this.salePrice,
    this.createdAt,
  });

  final String id;
  final String productId;
  final String productName;
  final String slug;
  final String? brand;
  final double price;
  final double? salePrice;
  final DateTime? createdAt;

  double get displayPrice => salePrice ?? price;
  bool get isOnSale => salePrice != null && salePrice! < price;

  factory FavoriteModel.fromJson(Map<String, dynamic> json) => FavoriteModel(
        id: json['id'].toString(),
        productId: json['productId'].toString(),
        productName: json['productName']?.toString() ?? '',
        slug: json['slug']?.toString() ?? '',
        brand: json['brand']?.toString(),
        price: (json['price'] as num?)?.toDouble() ?? 0,
        salePrice: (json['salePrice'] as num?)?.toDouble(),
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
      );
}
