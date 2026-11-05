import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../repositories/seller_activity_repository.dart';
import '../../widgets/seller_ui.dart';

/// API returns an activity feed; it does not expose read/unread state.
class SellerNotificationsPanel extends StatefulWidget {
  const SellerNotificationsPanel({super.key});
  @override
  State<SellerNotificationsPanel> createState() => _SellerNotificationsPanelState();
}

class _SellerNotificationsPanelState extends State<SellerNotificationsPanel> {
  late Future<List<Map<String, dynamic>>> _future;
  String _category = 'ALL';

  @override
  void initState() { super.initState(); _load(); }
  void _load() => _future = context.read<SellerActivityRepository>().mine();

  String _group(String value) {
    if (value == 'NEW_REVIEW') return 'REVIEWS';
    if (value.startsWith('RETURN_')) return 'RETURNS';
    if (value.contains('ORDER') || value.contains('PAID')) return 'ORDERS';
    return 'OTHER';
  }

  String _label(String value) => switch (value) {
    'ALL' => 'Tất cả',
    'ORDERS' => 'Đơn hàng',
    'REVIEWS' => 'Đánh giá',
    'RETURNS' => 'Trả hàng',
    'OTHER' => 'Khác',
    _ => value,
  };

  IconData _icon(String group) => switch (group) {
    'ORDERS' => Icons.receipt_long_outlined,
    'REVIEWS' => Icons.star_outline_rounded,
    'RETURNS' => Icons.assignment_return_outlined,
    _ => Icons.notifications_outlined,
  };

  Color _color(String group) => switch (group) {
    'ORDERS' => AppColors.primary,
    'REVIEWS' => AppColors.warning,
    'RETURNS' => AppColors.highlight,
    _ => AppColors.accent,
  };

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      SellerPageIntro(title: 'Trung tâm thông báo',
        subtitle: 'Theo dõi các sự kiện phát sinh từ đơn hàng, đánh giá và yêu cầu trả hàng.',
        icon: Icons.notifications_active_outlined,
        actions: [OutlinedButton.icon(onPressed: () => setState(_load),
          icon: const Icon(Icons.refresh_rounded, size: 18),
          label: const PpeeText('Cập nhật'))]),
      const SizedBox(height: 14),
      const SellerInfoNotice(compact: true,
        message: 'Đây là dòng hoạt động thực tế của shop. Dữ liệu được cập nhật khi nhấn Cập nhật; hệ thống hiện chưa có trạng thái đã đọc/chưa đọc.'),
      const SizedBox(height: 15),
      FutureBuilder<List<Map<String, dynamic>>>(future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const SellerPanel(child: Padding(padding: EdgeInsets.all(45),
              child: Center(child: CircularProgressIndicator())));
          }
          if (snapshot.hasError) return SellerEmptyState(
            title: 'Không tải được thông báo', message: '${snapshot.error}',
            icon: Icons.wifi_off_outlined,
            action: OutlinedButton(onPressed: () => setState(_load),
              child: const PpeeText('Thử lại')));
          final activities = snapshot.data ?? <Map<String, dynamic>>[];
          final filtered = activities.where((a) => _category == 'ALL' ||
            _group('${a['type'] ?? ''}') == _category).toList();
          final orderCount = activities.where(
            (a) => _group('${a['type']}') == 'ORDERS').length;
          final reviewCount = activities.where(
            (a) => _group('${a['type']}') == 'REVIEWS').length;
          return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SellerMetricGrid(children: [
              SellerMetric(label: 'Tổng hoạt động', value: '${activities.length}',
                icon: Icons.notifications_outlined),
              SellerMetric(label: 'Liên quan đơn hàng',
                value: '$orderCount',
                icon: Icons.receipt_long_outlined, color: AppColors.accent),
              SellerMetric(label: 'Đánh giá mới',
                value: '$reviewCount',
                icon: Icons.star_outline_rounded, color: AppColors.warning),
            ]),
            const SizedBox(height: 15),
            SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const SellerSectionTitle(title: 'Dòng hoạt động',
                detail: 'Lọc theo loại sự kiện', icon: Icons.timeline_rounded),
              const SizedBox(height: 14),
              Wrap(spacing: 8, runSpacing: 8, children: [
                for (final group in ['ALL', 'ORDERS', 'REVIEWS', 'RETURNS', 'OTHER'])
                  ChoiceChip(label: PpeeText(_label(group)),
                    selected: _category == group,
                    onSelected: (_) => setState(() => _category = group)),
              ]),
            ])),
            const SizedBox(height: 15),
            if (activities.isEmpty)
              const SellerEmptyState(title: 'Chưa có thông báo',
                message: 'Khi shop có hoạt động mới, sự kiện sẽ hiển thị tại đây.',
                icon: Icons.notifications_none_outlined)
            else if (filtered.isEmpty)
              const SellerEmptyState(title: 'Chưa có hoạt động phù hợp',
                message: 'Hãy chọn loại sự kiện khác.',
                icon: Icons.filter_alt_off_outlined)
            else
              SellerPanel(padding: const EdgeInsets.symmetric(horizontal: 19, vertical: 8),
                child: Column(children: [
                  for (var i = 0; i < filtered.length; i++) ...[
                    _activityTile(filtered[i]),
                    if (i < filtered.length - 1)
                      const Divider(height: 1, color: AppColors.gray300),
                  ],
                ])),
          ]);
        }),
    ]);

  Widget _activityTile(Map<String, dynamic> activity) {
    final group = _group('${activity['type'] ?? ''}');
    final color = _color(group);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(width: 45, height: 45,
          decoration: BoxDecoration(color: color.withValues(alpha: .10),
            borderRadius: BorderRadius.circular(14)),
          child: Icon(_icon(group), color: color, size: 23)),
        const SizedBox(width: 13),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(spacing: 9, runSpacing: 7,
              crossAxisAlignment: WrapCrossAlignment.center, children: [
                PpeeText('${activity['title'] ?? 'Hoạt động mới'}',
                  style: const TextStyle(color: AppColors.black,
                    fontWeight: FontWeight.w800, fontSize: 13.5)),
                SellerStatusBadge(label: _label(group), color: color),
              ]),
            const SizedBox(height: 6),
            PpeeText('${activity['message'] ?? ''}',
              style: const TextStyle(color: AppColors.charcoal, height: 1.55)),
            const SizedBox(height: 7),
            Row(children: [
              const Icon(Icons.schedule_outlined, size: 14, color: AppColors.gray700),
              const SizedBox(width: 5),
              Expanded(child: PpeeText('${activity['createdAt'] ?? ''}',
                style: const TextStyle(fontSize: 11, color: AppColors.gray700))),
            ]),
          ])),
      ]),
    );
  }
}
