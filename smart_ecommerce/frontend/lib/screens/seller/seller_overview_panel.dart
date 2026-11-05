import '../../core/l10n/language_controller.dart';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../repositories/seller_order_repository.dart';
import '../../repositories/seller_product_repository.dart';
import '../../widgets/seller_ui.dart';

/// A real-data operations overview. All business numbers come from seller APIs.
class SellerOverviewPanel extends StatefulWidget {
  const SellerOverviewPanel({super.key, required this.openOrders, required this.openProducts});
  final VoidCallback openOrders;
  final VoidCallback openProducts;

  @override
  State<SellerOverviewPanel> createState() => _SellerOverviewPanelState();
}

class _OverviewSnapshot {
  const _OverviewSnapshot(this.analytics, this.stats, this.orders, this.products);
  final Map<String, dynamic> analytics;
  final Map<String, dynamic> stats;
  final List<Map<String, dynamic>> orders;
  final List<Map<String, dynamic>> products;
}

class _SellerOverviewPanelState extends State<SellerOverviewPanel> {
  late Future<_OverviewSnapshot> _future;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  void _refresh() => _future = _fetch();

  Future<_OverviewSnapshot> _fetch() async {
    final ordersRepo = context.read<SellerOrderRepository>();
    final productsRepo = context.read<SellerProductRepository>();
    final results = await Future.wait<dynamic>([
      ordersRepo.analytics(days: 30),
      ordersRepo.stats(),
      ordersRepo.list(page: 0),
      productsRepo.list(),
    ]);
    final page = Map<String, dynamic>.from(results[2] as Map);
    final recent = (page['content'] as List? ?? const [])
        .map((o) => Map<String, dynamic>.from(o as Map))
        .take(5).toList();
    return _OverviewSnapshot(
      Map<String, dynamic>.from(results[0] as Map),
      Map<String, dynamic>.from(results[1] as Map),
      recent,
      List<Map<String, dynamic>>.from(results[3] as List),
    );
  }

  String _money(dynamic value) {
    final number = num.tryParse('$value') ?? 0;
    final formatted = number.toStringAsFixed(0).replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.',
    );
    return '$formatted đ';
  }

  String _status(String value) => switch (value) {
    'PENDING' => 'Chờ xác nhận',
    'CONFIRMED' => 'Đang chuẩn bị',
    'READY' => 'Đã chuẩn bị',
    'SHIPPING' => 'Đang vận chuyển',
    'DELIVERED' => 'Đã giao',
    'CANCELLED' => 'Đã hủy',
    _ => value,
  };

  Widget _metric({required String title, required String value,
    required IconData icon, required Color color, required String detail}) {
    return SellerPanel(
      padding: const EdgeInsets.fromLTRB(20, 19, 20, 18),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            width: 44, height: 44,
            decoration: BoxDecoration(
              color: color.withValues(alpha: .10),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(icon, color: color, size: 22),
          ),
          const Spacer(),
          const Icon(Icons.north_east_rounded, color: AppColors.gray500, size: 16),
        ]),
        const SizedBox(height: 20),
        PpeeText(value, maxLines: 1, overflow: TextOverflow.ellipsis,
          style: const TextStyle(color: AppColors.black, fontSize: 25,
            fontWeight: FontWeight.w900, letterSpacing: -.65)),
        const SizedBox(height: 5),
        PpeeText(title, style: const TextStyle(color: AppColors.black,
          fontSize: 13, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        PpeeText(detail, style: const TextStyle(fontSize: 11.5, color: AppColors.gray700)),
      ]),
    );
  }

  Widget _sectionHeader(String title, String detail, IconData icon, {VoidCallback? open}) =>
    Row(children: [
      Container(
        padding: const EdgeInsets.all(9),
        decoration: BoxDecoration(color: AppColors.primarySoft,
          borderRadius: BorderRadius.circular(11)),
        child: Icon(icon, color: AppColors.primary, size: 19),
      ),
      const SizedBox(width: 10),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        PpeeText(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w900,
          color: AppColors.black)),
        const SizedBox(height: 2),
        PpeeText(detail, style: const TextStyle(fontSize: 11.5, color: AppColors.gray700)),
      ])),
      if (open != null)
        TextButton(onPressed: open, child: const PpeeText('Xem tất cả')),
    ]);

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Row(children: [
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          PpeeText('Tổng quan kinh doanh', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 5),
          const PpeeText('Nắm bắt hiệu quả bán hàng và việc cần xử lý trong một nơi.',
            style: TextStyle(color: AppColors.gray700, fontSize: 13)),
        ])),
        IconButton.filledTonal(
          tooltip: tr(context, 'Làm mới dữ liệu gian hàng'),
          icon: const Icon(Icons.refresh_rounded),
          onPressed: () => setState(_refresh),
        ),
      ]),
      const SizedBox(height: 18),
      FutureBuilder<_OverviewSnapshot>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const SellerPanel(child: Padding(
              padding: EdgeInsets.symmetric(vertical: 56),
              child: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
                CircularProgressIndicator(), SizedBox(height: 15),
                PpeeText('Đang cập nhật dữ liệu gian hàng...'),
              ])),
            ));
          }
          if (snapshot.hasError) {
            final error = snapshot.error;
            final message = error is ApiException ? error.message : 'Không thể tải dữ liệu gian hàng.';
            return SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Icon(Icons.wifi_off_rounded, color: AppColors.error, size: 30),
              const SizedBox(height: 10),
              PpeeText(message, style: const TextStyle(color: AppColors.error)),
              const SizedBox(height: 10),
              OutlinedButton.icon(onPressed: () => setState(_refresh),
                icon: const Icon(Icons.refresh_rounded), label: const PpeeText('Thử lại')),
            ]));
          }
          final data = snapshot.data!;
          final counts = Map<String, dynamic>.from(data.stats['statusCounts'] as Map? ?? {});
          final pending = (num.tryParse('${counts['PENDING'] ?? 0}') ?? 0).toInt();
          final preparing = (num.tryParse('${counts['CONFIRMED'] ?? 0}') ?? 0).toInt();
          final active = data.products.where((p) => p['status'] == 'ACTIVE').length;
          final lowStock = data.products.where((p) => p['status'] == 'ACTIVE' &&
            (num.tryParse('${p['quantity']}') ?? 0) <= 5).toList();
          final daily = (data.analytics['dailyRevenue'] as List? ?? const [])
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e)).toList();
          return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 25, vertical: 25),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(22),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft, end: Alignment.bottomRight,
                  colors: [Color(0xFF123A96), Color(0xFF245FE8), Color(0xFF1893C4)],
                ),
                boxShadow: [BoxShadow(
                  color: AppColors.primary.withValues(alpha: .15),
                  blurRadius: 25, offset: const Offset(0, 10),
                )],
              ),
              child: Wrap(spacing: 20, runSpacing: 17,
                crossAxisAlignment: WrapCrossAlignment.center,
                alignment: WrapAlignment.spaceBetween,
                children: [
                  Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const PpeeText('BẢNG ĐIỀU KHIỂN  /  30 NGÀY',
                      style: TextStyle(color: Color(0xFFCBDEFF), fontSize: 11,
                        fontWeight: FontWeight.w800, letterSpacing: 1.2)),
                    const SizedBox(height: 10),
                    const PpeeText('Chào mừng trở lại!',
                      style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900,
                        fontSize: 24, letterSpacing: -.5)),
                    const SizedBox(height: 7),
                    const PpeeText('Theo dõi hoạt động và quản lý shop hiệu quả hơn mỗi ngày.',
                      style: TextStyle(color: Colors.white70, fontSize: 12.5)),
                    const SizedBox(height: 17),
                    Wrap(spacing: 8, runSpacing: 8, children: [
                      _heroBadge(Icons.pending_actions_rounded, '$pending đơn cần xác nhận'),
                      _heroBadge(Icons.inventory_2_outlined, '${lowStock.length} sản phẩm sắp hết'),
                    ]),
                  ]),
                  FilledButton.icon(
                    onPressed: widget.openOrders,
                    style: FilledButton.styleFrom(backgroundColor: Colors.white,
                      foregroundColor: AppColors.primaryDark),
                    icon: const Icon(Icons.arrow_forward_rounded),
                    label: const PpeeText('Xử lý đơn hàng'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 19),
            LayoutBuilder(builder: (context, box) {
              final columns = box.maxWidth >= 930 ? 4 : box.maxWidth >= 520 ? 2 : 1;
              const gap = 13.0;
              final cardWidth = (box.maxWidth - gap * (columns - 1)) / columns;
              return Wrap(spacing: gap, runSpacing: gap, children: [
                SizedBox(width: cardWidth, child: _metric(
                  title: 'Đơn cần xác nhận', value: '$pending',
                  icon: Icons.pending_actions_rounded, color: AppColors.highlight,
                  detail: 'Đơn mới đang chờ bạn')),
                SizedBox(width: cardWidth, child: _metric(
                  title: 'Đang chuẩn bị', value: '$preparing',
                  icon: Icons.inventory_2_outlined, color: AppColors.primary,
                  detail: 'Đã xác nhận, đang xử lý')),
                SizedBox(width: cardWidth, child: _metric(
                  title: 'Sản phẩm đang bán', value: '$active',
                  icon: Icons.storefront_outlined, color: AppColors.accent,
                  detail: 'Sản phẩm trạng thái ACTIVE')),
                SizedBox(width: cardWidth, child: _metric(
                  title: 'Doanh thu đủ điều kiện',
                  value: _money(data.analytics['paidDeliveredRevenue']),
                  icon: Icons.account_balance_wallet_outlined, color: AppColors.success,
                  detail: '30 ngày · Đã giao và thanh toán')),
              ]);
            }),
            const SizedBox(height: 19),
            LayoutBuilder(builder: (context, box) {
              final wide = box.maxWidth >= 810;
              final revenue = _revenuePanel(daily);
              final orders = _orderPanel(data.orders);
              if (!wide) return Column(children: [
                revenue, const SizedBox(height: 14), orders,
              ]);
              return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(flex: 6, child: revenue),
                const SizedBox(width: 14),
                Expanded(flex: 5, child: orders),
              ]);
            }),
            const SizedBox(height: 15),
            _stockPanel(lowStock),
            const SizedBox(height: 19),
            Wrap(spacing: 12, runSpacing: 10, children: [
              FilledButton.icon(onPressed: widget.openProducts,
                icon: const Icon(Icons.add_box_outlined),
                label: const PpeeText('Quản lý sản phẩm')),
              OutlinedButton.icon(onPressed: widget.openOrders,
                icon: const Icon(Icons.receipt_long_outlined),
                label: const PpeeText('Xem đơn bán')),
            ]),
          ]);
        },
      ),
    ],
  );

  Widget _heroBadge(IconData icon, String text) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
    decoration: BoxDecoration(color: Colors.white.withValues(alpha: .14),
      border: Border.all(color: Colors.white24),
      borderRadius: BorderRadius.circular(20)),
    child: Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(icon, size: 15, color: Colors.white),
      const SizedBox(width: 6),
      PpeeText(text, style: const TextStyle(color: Colors.white, fontSize: 11.5,
        fontWeight: FontWeight.w700)),
    ]),
  );

  Widget _revenuePanel(List<Map<String, dynamic>> daily) => SellerPanel(
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _sectionHeader('Doanh thu theo ngày', '30 ngày gần đây · Dữ liệu đơn đủ điều kiện',
        Icons.bar_chart_rounded),
      const SizedBox(height: 23),
      if (daily.isEmpty)
        const SizedBox(height: 184, child: Center(child: PpeeText(
          'Chưa có dữ liệu doanh thu để hiển thị biểu đồ.',
          textAlign: TextAlign.center,
          style: TextStyle(color: AppColors.gray700),
        )))
      else
        _RevenueBars(daily: daily, money: _money),
    ]),
  );

  Widget _orderPanel(List<Map<String, dynamic>> orders) => SellerPanel(
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _sectionHeader('Đơn hàng gần đây', 'Đơn bán mới nhất từ hệ thống',
        Icons.receipt_long_outlined, open: widget.openOrders),
      const SizedBox(height: 15),
      if (orders.isEmpty)
        const SizedBox(height: 183, child: Center(child: PpeeText(
          'Chưa có đơn hàng. Khi có đơn mới, chúng sẽ xuất hiện ở đây.',
          textAlign: TextAlign.center, style: TextStyle(color: AppColors.gray700),
        )))
      else for (final order in orders) ...[
        InkWell(
          onTap: widget.openOrders,
          borderRadius: BorderRadius.circular(11),
          child: Padding(padding: const EdgeInsets.symmetric(vertical: 10),
            child: Row(children: [
              const CircleAvatar(backgroundColor: AppColors.primarySoft,
                radius: 19, child: Icon(Icons.shopping_bag_outlined,
                  size: 19, color: AppColors.primary)),
              const SizedBox(width: 11),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PpeeText('${order['orderCode'] ?? 'Đơn hàng'}',
                  maxLines: 1, overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w800,
                    fontSize: 12.5, color: AppColors.black)),
                const SizedBox(height: 4),
                PpeeText(_money(order['netItemsAmount']), style: const TextStyle(
                  fontSize: 12, color: AppColors.gray700)),
              ])),
              const SizedBox(width: 5),
              Flexible(child: PpeeText(_status('${order['status']}'),
                textAlign: TextAlign.right, style: const TextStyle(
                  color: AppColors.primaryDark, fontSize: 11,
                  fontWeight: FontWeight.w800))),
            ])),
        ),
        if (order != orders.last) const Divider(height: 1),
      ],
    ]),
  );

  Widget _stockPanel(List<Map<String, dynamic>> lowStock) => SellerPanel(
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _sectionHeader('Theo dõi tồn kho', 'Sản phẩm đang bán có số lượng còn lại từ 5 trở xuống',
        Icons.inventory_2_outlined, open: widget.openProducts),
      const SizedBox(height: 14),
      if (lowStock.isEmpty)
        Container(width: double.infinity, padding: const EdgeInsets.all(15),
          decoration: BoxDecoration(color: AppColors.successSoft,
            borderRadius: BorderRadius.circular(12)),
          child: const Row(children: [
            Icon(Icons.check_circle_outline, color: AppColors.success),
            SizedBox(width: 10),
            Expanded(child: PpeeText('Chưa có sản phẩm ACTIVE nào có tồn kho từ 5 trở xuống.',
              style: TextStyle(color: AppColors.success, fontSize: 12.5))),
          ]))
      else for (final product in lowStock.take(5))
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const CircleAvatar(backgroundColor: AppColors.highlightSoft,
            child: Icon(Icons.priority_high_rounded, color: AppColors.highlight)),
          title: PpeeText('${product['name'] ?? 'Sản phẩm'}',
            style: const TextStyle(fontWeight: FontWeight.w700)),
          subtitle: PpeeText('SKU: ${product['sku'] ?? '—'}'),
          trailing: PpeeText('Còn ${product['quantity'] ?? 0}',
            style: const TextStyle(fontWeight: FontWeight.w900,
              color: AppColors.highlight)),
          onTap: widget.openProducts,
        ),
    ]),
  );
}

class _RevenueBars extends StatelessWidget {
  const _RevenueBars({required this.daily, required this.money});
  final List<Map<String, dynamic>> daily;
  final String Function(dynamic) money;

  @override
  Widget build(BuildContext context) {
    final values = daily.map((row) =>
      (num.tryParse('${row['revenue']}') ?? 0).toDouble()).toList();
    final maximum = math.max(1.0, values.fold<double>(0, math.max));
    return SizedBox(height: 190, child: LayoutBuilder(builder: (context, box) {
      const gap = 4.0;
      final width = math.min(36.0, math.max(9.0,
        (box.maxWidth - (daily.length - 1) * gap) / daily.length));
      return SingleChildScrollView(scrollDirection: Axis.horizontal,
        child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          for (var i = 0; i < daily.length; i++)
            Padding(padding: EdgeInsets.only(right: i == daily.length - 1 ? 0 : gap),
              child: Tooltip(
                message: '${daily[i]['date']}: ${money(daily[i]['revenue'])}',
                child: Column(mainAxisAlignment: MainAxisAlignment.end, children: [
                  Container(
                    width: width,
                    height: math.max(3.0, values[i] / maximum * 157),
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(begin: Alignment.bottomCenter,
                        end: Alignment.topCenter,
                        colors: [AppColors.primary, Color(0xFF72B5FA)]),
                      borderRadius: BorderRadius.circular(6),
                    ),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(width: width, height: 12,
                    child: PpeeText(i == 0 || i == daily.length - 1 ||
                      i % (daily.length <= 7 ? 1 : 7) == 0
                        ? '${daily[i]['date']}'.split('-').skip(1).join('/') : '',
                      style: const TextStyle(fontSize: 9, color: AppColors.gray700),
                      maxLines: 1, textAlign: TextAlign.center)),
                ]),
              )),
        ]));
    }));
  }
}
