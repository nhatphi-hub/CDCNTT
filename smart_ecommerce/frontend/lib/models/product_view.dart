class ProductViewModel {
  const ProductViewModel({required this.id,required this.productId,required this.productName,required this.productSlug,required this.viewedAt,this.userId,this.durationSeconds});
  final String id; final String? userId; final String productId; final String productName; final String productSlug; final DateTime viewedAt; final int? durationSeconds;
  factory ProductViewModel.fromJson(Map<String,dynamic> j)=>ProductViewModel(id:j['id'].toString(),userId:j['userId']?.toString(),productId:j['productId'].toString(),productName:j['productName']?.toString()??'',productSlug:j['productSlug']?.toString()??'',viewedAt:DateTime.tryParse(j['viewedAt']?.toString()??'')??DateTime.fromMillisecondsSinceEpoch(0),durationSeconds:(j['durationSeconds'] as num?)?.toInt());
}
