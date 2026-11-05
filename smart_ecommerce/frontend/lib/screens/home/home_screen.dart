import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/routes/ppee_route_observer.dart';
import '../../core/theme/app_colors.dart';
import '../../models/product.dart';
import '../../providers/category_provider.dart';
import '../../providers/product_provider.dart';
import '../../providers/auth_provider.dart';
import '../../providers/recommendation_provider.dart';
import '../../providers/behavior_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/marketplace_category_tile.dart';
import '../../widgets/product_card.dart';
import '../../widgets/recommendation_section.dart';
import '../../widgets/marketplace_sections.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with RouteAware {
  PageRoute<dynamic>? _observedRoute;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final route = ModalRoute.of(context);
    if (route is PageRoute<dynamic> && route != _observedRoute) {
      ppeeRouteObserver.unsubscribe(this);
      _observedRoute = route;
      ppeeRouteObserver.subscribe(this, route);
    }
  }

  @override
  void didPopNext() {
    final auth = context.read<AuthProvider>();
    context.read<RecommendationProvider>().loadHome(personalizedAllowed: auth.isCustomer);
  }

  @override
  void dispose() {
    ppeeRouteObserver.unsubscribe(this);
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    final products = context.read<ProductProvider>();
    final categories = context.read<CategoryProvider>();
    final auth = context.read<AuthProvider>();
    final recommendations = context.read<RecommendationProvider>();
    final behavior = context.read<BehaviorProvider>();
    Future.microtask(() {
      if (!mounted) return;
      products.loadProducts();
      categories.load();
      recommendations.loadHome(personalizedAllowed: auth.isCustomer);
      if (auth.isCustomer) behavior.load();
    });
  }

  @override
  Widget build(BuildContext context) {
    final products = context.watch<ProductProvider>();
    final categories = context.watch<CategoryProvider>();
    final recommendations = context.watch<RecommendationProvider>();
    final auth = context.watch<AuthProvider>();
    return Scaffold(
      appBar: const CatalogHeader(),
      bottomNavigationBar: const CatalogBottomNav(current: AppRoutes.home),
      body: RefreshIndicator(
        onRefresh: () async {
          await Future.wait([
            products.loadProducts(force: true),
            categories.load(force: true),
            recommendations.loadHome(personalizedAllowed: auth.isCustomer),
            if (auth.isCustomer) context.read<BehaviorProvider>().load(),
          ]);
        },
        child: ListView(padding: EdgeInsets.zero, children: [
          const SizedBox(height: 20),
          const _MarketplaceBanners(),
          const SizedBox(height: 12),
          _center(child: const MarketplaceQuickActions()),
          const SizedBox(height: 12),
          _center(child: MarketplaceSectionFrame(
            title: 'Danh mục nổi bật',
            subtitle: 'Chọn ngành hàng và khám phá các sản phẩm hiện có',
            icon: Icons.grid_view_rounded,
            onMore: () => Navigator.pushNamed(context, AppRoutes.categories),
            child: _CategoryGrid(provider: categories),
          )),
          const SizedBox(height: 16),
          _center(child: const MarketplaceExploreTiles()),
          const SizedBox(height: 16),
          _center(child: const MarketplaceSellerInvite()),
          const SizedBox(height: 16),
          if (products.saleProducts.isNotEmpty) ...[
            _center(child: MarketplaceSectionFrame(
              title: 'Ưu đãi đang có',
              subtitle: 'Sản phẩm có giá ưu đãi từ dữ liệu thực tế',
              icon: Icons.local_fire_department_rounded,
              highlight: true,
              onMore: () => Navigator.pushNamed(context, AppRoutes.products),
              child: _ProductGrid(products: products.saleProducts, provider: products, rows: 1),
            )),
            const SizedBox(height: 16),
          ],
          if (recommendations.loading)
            const Padding(padding: EdgeInsets.symmetric(vertical: 22),
              child: Center(child: CircularProgressIndicator()))
          else ...[
            RecommendationSection(title: 'SẢN PHẨM PHỔ BIẾN', type: 'POPULAR', items: recommendations.popular),
            if (auth.isCustomer && recommendations.personalized.isNotEmpty)
              RecommendationSection(title: 'GỢI Ý CHO BẠN', type: 'PERSONALIZED', items: recommendations.personalized),
            if (auth.isCustomer && recommendations.personalized.isEmpty)
              _center(child: Padding(padding: const EdgeInsets.all(18),
                child: Row(children: [
                  const Icon(Icons.auto_awesome_outlined, color: AppColors.primary),
                  const SizedBox(width: 10),
                  Expanded(child: Text(recommendations.error ??
                    'Chưa có đủ sản phẩm phù hợp để gợi ý lúc này. Hãy thử khám phá thêm danh mục.')),
                  TextButton(onPressed: () => context.read<RecommendationProvider>()
                    .loadHome(personalizedAllowed: true), child: const Text('Thử lại')),
                ]))),
          ],
          if (products.newProducts.isNotEmpty) ...[
            const SizedBox(height: 16),
            _center(child: MarketplaceSectionFrame(
              title: 'Sản phẩm mới cập nhật',
              subtitle: 'Những mặt hàng mới trong danh sách sản phẩm',
              icon: Icons.new_releases_outlined,
              onMore: () => Navigator.pushNamed(context, AppRoutes.products),
              child: _ProductGrid(products: products.newProducts, provider: products, rows: 1),
            )),
          ],
          const SizedBox(height: 16),
          _center(child: _ShoppingIdeas(onExplore: () => Navigator.pushNamed(context, AppRoutes.products))),
          const SizedBox(height: 16),
          _center(child: MarketplaceSectionFrame(
            title: 'Gợi ý khám phá hôm nay',
            subtitle: 'Khám phá tất cả mặt hàng đang được hiển thị trên PPee',
            icon: Icons.auto_awesome_rounded,
            highlight: true,
            onMore: () => Navigator.pushNamed(context, AppRoutes.products),
            child: _ProductGrid(products: products.products, provider: products, rows: 2),
          )),
          const SizedBox(height: 20),
          const MarketplaceFooter(),
        ]),
      ),
    );
  }
}

class _MarketplaceBanners extends StatelessWidget {
  const _MarketplaceBanners();

  @override
  Widget build(BuildContext context) => _center(
        child: LayoutBuilder(builder: (context, constraints) {
          final compact = constraints.maxWidth < 750;
          final mainBanner = _PromoBanner(
            title: 'Khám phá thế giới\nmua sắm đa dạng',
            subtitle: 'Nhiều ngành hàng • Thỏa sức lựa chọn',
            action: 'Khám phá ngay',
            eyebrow: 'PPee MARKETPLACE',
            icon: Icons.shopping_bag_outlined,
            gradient: const [Color(0xFF285BE8), Color(0xFF102C71)],
            onTap: () => Navigator.pushNamed(context, AppRoutes.products),
            large: true,
          );

          // Hai thẻ chiếm toàn bộ cột bên phải, không còn khoảng trắng.
          final secondary = Column(children: [
            Expanded(child: _PromoBanner(
              title: 'Sản phẩm mới\ncập nhật mỗi ngày',
              subtitle: 'Khám phá những mặt hàng mới nhất',
              action: 'Xem sản phẩm',
              eyebrow: 'KHÁM PHÁ XU HƯỚNG',
              icon: Icons.auto_awesome_rounded,
              gradient: const [Color(0xFF08A7C7), Color(0xFF174EBB)],
              onTap: () => Navigator.pushNamed(context, AppRoutes.products),
            )),
            const SizedBox(height: 12),
            Expanded(child: _PromoBanner(
              title: 'Danh mục đa dạng\ncho mọi nhu cầu',
              subtitle: 'Thời trang, công nghệ và nhiều hơn nữa',
              action: 'Xem danh mục',
              eyebrow: 'MUA SẮM DỄ DÀNG',
              icon: Icons.category_rounded,
              gradient: const [Color(0xFF6A62D9), Color(0xFF2A449E)],
              onTap: () => Navigator.pushNamed(context, AppRoutes.categories),
            )),
          ]);

          if (compact) {
            return Column(children: [
              SizedBox(height: 214, width: double.infinity, child: mainBanner),
              const SizedBox(height: 10),
              SizedBox(height: 100, child: Row(children: [
                Expanded(child: _MiniBanner(
                  title: 'Hàng mới', icon: Icons.new_releases_outlined,
                  onTap: () => Navigator.pushNamed(context, AppRoutes.products),
                  bg: const Color(0xFFEAF0FF))),
                const SizedBox(width: 10),
                Expanded(child: _MiniBanner(
                  title: 'Danh mục', icon: Icons.widgets_outlined,
                  onTap: () => Navigator.pushNamed(context, AppRoutes.categories),
                  bg: const Color(0xFFE7F7FF))),
              ])),
            ]);
          }
          return SizedBox(height: 300, child: Row(children: [
            Expanded(flex: 7, child: mainBanner),
            const SizedBox(width: 12),
            Expanded(flex: 4, child: secondary),
          ]));
        }),
      );
}

class _PromoBanner extends StatelessWidget {
  const _PromoBanner({
    required this.title,
    required this.subtitle,
    required this.action,
    required this.icon,
    required this.gradient,
    required this.onTap,
    this.eyebrow = 'PPee MARKETPLACE',
    this.large = false,
  });

  final String title;
  final String subtitle;
  final String action;
  final String eyebrow;
  final IconData icon;
  final List<Color> gradient;
  final VoidCallback onTap;
  final bool large;

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(22),
          child: Ink(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(22),
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: gradient,
              ),
            ),
            // Expand cho phép Stack lấp đầy chiều rộng banner.
            child: Stack(fit: StackFit.expand, children: [
              Positioned(
                right: large ? -50 : -25,
                bottom: large ? -80 : -55,
                child: Container(
                  width: large ? 290 : 170,
                  height: large ? 290 : 170,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: Colors.white.withValues(alpha: .14),
                      width: large ? 40 : 25,
                    ),
                  ),
                ),
              ),
              Positioned(
                right: large ? 35 : 26,
                top: large ? 70 : 26,
                child: Transform.rotate(
                  angle: -.18,
                  child: Icon(icon,
                    size: large ? 135 : 95,
                    color: Colors.white.withValues(alpha: .18)),
                ),
              ),
              Padding(
                padding: EdgeInsets.symmetric(
                  horizontal: large ? 30 : 20,
                  vertical: large ? 28 : 13,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    PpeeText(eyebrow,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.white70,
                        fontSize: large ? 11 : 9,
                        letterSpacing: 1.1,
                        fontWeight: FontWeight.w800,
                      )),
                    SizedBox(height: large ? 12 : 5),
                    PpeeText(title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: large ? 29 : 19,
                        fontWeight: FontWeight.w900,
                        height: 1.08,
                      )),
                    SizedBox(height: large ? 12 : 4),
                    PpeeText(subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: .92),
                        fontSize: large ? 13 : 10,
                      )),
                    SizedBox(height: large ? 18 : 7),
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Container(
                        padding: EdgeInsets.symmetric(
                          horizontal: large ? 16 : 10,
                          vertical: large ? 10 : 5,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(mainAxisSize: MainAxisSize.min, children: [
                          PpeeText(action, style: TextStyle(
                            color: gradient.last,
                            fontWeight: FontWeight.w800,
                            fontSize: large ? 12 : 10,
                          )),
                          const SizedBox(width: 5),
                          Icon(Icons.arrow_forward_rounded,
                            size: large ? 15 : 12,
                            color: gradient.last),
                        ]),
                      ),
                    ),
                  ],
                ),
              ),
            ]),
          ),
        ),
      );
}

class _MiniBanner extends StatelessWidget {
  const _MiniBanner({required this.title, required this.icon, required this.onTap, required this.bg});
  final String title;
  final IconData icon;
  final VoidCallback onTap;
  final Color bg;
  @override
  Widget build(BuildContext context) => Material(color: bg, borderRadius: BorderRadius.circular(10),
    child: InkWell(onTap: onTap, borderRadius: BorderRadius.circular(10), child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [Icon(icon, size: 36, color: AppColors.primary), const SizedBox(height: 8),
        PpeeText(title, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.black))])));
}

class _CategoryGrid extends StatelessWidget {
  const _CategoryGrid({required this.provider});
  final CategoryProvider provider;
  @override
  Widget build(BuildContext context) {
    if (provider.status == LoadStatus.loading || provider.status == LoadStatus.initial) {
      return const SizedBox(height: 140, child: Center(child: CircularProgressIndicator()));
    }
    if (provider.status == LoadStatus.error) {
      return PpeeText(provider.errorMessage ?? 'Không thể tải danh mục.');
    }
    if (provider.activeCategories.isEmpty) return const PpeeText('Chưa có danh mục.');
    // The homepage presents top-level departments; children appear inside the
    // selected department, not as unrelated tiles mixed into the root grid.
    final roots = provider.activeCategories.where((c) => c.parentId == null).toList();
    return LayoutBuilder(builder: (context, c) => GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: roots.length,
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: c.maxWidth < 530 ? 3 : c.maxWidth < 750 ? 4 : c.maxWidth < 1060 ? 6 : 10,
        mainAxisExtent: 122,
        crossAxisSpacing: 2, mainAxisSpacing: 4),
      itemBuilder: (context, index) => MarketplaceCategoryTile(
        category: roots[index], index: index),
    ));
  }
}

class _ProductGrid extends StatelessWidget {
  const _ProductGrid({required this.products, required this.provider, this.limit, this.rows});
  final List<Product> products;
  final ProductProvider provider;
  final int? limit;
  final int? rows;
  @override
  Widget build(BuildContext context) {
    if (provider.status == LoadStatus.loading || provider.status == LoadStatus.initial) {
      return const SizedBox(height: 170, child: Center(child: CircularProgressIndicator()));
    }
    if (provider.status == LoadStatus.error) return _ErrorState(
      message: provider.errorMessage ?? 'Không thể tải sản phẩm.', retry: () => provider.loadProducts(force: true));
    if (products.isEmpty) return const Padding(padding: EdgeInsets.symmetric(vertical: 24), child: PpeeText('Chưa có sản phẩm.'));
    return LayoutBuilder(builder: (context, c) {
      final columns = ShopLayout.gridColumns(c.maxWidth);
      final maxItems = rows == null ? limit : rows! * columns;
      final shown = maxItems == null ? products : products.take(maxItems).toList();
      if (rows == null && c.maxWidth > 880 && shown.length <= 2) {
        return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(width: shown.length * 238.0,
            child: GridView.builder(
              shrinkWrap: true, physics: const NeverScrollableScrollPhysics(),
              itemCount: shown.length,
              gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: shown.length, mainAxisExtent: 317,
                crossAxisSpacing: 10),
              itemBuilder: (_, i) => ProductCard(product: shown[i]),
            )),
          const SizedBox(width: 12),
          const Expanded(child: _ProductDiscoveryPromo()),
        ]);
      }
      return GridView.builder(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: shown.length,
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: columns,
          mainAxisExtent: c.maxWidth < 680 ? 290 : 317,
          crossAxisSpacing: 9, mainAxisSpacing: 11),
        itemBuilder: (_, i) => ProductCard(product: shown[i]),
      );
    });
  }
}

class _ProductDiscoveryPromo extends StatelessWidget {
  const _ProductDiscoveryPromo();
  @override
  Widget build(BuildContext context) => Container(
    height: 317,
    clipBehavior: Clip.antiAlias,
    decoration: BoxDecoration(borderRadius: BorderRadius.circular(10),
      gradient: const LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight,
        colors: [Color(0xFFE5F2FF), Color(0xFFF3F7FF)])),
    child: Stack(children: [
      Positioned(right: -45, top: -60,
        child: Icon(Icons.shopping_bag_rounded, size: 280,
          color: AppColors.primary.withValues(alpha: .075))),
      Padding(padding: const EdgeInsets.all(25), child: Column(
        crossAxisAlignment: CrossAxisAlignment.start, children: [
          const PpeeText('PPee DISCOVERY', style: TextStyle(letterSpacing: 1.4,
            color: AppColors.primary, fontSize: 10, fontWeight: FontWeight.w900)),
          const SizedBox(height: 12),
          const PpeeText('Khám phá thêm\nnhiều lựa chọn', style: TextStyle(
            color: AppColors.black, fontSize: 26, height: 1.16, fontWeight: FontWeight.w900)),
          const SizedBox(height: 10),
          const PpeeText('Tìm kiếm sản phẩm, duyệt danh mục và lưu lại món đồ bạn quan tâm.',
            style: TextStyle(color: AppColors.charcoal, fontSize: 13, height: 1.5)),
          const Spacer(),
          Wrap(spacing: 8, runSpacing: 7, children: [
            FilledButton.icon(onPressed: () => Navigator.pushNamed(context, AppRoutes.products),
              icon: const Icon(Icons.search_rounded, size: 17), label: const PpeeText('Xem sản phẩm')),
            OutlinedButton.icon(onPressed: () => Navigator.pushNamed(context, AppRoutes.categories),
              icon: const Icon(Icons.grid_view_rounded, size: 16), label: const PpeeText('Danh mục')),
          ]),
        ])),
    ]),
  );
}

class _ShoppingIdeas extends StatelessWidget {
  const _ShoppingIdeas({required this.onExplore});
  final VoidCallback onExplore;
  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(color: const Color(0xFFEAF3FF), borderRadius: BorderRadius.circular(12),
      border: Border.all(color: const Color(0xFFCDE1FF))),
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
    child: LayoutBuilder(builder: (context, box) {
      final label = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const PpeeText('KHÁM PHÁ THEO CÁCH CỦA BẠN', style: TextStyle(fontSize: 10,
          color: AppColors.primaryDark, fontWeight: FontWeight.w900, letterSpacing: .7)),
        const SizedBox(height: 7),
        const PpeeText('Tìm sản phẩm phù hợp với nhu cầu', style: TextStyle(fontSize: 19,
          fontWeight: FontWeight.w900, color: AppColors.black)),
        const SizedBox(height: 5),
        const PpeeText('Tìm kiếm, xem sản phẩm và lưu lại những món đồ bạn thích.',
          style: TextStyle(color: AppColors.gray700, fontSize: 12)),
      ]);
      final action = FilledButton.icon(onPressed: onExplore,
        icon: const Icon(Icons.search_rounded, size: 18), label: const PpeeText('Bắt đầu khám phá'));
      if (box.maxWidth < 580) return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        label, const SizedBox(height: 13), action,
      ]);
      return Row(children: [Expanded(child: label), const SizedBox(width: 12), action]);
    }),
  );
}

Widget _center({required Widget child}) => Center(child: ConstrainedBox(
  constraints: const BoxConstraints(maxWidth: 1270),
  child: Padding(padding: const EdgeInsets.symmetric(horizontal: 12), child: child),
));

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.retry});
  final String message;
  final VoidCallback retry;
  @override
  Widget build(BuildContext context) => Center(child: Column(children: [PpeeText(message),
    const SizedBox(height: 12), OutlinedButton(onPressed: retry, child: const PpeeText('Thử lại'))]));
}
