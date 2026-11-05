import 'dart:async';

import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../models/product.dart';
import '../../models/category.dart';
import '../../models/category_tree.dart';
import '../../repositories/category_repository.dart';
import '../../providers/category_provider.dart';
import '../../providers/product_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/product_card.dart';
import '../../widgets/marketplace_category_tile.dart';
import '../../widgets/marketplace_sections.dart';

class CategoryScreen extends StatefulWidget {
  const CategoryScreen({super.key});
  @override
  State<CategoryScreen> createState() => _CategoryScreenState();
}

class _CategoryScreenState extends State<CategoryScreen> {
  @override
  void initState() {
    super.initState();
    final provider = context.read<CategoryProvider>();
    Future.microtask(provider.load);
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<CategoryProvider>();
    return Scaffold(
      appBar: const CatalogHeader(),
      bottomNavigationBar: const CatalogBottomNav(current: AppRoutes.categories),
      body: ListView(padding: EdgeInsets.zero, children: [
        Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1270),
          child: Padding(padding: EdgeInsets.all(ShopLayout.padding(context)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                TextButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.home),
                  child: const PpeeText('Trang chủ')),
                const Icon(Icons.chevron_right_rounded, size: 16, color: AppColors.gray500),
                const PpeeText('Danh mục', style: TextStyle(color: AppColors.gray700, fontSize: 12)),
              ]),
              const SizedBox(height: 8),
              Container(width: double.infinity,
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(12),
                  gradient: const LinearGradient(colors: [Color(0xFF1054B4), Color(0xFF248FF2)])),
                padding: const EdgeInsets.all(25),
                child: Row(children: [
                  const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    PpeeText('PPee / DANH MỤC', style: TextStyle(color: Colors.white70,
                      fontWeight: FontWeight.w800, fontSize: 11, letterSpacing: 1)),
                    SizedBox(height: 10),
                    PpeeText('Cả thế giới mua sắm trong tầm tay',
                      style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 24, height: 1.2)),
                    SizedBox(height: 9),
                    PpeeText('Khám phá các ngành hàng đang có trên hệ thống.',
                      style: TextStyle(color: Colors.white70, fontSize: 12)),
                  ])),
                  const SizedBox(width: 9),
                  const Icon(Icons.category_outlined, color: Colors.white30, size: 66),
                ]),
              ),
              const SizedBox(height: 16),
              MarketplaceSectionFrame(
                title: 'Tất cả danh mục',
                subtitle: 'Chọn một danh mục để xem sản phẩm thuộc nhóm đó',
                icon: Icons.grid_view_rounded,
                child: _body(provider),
              ),
              const SizedBox(height: 16),
              const MarketplaceExploreTiles(),
            ]))),
        ),
        const SizedBox(height: 24),
        const MarketplaceFooter(),
      ]),
    );
  }

  Widget _body(CategoryProvider provider) {
    if (provider.status == LoadStatus.loading || provider.status == LoadStatus.initial) {
      return const Center(child: CircularProgressIndicator());
    }
    if (provider.status == LoadStatus.error) return ShopEmptyState(
      icon: Icons.wifi_off_rounded, title: 'Không thể tải danh mục',
      message: provider.errorMessage ?? 'Vui lòng kiểm tra kết nối và thử lại.',
      action: OutlinedButton(onPressed: () => provider.load(force: true), child: const PpeeText('Thử lại')),
    );
    final categories = provider.activeCategories.where((c) => c.parentId == null).toList();
    if (categories.isEmpty) return const ShopEmptyState(
      icon: Icons.category_outlined, title: 'Chưa có danh mục', message: 'Danh mục sản phẩm sẽ được hiển thị tại đây.',
    );
    return Container(
      color: AppColors.white,
      child: LayoutBuilder(builder: (context, c) => GridView.builder(
        shrinkWrap: true, physics: const NeverScrollableScrollPhysics(),
        itemCount: categories.length,
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: c.maxWidth < 480 ? 3 : c.maxWidth < 740 ? 4 : c.maxWidth < 1050 ? 6 : 8,
          mainAxisExtent: 124,
          crossAxisSpacing: 3,
          mainAxisSpacing: 3,
        ),
        itemBuilder: (_, i) => MarketplaceCategoryTile(category: categories[i], index: i),
      )),
    );
  }
}

/// Shopee-like taxonomy browsing, with PPee styling and real API data only.
/// Sorting by popularity uses the backend's default ordering (no invented
/// sales count or ratings are shown). Child categories are included in roots.
class CategoryProductsScreen extends StatefulWidget {
  const CategoryProductsScreen({super.key, required this.categoryId, this.categoryName});
  final String categoryId;
  final String? categoryName;
  @override
  State<CategoryProductsScreen> createState() => _CategoryProductsScreenState();
}

class _CategoryListing {
  const _CategoryListing({required this.tree, required this.products});
  final CategoryTree tree;
  final List<Product> products;
}

class _CategoryProductsScreenState extends State<CategoryProductsScreen> {
  late Future<_CategoryListing> _future;
  String _sort = 'default';
  bool _inStockOnly = false;
  int _page = 0;
  static const _pageSize = 20;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_CategoryListing> _load() async {
    // Capture dependencies before awaiting network calls. Avoid accessing a
    // deactivated BuildContext if the buyer navigates away during loading.
    final categoryRepo = context.read<CategoryRepository>();
    final provider = context.read<ProductProvider>();
    final categories = await categoryRepo.getAll();
    // One public catalog read includes the selected root and all descendants.
    final allProducts = await provider.getCatalog();
    final ids = CategoryTree(categories).subtreeIds(widget.categoryId);
    // Missing thumbnails may need the existing product-image endpoint. Do not
    // delay the main listing while they load; ProductCard watches the provider.
    for (final product in allProducts.where((p) =>
        ids.contains(p.categoryId) &&
        (p.imageUrl == null || p.imageUrl!.isEmpty)).take(24)) {
      unawaited(() async {
        try { await provider.ensureImages(product.id); } catch (_) { /* Placeholder remains available. */ }
      }());
    }
    return _CategoryListing(tree: CategoryTree(categories), products: allProducts);
  }

  void _retry() => setState(() => _future = _load());

  void _openCategory(Category category) {
    if (category.id == widget.categoryId) return;
    Navigator.pushReplacementNamed(context, '${AppRoutes.category}/${category.id}',
        arguments: category.name);
  }

  List<Product> _visibleProducts(_CategoryListing listing) {
    final selectedIds = listing.tree.subtreeIds(widget.categoryId);
    final result = listing.products.where((product) =>
      selectedIds.contains(product.categoryId) && (!_inStockOnly || product.inStock)).toList();
    if (_sort == 'new') {
      result.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    } else if (_sort == 'low') {
      result.sort((a, b) => a.displayPrice.compareTo(b.displayPrice));
    } else if (_sort == 'high') {
      result.sort((a, b) => b.displayPrice.compareTo(a.displayPrice));
    }
    // 'default' keeps the public API's default order; sales statistics
    // are not provided by the public products API.
    return result;
  }

  Widget _sideItem(Category category, {required bool active, int depth = 0}) =>
    Material(color: active ? AppColors.primarySoft : Colors.transparent,
      borderRadius: BorderRadius.circular(9),
      child: InkWell(borderRadius: BorderRadius.circular(9),
        onTap: () => _openCategory(category),
        child: Padding(
          padding: EdgeInsets.fromLTRB(10.0 + depth * 12, 11, 9, 11),
          child: Row(children: [
            if (depth == 0) const Icon(Icons.category_outlined, size: 16, color: AppColors.primary)
            else const Icon(Icons.chevron_right_rounded, size: 15, color: AppColors.gray500),
            const SizedBox(width: 6),
            Expanded(child: PpeeText(category.name, maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 12.5, fontWeight: active ? FontWeight.w800 : FontWeight.w500,
                color: active ? AppColors.primaryDark : AppColors.black))),
          ]),
        ),
      ),
    );

  Widget _sidebar(CategoryTree tree, Category root) {
    final shown = <Widget>[];
    final seen = <String>{};
    void visit(Category item, int depth) {
      if (!seen.add(item.id)) return;
      shown.add(_sideItem(item, depth: depth, active: item.id == widget.categoryId));
      for (final child in tree.childrenOf(item.id)) {
        visit(child, depth + 1);
      }
    }
    visit(root, 0);
    return Container(
      padding: const EdgeInsets.all(13),
      decoration: BoxDecoration(color: Colors.white,
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: AppColors.gray300)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Row(children: [Icon(Icons.format_list_bulleted_rounded,
          color: AppColors.primary, size: 19), SizedBox(width: 8),
          Expanded(child: PpeeText('Tất cả danh mục', style: TextStyle(
            fontSize: 15, fontWeight: FontWeight.w900, color: AppColors.black))) ]),
        const Divider(height: 22),
        ...shown,
        if (shown.length == 1) const Padding(
          padding: EdgeInsets.all(12), child: PpeeText('Chưa có danh mục con.',
            style: TextStyle(fontSize: 12, color: AppColors.gray700))),
      ]),
    );
  }

  Widget _mobileCategories(CategoryTree tree, Category root) {
    final children = <Category>[];
    final seen = <String>{root.id};
    void collect(String id) {
      for (final child in tree.childrenOf(id)) {
        if (seen.add(child.id)) {
          children.add(child);
          collect(child.id);
        }
      }
    }
    collect(root.id);
    return Container(padding: const EdgeInsets.all(13),
      decoration: BoxDecoration(color: Colors.white,
        borderRadius: BorderRadius.circular(15), border: Border.all(color: AppColors.gray300)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const PpeeText('Danh mục con', style: TextStyle(fontWeight: FontWeight.w800)),
        const SizedBox(height: 9),
        Wrap(spacing: 7, runSpacing: 7, children: [
          ActionChip(label: PpeeText(root.name),
            backgroundColor: widget.categoryId == root.id ? AppColors.primarySoft : null,
            onPressed: () => _openCategory(root)),
          for (final child in children)
            ActionChip(label: PpeeText(child.name),
              backgroundColor: widget.categoryId == child.id ? AppColors.primarySoft : null,
              onPressed: () => _openCategory(child)),
        ]),
      ]),
    );
  }

  Widget _catalog(_CategoryListing listing) {
    final tree = listing.tree;
    final current = tree.byId[widget.categoryId];
    if (current == null || !current.isActive) {
      return ShopEmptyState(icon: Icons.category_outlined,
        title: 'Không tìm thấy danh mục',
        message: 'Danh mục này có thể đã được ẩn hoặc không tồn tại.');
    }
    final root = tree.rootFor(current.id) ?? current;
    final products = _visibleProducts(listing);
    final pages = products.isEmpty ? 1 : ((products.length - 1) ~/ _pageSize) + 1;
    final page = _page < 0 ? 0 : (_page >= pages ? pages - 1 : _page);
    final shown = products.skip(page * _pageSize).take(_pageSize).toList();
    return Center(child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 1320),
      child: LayoutBuilder(builder: (context, outer) {
        final narrow = outer.maxWidth < 760;
        return SingleChildScrollView(
          padding: EdgeInsets.all(ShopLayout.padding(context)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Wrap(crossAxisAlignment: WrapCrossAlignment.center, children: [
              TextButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.home),
                child: const PpeeText('Trang chủ')),
              const Icon(Icons.chevron_right_rounded, color: AppColors.gray500, size: 16),
              TextButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.categories),
                child: const PpeeText('Danh mục')),
              if (current.id != root.id) ...[
                const Icon(Icons.chevron_right_rounded, color: AppColors.gray500, size: 16),
                TextButton(onPressed: () => _openCategory(root), child: PpeeText(root.name)),
              ],
              const Icon(Icons.chevron_right_rounded, color: AppColors.gray500, size: 16),
              PpeeText(current.name, style: const TextStyle(fontWeight: FontWeight.w800)),
            ]),
            const SizedBox(height: 12),
            Row(children: [
              CategoryArtwork(category: current, size: 62),
              const SizedBox(width: 15),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PpeeText(current.name, style: const TextStyle(fontSize: 25,
                  fontWeight: FontWeight.w900, color: AppColors.black)),
                const SizedBox(height: 4),
                PpeeText('${products.length} sản phẩm', style: const TextStyle(
                  color: AppColors.gray700, fontSize: 12)),
              ])),
            ]),
            const SizedBox(height: 18),
            if (narrow) ...[_mobileCategories(tree, root), const SizedBox(height: 14)],
            if (narrow) _listingBody(products, shown, page, pages)
            else Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              SizedBox(width: 238, child: _sidebar(tree, root)),
              const SizedBox(width: 16),
              Expanded(child: _listingBody(products, shown, page, pages)),
            ]),
            const SizedBox(height: 24),
          ]),
        );
      }),
    ));
  }

  Widget _listingBody(List<Product> products, List<Product> shown, int page, int pages) =>
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(13),
        decoration: BoxDecoration(color: const Color(0xFFECF1FA),
          border: Border.all(color: AppColors.gray300),
          borderRadius: BorderRadius.circular(13)),
        child: Wrap(spacing: 8, runSpacing: 8,
          crossAxisAlignment: WrapCrossAlignment.center, children: [
          const Padding(padding: EdgeInsets.symmetric(horizontal: 5),
            child: PpeeText('Sắp xếp theo', style: TextStyle(fontSize: 12,
              color: AppColors.gray700, fontWeight: FontWeight.w700))),
          for (final item in const [
            ('default', 'Mặc định'),
            ('new', 'Mới nhất'),
            ('low', 'Giá thấp → cao'),
            ('high', 'Giá cao → thấp'),
          ])
            ChoiceChip(label: PpeeText(item.$2), selected: _sort == item.$1,
              selectedColor: AppColors.primary,
              labelStyle: TextStyle(fontSize: 12, color: _sort == item.$1
                ? Colors.white : AppColors.black),
              onSelected: (_) => setState(() { _sort = item.$1; _page = 0; })),
          FilterChip(label: const PpeeText('Còn hàng'),
            selected: _inStockOnly, onSelected: (value) => setState(() {
              _inStockOnly = value; _page = 0;
            })),
        ]),
      ),
      const SizedBox(height: 13),
      if (shown.isEmpty)
        const Padding(padding: EdgeInsets.symmetric(vertical: 45),
          child: ShopEmptyState(icon: Icons.inventory_2_outlined,
            title: 'Chưa có sản phẩm',
            message: 'Danh mục này chưa có sản phẩm phù hợp.'))
      else LayoutBuilder(builder: (context, constraints) {
        final w = constraints.maxWidth;
        final columns = w >= 950 ? 5 : w >= 720 ? 4 : w >= 550 ? 3 : w >= 350 ? 2 : 1;
        return GridView.builder(
          itemCount: shown.length,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            mainAxisExtent: w < 550 ? 285 : 315,
            crossAxisSpacing: 9, mainAxisSpacing: 11),
          itemBuilder: (_, index) => ProductCard(product: shown[index]),
        );
      }),
      if (pages > 1) Padding(
        padding: const EdgeInsets.only(top: 20),
        child: Wrap(alignment: WrapAlignment.center, crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 10, children: [
          OutlinedButton(onPressed: page == 0 ? null : () => setState(() => _page = page - 1),
            child: const PpeeText('Trước')),
          PpeeText('${page + 1}/$pages'),
          OutlinedButton(onPressed: page >= pages - 1 ? null
            : () => setState(() => _page = page + 1), child: const PpeeText('Sau')),
        ]),
      ),
    ]);

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const CatalogHeader(),
    body: FutureBuilder<_CategoryListing>(future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return ShopEmptyState(icon: Icons.wifi_off_outlined,
            title: 'Không thể tải sản phẩm', message: '${snapshot.error}',
            action: OutlinedButton(onPressed: _retry, child: const PpeeText('Thử lại')));
        }
        return _catalog(snapshot.data!);
      }),
  );
}
