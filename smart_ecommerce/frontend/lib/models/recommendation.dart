class RecommendationItem {
  const RecommendationItem({required this.productId, required this.productName, required this.slug, required this.price, required this.score, required this.reason, this.imageUrl, this.salePrice});
  final String productId; final String productName; final String slug; final String? imageUrl; final double price; final double? salePrice; final double score; final String reason;
  double get displayPrice => salePrice ?? price;
  factory RecommendationItem.fromJson(Map<String,dynamic> j)=>RecommendationItem(productId:j['productId'].toString(),productName:j['productName']?.toString()??'',slug:j['slug']?.toString()??'',imageUrl:j['imageUrl']?.toString(),price:_n(j['price']),salePrice:j['salePrice']==null?null:_n(j['salePrice']),score:_n(j['score']),reason:j['reason']?.toString()??'');
  static double _n(dynamic v)=>v is num?v.toDouble():double.tryParse(v?.toString()??'')??0;
}

class RecommendationSource {
  const RecommendationSource({required this.type, required this.item, required this.position});
  final String type;
  final RecommendationItem item;
  final int position;
}
