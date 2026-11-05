import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/routes/app_routes.dart';
import '../core/theme/app_colors.dart';
import '../core/utils/formatters.dart';
import '../models/product.dart';
import '../providers/product_provider.dart';
import 'favorite_button.dart';
import 'product_image.dart';

/// API-backed product card, no invented ratings, sale values or stock counts.
class ProductCard extends StatefulWidget {
  const ProductCard({super.key, required this.product});
  final Product product;

  @override
  State<ProductCard> createState() => _ProductCardState();
}

class _ProductCardState extends State<ProductCard> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final product = widget.product;
    final catalog = context.watch<ProductProvider>();
    final images = catalog.imagesFor(product.id);
    final sold = catalog.salesCounts[product.id];
    final primaryImage = product.imageUrl?.trim();
    final imageUrl = primaryImage != null && primaryImage.isNotEmpty
        ? primaryImage : (images.isEmpty ? null : images.first.imageUrl);
    final discount = product.isOnSale && product.price > 0
        ? ((product.price - product.displayPrice) / product.price * 100).round() : 0;
    final categoryOrBrand = (product.brand ?? '').trim().isNotEmpty
        ? product.brand! : (product.categoryName ?? 'Sản phẩm PPee');

    return MouseRegion(
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 190),
        curve: Curves.easeOutCubic,
        decoration: BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.circular(19),
          border: Border.all(
            color: _hovered ? AppColors.primary.withValues(alpha: .55) : AppColors.gray300,
            width: _hovered ? 1.4 : 1,
          ),
          boxShadow: [BoxShadow(
            color: AppColors.primaryDeep.withValues(alpha: _hovered ? .13 : .045),
            blurRadius: _hovered ? 22 : 11,
            offset: Offset(0, _hovered ? 8 : 3),
          )],
        ),
        clipBehavior: Clip.antiAlias,
        child: Material(color: Colors.transparent, child: InkWell(
          onTap: () => Navigator.pushNamed(context, '${AppRoutes.product}/${product.id}'),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(child: Stack(fit: StackFit.expand, children: [
              Container(color: AppColors.gray100, child: ProductImage(url: imageUrl)),
              if (product.isOnSale) Positioned(top: 10, left: 10, child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
                decoration: BoxDecoration(
                  color: AppColors.highlight, borderRadius: BorderRadius.circular(9),
                ),
                child: PpeeText(discount > 0 ? 'GIẢM $discount%' : 'ĐANG ƯU ĐÃI',
                  style: const TextStyle(color: Colors.white,
                    fontSize: 10, fontWeight: FontWeight.w900)),
              )),
              if (!product.inStock) Positioned.fill(child: ColoredBox(
                color: Colors.black38,
                child: Center(child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                  decoration: BoxDecoration(color: AppColors.black.withValues(alpha: .9),
                    borderRadius: BorderRadius.circular(10)),
                  child: const PpeeText('HẾT HÀNG', style: TextStyle(color: Colors.white,
                    fontSize: 11, fontWeight: FontWeight.w800)))),
              )),
              Positioned(top: 8, right: 8,
                child: Material(color: Colors.white, elevation: 2,
                  shape: const CircleBorder(),
                  child: FavoriteButton(productId: product.id))),
            ])),
            Padding(
              padding: const EdgeInsets.fromLTRB(13, 13, 13, 13),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PpeeText(categoryOrBrand.toUpperCase(), maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 9.5,
                    letterSpacing: .5, color: AppColors.gray700, fontWeight: FontWeight.w800)),
                const SizedBox(height: 6),
                PpeeText(product.name, maxLines: 2, overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12.5, height: 1.3,
                    color: AppColors.black, fontWeight: FontWeight.w700)),
                const SizedBox(height: 11),
                PpeeText(Formatters.vnd(product.displayPrice), maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.primaryDark,
                    fontWeight: FontWeight.w900, fontSize: 17)),
                SizedBox(height: 16,
                  child: product.isOnSale
                      ? PpeeText(Formatters.vnd(product.price),
                          style: const TextStyle(fontSize: 11, color: AppColors.gray700,
                            decoration: TextDecoration.lineThrough))
                      : const SizedBox.shrink()),
                const SizedBox(height: 4),
                Row(children: [
                  Icon(product.inStock ? Icons.check_circle_outline_rounded : Icons.remove_circle_outline_rounded,
                    size: 13, color: product.inStock ? AppColors.success : AppColors.gray700),
                  const SizedBox(width: 5),
                  Expanded(child: PpeeText(
                    sold != null ? 'Đã bán $sold' : (product.inStock ? 'Còn hàng' : 'Hết hàng'),
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 10.5, color: AppColors.gray700,
                      fontWeight: FontWeight.w600))),
                  AnimatedRotation(
                    turns: _hovered ? -.125 : 0,
                    duration: const Duration(milliseconds: 180),
                    child: Icon(Icons.arrow_outward_rounded, size: 18,
                      color: _hovered ? AppColors.primary : AppColors.gray500),
                  ),
                ]),
              ]),
            ),
          ]),
        )),
      ),
    );
  }
}
