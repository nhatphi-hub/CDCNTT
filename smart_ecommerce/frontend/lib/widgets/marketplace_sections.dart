import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';

import '../core/routes/app_routes.dart';
import '../core/theme/app_colors.dart';

/// Shared marketplace widgets. Seller CTAs point to UI-only preview routes;
/// they do not create shop records or call seller APIs.
class MarketplaceSectionFrame extends StatelessWidget {
  const MarketplaceSectionFrame({super.key, required this.title, required this.child,
    this.subtitle, this.icon, this.onMore, this.highlight = false});
  final String title;
  final String? subtitle;
  final Widget child;
  final IconData? icon;
  final VoidCallback? onMore;
  final bool highlight;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(21),
      border: Border.all(color: AppColors.gray300.withValues(alpha: .80)),
      boxShadow: [BoxShadow(color: AppColors.primaryDeep.withValues(alpha: .03), blurRadius: 18, offset: const Offset(0, 6))]),
    clipBehavior: Clip.antiAlias,
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (highlight) const SizedBox(height: 3, child: ColoredBox(color: AppColors.primary)),
      Padding(padding: const EdgeInsets.fromLTRB(22, 20, 18, 18), child: Row(children: [
        if (icon != null) ...[
          Container(padding: const EdgeInsets.all(7), decoration: BoxDecoration(
            color: AppColors.primarySoft, borderRadius: BorderRadius.circular(12)),
            child: Icon(icon, color: AppColors.primary, size: 19)),
          const SizedBox(width: 11),
        ],
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          PpeeText(title, style: const TextStyle(color: AppColors.black, fontWeight: FontWeight.w800, fontSize: 19)),
          if (subtitle != null) ...[const SizedBox(height: 3),
            PpeeText(subtitle!, maxLines: 2, overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12, color: AppColors.gray700))],
        ])),
        if (onMore != null) TextButton.icon(
          onPressed: onMore,
          iconAlignment: IconAlignment.end,
          icon: const Icon(Icons.arrow_forward_ios_rounded, size: 12),
          label: const PpeeText('Xem tất cả', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700))),
      ])),
      const Divider(height: 1),
      Padding(padding: const EdgeInsets.all(12), child: child),
    ]),
  );
}

class MarketplaceQuickActions extends StatelessWidget {
  const MarketplaceQuickActions({super.key});

  @override
  Widget build(BuildContext context) {
    final actions = <_QuickDestination>[
      const _QuickDestination('Mua sắm ngay', 'Khám phá sản phẩm', Icons.shopping_bag_outlined, AppRoutes.products, Color(0xFFE6F0FF)),
      const _QuickDestination('Tìm theo ngành', 'Danh mục sản phẩm', Icons.grid_view_rounded, AppRoutes.categories, Color(0xFFE9F7FF)),
      const _QuickDestination('Ưu đãi hôm nay', 'Sản phẩm giảm giá', Icons.local_offer_outlined, AppRoutes.products, Color(0xFFFFF0E4)),
      const _QuickDestination('Dành cho bạn', 'Khám phá gợi ý', Icons.auto_awesome_outlined, AppRoutes.home, Color(0xFFEDF0FF)),
      const _QuickDestination('Yêu thích', 'Danh sách đã lưu', Icons.favorite_border_rounded, AppRoutes.favorites, Color(0xFFFFEEF2)),
      const _QuickDestination('Đơn mua', 'Theo dõi đơn hàng', Icons.receipt_long_outlined, AppRoutes.orders, Color(0xFFE9F8F0)),
    ];
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 15),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.gray300)),
      child: LayoutBuilder(builder: (context, constraints) {
        final columns = constraints.maxWidth < 520 ? 3 : constraints.maxWidth < 850 ? 3 : 6;
        final width = (constraints.maxWidth - 10) / columns;
        return Wrap(alignment: WrapAlignment.spaceAround, runSpacing: 13, children: [
          for (final item in actions)
            SizedBox(width: width, child: Material(color: Colors.transparent,
              child: InkWell(borderRadius: BorderRadius.circular(10),
                onTap: () => Navigator.pushNamed(context, item.route),
                child: Padding(padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 4),
                  child: Column(children: [
                    Container(width: 52, height: 52, decoration: BoxDecoration(color: item.bg,
                      borderRadius: BorderRadius.circular(17)),
                      child: Icon(item.icon, color: AppColors.primaryDark, size: 25)),
                    const SizedBox(height: 9),
                    PpeeText(item.title, maxLines: 2, textAlign: TextAlign.center, overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: AppColors.black)),
                    if (constraints.maxWidth >= 850) ...[const SizedBox(height: 3),
                      PpeeText(item.subtitle, maxLines: 1, overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 10, color: AppColors.gray700))],
                  ])),
              ),
            )),
        ]);
      }),
    );
  }
}

class _QuickDestination {
  const _QuickDestination(this.title, this.subtitle, this.icon, this.route, this.bg);
  final String title;
  final String subtitle;
  final IconData icon;
  final String route;
  final Color bg;
}

class MarketplaceExploreTiles extends StatelessWidget {
  const MarketplaceExploreTiles({super.key});

  @override
  Widget build(BuildContext context) {
    final tiles = <_ExploreItem>[
      const _ExploreItem('SẮC XANH CỦA MỖI NGÀY', 'Khám phá món đồ\nbạn đang tìm',
        'Xem sản phẩm', Icons.shopping_basket_outlined, AppRoutes.products,
        Color(0xFF125CC9), Color(0xFF37A6F5)),
      const _ExploreItem('GÓC KHÁM PHÁ', 'Mọi danh mục\ntrong tầm tay',
        'Xem danh mục', Icons.grid_view_rounded, AppRoutes.categories,
        Color(0xFF4B66D7), Color(0xFF9A9FF6)),
      const _ExploreItem('TRẢI NGHIỆM PPee', 'Tìm kiếm nhanh,\nchọn lựa dễ dàng',
        'Bắt đầu mua sắm', Icons.search_rounded, AppRoutes.products,
        Color(0xFF087F9E), Color(0xFF32C7D4)),
    ];
    return LayoutBuilder(builder: (context, c) {
      if (c.maxWidth < 630) return Column(children: [
        for (var i = 0; i < tiles.length; i++) ...[
          if (i > 0) const SizedBox(height: 10),
          SizedBox(height: 142, width: double.infinity, child: _ExploreCard(item: tiles[i])),
        ],
      ]);
      return Row(children: [for (var i = 0; i < tiles.length; i++) ...[
        if (i > 0) const SizedBox(width: 11),
        Expanded(child: SizedBox(height: 174, child: _ExploreCard(item: tiles[i]))),
      ]]);
    });
  }
}

class _ExploreItem {
  const _ExploreItem(this.eyebrow, this.title, this.action, this.icon, this.route, this.from, this.to);
  final String eyebrow;
  final String title;
  final String action;
  final IconData icon;
  final String route;
  final Color from;
  final Color to;
}

class _ExploreCard extends StatelessWidget {
  const _ExploreCard({required this.item});
  final _ExploreItem item;
  @override
  Widget build(BuildContext context) => Material(color: Colors.transparent, child: InkWell(
    onTap: () => Navigator.pushNamed(context, item.route),
    borderRadius: BorderRadius.circular(12),
    child: Ink(decoration: BoxDecoration(borderRadius: BorderRadius.circular(12),
      gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight,
        colors: [item.from, item.to])),
      child: ClipRRect(borderRadius: BorderRadius.circular(12), child: Stack(children: [
        Positioned(right: -33, top: -18, child: Container(width: 148, height: 148,
          decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: Colors.white24, width: 24)))),
        Positioned(right: 7, bottom: 3, child: Transform.rotate(angle: -.18,
          child: Icon(item.icon, size: 96, color: Colors.white.withValues(alpha: .25)))),
        Padding(padding: const EdgeInsets.all(17), child: Column(
          crossAxisAlignment: CrossAxisAlignment.start, children: [
          PpeeText(item.eyebrow, maxLines: 1, overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: Colors.white70, fontSize: 9, letterSpacing: .8, fontWeight: FontWeight.w800)),
          const SizedBox(height: 9),
          PpeeText(item.title, maxLines: 2, style: const TextStyle(color: Colors.white, fontSize: 19,
            height: 1.15, fontWeight: FontWeight.w900)),
          const Spacer(),
          Row(mainAxisSize: MainAxisSize.min, children: [
            PpeeText(item.action, style: const TextStyle(fontSize: 11, color: Colors.white, fontWeight: FontWeight.w700)),
            const SizedBox(width: 5), const Icon(Icons.arrow_forward_rounded, color: Colors.white, size: 13),
          ]),
        ])),
      ])),
    ),
  ));
}

/// Seller acquisition CTA, separate from customer purchase flows.
class MarketplaceSellerInvite extends StatelessWidget {
  const MarketplaceSellerInvite({super.key});

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(14),
      gradient: const LinearGradient(colors: [Color(0xFF103F91), Color(0xFF247AE6)]),
    ),
    child: LayoutBuilder(builder: (context, c) => Padding(
      padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 21),
      child: c.maxWidth < 670 ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _intro(), const SizedBox(height: 17), _action(context),
      ]) : Row(children: [
        Expanded(child: _intro()), const SizedBox(width: 18), _action(context),
      ]),
    )),
  );

  Widget _intro() => const Row(children: [
    Icon(Icons.storefront_rounded, color: Colors.white, size: 37),
    SizedBox(width: 17),
    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      PpeeText('Bạn muốn mở gian hàng trên PPee?', style: TextStyle(
        color: Colors.white, fontSize: 19, fontWeight: FontWeight.w900)),
      SizedBox(height: 6),
      PpeeText('Khám phá Kênh Người Bán và không gian quản lý shop riêng.',
        style: TextStyle(color: Color(0xFFE4EFFF), fontSize: 12, height: 1.5)),
    ])),
  ]);

  Widget _action(BuildContext context) => FilledButton.icon(
    onPressed: () => Navigator.pushNamed(context, AppRoutes.seller),
    style: FilledButton.styleFrom(backgroundColor: Colors.white,
      foregroundColor: AppColors.primaryDark),
    icon: const Icon(Icons.arrow_forward_rounded),
    label: const PpeeText('Trở thành người bán'),
  );
}

class MarketplaceFooter extends StatelessWidget {
  const MarketplaceFooter({super.key});
  @override
  Widget build(BuildContext context) => Container(
    color: const Color(0xFF102D57),
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 34),
    child: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1250),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Wrap(spacing: 40, runSpacing: 26, children: [
          SizedBox(width: 255, child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Row(children: [Icon(Icons.local_mall_rounded, color: Colors.white, size: 27),
              SizedBox(width: 8), PpeeText('PPee', style: TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w900))]),
            const SizedBox(height: 12),
            PpeeText('Không gian mua sắm trực tuyến với nhiều danh mục, giúp bạn khám phá và lựa chọn sản phẩm dễ dàng.',
              style: TextStyle(color: Colors.white.withValues(alpha: .76), fontSize: 12, height: 1.65)),
          ])),
          _FooterColumn(title: 'KHÁM PHÁ', links: const [
            ('Trang chủ', AppRoutes.home), ('Danh mục', AppRoutes.categories), ('Tất cả sản phẩm', AppRoutes.products)]),
          _FooterColumn(title: 'TÀI KHOẢN', links: const [
            ('Đăng nhập', AppRoutes.login), ('Yêu thích', AppRoutes.favorites), ('Đơn mua', AppRoutes.orders)]),
          _FooterColumn(title: 'NGƯỜI BÁN', links: const [
            ('Kênh Người Bán', AppRoutes.seller),
            ('Thiết lập gian hàng', AppRoutes.sellerSetup),
            ('Xem Seller Center', AppRoutes.sellerDashboard)]),
          _FooterColumn(title: 'TRẢI NGHIỆM', links: const [
            ('Tìm kiếm sản phẩm', AppRoutes.products), ('Khám phá gợi ý', AppRoutes.home)]),
        ]),
        const SizedBox(height: 25),
        Divider(color: Colors.white.withValues(alpha: .17)),
        const SizedBox(height: 12),
        const PpeeText('© PPee · Giao diện sàn thương mại điện tử',
          style: TextStyle(color: Colors.white60, fontSize: 11)),
      ])),
    ),
  );
}

class _FooterColumn extends StatelessWidget {
  const _FooterColumn({required this.title, required this.links});
  final String title;
  final List<(String, String)> links;
  @override
  Widget build(BuildContext context) => SizedBox(width: 150, child: Column(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      PpeeText(title, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 12, letterSpacing: .5)),
      const SizedBox(height: 11),
      for (final (label, route) in links)
        Padding(padding: const EdgeInsets.only(bottom: 6), child: InkWell(
          onTap: () => Navigator.pushNamed(context, route),
          child: Padding(padding: const EdgeInsets.symmetric(vertical: 3),
            child: PpeeText(label, style: const TextStyle(color: Color(0xFFD0DEF4), fontSize: 12))),
        )),
    ],
  ));
}
