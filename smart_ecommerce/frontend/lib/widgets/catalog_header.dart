import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/l10n/language_controller.dart';

import '../core/constants/app_constants.dart';
import '../core/routes/app_routes.dart';
import '../core/theme/app_colors.dart';
import '../providers/auth_provider.dart';
import '../providers/cart_provider.dart';

/// Shared marketplace header. Search and every navigation action reuse existing routes.
class CatalogHeader extends StatefulWidget implements PreferredSizeWidget {
  const CatalogHeader({super.key, this.onSearch});
  final ValueChanged<String>? onSearch;

  @override
  Size get preferredSize => const Size.fromHeight(103);

  @override
  State<CatalogHeader> createState() => _CatalogHeaderState();
}

class _CatalogHeaderState extends State<CatalogHeader> {
  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  void _submitSearch() {
    final keyword = _search.text.trim();
    if (widget.onSearch != null) {
      widget.onSearch!(keyword);
    } else {
      Navigator.pushNamed(context, AppRoutes.products, arguments: keyword.isEmpty ? null : keyword);
    }
  }

  void _open(String route) => Navigator.pushNamed(context, route);

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final count = context.watch<CartProvider>().cart?.totalQuantity ?? 0;
    final width = MediaQuery.sizeOf(context).width;
    final compact = width < 770;
    return Material(
      color: AppColors.primaryDeep,
      child: Column(children: [
        // Keep the original, slim 27px marketplace utility bar.
        SizedBox(
          height: 27,
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: compact ? 14 : 28),
            child: Row(children: [
              InkWell(
                onTap: () => _open(AppRoutes.seller),
                child: const Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(Icons.storefront_outlined, color: Colors.white, size: 15),
                  SizedBox(width: 6),
                  PpeeText('Kênh Người Bán', style: TextStyle(color: Colors.white,
                      fontSize: 11, fontWeight: FontWeight.w700)),
                ]),
              ),
              if (!compact) ...[
                const SizedBox(width: 15),
                _topLink('Trở thành người bán', () => _open(AppRoutes.sellerSetup)),
              ],
              const SizedBox(width: 12),
              // A scrollable right cluster prevents overflow on phones without
              // changing the legacy desktop spacing or font size.
              Expanded(child: Align(
                alignment: Alignment.centerRight,
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  reverse: compact,
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    _topLink('Thông báo', _openNotifications),
                    const SizedBox(width: 15),
                    _topLink('Hỗ trợ', _showHelp),
                    const SizedBox(width: 15),
                    _languageMenu(),
                    const SizedBox(width: 15),
                    _topLink('Tài khoản', () => _open(
                      auth.isAuthenticated ? AppRoutes.profile : AppRoutes.login)),
                  ]),
                ),
              )),
            ]),
          ),
        ),
        Expanded(child: Container(
          decoration: const BoxDecoration(gradient: LinearGradient(
            begin: Alignment.centerLeft, end: Alignment.centerRight,
            colors: [Color(0xFF1649B5), Color(0xFF286CEB), Color(0xFF2689EA)])),
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: compact ? 12 : 28),
            child: Row(children: [
              if (!compact && Navigator.of(context).canPop()) ...[
                IconButton(tooltip: tr(context, 'Quay lại'), onPressed: () => Navigator.maybePop(context),
                  icon: const Icon(Icons.arrow_back, color: Colors.white)),
                const SizedBox(width: 6),
              ],
              InkWell(
                onTap: () => Navigator.pushNamedAndRemoveUntil(context, AppRoutes.home, (_) => false),
                borderRadius: BorderRadius.circular(8),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  const Icon(Icons.local_mall_outlined, color: Colors.white, size: 30),
                  if (width >= 470) ...[
                    const SizedBox(width: 8),
                    PpeeText(AppConstants.appName, style: TextStyle(
                        color: Colors.white, fontSize: compact ? 17 : 22,
                        letterSpacing: compact ? .1 : .3, fontWeight: FontWeight.w800)),
                  ],
                ]),
              ),
              SizedBox(width: compact ? 10 : 44),
              Expanded(child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 790),
                child: SizedBox(height: 51, child: TextField(
                  controller: _search,
                  textInputAction: TextInputAction.search,
                  onSubmitted: (_) => _submitSearch(),
                  style: const TextStyle(fontSize: 14),
                  decoration: InputDecoration(
                    hintText: compact ? tr(context, 'Tìm sản phẩm...') : tr(context, 'Tìm sản phẩm, thương hiệu, danh mục...'),
                    filled: true, fillColor: Colors.white,
                    hintStyle: const TextStyle(color: AppColors.gray500, fontSize: 14),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
                    enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
                    focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
                    suffixIcon: Padding(
                      padding: const EdgeInsets.all(4),
                      child: IconButton.filled(
                        tooltip: tr(context, 'Tìm kiếm'), onPressed: _submitSearch,
                        style: IconButton.styleFrom(backgroundColor: AppColors.primary,
                            foregroundColor: Colors.white, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(11))),
                        icon: const Icon(Icons.search_rounded, size: 20),
                      ),
                    ),
                  ),
                )),
              )),
              SizedBox(width: compact ? 3 : 20),
              _action(Icons.shopping_cart_outlined, 'Giỏ hàng',
                auth.isCustomer ? AppRoutes.cart : (auth.isAuthenticated ? AppRoutes.profile : AppRoutes.login), count: count),
              if (!compact) ...[
                _action(Icons.history_rounded, 'Lịch sử tìm kiếm',
                  auth.isCustomer ? AppRoutes.activityHistory : (auth.isAuthenticated ? AppRoutes.profile : AppRoutes.login)),
                _action(Icons.favorite_border_rounded, 'Yêu thích',
                  auth.isCustomer ? AppRoutes.favorites : (auth.isAuthenticated ? AppRoutes.profile : AppRoutes.login)),
                _action(Icons.receipt_long_outlined, 'Đơn hàng',
                  auth.isCustomer ? AppRoutes.orders : (auth.isAuthenticated ? AppRoutes.profile : AppRoutes.login)),
                _action(Icons.person_outline_rounded, 'Tài khoản',
                  auth.isAuthenticated ? AppRoutes.profile : AppRoutes.login),
              ],
            ]),
          ),
        )),
      ]),
    );
  }

  void _openNotifications() {
    final auth = context.read<AuthProvider>();
    if (!auth.isAuthenticated) {
      _open(AppRoutes.login);
    } else if (auth.user?.roles.contains('SELLER') ?? false) {
      _open(AppRoutes.seller);
    } else if (auth.user?.roles.contains('ADMIN') ?? false) {
      _open(AppRoutes.adminDashboard);
    } else {
      _open(AppRoutes.notifications);
    }
  }

  void _showHelp() {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      backgroundColor: Colors.white,
      constraints: const BoxConstraints(maxWidth: 560),
      builder: (sheetContext) => SafeArea(child: Padding(
        padding: const EdgeInsets.fromLTRB(23, 10, 23, 28),
        child: Column(mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start, children: [
          const PpeeText('Trung tâm hỗ trợ PPee',
            style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          const PpeeText('Chọn nội dung bạn cần hỗ trợ. Đây là hướng dẫn sử dụng, không phải dịch vụ chat trực tuyến.',
            style: TextStyle(color: AppColors.gray700)),
          const SizedBox(height: 15),
          ListTile(
            leading: const Icon(Icons.shopping_bag_outlined),
            title: const PpeeText('Theo dõi đơn mua'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () { Navigator.pop(sheetContext); _open(AppRoutes.orders); },
          ),
          ListTile(
            leading: const Icon(Icons.location_on_outlined),
            title: const PpeeText('Quản lý địa chỉ nhận hàng'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () { Navigator.pop(sheetContext); _open(AppRoutes.addresses); },
          ),
          ListTile(
            leading: const Icon(Icons.storefront_outlined),
            title: const PpeeText('Kênh Người Bán'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () { Navigator.pop(sheetContext); _open(AppRoutes.seller); },
          ),
        ]),
      )),
    );
  }

  Widget _languageMenu() {
    final language = context.watch<LanguageController>().language;
    return PopupMenuButton<PpeeLanguage>(
      tooltip: tr(context, 'Ngôn ngữ'),
      onSelected: (value) => context.read<LanguageController>().select(value),
      itemBuilder: (context) => [
        for (final entry in PpeeLanguage.values)
          PopupMenuItem<PpeeLanguage>(
            value: entry,
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(Icons.language, size: 17,
                  color: entry == language ? AppColors.primary : AppColors.gray700),
              const SizedBox(width: 10),
              // Language names are proper labels and must not be auto-translated.
              Text(entry == PpeeLanguage.vi ? 'Tiếng Việt' : 'English'),
              const SizedBox(width: 20),
              if (entry == language)
                const Icon(Icons.check_circle_rounded, size: 17, color: AppColors.primary),
            ]),
          ),
      ],
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Text(language == PpeeLanguage.vi ? 'Tiếng Việt' : 'English',
            style: const TextStyle(color: Colors.white,
              fontSize: 11, fontWeight: FontWeight.w600)),
          const SizedBox(width: 2),
          const Icon(Icons.keyboard_arrow_down_rounded, size: 13, color: Colors.white),
        ]),
      ),
    );
  }

  Widget _action(IconData icon, String tooltip, String route, {int count = 0}) => IconButton(
    tooltip: tooltip,
    onPressed: () => _open(route),
    icon: Badge(
      isLabelVisible: count > 0,
      label: PpeeText(count > 99 ? '99+' : '$count'),
      backgroundColor: AppColors.highlight,
      textColor: Colors.white,
      child: Icon(icon, color: Colors.white, size: 25),
    ),
  );

  Widget _topLink(String label, VoidCallback callback) => InkWell(
    onTap: callback,
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: PpeeText(label, style: const TextStyle(
        fontSize: 11, color: Colors.white, fontWeight: FontWeight.w600)),
    ),
  );
}

/// Existing mobile destinations and route behavior are intentionally unchanged.
class CatalogBottomNav extends StatelessWidget {
  const CatalogBottomNav({super.key, required this.current});
  final String current;

  @override
  Widget build(BuildContext context) {
    if (MediaQuery.sizeOf(context).width >= 760) return const SizedBox.shrink();
    final auth = context.watch<AuthProvider>();
    final count = context.watch<CartProvider>().cart?.totalQuantity ?? 0;
    final destinations = [AppRoutes.home, AppRoutes.categories, AppRoutes.cart, AppRoutes.favorites,
      auth.isAuthenticated ? AppRoutes.profile : AppRoutes.login];
    final active = destinations.indexOf(current);
    return NavigationBar(
      height: 68,
      backgroundColor: AppColors.white,
      surfaceTintColor: Colors.transparent,
      indicatorColor: AppColors.primarySoft,
      selectedIndex: active == -1 ? (current == AppRoutes.products ? 1 : 0) : active,
      onDestinationSelected: (index) {
        final route = destinations[index];
        if (route == current) return;
        Navigator.pushNamed(context, route);
      },
      destinations: [
        NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home_rounded), label: tr(context, 'Trang chủ')),
        NavigationDestination(icon: Icon(Icons.grid_view_outlined), selectedIcon: Icon(Icons.grid_view_rounded), label: tr(context, 'Danh mục')),
        NavigationDestination(icon: Badge(isLabelVisible: count > 0, label: PpeeText(count > 99 ? '99+' : '$count'), child: const Icon(Icons.shopping_cart_outlined)),
          selectedIcon: const Icon(Icons.shopping_cart_rounded), label: tr(context, 'Giỏ hàng')),
        NavigationDestination(icon: Icon(Icons.favorite_border), selectedIcon: Icon(Icons.favorite), label: tr(context, 'Yêu thích')),
        NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: tr(context, 'Tài khoản')),
      ],
    );
  }
}
