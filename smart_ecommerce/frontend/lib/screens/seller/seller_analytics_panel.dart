import '../../core/l10n/language_controller.dart';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../repositories/seller_order_repository.dart';
import '../../widgets/seller_ui.dart';

/// Analytics is entirely seller-API-backed, with no client-generated revenue.
class SellerAnalyticsPanel extends StatefulWidget {
  const SellerAnalyticsPanel({super.key});
  @override
  State<SellerAnalyticsPanel> createState() => _SellerAnalyticsPanelState();
}

class _SellerAnalyticsPanelState extends State<SellerAnalyticsPanel> {
  int _days = 30;
  late Future<Map<String, dynamic>> _future;

  @override
  void initState() { super.initState(); _reload(); }
  void _reload() => _future = context.read<SellerOrderRepository>().analytics(days: _days);

  String _money(dynamic raw) {
    final value = num.tryParse('$raw') ?? 0;
    final text = value.toStringAsFixed(0);
    final formatted = text.replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (match) => '${match[1]}.');
    return '$formatted đ';
  }

  String _status(String value) => switch (value) {
    'PENDING' => 'Chờ xác nhận',
    'CONFIRMED' => 'Đã xác nhận',
    'READY' => 'Đã chuẩn bị',
    'SHIPPING' => 'Đang giao',
    'DELIVERED' => 'Đã giao',
    'CANCELLED' => 'Đã hủy',
    _ => value,
  };

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      SellerPageIntro(title: 'Phân tích kinh doanh',
        subtitle: 'Dữ liệu doanh thu, đơn hàng và sản phẩm bán chạy theo từng giai đoạn.',
        icon: Icons.query_stats_rounded,
        actions: [
          SizedBox(width: 145, child: DropdownButtonFormField<int>(
            initialValue: _days,
            isExpanded: true,
            decoration: InputDecoration(labelText: tr(context, 'Khoảng thời gian'),
              contentPadding: EdgeInsets.symmetric(horizontal: 13, vertical: 10)),
            items: const [
              DropdownMenuItem(value: 7, child: PpeeText('7 ngày')),
              DropdownMenuItem(value: 30, child: PpeeText('30 ngày')),
              DropdownMenuItem(value: 90, child: PpeeText('90 ngày')),
            ],
            onChanged: (value) {
              if (value == null) return;
              setState(() { _days = value; _reload(); });
            },
          )),
          OutlinedButton.icon(onPressed: () => setState(_reload),
            icon: const Icon(Icons.refresh_rounded, size: 18),
            label: const PpeeText('Làm mới')),
        ]),
      const SizedBox(height: 15),
      const SellerInfoNotice(
        message: 'Doanh thu chỉ tính tiền hàng của shop trong đơn đã giao và đã thanh toán, sau giảm giá phân bổ; không bao gồm phí vận chuyển. Biểu đồ sử dụng ngày đặt đơn.',
        compact: true),
      const SizedBox(height: 16),
      FutureBuilder<Map<String, dynamic>>(future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const SellerPanel(child: Padding(
              padding: EdgeInsets.all(44), child: Center(child: CircularProgressIndicator())));
          }
          if (snapshot.hasError) {
            final e = snapshot.error;
            final reason = e is ApiException ? 'HTTP ${e.statusCode}: ${e.message}' : '$e';
            return SellerEmptyState(title: 'Không tải được thống kê',
              message: reason, icon: Icons.wifi_off_outlined,
              action: OutlinedButton(onPressed: () => setState(_reload),
                child: const PpeeText('Thử lại')));
          }
          final data = snapshot.data!;
          final statuses = Map<String, dynamic>.from(data['statusCounts'] as Map? ?? {});
          final daily = (data['dailyRevenue'] as List? ?? [])
            .map((e) => Map<String, dynamic>.from(e as Map)).toList();
          final products = (data['topProducts'] as List? ?? [])
            .map((e) => Map<String, dynamic>.from(e as Map)).toList();
          return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SellerMetricGrid(children: [
              SellerMetric(label: 'Đơn hoàn tất', value: '${data['completedOrders']}',
                icon: Icons.check_circle_outline_rounded, color: AppColors.success),
              SellerMetric(label: 'Sản phẩm đã bán', value: '${data['unitsSold']}',
                icon: Icons.shopping_bag_outlined, color: AppColors.accent),
              SellerMetric(label: 'Doanh thu đã ghi nhận',
                value: _money(data['paidDeliveredRevenue']),
                icon: Icons.account_balance_wallet_outlined),
            ]),
            const SizedBox(height: 15),
            SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              SellerSectionTitle(title: 'Biến động doanh thu',
                detail: 'Theo ngày đặt đơn trong $_days ngày gần nhất',
                icon: Icons.show_chart_rounded),
              const SizedBox(height: 22),
              if (daily.isEmpty)
                const SellerEmptyState(title: 'Chưa có doanh thu',
                  message: 'Biểu đồ sẽ xuất hiện khi có đơn đủ điều kiện ghi nhận.',
                  icon: Icons.insights_outlined)
              else
                _DailyBars(data: daily),
            ])),
            const SizedBox(height: 15),
            LayoutBuilder(builder: (context, size) {
              final statusCard = _statusPanel(statuses);
              final productsCard = _productsPanel(products);
              if (size.maxWidth < 780) return Column(children: [
                statusCard, const SizedBox(height: 14), productsCard,
              ]);
              return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(child: statusCard), const SizedBox(width: 14),
                Expanded(child: productsCard),
              ]);
            }),
          ]);
        }),
    ]);

  Widget _statusPanel(Map<String, dynamic> statuses) {
    final total = statuses.values.fold<num>(0, (sum, v) =>
      sum + (num.tryParse('$v') ?? 0));
    return SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const SellerSectionTitle(title: 'Tình trạng đơn hàng',
        detail: 'Tỷ trọng theo trạng thái', icon: Icons.donut_large_rounded),
      const SizedBox(height: 22),
      if (statuses.isEmpty)
        const SellerEmptyState(title: 'Chưa có đơn bán',
          message: 'Chưa có dữ liệu trong khoảng được chọn.',
          icon: Icons.receipt_long_outlined)
      else
        for (final e in statuses.entries) Padding(
          padding: const EdgeInsets.only(bottom: 15),
          child: Column(children: [
            Row(children: [
              Expanded(child: PpeeText(_status(e.key),
                style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5))),
              PpeeText('${e.value}', style: const TextStyle(color: AppColors.black,
                fontWeight: FontWeight.w900)),
            ]),
            const SizedBox(height: 8),
            ClipRRect(borderRadius: BorderRadius.circular(20),
              child: LinearProgressIndicator(
                minHeight: 9,
                value: total == 0 ? 0 :
                  ((num.tryParse('${e.value}') ?? 0) / total).clamp(0.0, 1.0).toDouble(),
                backgroundColor: AppColors.gray100,
                color: e.key == 'DELIVERED' ? AppColors.success
                  : e.key == 'CANCELLED' ? AppColors.error : AppColors.primary,
              )),
          ])),
    ]));
  }

  Widget _productsPanel(List<Map<String, dynamic>> products) => SellerPanel(
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const SellerSectionTitle(title: 'Sản phẩm bán chạy',
        detail: 'Đơn đã giao và thanh toán', icon: Icons.emoji_events_outlined),
      const SizedBox(height: 17),
      if (products.isEmpty)
        const SellerEmptyState(title: 'Chưa có sản phẩm đủ điều kiện',
          message: 'Khi có giao dịch hoàn tất, sản phẩm bán chạy sẽ hiển thị ở đây.',
          icon: Icons.inventory_2_outlined)
      else
        for (var i = 0; i < products.length; i++) Container(
          padding: const EdgeInsets.symmetric(vertical: 11),
          decoration: const BoxDecoration(border: Border(
            bottom: BorderSide(color: AppColors.gray300))),
          child: Row(children: [
            Container(width: 35, height: 35, alignment: Alignment.center,
              decoration: BoxDecoration(color: i == 0
                ? AppColors.highlightSoft : AppColors.primarySoft,
                borderRadius: BorderRadius.circular(11)),
              child: PpeeText('${i + 1}', style: TextStyle(fontWeight: FontWeight.w900,
                color: i == 0 ? AppColors.highlight : AppColors.primary))),
            const SizedBox(width: 11),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                PpeeText('${products[i]['productName']}', maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w800)),
                PpeeText('${products[i]['unitsSold']} đã bán',
                  style: const TextStyle(fontSize: 11.5, color: AppColors.gray700)),
              ])),
            const SizedBox(width: 8),
            PpeeText(_money(products[i]['revenue']),
              style: const TextStyle(color: AppColors.primaryDark,
                fontWeight: FontWeight.w900)),
          ]),
        ),
    ]));
}

class _DailyBars extends StatelessWidget {
  const _DailyBars({required this.data});
  final List<Map<String, dynamic>> data;

  @override
  Widget build(BuildContext context) {
    final values = data.map((e) =>
      (num.tryParse('${e['revenue']}') ?? 0).toDouble()).toList();
    final ceiling = math.max(1.0, values.fold<double>(0, math.max));
    return SizedBox(height: 238, child: LayoutBuilder(builder: (context, constraints) {
      final barWidth = math.max(10.0,
        math.min(36.0, constraints.maxWidth / (data.length * 1.45)));
      return SingleChildScrollView(scrollDirection: Axis.horizontal, child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [for (var i = 0; i < data.length; i++) Padding(
          padding: const EdgeInsets.only(right: 6), child: Tooltip(
            message: '${data[i]['date']}: ${data[i]['revenue']} đ',
            child: Column(mainAxisAlignment: MainAxisAlignment.end, children: [
              Container(width: barWidth,
                height: math.max(3.0, (values[i] / ceiling) * 194),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [AppColors.primaryDeep, AppColors.primary, AppColors.accent]),
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(6)))),
              const SizedBox(height: 8),
              if (i == 0 || i == data.length - 1 ||
                  i % (data.length <= 7 ? 1 : 7) == 0)
                PpeeText(_dayLabel('${data[i]['date']}'),
                  style: const TextStyle(fontSize: 9, color: AppColors.gray700))
              else const SizedBox(height: 11),
            ]),
          ),
        )],
      ));
    }));
  }

  String _dayLabel(String raw) => raw.length >= 10 ? raw.substring(5, 10) : raw;
}
