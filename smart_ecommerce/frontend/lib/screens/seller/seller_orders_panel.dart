import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../repositories/seller_order_repository.dart';
import '../../widgets/seller_ui.dart';

/// The order workflow, pagination and PATCH payloads are unchanged.
class SellerOrdersPanel extends StatefulWidget {
  const SellerOrdersPanel({super.key});
  @override
  State<SellerOrdersPanel> createState() => _SellerOrdersPanelState();
}

class _SellerOrdersPanelState extends State<SellerOrdersPanel> {
  Future<Map<String, dynamic>>? _future;
  String _status = '';
  int _page = 0;
  String? _busy;
  String? _message;

  static const _statuses = <String, String>{
    '': 'Tất cả trạng thái',
    'PENDING': 'Chờ xác nhận',
    'CONFIRMED': 'Đã xác nhận',
    'READY': 'Đã chuẩn bị',
    'SHIPPING': 'Đang giao',
    'DELIVERED': 'Đã giao',
    'CANCELLED': 'Đã hủy',
  };

  @override
  void initState() { super.initState(); _load(); }
  void _load() {
    _future = context.read<SellerOrderRepository>().list(status: _status, page: _page);
  }

  Future<void> _change(String orderId, String next) async {
    setState(() { _busy = orderId; _message = null; });
    try {
      await context.read<SellerOrderRepository>().changeStatus(orderId, next);
      if (!mounted) return;
      setState(() { _busy = null; _message = 'Cập nhật trạng thái đơn hàng thành công.'; _load(); });
    } catch (e) {
      if (!mounted) return;
      setState(() { _busy = null;
        _message = e is ApiException ? e.message : 'Không thể cập nhật đơn hàng'; });
    }
  }

  String _money(dynamic amount) {
    final n = num.tryParse('$amount') ?? 0;
    final s = n.toStringAsFixed(0);
    final formatted = s.replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.');
    return '$formatted đ';
  }

  Color _statusColor(String s) => switch(s) {
    'DELIVERED' => AppColors.success,
    'CANCELLED' => AppColors.error,
    'PENDING' => AppColors.highlight,
    'SHIPPING' => AppColors.accent,
    _ => AppColors.primary,
  };

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      SellerPageIntro(
        title: 'Quản lý đơn bán',
        subtitle: 'Theo dõi và xử lý đơn thuộc gian hàng. Thông tin thanh toán và vận chuyển do hệ thống quản lý.',
        icon: Icons.receipt_long_rounded,
        actions: [OutlinedButton.icon(onPressed: () => setState(_load),
          icon: const Icon(Icons.refresh_rounded, size: 18), label: const PpeeText('Làm mới'))],
      ),
      const SizedBox(height: 17),
      SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const SellerSectionTitle(title: 'Bộ lọc đơn hàng',
          detail: 'Lọc theo trạng thái xử lý của shop', icon: Icons.filter_alt_outlined),
        const SizedBox(height: 15),
        LayoutBuilder(builder: (context, size) => SizedBox(
          width: size.maxWidth < 380 ? size.maxWidth : 350,
          child: DropdownButtonFormField<String>(
            initialValue: _status,
            isExpanded: true,
            decoration: InputDecoration(labelText: tr(context, 'Trạng thái đơn hàng'),
              prefixIcon: Icon(Icons.tune_rounded)),
            items: _statuses.entries.map((e) => DropdownMenuItem<String>(
              value: e.key, child: PpeeText(e.value))).toList(),
            onChanged: (v) => setState(() { _status = v ?? ''; _page = 0; _load(); }),
          ),
        )),
      ])),
      if (_message != null) ...[
        const SizedBox(height: 12),
        SellerInfoNotice(message: _message!),
      ],
      const SizedBox(height: 16),
      FutureBuilder<Map<String, dynamic>>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const SellerPanel(child: Padding(
              padding: EdgeInsets.symmetric(vertical: 42),
              child: Center(child: CircularProgressIndicator())));
          }
          if (snapshot.hasError) {
            final e = snapshot.error;
            final reason = e is ApiException
              ? 'API ${e.statusCode}: ${e.message}'
              : 'Không thể tải đơn hàng. Kiểm tra kết nối và đăng nhập.';
            return SellerEmptyState(title: 'Không thể tải đơn bán',
              message: reason, icon: Icons.wifi_off_rounded,
              action: OutlinedButton.icon(onPressed: () => setState(_load),
                icon: const Icon(Icons.refresh), label: const PpeeText('Thử lại')));
          }
          final response = snapshot.data!;
          final orders = (response['content'] as List? ?? const []);
          if (orders.isEmpty) {
            return SellerEmptyState(title: 'Chưa có đơn bán',
              message: _status.isEmpty
                ? 'Khi khách đặt hàng, đơn sẽ hiển thị tại đây.'
                : 'Không có đơn ở trạng thái đã chọn.',
              icon: Icons.receipt_long_outlined);
          }
          final last = response['last'] == true;
          return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SellerPanel(padding: const EdgeInsets.all(16), child: Row(children: [
              const Icon(Icons.list_alt_rounded, color: AppColors.primary, size: 20),
              const SizedBox(width: 9),
              Expanded(child: PpeeText('${orders.length} đơn trên trang ${_page + 1}',
                style: const TextStyle(color: AppColors.black, fontWeight: FontWeight.w800))),
              SellerStatusBadge(label: _status.isEmpty ? 'Tất cả' : _statuses[_status] ?? _status),
            ])),
            const SizedBox(height: 13),
            for (final raw in orders) _orderCard(Map<String, dynamic>.from(raw as Map)),
            const SizedBox(height: 3),
            Wrap(alignment: WrapAlignment.end, crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8, runSpacing: 8, children: [
                SellerStatusBadge(label: 'Trang ${_page + 1}', icon: Icons.layers_outlined),
                OutlinedButton.icon(
                  onPressed: _page == 0 ? null : () => setState(() { _page--; _load(); }),
                  icon: const Icon(Icons.chevron_left_rounded), label: const PpeeText('Trước')),
                OutlinedButton.icon(
                  onPressed: last ? null : () => setState(() { _page++; _load(); }),
                  icon: const Icon(Icons.chevron_right_rounded), label: const PpeeText('Sau')),
              ]),
          ]);
        },
      ),
    ],
  );

  Widget _orderCard(Map<String, dynamic> order) {
    final id = '${order['orderId']}';
    final state = '${order['status']}';
    final products = (order['items'] as List? ?? const []);
    final next = state == 'PENDING' ? 'CONFIRMED' : state == 'CONFIRMED' ? 'READY' : null;
    final label = state == 'PENDING' ? 'Xác nhận đơn' : 'Đã chuẩn bị hàng';
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: SellerPanel(padding: EdgeInsets.zero, child: Column(
        crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            decoration: const BoxDecoration(color: Color(0xFFF6F9FF),
              borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
            child: Wrap(alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 12, runSpacing: 9, children: [
                Row(mainAxisSize: MainAxisSize.min, children: [
                  const Icon(Icons.receipt_long_outlined, color: AppColors.primary, size: 19),
                  const SizedBox(width: 9),
                  Flexible(child: PpeeText('${order['orderCode']}',
                    style: const TextStyle(color: AppColors.black,
                      fontWeight: FontWeight.w900, fontSize: 15))),
                ]),
                SellerStatusBadge(label: _statuses[state] ?? state, color: _statusColor(state)),
              ]),
          ),
          Padding(padding: const EdgeInsets.all(19), child: Column(
            crossAxisAlignment: CrossAxisAlignment.start, children: [
              Wrap(spacing: 9, runSpacing: 8, children: [
                SellerStatusBadge(icon: Icons.payments_outlined,
                  label: 'Thanh toán: ${order['paymentStatus'] ?? '—'}',
                  color: AppColors.charcoal),
                SellerStatusBadge(icon: Icons.inventory_2_outlined,
                  label: '${products.length} dòng sản phẩm'),
              ]),
              const SizedBox(height: 15),
              for (final raw in products) Padding(
                padding: const EdgeInsets.only(bottom: 11),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Container(width: 31, height: 31,
                    decoration: BoxDecoration(color: AppColors.primarySoft,
                      borderRadius: BorderRadius.circular(9)),
                    child: const Icon(Icons.shopping_bag_outlined,
                      color: AppColors.primary, size: 17)),
                  const SizedBox(width: 10),
                  Expanded(child: PpeeText('${(raw as Map)['productName']} × ${raw['quantity']}',
                    style: const TextStyle(fontWeight: FontWeight.w600))),
                  const SizedBox(width: 10),
                  PpeeText(_money(raw['subtotal']),
                    style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.black)),
                ]),
              ),
              const Divider(height: 22),
              Wrap(alignment: WrapAlignment.spaceBetween,
                crossAxisAlignment: WrapCrossAlignment.center, runSpacing: 8,
                spacing: 12, children: [
                  const PpeeText('Tiền hàng shop sau phân bổ giảm giá',
                    style: TextStyle(color: AppColors.gray700, fontSize: 12)),
                  PpeeText(_money(order['netItemsAmount']),
                    style: const TextStyle(fontSize: 19,
                      color: AppColors.primaryDark, fontWeight: FontWeight.w900)),
                ]),
              const SizedBox(height: 5),
              ExpansionTile(
                tilePadding: EdgeInsets.zero,
                childrenPadding: EdgeInsets.zero,
                leading: const Icon(Icons.local_shipping_outlined, color: AppColors.primary),
                title: const PpeeText('Thông tin nhận hàng',
                  style: TextStyle(fontWeight: FontWeight.w800, fontSize: 13)),
                children: [ListTile(
                  title: PpeeText('${order['recipientName'] ?? ''} • ${order['recipientPhone'] ?? ''}'),
                  subtitle: PpeeText('${order['address'] ?? ''}'),
                )],
              ),
              if (next != null) ...[
                const SizedBox(height: 10),
                Align(alignment: Alignment.centerRight,
                  child: FilledButton.icon(
                    onPressed: _busy != null ? null : () => _change(id, next),
                    icon: const Icon(Icons.check_circle_outline_rounded, size: 18),
                    label: PpeeText(_busy == id ? 'Đang cập nhật...' : label),
                  )),
              ],
            ])),
        ])),
    );
  }
}

/// Keep the existing optional stats panel working for other callers.
class SellerOrderStatsPanel extends StatefulWidget {
  const SellerOrderStatsPanel({super.key});
  @override
  State<SellerOrderStatsPanel> createState() => _SellerOrderStatsPanelState();
}

class _SellerOrderStatsPanelState extends State<SellerOrderStatsPanel> {
  late Future<Map<String, dynamic>> _future;
  @override
  void initState() { super.initState(); _load(); }
  void _load() { _future = context.read<SellerOrderRepository>().stats(); }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      SellerSectionTitle(title: 'Thống kê đơn hàng thực tế',
        icon: Icons.analytics_outlined,
        trailing: IconButton(onPressed: () => setState(_load),
          tooltip: tr(context, 'Tải lại'), icon: const Icon(Icons.refresh))),
      const SizedBox(height: 9),
      const SellerInfoNotice(message: 'Doanh thu chỉ tính tiền hàng thuộc shop của đơn đã giao và đã thanh toán; không bao gồm phí vận chuyển.', compact: true),
      const SizedBox(height: 14),
      FutureBuilder<Map<String, dynamic>>(future: _future, builder: (context, snapshot) {
        if (!snapshot.hasData) {
          return SellerEmptyState(
            title: snapshot.hasError ? 'Không thể tải thống kê' : 'Đang tải thống kê',
            message: snapshot.hasError ? '${snapshot.error}' : 'Dữ liệu lấy trực tiếp từ gian hàng.',
            icon: snapshot.hasError ? Icons.wifi_off_outlined : Icons.insights_outlined);
        }
        final s = snapshot.data!;
        final entries = <Widget>[
          SellerMetric(label: 'Tổng đơn', value: '${s['totalOrders']}', icon: Icons.receipt_long_outlined),
          SellerMetric(label: 'Số lượng đã đặt', value: '${s['unitsSold']}', icon: Icons.shopping_bag_outlined),
          SellerMetric(label: 'Doanh thu đã giao/đã trả', value: '${s['paidDeliveredRevenue']} đ', icon: Icons.payments_outlined),
          ...((s['statusCounts'] as Map?) ?? {}).entries.map((e) => SellerMetric(
            label: _SellerOrdersPanelState._statuses['${e.key}'] ?? '${e.key}',
            value: '${e.value}', icon: Icons.circle_outlined)),
        ];
        return SellerMetricGrid(children: entries);
      }),
    ]);
}
