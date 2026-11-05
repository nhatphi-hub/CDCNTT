import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/l10n/language_controller.dart';
import '../../core/network/api_exception.dart';
import '../../repositories/admin_console_repository.dart';
import '../../widgets/ppee_ui.dart';

class AdminOrderDetailScreen extends StatefulWidget {
  const AdminOrderDetailScreen({super.key, required this.orderId});
  final String orderId;

  @override
  State<AdminOrderDetailScreen> createState() => _AdminOrderDetailScreenState();
}

class _OrderWithShipping {
  const _OrderWithShipping(this.order, this.shipping);
  final Map<String, dynamic> order;
  final Map<String, dynamic>? shipping;
}

class _AdminOrderDetailScreenState extends State<AdminOrderDetailScreen> {
  late Future<_OrderWithShipping> _future;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _future = _fetch();
  }

  Future<_OrderWithShipping> _fetch() async {
    final repo = context.read<AdminConsoleRepository>();
    final order = await repo.orderDetail(widget.orderId);
    final shipping = await repo.shipping(widget.orderId);
    return _OrderWithShipping(order, shipping);
  }

  void _refresh() {
    final future = _fetch();
    setState(() {
      _future = future;
    });
  }

  Future<void> _shippingAction({String? shippingId, String? status}) async {
    if (_busy) return;
    final action = shippingId == null
        ? 'Tạo vận đơn mô phỏng'
        : 'Chuyển vận đơn sang $status';
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialog) => AlertDialog(
        title: PpeeText(action),
        content: const PpeeText(
          'Chỉ thực hiện sau khi xác minh hoạt động giao nhận. '
          'Với COD, đánh dấu DELIVERED có thể ghi nhận PAID theo nghiệp vụ backend.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialog, false),
            child: const PpeeText('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialog, true),
            child: const PpeeText('Xác nhận'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final repo = context.read<AdminConsoleRepository>();
    setState(() {
      _busy = true;
    });
    try {
      if (shippingId == null) {
        await repo.createShipping(widget.orderId);
      } else {
        await repo.setShippingStatus(shippingId, status!);
      }
      if (!mounted) return;
      _refresh();
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: PpeeText('Đã cập nhật: $action')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: PpeeText(e is ApiException ? e.message : '$e')),
      );
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const PpeeText('ADMIN · Chi tiết đơn và giao vận')),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 850),
        child: FutureBuilder<_OrderWithShipping>(
          future: _future,
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return PpeeStateView(
                title: 'Không thể tải chi tiết đơn',
                message: '${snapshot.error}',
                icon: Icons.wifi_off_rounded,
                actionLabel: 'Thử lại',
                onAction: _refresh,
              );
            }
            final data = snapshot.data!;
            final order = data.order;
            final shipping = data.shipping;
            final items = (order['items'] as List?) ?? const [];
            final shippingStatus = '${shipping?['shippingStatus'] ?? ''}';
            final status = '${order['status']}';
            final paymentStatus = '${order['paymentStatus']}';
            final paymentMethod = '${order['paymentMethod']}';
            final canShip =
                (status == 'CONFIRMED' || status == 'SHIPPING') &&
                (paymentStatus == 'PAID' || paymentMethod == 'COD');
            return ListView(
              padding: const EdgeInsets.all(20),
              children: [
                PpeeText(
                  'Đơn ${order['orderCode']}',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                PpeeText(
                  'Đơn: $status · Thanh toán: $paymentStatus ($paymentMethod)',
                ),
                PpeeText(
                  'Tiền đơn: ${order['totalAmount']} VND · Phí giao: ${order['shippingFee']} VND',
                ),
                const SizedBox(height: 15),
                PpeeText(
                  'Sản phẩm',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                for (final raw in items)
                  ListTile(
                    title: PpeeText('${(raw as Map)['productName']}'),
                    subtitle: PpeeText('SKU: ${raw['sku'] ?? '—'}'),
                    trailing: PpeeText('×${raw['quantity']}'),
                  ),
                const Divider(height: 30),
                PpeeText(
                  'Vận chuyển (mô phỏng)',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                if (shipping == null) ...[
                  const PpeeText('Chưa có vận đơn.'),
                  const SizedBox(height: 10),
                  FilledButton(
                    onPressed: !_busy && canShip
                        ? () => _shippingAction()
                        : null,
                    child: const PpeeText('Tạo vận đơn'),
                  ),
                ] else ...[
                  PpeeText(
                    'Mã: ${shipping['trackingCode']} · Trạng thái: $shippingStatus',
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      if (shippingStatus == 'READY' ||
                          shippingStatus == 'FAILED')
                        FilledButton.tonal(
                          onPressed: _busy
                              ? null
                              : () => _shippingAction(
                                  shippingId: '${shipping['id']}',
                                  status: 'IN_TRANSIT',
                                ),
                          child: const PpeeText('Bàn giao vận chuyển'),
                        ),
                      if (shippingStatus == 'IN_TRANSIT') ...[
                        FilledButton(
                          onPressed: _busy
                              ? null
                              : () => _shippingAction(
                                  shippingId: '${shipping['id']}',
                                  status: 'DELIVERED',
                                ),
                          child: const PpeeText('Xác nhận đã giao'),
                        ),
                        OutlinedButton(
                          onPressed: _busy
                              ? null
                              : () => _shippingAction(
                                  shippingId: '${shipping['id']}',
                                  status: 'FAILED',
                                ),
                          child: const PpeeText('Giao thất bại'),
                        ),
                      ],
                    ],
                  ),
                ],
                const SizedBox(height: 16),
                OutlinedButton.icon(
                  onPressed: _busy ? null : _refresh,
                  icon: const Icon(Icons.refresh),
                  label: const PpeeText('Tải lại trạng thái'),
                ),
                const SizedBox(height: 12),
                const PpeeText(
                  'ADMIN không được tự sửa payment_status. Thanh toán online phải qua callback đã xác minh; '
                  'COD chỉ được ghi nhận sau xác nhận giao thành công đáng tin cậy.',
                  style: TextStyle(fontSize: 12, color: Color(0xFF5B677A)),
                ),
              ],
            );
          },
        ),
      ),
    ),
  );
}
