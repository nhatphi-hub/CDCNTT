import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

class ShopLayout {
  ShopLayout._();
  static bool isMobile(BuildContext context) => MediaQuery.sizeOf(context).width < 700;
  static bool isDesktop(BuildContext context) => MediaQuery.sizeOf(context).width >= 1050;
  static double padding(BuildContext context) => isMobile(context) ? 16 : 28;
  static int gridColumns(double width) => width < 420 ? 2 : width < 680 ? 2 : width < 900 ? 3 : width < 1200 ? 4 : 5;
}

/// Shared section heading for consistent desktop and mobile hierarchy.
class ShopHeading extends StatelessWidget {
  const ShopHeading({super.key, required this.title, this.description, this.trailing});
  final String title;
  final String? description;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: Theme.of(context).textTheme.headlineMedium),
        if (description != null) ...[
          const SizedBox(height: 5),
          Text(description!, style: const TextStyle(color: AppColors.gray700, fontSize: 13)),
        ],
      ])),
      ?trailing,
    ],
  );
}

class ShopEmptyState extends StatelessWidget {
  const ShopEmptyState({super.key, required this.icon, required this.title, required this.message, this.action});
  final IconData icon;
  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 42),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Container(padding: const EdgeInsets.all(23), decoration: const BoxDecoration(color: AppColors.primarySoft, shape: BoxShape.circle), child: Icon(icon, size: 34, color: AppColors.primary)),
        const SizedBox(height: 18),
        Text(title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        ConstrainedBox(constraints: const BoxConstraints(maxWidth: 360), child: Text(message, textAlign: TextAlign.center, style: const TextStyle(color: AppColors.gray700, height: 1.5))),
        if (action != null) ...[const SizedBox(height: 20), action!],
      ]),
    ),
  );
}
