import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import '../core/theme/app_colors.dart';

class OrderStatusPill extends StatelessWidget {
  const OrderStatusPill({super.key, required this.status});
  final String status;
  static String labelFor(String status) {
    switch (status.toUpperCase()) {
      case 'PENDING': return 'Chờ xác nhận';
      case 'CONFIRMED': return 'Đã xác nhận';
      case 'SHIPPING': return 'Đang giao';
      case 'DELIVERED': return 'Đã giao';
      case 'CANCELLED': return 'Đã hủy';
      default: return status;
    }
  }
  @override
  Widget build(BuildContext context) {
    final state = status.toUpperCase();
    final Color color = switch (state) {
      'PENDING' => AppColors.warning,
      'CONFIRMED' || 'SHIPPING' => AppColors.primary,
      'DELIVERED' => AppColors.success,
      'CANCELLED' => AppColors.error,
      _ => AppColors.gray700,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
      decoration: BoxDecoration(color: color.withValues(alpha: .10), borderRadius: BorderRadius.circular(99)),
      child: PpeeText(labelFor(status), style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w700)),
    );
  }
}
