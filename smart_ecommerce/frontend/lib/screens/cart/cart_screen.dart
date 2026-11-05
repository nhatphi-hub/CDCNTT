import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/formatters.dart';
import '../../models/cart.dart';
import '../../providers/cart_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/product_image.dart';
import '../../widgets/ppee_ui.dart';

/// Shopping-cart table. Checkbox selection is sent to the actual checkout API;
/// no client-side prices or order totals are trusted by the backend.
class CartScreen extends StatefulWidget {
  const CartScreen({super.key});

  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => context.read<CartProvider>().load());
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<CartProvider>();
    final cart = provider.cart;
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: const CatalogHeader(),
      bottomNavigationBar: cart == null || cart.items.isEmpty
          ? const CatalogBottomNav(current: AppRoutes.cart)
          : _selectionFooter(provider, cart),
      body: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1310),
        child: _content(provider),
      )),
    );
  }

  Widget _content(CartProvider provider) {
    if (provider.loading && provider.cart == null) {
      return const PpeeLoadingView(message: 'Đang tải giỏ hàng...');
    }
    if (provider.error != null && provider.cart == null) {
      return PpeeStateView(icon: Icons.wifi_off_rounded,
        title: 'Không thể tải giỏ hàng', message: provider.error!,
        actionLabel: 'Thử lại', onAction: provider.load);
    }
    final cart = provider.cart;
    if (cart == null || cart.items.isEmpty) {
      return ShopEmptyState(icon: Icons.shopping_cart_outlined,
        title: 'Giỏ hàng của bạn đang trống',
        message: 'Khám phá các sản phẩm PPee và thêm vào giỏ hàng của bạn.',
        action: FilledButton.icon(
          onPressed: () => Navigator.pushNamed(context, AppRoutes.products),
          icon: const Icon(Icons.shopping_bag_outlined),
          label: const PpeeText('Mua sắm ngay')));
    }

    final groups = <String, List<CartItemModel>>{};
    for (final item in cart.items) {
      final shop = (item.shopName ?? '').trim();
      groups.putIfAbsent(shop.isEmpty ? 'PPee Marketplace' : shop,
          () => <CartItemModel>[]).add(item);
    }
    return LayoutBuilder(builder: (context, box) {
      final wide = box.maxWidth >= 900;
      return ListView(padding: EdgeInsets.all(ShopLayout.padding(context)), children: [
        const ShopHeading(title: 'Giỏ hàng của bạn',
          description: 'Kiểm tra sản phẩm và chọn những món bạn muốn thanh toán'),
        const SizedBox(height: 19),
        if (wide) _tableHeader(),
        const SizedBox(height: 8),
        for (final group in groups.entries) ...[
          Card(margin: const EdgeInsets.only(bottom: 15), clipBehavior: Clip.antiAlias,
            child: Column(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                color: AppColors.primarySoft,
                child: Row(children: [
                  Checkbox(
                    value: group.value.every((item) => provider.selectedIds.contains(item.id)),
                    onChanged: provider.loading ? null : (value) {
                      for (final item in group.value) {
                        provider.setSelected(item.id, value ?? false);
                      }
                    }),
                  const Icon(Icons.storefront_outlined, color: AppColors.primary, size: 19),
                  const SizedBox(width: 9),
                  Expanded(child: PpeeText(group.key,
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800))),
                  PpeeText('${group.value.length} sản phẩm',
                    style: const TextStyle(fontSize: 12, color: AppColors.gray700)),
                ]),
              ),
              for (var i = 0; i < group.value.length; i++) ...[
                if (i > 0) const Divider(height: 1),
                _itemRow(group.value[i], provider, wide),
              ],
            ])),
        ],
        if (provider.error != null) PpeeNotice(message: provider.error!, isError: true),
        const SizedBox(height: 18),
        const PpeeNotice(message:
          'Giá chính thức, mã giảm giá và phí vận chuyển sẽ được xác nhận khi đặt hàng.'),
        const SizedBox(height: 22),
      ]);
    });
  }

  Widget _tableHeader() => Container(
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
    decoration: BoxDecoration(color: Colors.white,
      borderRadius: BorderRadius.circular(13),
      border: Border.all(color: AppColors.gray300)),
    child: const Row(children: [
      Expanded(flex: 7, child: PpeeText('Sản phẩm', style: TextStyle(fontWeight: FontWeight.w700))),
      Expanded(flex: 2, child: Center(child: PpeeText('Đơn giá'))),
      Expanded(flex: 3, child: Center(child: PpeeText('Số lượng'))),
      Expanded(flex: 2, child: Center(child: PpeeText('Số tiền'))),
      SizedBox(width: 56, child: Center(child: PpeeText('Xóa'))),
    ]),
  );

  Widget _itemRow(CartItemModel item, CartProvider provider, bool wide) {
    final selected = provider.selectedIds.contains(item.id);
    final image = ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: SizedBox(width: wide ? 91 : 76, height: wide ? 91 : 84,
        child: ProductImage(url: item.productImage)));
    final name = InkWell(
      onTap: () => Navigator.pushNamed(context, '${AppRoutes.product}/${item.productId}'),
      child: PpeeText(item.productName, maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700)));
    final variant = (item.variantName ?? '').trim();
    final info = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      name,
      if (variant.isNotEmpty) ...[
        const SizedBox(height: 6),
        PpeeText('Phân loại: $variant', maxLines: 2,
          style: const TextStyle(fontSize: 12, color: AppColors.gray700)),
      ],
      const SizedBox(height: 5),
      PpeeText('Kho hiện tại: ${item.availableQuantity}',
        style: const TextStyle(color: AppColors.gray700, fontSize: 11)),
    ]);
    final checkbox = Checkbox(value: selected,
      onChanged: provider.loading ? null : (v) => provider.setSelected(item.id, v ?? false));
    final amount = PpeeText(Formatters.vnd(item.subtotal),
      style: const TextStyle(color: AppColors.primary, fontSize: 15,
        fontWeight: FontWeight.w800));
    final remove = IconButton(tooltip: tr(context, 'Xóa khỏi giỏ hàng'),
      onPressed: provider.loading ? null : () => provider.remove(item.id),
      icon: const Icon(Icons.delete_outline_rounded, color: AppColors.error, size: 19));

    if (!wide) {
      return Padding(padding: const EdgeInsets.fromLTRB(8, 13, 12, 14),
        child: Column(children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            checkbox, image, const SizedBox(width: 10), Expanded(child: info),
          ]),
          const SizedBox(height: 12),
          Row(children: [
            const SizedBox(width: 12),
            PpeeText(Formatters.vnd(item.unitPrice),
              style: const TextStyle(fontSize: 12, color: AppColors.gray700)),
            const Spacer(), _quantity(item, provider),
            const SizedBox(width: 12), amount, remove,
          ]),
        ]));
    }
    return Padding(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
      child: Row(children: [
        Expanded(flex: 7, child: Row(children: [
          checkbox, image, const SizedBox(width: 13), Expanded(child: info),
        ])),
        Expanded(flex: 2, child: Center(child: PpeeText(Formatters.vnd(item.unitPrice),
          style: const TextStyle(fontSize: 13, color: AppColors.gray700)))),
        Expanded(flex: 3, child: Center(child: _quantity(item, provider))),
        Expanded(flex: 2, child: Center(child: amount)),
        SizedBox(width: 56, child: remove),
      ]));
  }

  Widget _quantity(CartItemModel item, CartProvider provider) => Container(
    decoration: BoxDecoration(border: Border.all(color: AppColors.gray300),
      borderRadius: BorderRadius.circular(10)),
    child: Row(mainAxisSize: MainAxisSize.min, children: [
      IconButton(visualDensity: VisualDensity.compact, icon: const Icon(Icons.remove, size: 15),
        onPressed: provider.loading || item.quantity <= 1 ? null
          : () => provider.update(item.id, item.quantity - 1)),
      PpeeText('${item.quantity}', style: const TextStyle(fontWeight: FontWeight.w800)),
      IconButton(visualDensity: VisualDensity.compact, icon: const Icon(Icons.add, size: 15),
        onPressed: provider.loading || item.quantity >= item.availableQuantity ? null
          : () => provider.update(item.id, item.quantity + 1)),
    ]),
  );

  Widget _selectionFooter(CartProvider provider, CartModel cart) {
    final compact = MediaQuery.sizeOf(context).width < 720;
    final selectedRows = provider.selectedItems.length;
    final actions = Row(mainAxisSize: MainAxisSize.min, children: [
      Checkbox(value: provider.isAllSelected,
        onChanged: provider.loading ? null : (v) => provider.selectAll(v ?? false)),
      PpeeText('Tất cả (${cart.items.length})',
        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
      TextButton(onPressed: provider.loading || selectedRows == 0 ? null
        : () => _removeSelected(provider),
        child: const PpeeText('Xóa đã chọn')),
    ]);
    final total = Column(crossAxisAlignment: CrossAxisAlignment.end,
      mainAxisSize: MainAxisSize.min, children: [
        PpeeText('Tổng (${provider.selectedQuantity} sản phẩm):',
          style: const TextStyle(fontSize: 11, color: AppColors.gray700)),
        PpeeText(Formatters.vnd(provider.selectedTotal),
          style: const TextStyle(color: AppColors.primary,
            fontSize: 19, fontWeight: FontWeight.w900)),
      ]);
    final checkout = FilledButton.icon(
      onPressed: provider.loading || selectedRows == 0 ? null
        : () => Navigator.pushNamed(context, AppRoutes.checkout),
      icon: const Icon(Icons.shopping_bag_outlined, size: 17),
      label: PpeeText('Mua hàng ($selectedRows)'),
    );
    return Material(color: Colors.white, elevation: 12,
      child: SafeArea(top: false, child: Center(heightFactor: 1,
        child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1310),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            child: compact
              ? Column(crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min, children: [
                    actions,
                    const SizedBox(height: 5),
                    Row(children: [
                      Expanded(child: total),
                      const SizedBox(width: 12),
                      Expanded(child: checkout),
                    ]),
                  ])
              : Row(children: [
                  actions, const Spacer(), total, const SizedBox(width: 18), checkout,
                ]),
          ),
        ),
      )),
    );
  }

  Future<void> _removeSelected(CartProvider provider) async {
    final ids = provider.selectedIds.toList();
    if (ids.isEmpty) return;
    final confirmed = await showDialog<bool>(context: context,
      builder: (ctx) => AlertDialog(
        title: const PpeeText('Xóa sản phẩm đã chọn?'),
        content: PpeeText('Xóa ${ids.length} dòng sản phẩm khỏi giỏ hàng?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false),
            child: const PpeeText('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true),
            child: const PpeeText('Xóa')),
        ],
      ));
    if (confirmed != true || !mounted) return;
    for (final id in ids) {
      if (!await provider.remove(id)) break;
    }
  }
}
