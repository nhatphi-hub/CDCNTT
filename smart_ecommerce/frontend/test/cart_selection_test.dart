import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/models/cart.dart';
import 'package:frontend/providers/cart_provider.dart';
import 'package:frontend/repositories/cart_repository.dart';

const _itemA = CartItemModel(id: 'a', productId: 'p1', productName: 'Áo',
  productSlug: 'ao', unitPrice: 100, quantity: 2, subtotal: 200,
  availableQuantity: 20, shopName: 'Shop A', productImage: '/img/a.jpg');
const _itemB = CartItemModel(id: 'b', productId: 'p2', productName: 'Quần',
  productSlug: 'quan', unitPrice: 50, quantity: 1, subtotal: 50,
  availableQuantity: 20, shopName: 'Shop B');

class _CartRepo extends Fake implements CartRepository {
  CartModel value = const CartModel(id: 'cart', userId: 'customer',
    items: [_itemA, _itemB], totalQuantity: 3, totalAmount: 250);

  @override
  Future<CartModel> getCart() async => value;

  @override
  Future<CartModel> remove(String id) async {
    final items = value.items.where((item) => item.id != id).toList();
    value = CartModel(id: 'cart', userId: 'customer', items: items,
      totalQuantity: items.fold(0, (n, item) => n + item.quantity),
      totalAmount: items.fold(0, (n, item) => n + item.subtotal));
    return value;
  }
}

void main() {
  test('cart selects all on first load and computes selected total', () async {
    final provider = CartProvider(_CartRepo());
    addTearDown(provider.dispose);
    provider.syncUser('customer');
    await provider.load();
    expect(provider.isAllSelected, isTrue);
    expect(provider.selectedQuantity, 3);
    expect(provider.selectedTotal, 250);
    provider.setSelected('b', false);
    expect(provider.selectedIds, {'a'});
    expect(provider.selectedQuantity, 2);
    expect(provider.selectedTotal, 200);
  });

  test('unselected cart rows are retained when another row is removed', () async {
    final provider = CartProvider(_CartRepo());
    addTearDown(provider.dispose);
    provider.syncUser('customer');
    await provider.load();
    provider.setSelected('b', false);
    await provider.remove('a');
    expect(provider.cart!.items.single.id, 'b');
    expect(provider.selectedIds, isEmpty);
    provider.selectAll(true);
    expect(provider.selectedIds, {'b'});
  });

  test('selected items are never shared between customer accounts', () async {
    final provider = CartProvider(_CartRepo());
    addTearDown(provider.dispose);
    provider.syncUser('customer');
    await provider.load();
    provider.setSelected('a', false);
    provider.syncUser('customer-2');
    expect(provider.cart, isNull);
    expect(provider.selectedIds, isEmpty);
  });
}
