class Product {
  const Product({
    required this.id,
    required this.name,
    required this.slug,
    required this.sku,
    required this.price,
    required this.quantity,
    required this.status,
    required this.createdAt,
    required this.updatedAt,
    this.categoryId,
    this.categoryName,
    this.brand,
    this.description,
    this.salePrice,
    this.gender,
    this.productType,
    this.imageUrl,
  });

  final String id;
  final String? categoryId;
  final String? categoryName;
  final String name;
  final String slug;
  final String sku;
  final String? brand;
  final String? description;
  final double price;
  final double? salePrice;
  final int quantity;
  final String? gender;
  final String? productType;
  final String? imageUrl;
  final String status;
  final DateTime createdAt;
  final DateTime updatedAt;

  double get displayPrice => salePrice ?? price;
  bool get isOnSale => salePrice != null && salePrice! < price;
  bool get inStock => quantity > 0;

  factory Product.fromJson(Map<String, dynamic> json) {
    return Product(
      id: json['id'].toString(),
      categoryId: json['categoryId']?.toString(),
      categoryName: json['categoryName']?.toString(),
      name: json['name']?.toString() ?? '',
      slug: json['slug']?.toString() ?? '',
      sku: json['sku']?.toString() ?? '',
      brand: json['brand']?.toString(),
      description: json['description']?.toString(),
      price: _number(json['price']),
      salePrice: json['salePrice'] == null ? null : _number(json['salePrice']),
      quantity: (json['quantity'] as num?)?.toInt() ?? 0,
      gender: json['gender']?.toString(),
      productType: json['productType']?.toString(),
      imageUrl: json['imageUrl']?.toString(),
      status: json['status']?.toString() ?? '',
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }

  static double _number(dynamic value) {
    if (value is num) return value.toDouble();
    return double.tryParse(value?.toString() ?? '') ?? 0;
  }
}
