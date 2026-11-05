import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';

import '../core/routes/app_routes.dart';
import '../core/theme/app_colors.dart';
import '../core/utils/image_url.dart';
import '../models/category.dart';

/// A visual category tile powered by the existing Category model and routes.
class MarketplaceCategoryTile extends StatelessWidget {
  const MarketplaceCategoryTile({super.key, required this.category, this.index = 0});
  final Category category;
  final int index;

  @override
  Widget build(BuildContext context) {
    const tones = [
      Color(0xFFEAF3FF), Color(0xFFE6F7FF), Color(0xFFEBF4FF),
      Color(0xFFF0F4FF), Color(0xFFEEF1FF), Color(0xFFE7F8FC),
    ];
    return Material(
      color: Colors.white,
      child: InkWell(
        onTap: () => Navigator.pushNamed(context, '${AppRoutes.category}/${category.id}', arguments: category.name),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 8),
          child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            Container(
              height: 57, width: 57,
              decoration: BoxDecoration(color: tones[index % tones.length], borderRadius: BorderRadius.circular(18)),
              child: _CategoryVisual(category: category,
                fallback: marketplaceCategoryIcon(category.name),
                accent: index.isEven ? AppColors.primary : AppColors.charcoal),
            ),
            const SizedBox(height: 9),
            PpeeText(category.name, maxLines: 2, overflow: TextOverflow.ellipsis, textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.black, fontSize: 12, fontWeight: FontWeight.w600, height: 1.25)),
          ]),
        ),
      ),
    );
  }
}

class _CategoryVisual extends StatelessWidget {
  const _CategoryVisual({required this.category, required this.fallback, required this.accent, this.size = 57});
  final Category category;
  final IconData fallback;
  final Color accent;
  final double size;
  @override
  Widget build(BuildContext context) {
    final icon = iconForCategory(category);
    final url = category.image?.startsWith('icon:') == true ? '' : ImageUrl.resolve(category.image ?? '');
    if (url.isEmpty) return Icon(icon ?? fallback, color: accent, size: 27);
    return ClipRRect(borderRadius: BorderRadius.circular(17), child: Image.network(
      url, width: size, height: size, fit: BoxFit.cover,
      errorBuilder: (_, __, ___) => Icon(icon ?? fallback, color: accent, size: 27),
    ));
  }
}

IconData marketplaceCategoryIcon(String value) {
  final name = value.toLowerCase();
  if (name.contains('shoe') || name.contains('giày') || name.contains('sneaker') || name.contains('sandal')) return Icons.directions_run_outlined;
  if (name.contains('shirt') || name.contains('clothe') || name.contains('dress') || name.contains('áo') || name.contains('quần') || name.contains('fashion') || name.contains('thời trang')) return Icons.checkroom_outlined;
  if (name.contains('phone') || name.contains('điện thoại')) return Icons.smartphone_outlined;
  if (name.contains('laptop') || name.contains('máy tính')) return Icons.laptop_mac_outlined;
  if (name.contains('camera') || name.contains('máy ảnh')) return Icons.photo_camera_outlined;
  if (name.contains('accessor') || name.contains('phụ kiện') || name.contains('watch') || name.contains('đồng hồ')) return Icons.watch_outlined;
  if (name.contains('home') || name.contains('gia dụng')) return Icons.home_outlined;
  if (name.contains('beauty') || name.contains('làm đẹp')) return Icons.spa_outlined;
  if (name.contains('sport') || name.contains('thể thao')) return Icons.sports_soccer_outlined;
  if (name.contains('book') || name.contains('sách')) return Icons.menu_book_outlined;
  if (name.contains('food') || name.contains('thực phẩm')) return Icons.restaurant_outlined;
  if (name.contains('car') || name.contains('xe')) return Icons.directions_car_outlined;
  return Icons.category_outlined;
}

/// Admin-picked icons are stored as small stable `icon:key` identifiers inside
/// the existing categories.image field, rather than random icons by list order.
const Map<String, IconData> categoryIconChoices = {
  'fashion': Icons.checkroom_outlined,
  'shoes': Icons.directions_run_outlined,
  'beauty': Icons.spa_outlined,
  'electronics': Icons.devices_outlined,
  'home': Icons.chair_outlined,
  'sport': Icons.sports_soccer_outlined,
  'books': Icons.menu_book_outlined,
  'food': Icons.restaurant_outlined,
  'accessories': Icons.watch_outlined,
  'kids': Icons.child_care_outlined,
  'general': Icons.category_outlined,
};

IconData? iconForCategory(Category category) {
  final value = category.image;
  if (value == null || !value.startsWith('icon:')) return null;
  return categoryIconChoices[value.substring(5)];
}

class CategoryArtwork extends StatelessWidget {
  const CategoryArtwork({super.key, required this.category, this.size = 56});
  final Category category;
  final double size;
  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(12),
    child: Container(
      width: size, height: size, color: const Color(0xFFEAF3FF),
      child: _CategoryVisual(
        category: category,
        fallback: marketplaceCategoryIcon(category.name),
        accent: AppColors.primary,
        size: size,
      ),
    ),
  );
}
