import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/catalog_header.dart';

/// A focused account hub using only the actual authenticated profile and
/// existing routes. No hardcoded order counts, points or mock purchases.
class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  Future<void> _logout(BuildContext context) async {
    await context.read<AuthProvider>().logout();
    if (!context.mounted) return;
    Navigator.of(context).pushNamedAndRemoveUntil(AppRoutes.login, (_) => false);
  }

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthProvider>().user!;
    final isAdmin = user.roles.contains('ADMIN');
    final isSeller = user.roles.contains('SELLER');
    final isCustomer = context.watch<AuthProvider>().isCustomer;
    return Scaffold(
      appBar: const CatalogHeader(),
      bottomNavigationBar: const CatalogBottomNav(current: AppRoutes.profile),
      body: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1160),
        child: LayoutBuilder(builder: (context, size) {
          final desktop = size.maxWidth >= 800;
          final horizontal = ShopLayout.padding(context);
          return ListView(padding: EdgeInsets.fromLTRB(horizontal, 25, horizontal, 48),
            children: [
              const PpeeText('Tài khoản của tôi', style: TextStyle(
                fontSize: 28, color: AppColors.black,
                fontWeight: FontWeight.w900, letterSpacing: -.7)),
              const SizedBox(height: 6),
              const PpeeText('Một nơi để quản lý thông tin, đơn mua và hoạt động của bạn.',
                style: TextStyle(fontSize: 13.5, color: AppColors.gray700)),
              const SizedBox(height: 22),
              if (desktop)
                Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  SizedBox(width: 320, child: _IdentityCard(name: user.fullName,
                    email: user.email, onSettings: () => Navigator.pushNamed(context, AppRoutes.settings))),
                  const SizedBox(width: 20),
                  Expanded(child: _AccountActions(isAdmin: isAdmin, isCustomer: isCustomer,
                    isSeller: isSeller, onLogout: () => _logout(context))),
                ])
              else ...[
                _IdentityCard(name: user.fullName, email: user.email,
                  onSettings: () => Navigator.pushNamed(context, AppRoutes.settings)),
                const SizedBox(height: 18),
                _AccountActions(isAdmin: isAdmin, isCustomer: isCustomer,
                  isSeller: isSeller, onLogout: () => _logout(context)),
              ],
            ]);
        }),
      )),
    );
  }
}

class _IdentityCard extends StatelessWidget {
  const _IdentityCard({required this.name, required this.email,
    required this.onSettings});
  final String name;
  final String email;
  final VoidCallback onSettings;

  @override
  Widget build(BuildContext context) => Container(
    clipBehavior: Clip.antiAlias,
    decoration: BoxDecoration(color: Colors.white,
      borderRadius: BorderRadius.circular(24),
      border: Border.all(color: AppColors.gray300),
      boxShadow: const [BoxShadow(color: Color(0x09102454),
        blurRadius: 18, offset: Offset(0, 7))]),
    child: Column(children: [
      Container(width: double.infinity, height: 124,
        decoration: const BoxDecoration(gradient: LinearGradient(
          colors: [AppColors.primaryDeep, AppColors.primary, Color(0xFF40A2F3)])),
        child: const Stack(children: [
          Positioned(right: -24, top: -28,
            child: Icon(Icons.blur_circular, color: Color(0x22FFFFFF), size: 145)),
          Positioned(left: 21, top: 18,
            child: PpeeText('PPee  /  MY ACCOUNT', style: TextStyle(
              color: Colors.white, fontWeight: FontWeight.w800,
              fontSize: 11, letterSpacing: .6))),
        ])),
      Transform.translate(offset: const Offset(0, -39),
        child: Column(children: [
          Container(padding: const EdgeInsets.all(4),
            decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
            child: CircleAvatar(radius: 43, backgroundColor: AppColors.primarySoft,
              child: PpeeText(name.trim().isEmpty ? '?' : name.characters.first.toUpperCase(),
                style: const TextStyle(color: AppColors.primary,
                  fontWeight: FontWeight.w900, fontSize: 32)))),
          const SizedBox(height: 8),
          Padding(padding: const EdgeInsets.symmetric(horizontal: 15),
            child: PpeeText(name, textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.black,
                fontWeight: FontWeight.w900, fontSize: 21))),
          const SizedBox(height: 5),
          Padding(padding: const EdgeInsets.symmetric(horizontal: 15),
            child: PpeeText(email, textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.gray700, fontSize: 13))),
          const SizedBox(height: 13),
          Container(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
            decoration: BoxDecoration(color: AppColors.primarySoft,
              borderRadius: BorderRadius.circular(18)),
            child: const Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(Icons.verified_user_outlined, color: AppColors.primary, size: 16),
              SizedBox(width: 6),
              PpeeText('THÀNH VIÊN PPee', style: TextStyle(
                color: AppColors.primary, fontSize: 10,
                fontWeight: FontWeight.w800, letterSpacing: .6)),
            ])),
          const SizedBox(height: 21),
          Padding(padding: const EdgeInsets.symmetric(horizontal: 23),
            child: OutlinedButton.icon(onPressed: onSettings,
              icon: const Icon(Icons.settings_outlined, size: 18),
              label: const PpeeText('Cài đặt tài khoản'))),
        ])),
    ]),
  );
}

class _AccountActions extends StatelessWidget {
  const _AccountActions({required this.isAdmin, required this.isCustomer,
    required this.isSeller, required this.onLogout});
  final bool isAdmin;
  final bool isCustomer;
  final bool isSeller;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    final shopping = <_AccountLink>[
      if (isCustomer) const _AccountLink(Icons.receipt_long_outlined, 'Đơn hàng của tôi',
        'Trạng thái và hành trình giao hàng', AppRoutes.orders, AppColors.primary),
      if (isCustomer) const _AccountLink(Icons.favorite_border_rounded, 'Sản phẩm yêu thích',
        'Những sản phẩm bạn đã lưu', AppRoutes.favorites, Color(0xFFE84D82)),
      if (isCustomer)
        const _AccountLink(Icons.history_rounded, 'Lịch sử tìm kiếm và đã xem',
          'Hoạt động riêng của tài khoản này', AppRoutes.activityHistory, Color(0xFF795FE8)),
      if (isCustomer) const _AccountLink(Icons.rate_review_outlined, 'Đánh giá của tôi',
        'Xem lại nhận xét đã gửi', AppRoutes.myReviews, Color(0xFF159A9A)),
    ];
    final personal = <_AccountLink>[
      if (isCustomer) const _AccountLink(Icons.location_on_outlined, 'Địa chỉ của tôi',
        'Quản lý nơi nhận hàng', AppRoutes.addresses, Color(0xFF237FBC)),
      const _AccountLink(Icons.settings_outlined, 'Cài đặt tài khoản',
        'Tùy chọn bảo mật và hồ sơ', AppRoutes.settings, AppColors.primary),
      if (isCustomer || isSeller) const _AccountLink(Icons.storefront_outlined, 'Kênh Người Bán',
        'Quản lý hoặc mở gian hàng PPee', AppRoutes.seller, Color(0xFF188766)),
      if (isAdmin)
        const _AccountLink(Icons.admin_panel_settings_outlined, 'Trung tâm ADMIN',
          'Gian hàng, loại sản phẩm, đơn và thanh toán',
          AppRoutes.adminDashboard, Color(0xFF5C51B2)),
    ];
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (shopping.isNotEmpty) ...[
        _section(context, 'Mua sắm của bạn', 'Truy cập nhanh những hoạt động thường dùng', shopping),
        const SizedBox(height: 16),
      ],
      _section(context, 'Cá nhân và gian hàng', 'Địa chỉ, bảo mật và quản lý bán hàng', personal),
      const SizedBox(height: 16),
      OutlinedButton.icon(
        onPressed: onLogout,
        style: OutlinedButton.styleFrom(
          side: const BorderSide(color: Color(0xFFFFCBD6)),
          foregroundColor: AppColors.error,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 17)),
        icon: const Icon(Icons.logout_rounded, size: 19),
        label: const PpeeText('Đăng xuất'),
      ),
    ]);
  }

  Widget _section(BuildContext context, String title, String subtitle,
      List<_AccountLink> links) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(color: Colors.white,
      borderRadius: BorderRadius.circular(24),
      border: Border.all(color: AppColors.gray300)),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      PpeeText(title, style: const TextStyle(fontWeight: FontWeight.w900,
        fontSize: 17, color: AppColors.black)),
      const SizedBox(height: 5),
      PpeeText(subtitle, style: const TextStyle(fontSize: 12,
        color: AppColors.gray700)),
      const SizedBox(height: 16),
      LayoutBuilder(builder: (context, box) {
        final wide = box.maxWidth >= 510;
        final tileWidth = wide ? (box.maxWidth - 12) / 2 : box.maxWidth;
        return Wrap(spacing: 12, runSpacing: 12, children: [
          for (final item in links)
            SizedBox(width: tileWidth, child: Material(color: AppColors.gray100,
              borderRadius: BorderRadius.circular(16),
              child: InkWell(borderRadius: BorderRadius.circular(16),
                onTap: () => Navigator.pushNamed(context, item.route),
                child: Padding(padding: const EdgeInsets.all(14),
                  child: Row(children: [
                    Container(width: 42, height: 42,
                      decoration: BoxDecoration(color: Colors.white,
                        borderRadius: BorderRadius.circular(12)),
                      child: Icon(item.icon, color: item.color, size: 23)),
                    const SizedBox(width: 10),
                    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        PpeeText(item.title, maxLines: 2, overflow: TextOverflow.ellipsis,
                          style: const TextStyle(color: AppColors.black,
                            fontSize: 13, fontWeight: FontWeight.w800)),
                        const SizedBox(height: 4),
                        PpeeText(item.subtitle, maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(color: AppColors.gray700,
                            fontSize: 11.5)),
                      ])),
                    const Icon(Icons.chevron_right_rounded,
                      color: AppColors.gray500, size: 18),
                  ])),
              ),
            )),
        ]);
      }),
    ]),
  );
}

class _AccountLink {
  const _AccountLink(this.icon, this.title, this.subtitle, this.route, this.color);
  final IconData icon;
  final String title;
  final String subtitle;
  final String route;
  final Color color;
}
