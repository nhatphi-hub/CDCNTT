import '../../core/l10n/language_controller.dart';
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../models/product.dart';
import '../../providers/product_provider.dart';
import '../../providers/auth_provider.dart';
import '../../providers/behavior_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/product_card.dart';
import '../../widgets/marketplace_sections.dart';
import '../../widgets/ppee_ui.dart';
import '../../widgets/recent_searches.dart';

class ProductListScreen extends StatefulWidget {
  const ProductListScreen({super.key, this.initialSearch});
  final String? initialSearch;
  @override
  State<ProductListScreen> createState() => _ProductListScreenState();
}

class _ProductListScreenState extends State<ProductListScreen> {
  late final TextEditingController _search;
  bool _loading = false;
  bool _availableOnly = false;
  String _sort = 'default';
  String? _error;
  List<Product>? _results;
  int _searchRequest = 0;

  @override
  void initState() {
    super.initState();
    _search = TextEditingController(text: widget.initialSearch ?? '');
    final products = context.read<ProductProvider>();
    Future.microtask(() async {
      await products.loadProducts();
      if (mounted && (widget.initialSearch ?? '').isNotEmpty) await _runSearch();
    });
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _runSearch() async {
    final request = ++_searchRequest;
    final keyword = _search.text.trim();
    if (keyword.isEmpty) {
      setState(() { _results = null; _error = null; });
      return;
    }
    setState(() { _loading = true; _error = null; });
    final products = context.read<ProductProvider>();
    final behavior = context.read<BehaviorProvider>();
    if (context.read<AuthProvider>().isCustomer) {
      unawaited(behavior.recordSearch(keyword));
    }
    if (!mounted) return;
    try {
      final items = await products.search(keyword);
      await Future.wait(items.map((item) => products.ensureImages(item.id)));
      if (mounted && request == _searchRequest) setState(() => _results = items);
    } on ApiException catch (e) {
      if (mounted && request == _searchRequest) setState(() => _error = e.message);
    } catch (_) {
      if (mounted && request == _searchRequest) setState(() => _error = 'Không thể tìm kiếm sản phẩm.');
    } finally {
      if (mounted && request == _searchRequest) setState(() => _loading = false);
    }
  }

  List<Product> _filtered(List<Product> items) {
    final result = items.where((item) => !_availableOnly || item.inStock).toList();
    if (_sort == 'priceAsc') result.sort((a, b) => a.displayPrice.compareTo(b.displayPrice));
    if (_sort == 'priceDesc') result.sort((a, b) => b.displayPrice.compareTo(a.displayPrice));
    if (_sort == 'newest') result.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return result;
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ProductProvider>();
    final items = _filtered(_results ?? provider.products);
    return Scaffold(
      appBar: const CatalogHeader(),
      bottomNavigationBar: const CatalogBottomNav(current: AppRoutes.products),
      body: ListView(padding: EdgeInsets.zero, children: [
        Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1270),
          child: Padding(padding: EdgeInsets.all(ShopLayout.padding(context)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                TextButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.home),
                  child: const PpeeText('Trang chủ')),
                const Icon(Icons.chevron_right_rounded, size: 16, color: AppColors.gray500),
                const PpeeText('Tất cả sản phẩm', style: TextStyle(fontSize: 12, color: AppColors.gray700)),
              ]),
              const SizedBox(height: 8),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 30),
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(22),
                  gradient: const LinearGradient(colors: [AppColors.primaryDeep, AppColors.primary, Color(0xFF26A8DC)])),
                child: Wrap(crossAxisAlignment: WrapCrossAlignment.center, spacing: 24, runSpacing: 12, children: [
                  const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    PpeeText('PPee / KHÁM PHÁ', style: TextStyle(color: Color(0xFFBCD8FF),
                      fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 1)),
                    SizedBox(height: 6),
                    PpeeText('Tìm món đồ bạn yêu thích', style: TextStyle(color: Colors.white,
                      fontSize: 25, fontWeight: FontWeight.w900)),
                    SizedBox(height: 5),
                    PpeeText('Khám phá, tìm kiếm và lựa chọn sản phẩm trong hệ thống.',
                      style: TextStyle(color: Colors.white70, fontSize: 12)),
                  ]),
                  const Icon(Icons.shopping_bag_outlined, size: 64, color: Colors.white30),
                ]),
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: AppColors.white, borderRadius: BorderRadius.circular(21),
                  border: Border.all(color: AppColors.gray300),
                  boxShadow: [BoxShadow(color: AppColors.primaryDeep.withValues(alpha: .035),
                    blurRadius: 18, offset: const Offset(0, 6))]),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  TextField(
                    controller: _search,
                    textInputAction: TextInputAction.search,
                    onChanged: (_) => setState(() {}),
                    onSubmitted: (_) => _runSearch(),
                    decoration: InputDecoration(
                      hintText: tr(context, 'Tìm theo tên sản phẩm, thương hiệu...'),
                      filled: true, fillColor: AppColors.gray100,
                      prefixIcon: const Icon(Icons.search_rounded, color: AppColors.primary),
                      suffixIcon: _search.text.isEmpty
                        ? IconButton(tooltip: tr(context, 'Tìm kiếm'), onPressed: _runSearch,
                            icon: const Icon(Icons.arrow_forward_rounded))
                        : IconButton(tooltip: tr(context, 'Xóa tìm kiếm'), onPressed: () {
                            _search.clear(); _runSearch(); setState(() {});
                          }, icon: const Icon(Icons.close_rounded)),
                    ),
                  ),
                  RecentSearches(onSelect: (keyword) {
                    _search.text = keyword;
                    _search.selection = TextSelection.collapsed(offset: keyword.length);
                    _runSearch();
                  }),
                  const SizedBox(height: 13),
                  Wrap(spacing: 12, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
                    FilterChip(label: const PpeeText('Chỉ sản phẩm còn hàng'),
                      avatar: const Icon(Icons.inventory_2_outlined, size: 16),
                      selected: _availableOnly, onSelected: (value) => setState(() => _availableOnly = value)),
                    SizedBox(width: 210, child: DropdownButtonFormField<String>(
                      initialValue: _sort, isDense: true,
                      decoration: InputDecoration(labelText: tr(context, 'Sắp xếp'),
                        contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10)),
                      items: const [
                        DropdownMenuItem(value: 'default', child: PpeeText('Mặc định')),
                        DropdownMenuItem(value: 'newest', child: PpeeText('Mới nhất')),
                        DropdownMenuItem(value: 'priceAsc', child: PpeeText('Giá tăng dần')),
                        DropdownMenuItem(value: 'priceDesc', child: PpeeText('Giá giảm dần')),
                      ],
                      onChanged: (value) => setState(() => _sort = value ?? 'default'),
                    )),
                    PpeeText('${items.length} sản phẩm', style: const TextStyle(
                      color: AppColors.gray700, fontWeight: FontWeight.w600, fontSize: 13)),
                  ]),
                ]),
              ),
              if (_error != null) ...[const SizedBox(height: 14),
                PpeeNotice(message: _error!, isError: true)],
              const SizedBox(height: 16),
              LayoutBuilder(builder: (context, box) {
                if (box.maxWidth < 850) return MarketplaceSectionFrame(
                  title: 'Danh sách sản phẩm',
                  subtitle: 'Sản phẩm lấy từ dữ liệu hiện tại',
                  icon: Icons.view_module_rounded,
                  child: _body(provider, items),
                );
                return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  SizedBox(width: 215, child: _BrowseSidebar(products: provider.products)),
                  const SizedBox(width: 15),
                  Expanded(child: MarketplaceSectionFrame(
                    title: 'Danh sách sản phẩm',
                    subtitle: 'Sản phẩm lấy từ dữ liệu hiện tại',
                    icon: Icons.view_module_rounded,
                    child: _body(provider, items),
                  )),
                ]);
              }),
              const SizedBox(height: 16),
              const MarketplaceExploreTiles(),
            ]))),
        ),
        const SizedBox(height: 24),
        const MarketplaceFooter(),
      ]),
    );
  }

  Widget _body(ProductProvider provider, List<Product> items) {
    if (_loading || (provider.status == LoadStatus.loading && _results == null)) return const Center(child: CircularProgressIndicator());
    if (_error != null) return ShopEmptyState(icon: Icons.wifi_off_outlined, title: 'Có lỗi khi tìm kiếm', message: _error!,
      action: OutlinedButton(onPressed: _runSearch, child: const PpeeText('Thử lại')));
    if (provider.status == LoadStatus.error && _results == null) return ShopEmptyState(
      icon: Icons.wifi_off_outlined, title: 'Không thể tải sản phẩm', message: provider.errorMessage ?? 'Vui lòng thử lại.',
      action: OutlinedButton(onPressed: () => provider.loadProducts(force: true), child: const PpeeText('Thử lại')));
    if (items.isEmpty) return const ShopEmptyState(icon: Icons.search_off_rounded, title: 'Không tìm thấy sản phẩm', message: 'Hãy thử từ khóa hoặc bộ lọc khác.');
    return LayoutBuilder(builder: (context, c) => GridView.builder(
      shrinkWrap: true, physics: const NeverScrollableScrollPhysics(),
      itemCount: items.length,
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: ShopLayout.gridColumns(c.maxWidth),
        mainAxisExtent: c.maxWidth < 600 ? 290 : 320,
        crossAxisSpacing: 10, mainAxisSpacing: 10,
      ),
      itemBuilder: (_, i) => ProductCard(product: items[i]),
    ));
  }
}


class _BrowseSidebar extends StatelessWidget {
  const _BrowseSidebar({required this.products});
  final List<Product> products;
  @override
  Widget build(BuildContext context) {
    final categories = <String, (String, String)>{};
    for (final product in products) {
      if (product.categoryId != null && product.categoryName != null && product.categoryName!.trim().isNotEmpty) {
        categories[product.categoryId!] = (product.categoryId!, product.categoryName!);
      }
    }
    return Column(children: [
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(color: AppColors.white, borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.gray300)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Row(children: [Icon(Icons.category_outlined, color: AppColors.primary, size: 18),
            SizedBox(width: 8), PpeeText('Danh mục', style: TextStyle(fontWeight: FontWeight.w800))]),
          const SizedBox(height: 12),
          for (final category in categories.values.take(8))
            ListTile(dense: true, contentPadding: EdgeInsets.zero,
              title: PpeeText(category.$2, maxLines: 1, overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12)),
              trailing: const Icon(Icons.chevron_right_rounded, size: 16),
              onTap: () => Navigator.pushNamed(context,
                '${AppRoutes.category}/${category.$1}', arguments: category.$2)),
          if (categories.isEmpty) const PpeeText('Khám phá sản phẩm theo danh mục.',
            style: TextStyle(color: AppColors.gray700, fontSize: 12)),
          const SizedBox(height: 9),
          OutlinedButton.icon(onPressed: () => Navigator.pushNamed(context, AppRoutes.categories),
            icon: const Icon(Icons.grid_view_rounded, size: 16),
            label: const PpeeText('Tất cả danh mục')),
        ]),
      ),
      const SizedBox(height: 12),
      Container(width: double.infinity, padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(color: AppColors.primarySoft, borderRadius: BorderRadius.circular(12)),
        child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.tips_and_updates_outlined, size: 22, color: AppColors.primary),
          SizedBox(height: 10),
          PpeeText('Mẹo tìm kiếm', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 14)),
          SizedBox(height: 7),
          PpeeText('Bạn có thể nhập tên sản phẩm, chọn bộ lọc còn hàng hoặc sắp xếp theo giá.',
            style: TextStyle(color: AppColors.charcoal, fontSize: 12, height: 1.5)),
        ]),
      ),
    ]);
  }
}
