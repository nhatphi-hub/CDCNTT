import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';

import '../core/routes/app_routes.dart';
import '../core/theme/app_colors.dart';
import 'ppee_ui.dart';

/// Shared seller presentation components. All actions receive callbacks from
/// their existing screens; no repository, JWT or API behaviour lives here.
class SellerScaffold extends StatelessWidget {
  const SellerScaffold({
    super.key,
    required this.child,
    this.maxWidth = 1180,
    this.shopName,
    this.onProducts,
    this.onOrders,
  });

  final Widget child;
  final double maxWidth;
  final String? shopName;
  final VoidCallback? onProducts;
  final VoidCallback? onOrders;

  void _home(BuildContext context) => Navigator.pushNamedAndRemoveUntil(
    context, AppRoutes.home, (_) => false,
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: AppColors.background,
    body: Column(children: [
      Container(
        height: 5,
        decoration: const BoxDecoration(gradient: LinearGradient(
          colors: [AppColors.primaryDeep, AppColors.primary, AppColors.accent],
        )),
      ),
      Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(bottom: BorderSide(color: AppColors.gray300)),
          boxShadow: [BoxShadow(color: Color(0x090F2347), blurRadius: 14, offset: Offset(0, 5))],
        ),
        child: SafeArea(bottom: false, child: LayoutBuilder(builder: (context, constraints) {
            final compact = constraints.maxWidth < 690;
            final shop = (shopName ?? '').trim();
            return Padding(
              padding: EdgeInsets.symmetric(horizontal: compact ? 14 : 27, vertical: compact ? 12 : 17),
              child: Row(children: [
                Material(
                  color: AppColors.primarySoft,
                  borderRadius: BorderRadius.circular(15),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(15),
                    onTap: () => _home(context),
                    child: const SizedBox(width: 48, height: 48, child: Icon(
                      Icons.shopping_bag_rounded, color: AppColors.primary, size: 26,
                    )),
                  ),
                ),
                const SizedBox(width: 11),
                Flexible(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(mainAxisSize: MainAxisSize.min, children: [
                    const PpeeText('PPee', style: TextStyle(color: AppColors.primary,
                      fontSize: 21, fontWeight: FontWeight.w900, letterSpacing: -.7)),
                    if (!compact) ...[
                      Container(width: 1, height: 22, color: AppColors.gray300,
                        margin: const EdgeInsets.symmetric(horizontal: 13)),
                      const Flexible(child: PpeeText('Seller Center', overflow: TextOverflow.ellipsis,
                        style: TextStyle(fontWeight: FontWeight.w800,
                          color: AppColors.black, fontSize: 16))),
                    ],
                  ]),
                  const SizedBox(height: 2),
                  PpeeText(shop.isEmpty ? 'Kênh Người Bán' : shop,
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: AppColors.gray700,
                      fontWeight: FontWeight.w600, fontSize: 11.5)),
                ])),
                const Spacer(),
                if (compact)
                  IconButton(
                    tooltip: tr(context, 'Về PPee'), onPressed: () => _home(context),
                    icon: const Icon(Icons.storefront_outlined, color: AppColors.primary))
                else
                  TextButton.icon(
                    onPressed: () => _home(context),
                    icon: const Icon(Icons.arrow_outward_rounded, size: 17),
                    label: const PpeeText('Về trang mua sắm')),
              ]),
            );
          })),
      ),
      Expanded(child: child),
    ]),
  );
}

class SellerPill extends StatelessWidget {
  const SellerPill({super.key, required this.label, this.icon = Icons.auto_awesome_rounded});
  final String label;
  final IconData icon;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
    decoration: BoxDecoration(color: AppColors.primarySoft,
      borderRadius: BorderRadius.circular(30), border: Border.all(color: const Color(0xFFCDE1FF))),
    child: Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(icon, size: 15, color: AppColors.primary),
      const SizedBox(width: 6),
      Flexible(child: PpeeText(label, style: const TextStyle(color: AppColors.primaryDark,
        fontSize: 12, fontWeight: FontWeight.w800))),
    ]),
  );
}

class SellerPanel extends StatelessWidget {
  const SellerPanel({super.key, required this.child, this.padding = const EdgeInsets.all(22)});
  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) => PpeeSurface(padding: padding, child: child);
}

class SellerInfoNotice extends StatelessWidget {
  const SellerInfoNotice({super.key, required this.message, this.compact = false});
  final String message;
  final bool compact;

  @override
  Widget build(BuildContext context) => Container(
    padding: EdgeInsets.all(compact ? 12 : 15),
    decoration: BoxDecoration(color: AppColors.primarySoft,
      borderRadius: BorderRadius.circular(12), border: Border.all(color: const Color(0xFFCDE1FF))),
    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Icon(Icons.info_outline_rounded, color: AppColors.primary, size: 20),
      const SizedBox(width: 10),
      Expanded(child: PpeeText(message, style: const TextStyle(color: AppColors.primaryDark,
        height: 1.45, fontSize: 12, fontWeight: FontWeight.w600))),
    ]),
  );
}

/// Consistent banner for all seller sub-pages. It wraps naturally on mobile.
class SellerPageIntro extends StatelessWidget {
  const SellerPageIntro({super.key, required this.title, required this.subtitle,
    required this.icon, this.actions = const [], this.eyebrow = 'QUẢN LÝ GIAN HÀNG'});
  final String title;
  final String subtitle;
  final String eyebrow;
  final IconData icon;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(22),
      border: Border.all(color: AppColors.gray300),
      boxShadow: const [BoxShadow(color: Color(0x080B2B69),
        blurRadius: 20, offset: Offset(0, 9))],
    ),
    child: LayoutBuilder(builder: (context, size) => Wrap(
      alignment: WrapAlignment.spaceBetween,
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: 15, runSpacing: 15,
      children: [
        SizedBox(width: size.maxWidth < 680 ? size.maxWidth : size.maxWidth * .56,
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Container(width: 51, height: 51,
              decoration: BoxDecoration(gradient: const LinearGradient(
                colors: [AppColors.primaryDeep, AppColors.primary]),
                borderRadius: BorderRadius.circular(16)),
              child: Icon(icon, color: Colors.white, size: 25)),
            const SizedBox(width: 15),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                PpeeText(eyebrow, style: const TextStyle(fontSize: 10.5,
                  fontWeight: FontWeight.w900, letterSpacing: 1.1,
                  color: AppColors.primary)),
                const SizedBox(height: 4),
                PpeeText(title, style: const TextStyle(fontSize: 23,
                  fontWeight: FontWeight.w900, letterSpacing: -.5,
                  color: AppColors.black)),
                const SizedBox(height: 5),
                PpeeText(subtitle, style: const TextStyle(color: AppColors.gray700,
                  fontSize: 12.5, height: 1.5)),
              ])),
          ])),
        if (actions.isNotEmpty)
          Wrap(spacing: 8, runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center, children: actions),
      ],
    )),
  );
}

class SellerMetric extends StatelessWidget {
  const SellerMetric({super.key, required this.label, required this.value,
    required this.icon, this.color = AppColors.primary, this.caption});
  final String label;
  final String value;
  final IconData icon;
  final Color color;
  final String? caption;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(17),
    decoration: BoxDecoration(color: Colors.white,
      borderRadius: BorderRadius.circular(18),
      border: Border.all(color: AppColors.gray300)),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Container(width: 39, height: 39,
          decoration: BoxDecoration(color: color.withValues(alpha: .09),
            borderRadius: BorderRadius.circular(12)),
          child: Icon(icon, color: color, size: 20)),
        const Spacer(),
        Container(width: 6, height: 6, decoration:
          BoxDecoration(color: color, shape: BoxShape.circle)),
      ]),
      const SizedBox(height: 14),
      PpeeText(value, maxLines: 1, overflow: TextOverflow.ellipsis,
        style: const TextStyle(color: AppColors.black, fontSize: 24,
          fontWeight: FontWeight.w900, letterSpacing: -.7)),
      const SizedBox(height: 4),
      PpeeText(label, style: const TextStyle(color: AppColors.charcoal,
        fontSize: 12.5, fontWeight: FontWeight.w800)),
      if (caption != null) ...[
        const SizedBox(height: 5),
        PpeeText(caption!, style: const TextStyle(fontSize: 11,
          height: 1.4, color: AppColors.gray700)),
      ],
    ]),
  );
}

/// Responsive grid; cards keep their own height for non-uniform content.
class SellerMetricGrid extends StatelessWidget {
  const SellerMetricGrid({super.key, required this.children, this.minWidth = 205});
  final List<Widget> children;
  final double minWidth;

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (context, box) {
    const gap = 12.0;
    final cols = ((box.maxWidth + gap) / (minWidth + gap))
      .floor().clamp(1, children.isEmpty ? 1 : children.length);
    final width = (box.maxWidth - (cols - 1) * gap) / cols;
    return Wrap(spacing: gap, runSpacing: gap, children: [
      for (final child in children) SizedBox(width: width, child: child),
    ]);
  });
}

class SellerStatusBadge extends StatelessWidget {
  const SellerStatusBadge({super.key, required this.label,
    this.color = AppColors.primary, this.icon});
  final String label;
  final Color color;
  final IconData? icon;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
    decoration: BoxDecoration(color: color.withValues(alpha: .09),
      borderRadius: BorderRadius.circular(40),
      border: Border.all(color: color.withValues(alpha: .13))),
    child: Row(mainAxisSize: MainAxisSize.min, children: [
      if (icon != null) ...[Icon(icon, size: 14, color: color), const SizedBox(width: 5)],
      PpeeText(label, style: TextStyle(color: color, fontSize: 11,
        fontWeight: FontWeight.w800)),
    ]),
  );
}

class SellerSectionTitle extends StatelessWidget {
  const SellerSectionTitle({super.key, required this.title,
    required this.icon, this.detail, this.trailing});
  final String title;
  final String? detail;
  final IconData icon;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Row(children: [
    Container(width: 36, height: 36,
      decoration: BoxDecoration(color: AppColors.primarySoft,
        borderRadius: BorderRadius.circular(11)),
      child: Icon(icon, color: AppColors.primary, size: 19)),
    const SizedBox(width: 11),
    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      PpeeText(title, style: const TextStyle(fontSize: 16,
        fontWeight: FontWeight.w900, color: AppColors.black)),
      if (detail != null) ...[const SizedBox(height: 2),
        PpeeText(detail!, style: const TextStyle(color: AppColors.gray700, fontSize: 12))],
    ])),
    if (trailing != null) trailing!,
  ]);
}

class SellerEmptyState extends StatelessWidget {
  const SellerEmptyState({super.key, required this.title,
    required this.message, required this.icon, this.action});
  final String title;
  final String message;
  final IconData icon;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.symmetric(vertical: 46, horizontal: 22),
    decoration: BoxDecoration(color: Colors.white,
      borderRadius: BorderRadius.circular(18),
      border: Border.all(color: AppColors.gray300)),
    child: Column(mainAxisSize: MainAxisSize.min, children: [
      Container(width: 64, height: 64,
        decoration: const BoxDecoration(color: AppColors.primarySoft,
          shape: BoxShape.circle),
        child: Icon(icon, color: AppColors.primary, size: 30)),
      const SizedBox(height: 15),
      PpeeText(title, textAlign: TextAlign.center,
        style: const TextStyle(fontSize: 16, color: AppColors.black,
          fontWeight: FontWeight.w900)),
      const SizedBox(height: 6),
      PpeeText(message, textAlign: TextAlign.center,
        style: const TextStyle(color: AppColors.gray700, fontSize: 12.5)),
      if (action != null) ...[const SizedBox(height: 18), action!],
    ]),
  );
}
