import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/formatters.dart';
import '../../providers/favorite_provider.dart';
import '../../providers/product_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/product_image.dart';

class FavoritesScreen extends StatefulWidget {
  const FavoritesScreen({super.key});
  @override
  State<FavoritesScreen> createState() => _FavoritesScreenState();
}

class _FavoritesScreenState extends State<FavoritesScreen> {
  @override
  void initState() {
    super.initState();
    final favorites = context.read<FavoriteProvider>();
    final products = context.read<ProductProvider>();
    Future.microtask(() async {
      await favorites.loadAll();
      if (!mounted) return;
      await Future.wait(favorites.favorites.map((item) async {
        try { await products.ensureImages(item.productId); } catch (_) { /* Keep the fallback artwork. */ }
      }));
    });
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<FavoriteProvider>();
    final products = context.watch<ProductProvider>();
    return Scaffold(
      appBar: const CatalogHeader(),
      bottomNavigationBar: const CatalogBottomNav(current: AppRoutes.favorites),
      body: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1240),
        child: Padding(padding: EdgeInsets.all(ShopLayout.padding(context)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            ShopHeading(title: 'Sản phẩm yêu thích',
              description: provider.favorites.isEmpty ? 'Lưu sản phẩm để dễ dàng tìm lại' : '${provider.favorites.length} sản phẩm đã lưu'),
            const SizedBox(height: 24),
            if (provider.listLoading)
              const Expanded(child: Center(child: CircularProgressIndicator()))
            else if (provider.error != null && provider.favorites.isEmpty)
              Expanded(child: ShopEmptyState(icon: Icons.wifi_off_outlined, title: 'Không thể tải yêu thích', message: provider.error!,
                action: OutlinedButton(onPressed: provider.loadAll, child: const PpeeText('Thử lại'))))
            else if (provider.favorites.isEmpty)
              Expanded(child: ShopEmptyState(
                icon: Icons.favorite_border_rounded, title: 'Chưa có sản phẩm yêu thích',
                message: 'Chạm vào biểu tượng trái tim trên sản phẩm để lưu lại những món đồ bạn quan tâm.',
                action: FilledButton(onPressed: () => Navigator.pushNamed(context, AppRoutes.products),
                  child: const PpeeText('Khám phá sản phẩm')),
              ))
            else
              Expanded(child: LayoutBuilder(builder: (context, c) => GridView.builder(
                itemCount: provider.favorites.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: ShopLayout.gridColumns(c.maxWidth),
                  mainAxisExtent: c.maxWidth < 650 ? 298 : 333,
                  crossAxisSpacing: 14, mainAxisSpacing: 14,
                ),
                itemBuilder: (context, index) {
                  final item = provider.favorites[index];
                  final images = products.imagesFor(item.productId);
                  return Card(clipBehavior: Clip.antiAlias, child: InkWell(
                    onTap: () => Navigator.pushNamed(context, '${AppRoutes.product}/${item.productId}'),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Expanded(child: Stack(fit: StackFit.expand, children: [
                        ProductImage(url: images.isEmpty ? null : images.first.imageUrl),
                        Positioned(right: 6, top: 6,
                          child: Material(color: Colors.white, shape: const CircleBorder(), child: IconButton(
                            tooltip: tr(context, 'Bỏ yêu thích'),
                            icon: const Icon(Icons.favorite_rounded, color: AppColors.accent),
                            onPressed: provider.isLoading(item.productId) ? null : () async {
                              final ok = await context.read<FavoriteProvider>().toggle(item.productId);
                              if (!ok && context.mounted) ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: PpeeText(context.read<FavoriteProvider>().error ?? 'Không thể cập nhật yêu thích')));
                            },
                          )),
                        ),
                      ])),
                      Padding(padding: const EdgeInsets.all(13), child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start, children: [
                          if ((item.brand ?? '').isNotEmpty) PpeeText(item.brand!.toUpperCase(), maxLines: 1,
                            style: const TextStyle(color: AppColors.gray700, fontSize: 10, letterSpacing: 1)),
                          const SizedBox(height: 4),
                          PpeeText(item.productName, maxLines: 2, overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                          const SizedBox(height: 8),
                          PpeeText(Formatters.vnd(item.displayPrice),
                            style: const TextStyle(color: AppColors.primary, fontSize: 16, fontWeight: FontWeight.w800)),
                          const SizedBox(height: 8),
                          Row(children: [const Icon(Icons.arrow_forward_rounded, size: 15, color: AppColors.primary),
                            const SizedBox(width: 5), PpeeText('Xem chi tiết', style: Theme.of(context).textTheme.bodySmall?.copyWith(color: AppColors.primary))]),
                        ],
                      )),
                    ]),
                  ));
                },
              ))),
          ]),
        ),
      )),
    );
  }
}
