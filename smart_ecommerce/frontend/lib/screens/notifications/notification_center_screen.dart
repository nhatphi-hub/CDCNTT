import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/l10n/language_controller.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../providers/order_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/order_status_pill.dart';

/// Displays the user's real order status as activity. This does not invent
/// notification records or claim that the backend has a notification inbox.
class NotificationCenterScreen extends StatefulWidget {
  const NotificationCenterScreen({super.key});
  @override
  State<NotificationCenterScreen> createState() => _NotificationCenterScreenState();
}

class _NotificationCenterScreenState extends State<NotificationCenterScreen> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() { if (mounted) context.read<OrderProvider>().load(); });
  }

  @override
  Widget build(BuildContext context) {
    final orders = context.watch<OrderProvider>();
    return Scaffold(
      appBar: const CatalogHeader(),
      bottomNavigationBar: const CatalogBottomNav(current: AppRoutes.orders),
      body: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1010),
        child: ListView(padding: const EdgeInsets.all(24), children: [
          Row(children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: AppColors.primarySoft,
                  borderRadius: BorderRadius.circular(15)),
              child: const Icon(Icons.notifications_active_outlined,
                color: AppColors.primary, size: 24),
            ),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const PpeeText('Thông báo', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 24)),
              const SizedBox(height: 4),
              const PpeeText('Cập nhật trạng thái từ đơn mua của tài khoản này.',
                style: TextStyle(color: AppColors.gray700)),
            ])),
            IconButton(tooltip: tr(context, 'Làm mới'),
              onPressed: orders.loading ? null : orders.load,
              icon: const Icon(Icons.refresh_rounded)),
          ]),
          const SizedBox(height: 22),
          if (orders.loading && orders.orders.isEmpty)
            const Center(child: Padding(padding: EdgeInsets.all(40),
              child: CircularProgressIndicator()))
          else if (orders.error != null && orders.orders.isEmpty)
            Card(child: Padding(padding: const EdgeInsets.all(20), child: Column(children: [
              const PpeeText('Không thể tải đơn hàng.'),
              const SizedBox(height: 6),
              PpeeText(orders.error!),
              TextButton(onPressed: orders.load, child: const PpeeText('Thử lại')),
            ])))
          else if (orders.orders.isEmpty)
            const Card(child: Padding(padding: EdgeInsets.all(38),
              child: Center(child: PpeeText('Chưa có đơn hàng. Khi có đơn mua, trạng thái đơn sẽ xuất hiện ở đây.'))))
          else
            ...orders.orders.map((order) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Card(child: ListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 19, vertical: 11),
                leading: const CircleAvatar(backgroundColor: AppColors.primarySoft,
                    child: Icon(Icons.receipt_long_outlined, color: AppColors.primary)),
                title: PpeeText(order.orderCode,
                    style: const TextStyle(fontWeight: FontWeight.w800)),
                subtitle: const PpeeText('Xem thông tin và trạng thái đơn hàng'),
                trailing: OrderStatusPill(status: order.status),
                onTap: () => Navigator.pushNamed(context, '${AppRoutes.order}/${order.id}'),
              )),
            )),
          const SizedBox(height: 12),
          const PpeeText('Lưu ý: Đây là trạng thái đơn hàng thực tế, không phải lịch sử thông báo đẩy hoặc trạng thái đã đọc.' ,
            style: TextStyle(fontSize: 12, color: AppColors.gray700)),
        ]),
      )),
    );
  }
}
