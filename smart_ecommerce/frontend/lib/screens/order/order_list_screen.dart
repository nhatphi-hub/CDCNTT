import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/formatters.dart';
import '../../models/order.dart';
import '../../providers/order_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/order_status_pill.dart';
import '../../widgets/product_image.dart';

/// Purchase history grouped by real shops, with actual order-line images,
/// variants and totals provided by the API.
class OrderListScreen extends StatefulWidget {
  const OrderListScreen({super.key});

  @override
  State<OrderListScreen> createState() => _OrderListScreenState();
}

class _OrderListScreenState extends State<OrderListScreen> {
  String _filter = 'ALL';
  String _query = '';
  final _search = TextEditingController();
  static const _statuses = ['ALL', 'PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED'];
  static const _labels = ['Tất cả', 'Chờ xác nhận', 'Đã xác nhận', 'Đang giao', 'Hoàn thành', 'Đã hủy'];

  @override
  void initState() {
    super.initState();
    Future.microtask(() => context.read<OrderProvider>().load());
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<OrderProvider>();
    final orders = provider.orders.where((order) {
      if (_filter != 'ALL' && order.status.toUpperCase() != _filter) return false;
      final keyword = _query.trim().toLowerCase();
      if (keyword.isEmpty) return true;
      return order.orderCode.toLowerCase().contains(keyword)
          || order.items.any((item) => item.productName.toLowerCase().contains(keyword)
             || (item.shopName ?? '').toLowerCase().contains(keyword));
    }).toList();
    orders.sort((a, b) => (b.createdAt ?? DateTime(1970))
        .compareTo(a.createdAt ?? DateTime(1970)));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: const CatalogHeader(),
      body: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1240),
        child: Padding(padding: EdgeInsets.all(ShopLayout.padding(context)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            ShopHeading(title: 'Đơn mua của tôi',
              description: 'Theo dõi từng sản phẩm và tiến độ giao hàng',
              trailing: IconButton(tooltip: tr(context, 'Làm mới'),
                onPressed: provider.loading ? null : provider.load,
                icon: const Icon(Icons.refresh_rounded))),
            const SizedBox(height: 20),
            Container(decoration: BoxDecoration(color: Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.gray300)),
              child: SizedBox(height: 57, child: ListView.builder(
                scrollDirection: Axis.horizontal,
                itemCount: _statuses.length,
                itemBuilder: (_, index) {
                  final status = _statuses[index];
                  final selected = status == _filter;
                  return InkWell(
                    onTap: () => setState(() => _filter = status),
                    child: Container(
                      constraints: const BoxConstraints(minWidth: 128),
                      alignment: Alignment.center,
                      padding: const EdgeInsets.symmetric(horizontal: 13),
                      decoration: BoxDecoration(border: Border(bottom: BorderSide(
                        color: selected ? AppColors.primary : Colors.transparent,
                        width: selected ? 3 : 0))),
                      child: PpeeText(_labels[index],
                        style: TextStyle(color: selected ? AppColors.primary : AppColors.gray700,
                          fontSize: 13, fontWeight: selected ? FontWeight.w800 : FontWeight.w600)),
                    ),
                  );
                },
              )),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _search,
              onChanged: (v) => setState(() => _query = v),
              decoration: InputDecoration(
                hintText: tr(context, 'Tìm mã đơn, tên shop hoặc tên sản phẩm'),
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: _query.isEmpty ? null : IconButton(
                  tooltip: tr(context, 'Xóa tìm kiếm'),
                  onPressed: () { _search.clear(); setState(() => _query = ''); },
                  icon: const Icon(Icons.close_rounded)),
              ),
            ),
            const SizedBox(height: 15),
            if (provider.loading && provider.orders.isEmpty)
              const Expanded(child: Center(child: CircularProgressIndicator()))
            else if (provider.error != null && provider.orders.isEmpty)
              Expanded(child: ShopEmptyState(icon: Icons.wifi_off_rounded,
                title: 'Không thể tải đơn hàng', message: provider.error!,
                action: OutlinedButton(onPressed: provider.load,
                  child: const PpeeText('Thử lại'))))
            else if (orders.isEmpty)
              Expanded(child: ShopEmptyState(icon: Icons.receipt_long_outlined,
                title: 'Chưa tìm thấy đơn hàng',
                message: 'Đơn hàng của bạn sẽ xuất hiện tại đây.',
                action: OutlinedButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.products),
                  child: const PpeeText('Khám phá sản phẩm'))))
            else Expanded(child: ListView.separated(
              itemCount: orders.length,
              separatorBuilder: (_, __) => const SizedBox(height: 15),
              itemBuilder: (_, index) => _orderCard(orders[index]),
            )),
          ]),
        ),
      )),
    );
  }

  Widget _orderCard(OrderModel order) {
    final shops = <String, List<OrderItemModel>>{};
    for (final item in order.items) {
      final shopName = (item.shopName ?? '').trim();
      shops.putIfAbsent(shopName.isEmpty ? 'PPee Marketplace' : shopName,
        () => <OrderItemModel>[]).add(item);
    }
    final detail = '${AppRoutes.order}/${order.id}';
    final canReview = order.status == 'DELIVERED' && order.paymentStatus == 'PAID';
    return Card(margin: EdgeInsets.zero, clipBehavior: Clip.antiAlias,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Padding(padding: const EdgeInsets.symmetric(horizontal: 17, vertical: 12),
          child: Wrap(alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center, spacing: 12, runSpacing: 8,
            children: [
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PpeeText(order.orderCode,
                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800)),
                if (order.createdAt != null)
                  PpeeText(_dateLabel(order.createdAt!),
                    style: const TextStyle(fontSize: 11, color: AppColors.gray700)),
              ]),
              Row(mainAxisSize: MainAxisSize.min, children: [
                if (order.status == 'DELIVERED') ...[
                  const Icon(Icons.local_shipping_outlined, size: 17, color: AppColors.success),
                  const SizedBox(width: 6),
                ],
                OrderStatusPill(status: order.status),
              ]),
            ]),
        ),
        const Divider(height: 1),
        for (final group in shops.entries) ...[
          Padding(padding: const EdgeInsets.fromLTRB(17, 12, 17, 9),
            child: Row(children: [
              const Icon(Icons.storefront_outlined, size: 18, color: AppColors.primary),
              const SizedBox(width: 9),
              Expanded(child: PpeeText(group.key,
                style: const TextStyle(fontWeight: FontWeight.w800))),
            ])),
          for (final item in group.value) ...[
            const Divider(height: 1, indent: 16, endIndent: 16),
            InkWell(onTap: () => Navigator.pushNamed(context,
              '${AppRoutes.product}/${item.productId}'),
              child: Padding(padding: const EdgeInsets.symmetric(horizontal: 17, vertical: 12),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  ClipRRect(borderRadius: BorderRadius.circular(10),
                    child: SizedBox(width: 85, height: 88,
                      child: ProductImage(url: item.productImage))),
                  const SizedBox(width: 13),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      PpeeText(item.productName, maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
                      const SizedBox(height: 5),
                      if ((item.variantName ?? '').isNotEmpty)
                        PpeeText('Phân loại: ${item.variantName}',
                          style: const TextStyle(fontSize: 12, color: AppColors.gray700)),
                      const SizedBox(height: 5),
                      PpeeText('x${item.quantity}',
                        style: const TextStyle(fontSize: 12, color: AppColors.gray700)),
                    ])),
                  const SizedBox(width: 8),
                  PpeeText(Formatters.vnd(item.subtotal),
                    style: const TextStyle(fontWeight: FontWeight.w800,
                      color: AppColors.primary, fontSize: 14)),
                ]),
              ),
            ),
          ],
        ],
        const Divider(height: 1),
        Container(color: AppColors.gray100,
          padding: const EdgeInsets.symmetric(horizontal: 17, vertical: 14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Wrap(alignment: WrapAlignment.end, spacing: 10,
              crossAxisAlignment: WrapCrossAlignment.center, children: [
                const PpeeText('Thành tiền:',
                  style: TextStyle(color: AppColors.gray700, fontWeight: FontWeight.w600)),
                PpeeText(Formatters.vnd(order.totalAmount),
                  style: const TextStyle(color: AppColors.primary,
                    fontWeight: FontWeight.w900, fontSize: 20)),
              ]),
            const SizedBox(height: 10),
            Wrap(alignment: WrapAlignment.end, spacing: 9, runSpacing: 8, children: [
              if (canReview)
                OutlinedButton.icon(onPressed: () => Navigator.pushNamed(context, detail),
                  icon: const Icon(Icons.rate_review_outlined, size: 16),
                  label: const PpeeText('Đánh giá / Trả hàng')),
              OutlinedButton.icon(onPressed: () => Navigator.pushNamed(context, detail),
                icon: const Icon(Icons.receipt_long_outlined, size: 16),
                label: const PpeeText('Xem chi tiết')),
            ]),
            const SizedBox(height: 4),
            PpeeText('${order.paymentMethod} · ${order.paymentStatus}',
              style: const TextStyle(fontSize: 11, color: AppColors.gray700)),
          ]),
        ),
      ]),
    );
  }

  String _dateLabel(DateTime date) =>
    '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
}
