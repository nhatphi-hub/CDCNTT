import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../providers/behavior_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/ppee_ui.dart';

/// The backend selects history by JWT's current user, never a client userId.
class ActivityHistoryScreen extends StatefulWidget {
  const ActivityHistoryScreen({super.key});

  @override
  State<ActivityHistoryScreen> createState() => _ActivityHistoryScreenState();
}

class _ActivityHistoryScreenState extends State<ActivityHistoryScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<BehaviorProvider>().load();
    });
  }

  String _date(DateTime value) {
    final d = value.toLocal();
    String pad(int n) => n.toString().padLeft(2, '0');
    return '${pad(d.day)}/${pad(d.month)}/${d.year} · ${pad(d.hour)}:${pad(d.minute)}';
  }

  Future<bool> _confirm(String title, String message) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: PpeeText(title),
        content: PpeeText(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const PpeeText('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const PpeeText('Xóa lịch sử'),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  Future<void> _clearSearches(BehaviorProvider provider) async {
    if (!await _confirm(
      'Xóa toàn bộ từ khóa?',
      'Lịch sử tìm kiếm của tài khoản hiện tại sẽ bị xóa khỏi hệ thống.',
    ))
      return;
    final ok = await provider.clearSearches();
    if (!ok && mounted) _showError(provider.searchesError);
  }

  Future<void> _clearViews(BehaviorProvider provider) async {
    if (!await _confirm(
      'Xóa sản phẩm đã xem?',
      'Danh sách sản phẩm đã xem của tài khoản hiện tại sẽ bị xóa.',
    ))
      return;
    final ok = await provider.clearViews();
    if (!ok && mounted) _showError(provider.error);
  }

  Future<void> _deleteSearch(BehaviorProvider provider, String id) async {
    final ok = await provider.deleteSearch(id);
    if (!ok && mounted) _showError(provider.searchesError);
  }

  void _showError(String? message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: PpeeText(message ?? 'Không thể cập nhật lịch sử.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<BehaviorProvider>();
    return Scaffold(
      appBar: const CatalogHeader(),
      bottomNavigationBar: const CatalogBottomNav(current: AppRoutes.profile),
      body: RefreshIndicator(
        onRefresh: provider.load,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 50),
          children: [
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1040),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    PpeeText(
                      'Hoạt động của tôi',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 7),
                    const PpeeText(
                      'Các hoạt động gắn với tài khoản đang đăng nhập, được lưu trên PPee.',
                      style: TextStyle(color: AppColors.gray700, fontSize: 13),
                    ),
                    const SizedBox(height: 23),
                    if (provider.loading)
                      const PpeeSurface(
                        child: Padding(
                          padding: EdgeInsets.all(35),
                          child: Center(child: CircularProgressIndicator()),
                        ),
                      )
                    else if (provider.error != null &&
                        provider.views.isEmpty &&
                        provider.searchHistory.isEmpty)
                      PpeeSurface(
                        child: Column(
                          children: [
                            const Icon(
                              Icons.wifi_off_outlined,
                              color: AppColors.error,
                              size: 35,
                            ),
                            const SizedBox(height: 10),
                            PpeeText(provider.error!, textAlign: TextAlign.center),
                            const SizedBox(height: 12),
                            OutlinedButton.icon(
                              onPressed: provider.load,
                              icon: const Icon(Icons.refresh_rounded),
                              label: const PpeeText('Thử lại'),
                            ),
                          ],
                        ),
                      )
                    else ...[
                      PpeeSurface(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _sectionHeader(
                              'Tìm kiếm gần đây',
                              'Từ khóa của riêng tài khoản này',
                              Icons.manage_search_outlined,
                              provider.searchHistory.isNotEmpty
                                  ? () => _clearSearches(provider)
                                  : null,
                            ),
                            const SizedBox(height: 14),
                            if (provider.searchHistory.isEmpty)
                              _empty(
                                'Bạn chưa tìm kiếm sản phẩm nào bằng tài khoản này.',
                              )
                            else
                              for (final item in provider.searchHistory.take(
                                50,
                              )) ...[
                                ListTile(
                                  contentPadding: EdgeInsets.zero,
                                  leading: const CircleAvatar(
                                    backgroundColor: AppColors.primarySoft,
                                    child: Icon(
                                      Icons.history_rounded,
                                      color: AppColors.primary,
                                      size: 19,
                                    ),
                                  ),
                                  title: PpeeText(
                                    item.keyword,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w800,
                                    ),
                                  ),
                                  subtitle: PpeeText(
                                    _date(item.searchedAt),
                                    style: const TextStyle(fontSize: 11.5),
                                  ),
                                  trailing: Wrap(
                                    spacing: 0,
                                    children: [
                                      IconButton(
                                        tooltip: tr(context, 'Tìm lại từ khóa'),
                                        onPressed: () => Navigator.pushNamed(
                                          context,
                                          AppRoutes.products,
                                          arguments: item.keyword,
                                        ),
                                        icon: const Icon(
                                          Icons.north_west_rounded,
                                          color: AppColors.primary,
                                        ),
                                      ),
                                      IconButton(
                                        tooltip: tr(context, 'Xóa từ khóa này'),
                                        onPressed: () =>
                                            _deleteSearch(provider, item.id),
                                        icon: const Icon(
                                          Icons.close_rounded,
                                          color: AppColors.gray700,
                                        ),
                                      ),
                                    ],
                                  ),
                                  onTap: () => Navigator.pushNamed(
                                    context,
                                    AppRoutes.products,
                                    arguments: item.keyword,
                                  ),
                                ),
                                if (item !=
                                    provider.searchHistory.take(50).last)
                                  const Divider(height: 1),
                              ],
                            if (provider.searchHistory.length > 50) ...[
                              const SizedBox(height: 8),
                              PpeeText(
                                'Đang hiển thị 50 lượt tìm kiếm gần nhất trong ${provider.searchHistory.length} lượt.',
                                style: const TextStyle(
                                  fontSize: 12,
                                  color: AppColors.gray700,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                      const SizedBox(height: 17),
                      PpeeSurface(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _sectionHeader(
                              'Sản phẩm đã xem',
                              'Xem lại các sản phẩm bạn từng ghé thăm',
                              Icons.visibility_outlined,
                              provider.views.isNotEmpty
                                  ? () => _clearViews(provider)
                                  : null,
                            ),
                            const SizedBox(height: 12),
                            if (provider.views.isEmpty)
                              _empty('Bạn chưa xem sản phẩm nào.')
                            else
                              for (final view in provider.views.take(30)) ...[
                                ListTile(
                                  contentPadding: EdgeInsets.zero,
                                  leading: const CircleAvatar(
                                    backgroundColor: AppColors.primarySoft,
                                    child: Icon(
                                      Icons.inventory_2_outlined,
                                      color: AppColors.primary,
                                    ),
                                  ),
                                  title: PpeeText(
                                    view.productName,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w800,
                                    ),
                                  ),
                                  subtitle: PpeeText(_date(view.viewedAt)),
                                  trailing: const Icon(
                                    Icons.chevron_right_rounded,
                                  ),
                                  onTap: () => Navigator.pushNamed(
                                    context,
                                    '${AppRoutes.product}/${view.productId}',
                                  ),
                                ),
                                if (view != provider.views.take(30).last)
                                  const Divider(height: 1),
                              ],
                          ],
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _empty(String message) => Container(
    padding: const EdgeInsets.symmetric(vertical: 26, horizontal: 14),
    alignment: Alignment.centerLeft,
    child: PpeeText(
      message,
      style: const TextStyle(color: AppColors.gray700, fontSize: 13),
    ),
  );

  Widget _sectionHeader(
    String title,
    String subtitle,
    IconData icon,
    VoidCallback? onClear,
  ) => Row(
    children: [
      Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: AppColors.primarySoft,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(icon, color: AppColors.primary, size: 23),
      ),
      const SizedBox(width: 12),
      Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            PpeeText(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 3),
            PpeeText(
              subtitle,
              style: const TextStyle(color: AppColors.gray700, fontSize: 12),
            ),
          ],
        ),
      ),
      if (onClear != null)
        TextButton.icon(
          onPressed: onClear,
          icon: const Icon(Icons.delete_outline_rounded, size: 17),
          label: const PpeeText('Xóa tất cả'),
        ),
    ],
  );
}
