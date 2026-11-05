import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/formatters.dart';
import '../../models/product.dart';
import '../../models/product_image.dart';
import '../../models/review.dart';
import '../../models/product_variant.dart';
import '../../models/recommendation.dart';
import '../../providers/product_provider.dart';
import '../../providers/product_variant_provider.dart';
import '../../providers/cart_provider.dart';
import '../../providers/auth_provider.dart';
import '../../providers/recommendation_provider.dart';
import '../../providers/behavior_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/favorite_button.dart';
import '../../widgets/product_image.dart';
import '../../widgets/recommendation_section.dart';
import '../../widgets/ppee_ui.dart';
import 'product_reviews_section.dart';

class ProductDetailScreen extends StatefulWidget {
  const ProductDetailScreen({super.key, required this.productId, this.recommendationSource});
  final String productId;
  final RecommendationSource? recommendationSource;
  @override
  State<ProductDetailScreen> createState() => _ProductDetailScreenState();
}

class _ProductDetailScreenState extends State<ProductDetailScreen> {
  late Future<_DetailData> _future;
  final PageController _pageController = PageController();
  int _quantity = 1;
  int _imageIndex = 0;
  ProductVariantModel? _variant;
  List<ProductVariantModel> _variants = const [];
  String? _selectedSampleKey;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<_DetailData> _load() async {
    final provider = context.read<ProductProvider>();
    final variantProvider = context.read<ProductVariantProvider>();
    final recommendationProvider = context.read<RecommendationProvider>();
    final auth = context.read<AuthProvider>();
    final behavior = context.read<BehaviorProvider>();
    final product = await provider.getById(widget.productId);
    final results = await Future.wait<dynamic>([
      provider.ensureImages(widget.productId),
      provider.ensureReview(widget.productId),
      variantProvider.byProduct(widget.productId),
      recommendationProvider.related(widget.productId),
    ]);
    _variants = results[2] as List<ProductVariantModel>;
    if (_variants.length == 1) {
      _variant = _variants.first;
      _selectedSampleKey = _groupKey(_variant!);
    }
    if (mounted && auth.isCustomer) {
      behavior.recordView(widget.productId);
      final source = widget.recommendationSource;
      if (source != null) {
        recommendationProvider.track(source.type, source.item, 'VIEW', source.position, authenticated: true);
      }
    }
    return _DetailData(product, results[0] as List<ProductImageModel>, results[1] as ReviewSummary,
      results[3] as List<RecommendationItem>);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const CatalogHeader(),
    body: FutureBuilder<_DetailData>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) return const PpeeLoadingView(message: 'Đang tải chi tiết sản phẩm...');
        if (snapshot.hasError) return ShopEmptyState(icon: Icons.wifi_off_outlined,
          title: 'Không thể tải sản phẩm', message: _errorMessage(snapshot.error),
          action: OutlinedButton(onPressed: () => setState(() => _future = _load()), child: const PpeeText('Thử lại')));
        return _content(snapshot.data!);
      },
    ),
  );

  String _errorMessage(Object? error) {
    if (error is ApiException) return error.message;
    return 'Không thể tải thông tin sản phẩm.';
  }

  String _groupKey(ProductVariantModel v) {
    if (v.imageId != null && v.imageId!.isNotEmpty) return 'image:${v.imageId}';
    if (v.color != null && v.color!.trim().isNotEmpty) return 'color:${v.color!.trim()}';
    return '';
  }

  void _pickSample(String key, List<ProductVariantModel> group,
      List<ProductImageModel> photos) {
    final previousSize = _variant?.size;
    final matching = group.where((v) => v.size == previousSize && v.quantity > 0).toList();
    setState(() {
      _selectedSampleKey = key;
      _variant = matching.isNotEmpty ? matching.first : group.length == 1 ? group.first : null;
      _quantity = 1;
    });
    if (key.startsWith('image:')) {
      final imageId = key.substring(6);
      final idx = photos.indexWhere((photo) => photo.id == imageId);
      if (idx >= 0 && _pageController.hasClients) {
        _pageController.animateToPage(idx,
          duration: const Duration(milliseconds: 220), curve: Curves.easeOut);
      }
    }
  }

  Widget _content(_DetailData data) {
    return SingleChildScrollView(
      child: Column(children: [
        Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1300),
          child: Padding(padding: EdgeInsets.all(ShopLayout.padding(context)),
            child: LayoutBuilder(builder: (context, layout) {
              final desktop = layout.maxWidth >= 820;
              return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PpeeText('Trang chủ  /  ${data.product.categoryName ?? 'Danh mục'}  /  ${data.product.name}',
                  maxLines: 1, overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.gray700, fontSize: 12)),
                const SizedBox(height: 16),
                if (desktop)
                  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Expanded(flex: 11, child: _gallery(data.images)),
                    const SizedBox(width: 30),
                    Expanded(flex: 12, child: _info(data.product, data.review, data.images)),
                  ])
                else ...[
                  _gallery(data.images),
                  const SizedBox(height: 20),
                  _info(data.product, data.review, data.images),
                ],
                const SizedBox(height: 25),
                ProductReviewsSection(productId: data.product.id),
              ]);
            }),
          ),
        )),
        if (data.related.isNotEmpty) RecommendationSection(
          title: 'Có thể bạn cũng thích', type: 'RELATED', items: data.related),
        const SizedBox(height: 50),
      ]),
    );
  }

  Widget _gallery(List<ProductImageModel> images) => Column(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      AspectRatio(aspectRatio: 0.98, child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: ColoredBox(color: Colors.white,
          child: images.isEmpty
            ? const ProductImage(url: null, fit: BoxFit.contain)
            : PageView.builder(
                controller: _pageController,
                itemCount: images.length,
                onPageChanged: (index) => setState(() => _imageIndex = index),
                itemBuilder: (_, index) => ProductImage(url: images[index].imageUrl,
                  fit: BoxFit.contain),
              ),
        ),
      )),
      if (images.length > 1) ...[
        const SizedBox(height: 12),
        SizedBox(height: 80, child: ListView.separated(
          scrollDirection: Axis.horizontal, itemCount: images.length,
          separatorBuilder: (_, __) => const SizedBox(width: 9),
          itemBuilder: (_, i) => InkWell(
            onTap: () => _pageController.animateToPage(i,
              duration: const Duration(milliseconds: 220), curve: Curves.easeOut),
            borderRadius: BorderRadius.circular(8),
            child: Container(width: 78,
              clipBehavior: Clip.antiAlias,
              decoration: BoxDecoration(borderRadius: BorderRadius.circular(8),
                border: Border.all(color: i == _imageIndex ? AppColors.primary : AppColors.gray300,
                  width: i == _imageIndex ? 2 : 1)),
              child: ProductImage(url: images[i].imageUrl, fit: BoxFit.contain),
            ),
          ),
        )),
      ],
    ],
  );

  Widget _info(Product p, ReviewSummary review, List<ProductImageModel> photos) {
    final sampleKeys = <String>[];
    for (final v in _variants) {
      final key = _groupKey(v);
      if (key.isNotEmpty && !sampleKeys.contains(key)) sampleKeys.add(key);
    }
    // When all variants have a sample photo / label, require the sample first.
    final candidateKey = _selectedSampleKey ?? (_variant == null ? null : _groupKey(_variant!));
    final selectedKey = candidateKey != null && sampleKeys.contains(candidateKey)
        ? candidateKey : null;
    final pool = selectedKey == null || selectedKey.isEmpty
        ? (sampleKeys.isEmpty ? _variants : <ProductVariantModel>[])
        : _variants.where((v) => _groupKey(v) == selectedKey).toList();
    final sizes = <String>[];
    for (final v in pool) {
      final size = v.size?.trim() ?? '';
      if (size.isNotEmpty && !sizes.contains(size)) sizes.add(size);
    }
    final variantPrice = _variant?.price ?? p.price;
    final variantSale = _variant?.salePrice;
    final effective = variantSale != null && variantSale < variantPrice
        ? variantSale : (_variant?.price == null ? p.displayPrice : variantPrice);
    final oldPrice = effective < variantPrice ? variantPrice : null;
    final discount = oldPrice != null && oldPrice > 0
        ? ((oldPrice - effective) / oldPrice * 100).round() : 0;
    final canBuy = _variants.isEmpty ? p.inStock : _variant != null && _variant!.quantity > 0;
    final currentStock = _variants.isEmpty ? p.quantity : (_variant?.quantity ?? 0);
    final hasOptions = _variants.isNotEmpty;

    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if ((p.brand ?? '').isNotEmpty) ...[
        PpeeText(p.brand!.toUpperCase(), style: const TextStyle(
          letterSpacing: 1.3, color: AppColors.primary, fontSize: 12,
          fontWeight: FontWeight.w800)),
        const SizedBox(height: 9),
      ],
      PpeeText(p.name, style: Theme.of(context).textTheme.headlineMedium?.copyWith(
        fontSize: 31, height: 1.22, fontWeight: FontWeight.w800)),
      const SizedBox(height: 14),
      if (review.count > 0) Wrap(spacing: 7, crossAxisAlignment: WrapCrossAlignment.center, children: [
        const Icon(Icons.star_rounded, color: Color(0xFFFFB84D), size: 20),
        PpeeText(review.average.toStringAsFixed(1),
          style: const TextStyle(fontWeight: FontWeight.w800)),
        PpeeText('(${review.count} đánh giá)',
          style: const TextStyle(color: AppColors.gray700, fontSize: 12)),
      ]),
      const SizedBox(height: 19),
      Wrap(spacing: 10, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
        PpeeText(Formatters.vnd(effective), style: const TextStyle(
          fontSize: 30, fontWeight: FontWeight.w800, color: AppColors.primary)),
        if (oldPrice != null) PpeeText(Formatters.vnd(oldPrice),
          style: const TextStyle(color: AppColors.gray500,
            decoration: TextDecoration.lineThrough)),
        if (discount > 0) Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(color: AppColors.accentSoft,
            borderRadius: BorderRadius.circular(5)),
          child: PpeeText('-$discount%',
            style: const TextStyle(color: AppColors.accent, fontWeight: FontWeight.w800))),
      ]),
      const SizedBox(height: 14),
      Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(canBuy ? Icons.check_circle_rounded : Icons.cancel_rounded,
          color: canBuy ? AppColors.success : AppColors.error, size: 18),
        const SizedBox(width: 7),
        PpeeText(hasOptions && _variant == null ? 'Chọn mẫu và kích cỡ' :
            canBuy ? 'Còn $currentStock sản phẩm' : 'Hết hàng',
          style: TextStyle(fontWeight: FontWeight.w700,
            color: canBuy ? AppColors.success : AppColors.error)),
      ]),
      const Divider(height: 30),
      if (sampleKeys.isNotEmpty) ...[
        PpeeText(selectedKey == null ? 'Mẫu sản phẩm' :
          'Mẫu sản phẩm • ${_variants.where((v) => _groupKey(v) == selectedKey).first.color ?? ''}',
          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14)),
        const SizedBox(height: 10),
        Wrap(spacing: 9, runSpacing: 9, children: [
          for (final key in sampleKeys)
            _sampleChip(key, photos,
              _variants.where((v) => _groupKey(v) == key).toList(),
              selectedKey == key),
        ]),
        const SizedBox(height: 19),
      ],
      if (sizes.isNotEmpty) ...[
        const PpeeText('Kích thước', style: TextStyle(fontWeight: FontWeight.w800)),
        const SizedBox(height: 10),
        Wrap(spacing: 9, runSpacing: 9, children: [
          for (final size in sizes)
            _sizeChip(size, pool.where((v) => v.size?.trim() == size).toList()),
        ]),
        const SizedBox(height: 19),
      ] else if (hasOptions && sampleKeys.isEmpty) ...[
        // Legacy variants without size or photo: preserve choice by SKU.
        const PpeeText('Phiên bản', style: TextStyle(fontWeight: FontWeight.w800)),
        const SizedBox(height: 9),
        Wrap(spacing: 8, children: [
          for (final v in _variants)
            ChoiceChip(label: PpeeText(v.label.isEmpty ? v.sku : v.label),
              selected: _variant?.id == v.id,
              onSelected: v.quantity > 0 ? (_) => setState(() { _variant = v; _quantity = 1; }) : null),
        ]),
        const SizedBox(height: 16),
      ],
      const PpeeText('Số lượng', style: TextStyle(fontWeight: FontWeight.w800)),
      const SizedBox(height: 9),
      Container(decoration: BoxDecoration(border: Border.all(color: AppColors.gray300),
        borderRadius: BorderRadius.circular(8)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          IconButton(tooltip: tr(context, 'Giảm số lượng'),
            onPressed: _quantity > 1 ? () => setState(() => _quantity--) : null,
            icon: const Icon(Icons.remove)),
          SizedBox(width: 42, child: PpeeText('$_quantity', textAlign: TextAlign.center,
            style: const TextStyle(fontWeight: FontWeight.w800))),
          IconButton(tooltip: tr(context, 'Tăng số lượng'),
            onPressed: canBuy && _quantity < currentStock
                ? () => setState(() => _quantity++) : null,
            icon: const Icon(Icons.add)),
        ])),
      const SizedBox(height: 20),
      Row(children: [
        Expanded(child: FilledButton.icon(
          onPressed: !canBuy ? null : () => _addToCart(p),
          icon: const Icon(Icons.add_shopping_cart_rounded, size: 19),
          label: const PpeeText('Thêm vào giỏ hàng'))),
        const SizedBox(width: 12),
        Container(decoration: BoxDecoration(border: Border.all(color: AppColors.gray300),
          borderRadius: BorderRadius.circular(10)),
          child: FavoriteButton(productId: p.id)),
      ]),
      const SizedBox(height: 24),
      if ((p.description ?? '').trim().isNotEmpty) ...[
        PpeeText('Mô tả sản phẩm', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 10),
        PpeeText(p.description!, style: const TextStyle(color: AppColors.gray700, height: 1.6)),
        const SizedBox(height: 17),
      ],
      const Divider(height: 20),
      Wrap(spacing: 16, runSpacing: 8, children: [
        if ((p.categoryName ?? '').isNotEmpty) _detailTag('Danh mục', p.categoryName!),
        if ((p.productType ?? '').isNotEmpty) _detailTag('Loại', p.productType!),
        if (p.sku.isNotEmpty) _detailTag('SKU', p.sku),
      ]),
    ]);
  }

  Widget _sampleChip(String key, List<ProductImageModel> photos,
      List<ProductVariantModel> group, bool selected) {
    final imageId = key.startsWith('image:') ? key.substring(6) : null;
    final image = imageId == null ? null :
        photos.where((p) => p.id == imageId).firstOrNull;
    final label = group.first.color?.trim().isNotEmpty == true
        ? group.first.color!.trim() : 'Mẫu ${group.first.sku}';
    return InkWell(
      onTap: () => _pickSample(key, group, photos),
      borderRadius: BorderRadius.circular(9),
      child: Tooltip(message: label, child: Container(
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(9),
          border: Border.all(color: selected ? AppColors.primary : AppColors.gray300,
            width: selected ? 2 : 1)),
        child: image != null
          ? SizedBox(width: 68, height: 68,
              child: ClipRRect(borderRadius: BorderRadius.circular(5),
                child: ProductImage(url: image.imageUrl, fit: BoxFit.contain)))
          : Padding(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 13),
              child: PpeeText(label, style: const TextStyle(fontWeight: FontWeight.w700))),
      )),
    );
  }

  Widget _sizeChip(String size, List<ProductVariantModel> entries) {
    final available = entries.where((v) => v.quantity > 0).toList();
    final v = available.isNotEmpty ? available.first : entries.first;
    final selected = _variant?.id == v.id;
    return Tooltip(message: available.isEmpty ? 'Hết hàng' : '$size • Còn ${v.quantity}',
      child: OutlinedButton(
        onPressed: available.isEmpty ? null : () => setState(() {
          _variant = v;
          _quantity = 1;
        }),
        style: OutlinedButton.styleFrom(
          foregroundColor: selected ? AppColors.primary : AppColors.gray700,
          side: BorderSide(color: selected ? AppColors.primary : AppColors.gray300,
            width: selected ? 2 : 1),
          backgroundColor: selected ? AppColors.primarySoft : Colors.white,
          minimumSize: const Size(54, 45),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6))),
        child: PpeeText(size, style: const TextStyle(fontWeight: FontWeight.w700)),
      ),
    );
  }

  Widget _detailTag(String label, String value) => PpeeText('$label: $value',
    style: const TextStyle(color: AppColors.gray700, fontSize: 12));

  Future<void> _addToCart(Product p) async {
    final auth = context.read<AuthProvider>();
    if (!auth.isAuthenticated) {
      Navigator.pushNamed(context, AppRoutes.login);
      return;
    }
    if (!auth.isCustomer) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: PpeeText('Chức năng mua hàng chỉ dành cho tài khoản CUSTOMER.')));
      return;
    }
    final ok = await context.read<CartProvider>().add(p.id, _quantity, variantId: _variant?.id);
    if (!mounted) return;
    if (ok && widget.recommendationSource != null) {
      final source = widget.recommendationSource!;
      context.read<RecommendationProvider>().track(source.type, source.item, 'ADD_TO_CART', source.position, authenticated: true);
    }
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: PpeeText(ok
      ? 'Đã thêm vào giỏ hàng'
      : context.read<CartProvider>().error ?? 'Không thể thêm vào giỏ')));
  }
}

class _DetailData {
  const _DetailData(this.product, this.images, this.review, this.related);
  final Product product;
  final List<ProductImageModel> images;
  final ReviewSummary review;
  final List<RecommendationItem> related;
}
