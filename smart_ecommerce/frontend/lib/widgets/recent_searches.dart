import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/routes/app_routes.dart';
import '../core/theme/app_colors.dart';
import '../models/search_history.dart';
import '../providers/auth_provider.dart';
import '../providers/behavior_provider.dart';

/// Latest unique searches of the CURRENT signed-in customer, from the server.
class RecentSearches extends StatefulWidget {
  const RecentSearches({super.key, required this.onSelect});
  final ValueChanged<String> onSelect;

  @override
  State<RecentSearches> createState() => _RecentSearchesState();
}

class _RecentSearchesState extends State<RecentSearches> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && context.read<AuthProvider>().isCustomer) {
        context.read<BehaviorProvider>().loadSearches();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final isCustomer = context.watch<AuthProvider>().isCustomer;
    if (!isCustomer) {
      return Padding(
        padding: const EdgeInsets.only(top: 14),
        child: Wrap(crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 6, runSpacing: 4, children: [
          const Icon(Icons.history_outlined,
            size: 17, color: AppColors.gray700),
          const PpeeText('Đăng nhập để lưu và xem lịch sử tìm kiếm riêng của bạn.',
            style: TextStyle(fontSize: 12, color: AppColors.gray700)),
          TextButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.login),
            child: const PpeeText('Đăng nhập')),
        ]),
      );
    }
    final behavior = context.watch<BehaviorProvider>();
    final seen = <String>{};
    final recent = <SearchHistoryItem>[];
    for (final item in behavior.searchHistory) {
      final key = item.keyword.trim().toLowerCase();
      if (key.isNotEmpty && seen.add(key)) recent.add(item);
      if (recent.length == 6) break;
    }
    if (recent.isEmpty && !behavior.searchesLoading && behavior.searchesError == null) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(top: 15),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Icon(Icons.history_rounded, color: AppColors.primary, size: 17),
          const SizedBox(width: 7),
          const Expanded(child: PpeeText('Tìm kiếm gần đây của bạn',
            style: TextStyle(fontWeight: FontWeight.w800, fontSize: 12.5,
              color: AppColors.black))),
          TextButton(
            onPressed: () => Navigator.pushNamed(context, AppRoutes.activityHistory),
            child: const PpeeText('Xem tất cả'),
          ),
        ]),
        if (behavior.searchesLoading && recent.isEmpty)
          const Padding(padding: EdgeInsets.symmetric(vertical: 7),
            child: LinearProgressIndicator(minHeight: 2)),
        if (behavior.searchesError != null) ...[
          PpeeText(behavior.searchesError!, style: const TextStyle(
            fontSize: 12, color: AppColors.error)),
          TextButton.icon(onPressed: behavior.loadSearches,
            icon: const Icon(Icons.refresh_rounded, size: 16),
            label: const PpeeText('Thử lại')),
        ],
        if (recent.isNotEmpty) ...[
          const SizedBox(height: 5),
          Wrap(spacing: 8, runSpacing: 7, children: [
            for (final item in recent)
              InputChip(
                avatar: const Icon(Icons.north_west_rounded,
                  size: 16, color: AppColors.primary),
                label: PpeeText(item.keyword, style: const TextStyle(fontSize: 12)),
                onPressed: () => widget.onSelect(item.keyword),
                deleteIcon: const Icon(Icons.close_rounded, size: 14),
                deleteButtonTooltipMessage: 'Xóa ${item.keyword} khỏi lịch sử',
                onDeleted: () async {
                  final ok = await behavior.deleteSearch(item.id);
                  if (!ok && context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: PpeeText(behavior.searchesError ?? 'Không xóa được lịch sử.'),
                    ));
                  }
                },
              ),
          ]),
        ],
      ]),
    );
  }
}
