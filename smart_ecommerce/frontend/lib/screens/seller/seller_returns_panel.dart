import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../repositories/shop_return_repository.dart';
import '../../widgets/seller_ui.dart';

class SellerReturnsPanel extends StatefulWidget {
  const SellerReturnsPanel({super.key});
  @override
  State<SellerReturnsPanel> createState() => _SellerReturnsPanelState();
}

class _SellerReturnsPanelState extends State<SellerReturnsPanel> {
  late Future<List<Map<String, dynamic>>> _future;
  bool _updating = false;
  String _filter = 'ALL';
  String _search = '';

  @override
  void initState() { super.initState(); _load(); }
  void _load() { _future = context.read<ShopReturnRepository>().seller(); }

  String _statusLabel(String status) => switch (status) {
    'REQUESTED' => 'Chờ tiếp nhận',
    'ACKNOWLEDGED' => 'Đã tiếp nhận',
    'DISPUTED' => 'Cần xác minh thêm',
    _ => status,
  };

  Color _statusColor(String status) => switch (status) {
    'REQUESTED' => AppColors.highlight,
    'ACKNOWLEDGED' => AppColors.success,
    'DISPUTED' => AppColors.warning,
    _ => AppColors.gray700,
  };

  Future<void> _status(String id, String status) async {
    if (_updating) return;
    setState(() => _updating = true);
    try {
      final repository = context.read<ShopReturnRepository>();
      await repository.update(id, status);
      final next = repository.seller();
      if (!mounted) return;
      setState(() => _future = next);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: PpeeText(status == 'ACKNOWLEDGED'
          ? 'Đã tiếp nhận yêu cầu. Đây chưa phải phê duyệt trả hàng hay hoàn tiền.'
          : 'Đã chuyển yêu cầu sang trạng thái cần xác minh thêm.'),
      ));
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: PpeeText(error is ApiException ? error.message
          : 'Không cập nhật được: $error'),
      ));
    } finally {
      if (mounted) setState(() => _updating = false);
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      SellerPageIntro(title: 'Yêu cầu trả hàng',
        subtitle: 'Theo dõi và phản hồi những yêu cầu hậu mãi liên quan đến shop.',
        icon: Icons.assignment_return_rounded,
        actions: [OutlinedButton.icon(onPressed: () => setState(_load),
          icon: const Icon(Icons.refresh_rounded, size: 18),
          label: const PpeeText('Làm mới'))]),
      const SizedBox(height: 14),
      const SellerInfoNotice(compact: true,
        message: 'Đã tiếp nhận chỉ ghi nhận shop đã xem yêu cầu, không có nghĩa là duyệt hoàn tiền. PPee hiện chưa tự thực hiện hoàn tiền.'),
      const SizedBox(height: 15),
      FutureBuilder<List<Map<String, dynamic>>>(future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const SellerPanel(child: Padding(padding: EdgeInsets.all(45),
              child: Center(child: CircularProgressIndicator())));
          }
          if (snapshot.hasError) return SellerEmptyState(
            title: 'Không tải được yêu cầu trả hàng',
            message: '${snapshot.error}', icon: Icons.wifi_off_outlined,
            action: OutlinedButton(onPressed: () => setState(_load),
              child: const PpeeText('Thử lại')));
          final requests = snapshot.data ?? <Map<String, dynamic>>[];
          final filtered = requests.where((r) =>
            (_filter == 'ALL' || '${r['status']}' == _filter) &&
            '${r['orderCode'] ?? ''} ${r['reason'] ?? ''}'
              .toLowerCase().contains(_search.toLowerCase().trim())).toList();
          return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SellerMetricGrid(children: [
              SellerMetric(label: 'Tất cả yêu cầu', value: '${requests.length}',
                icon: Icons.assignment_return_outlined),
              SellerMetric(label: 'Chờ tiếp nhận',
                value: '${requests.where((r) => r['status'] == 'REQUESTED').length}',
                icon: Icons.pending_actions_outlined, color: AppColors.highlight),
              SellerMetric(label: 'Đã tiếp nhận',
                value: '${requests.where((r) => r['status'] == 'ACKNOWLEDGED').length}',
                icon: Icons.task_alt_outlined, color: AppColors.success),
              SellerMetric(label: 'Cần xác minh',
                value: '${requests.where((r) => r['status'] == 'DISPUTED').length}',
                icon: Icons.fact_check_outlined, color: AppColors.warning),
            ]),
            const SizedBox(height: 15),
            SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SellerSectionTitle(title: 'Danh sách yêu cầu',
                  detail: 'Lọc theo trạng thái hoặc mã đơn hàng',
                  icon: Icons.filter_alt_outlined),
                const SizedBox(height: 14),
                LayoutBuilder(builder: (context, size) => Wrap(
                  spacing: 12, runSpacing: 9,
                  crossAxisAlignment: WrapCrossAlignment.center, children: [
                    SizedBox(width: size.maxWidth < 450 ? size.maxWidth : 310,
                      child: TextField(onChanged: (v) => setState(() => _search = v),
                        decoration: InputDecoration(hintText: tr(context, 'Tìm đơn hàng, lý do...'),
                          prefixIcon: Icon(Icons.search_rounded)))),
                    for (final status in ['ALL', 'REQUESTED', 'ACKNOWLEDGED', 'DISPUTED'])
                      ChoiceChip(
                        label: PpeeText(status == 'ALL' ? 'Tất cả' : _statusLabel(status)),
                        selected: _filter == status,
                        onSelected: (_) => setState(() => _filter = status)),
                  ])),
              ])),
            const SizedBox(height: 14),
            if (requests.isEmpty)
              const SellerEmptyState(title: 'Chưa có yêu cầu trả hàng',
                message: 'Các yêu cầu hậu mãi của khách sẽ xuất hiện tại đây.',
                icon: Icons.assignment_return_outlined)
            else if (filtered.isEmpty)
              const SellerEmptyState(title: 'Không có yêu cầu phù hợp',
                message: 'Hãy chọn bộ lọc khác hoặc xóa từ khóa tìm kiếm.',
                icon: Icons.filter_alt_off_outlined)
            else
              for (final request in filtered) _requestCard(request),
          ]);
        }),
    ]);

  Widget _requestCard(Map<String, dynamic> request) {
    final status = '${request['status']}';
    return Padding(padding: const EdgeInsets.only(bottom: 12),
      child: SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(spacing: 12, runSpacing: 9,
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center, children: [
              Row(mainAxisSize: MainAxisSize.min, children: [
                const Icon(Icons.receipt_long_outlined,
                  color: AppColors.primary, size: 20),
                const SizedBox(width: 8),
                PpeeText('${request['orderCode'] ?? '—'}',
                  style: const TextStyle(fontWeight: FontWeight.w900,
                    color: AppColors.black, fontSize: 15)),
              ]),
              SellerStatusBadge(label: _statusLabel(status),
                color: _statusColor(status)),
            ]),
          const Divider(height: 26),
          const PpeeText('LÝ DO TRẢ HÀNG', style: TextStyle(fontSize: 11,
            fontWeight: FontWeight.w900, color: AppColors.gray700,
            letterSpacing: .7)),
          const SizedBox(height: 5),
          PpeeText('${request['reason'] ?? '—'}', style: const TextStyle(height: 1.6)),
          if (request['sellerNote'] != null) ...[
            const SizedBox(height: 12),
            Container(width: double.infinity, padding: const EdgeInsets.all(13),
              decoration: BoxDecoration(color: AppColors.gray100,
                borderRadius: BorderRadius.circular(12)),
              child: PpeeText('Ghi chú: ${request['sellerNote']}',
                style: const TextStyle(color: AppColors.charcoal))),
          ],
          if (status == 'REQUESTED') ...[
            const SizedBox(height: 16),
            Wrap(spacing: 9, runSpacing: 9, children: [
              FilledButton.icon(
                onPressed: _updating ? null : () => _status('${request['id']}', 'ACKNOWLEDGED'),
                icon: const Icon(Icons.check_circle_outline_rounded, size: 18),
                label: const PpeeText('Đã tiếp nhận')),
              OutlinedButton.icon(
                onPressed: _updating ? null : () => _status('${request['id']}', 'DISPUTED'),
                icon: const Icon(Icons.fact_check_outlined, size: 18),
                label: const PpeeText('Cần xác minh thêm')),
            ]),
          ],
        ])));
  }
}
