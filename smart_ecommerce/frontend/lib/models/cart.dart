class CartItemModel {
  const CartItemModel({required this.id, required this.productId, required this.productName, required this.productSlug, this.productImage, required this.unitPrice, required this.quantity, required this.subtotal, required this.availableQuantity, this.variantId,this.shopName,this.variantName});
  final String id, productId, productName, productSlug; final String? productImage, variantId, shopName, variantName; final double unitPrice, subtotal; final int quantity, availableQuantity;
  factory CartItemModel.fromJson(Map<String,dynamic> j)=>CartItemModel(id:j['id'].toString(),productId:j['productId'].toString(),productName:j['productName']?.toString()??'',productSlug:j['productSlug']?.toString()??'',productImage:j['productImage']?.toString(),unitPrice:(j['unitPrice'] as num?)?.toDouble()??0,quantity:(j['quantity'] as num?)?.toInt()??0,subtotal:(j['subtotal'] as num?)?.toDouble()??0,availableQuantity:(j['availableQuantity'] as num?)?.toInt()??0,variantId:j['variantId']?.toString(),shopName:j['shopName']?.toString(),variantName:j['variantName']?.toString());
}
class CartModel {
  const CartModel({required this.id,required this.userId,required this.items,required this.totalQuantity,required this.totalAmount});
  final String id,userId; final List<CartItemModel> items; final int totalQuantity; final double totalAmount;
  factory CartModel.fromJson(Map<String,dynamic> j)=>CartModel(id:j['id']?.toString()??'',userId:j['userId']?.toString()??'',items:(j['items'] as List? ?? const []).whereType<Map<String,dynamic>>().map(CartItemModel.fromJson).toList(),totalQuantity:(j['totalQuantity'] as num?)?.toInt()??0,totalAmount:(j['totalAmount'] as num?)?.toDouble()??0);
}