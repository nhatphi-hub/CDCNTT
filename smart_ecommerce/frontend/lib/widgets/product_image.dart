import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';

import '../core/theme/app_colors.dart';
import '../core/utils/image_url.dart';

/// Never manufactures a product photo: if the backend has no valid image,
/// show a styled neutral placeholder instead of a broken image icon.
class ProductImage extends StatelessWidget {
  const ProductImage({super.key, required this.url, this.fit = BoxFit.cover});
  final String? url;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) {
    final resolved = ImageUrl.resolve(url ?? '');
    if (resolved.isEmpty) return _placeholder();
    return Image.network(
      resolved,
      fit: fit,
      // Flutter Web: prefer an HTML <img> for cross-origin public images.
      // This avoids the XHR CORS failure seen with external product photos.
      webHtmlElementStrategy: WebHtmlElementStrategy.prefer,
      width: double.infinity,
      height: double.infinity,
      errorBuilder: (_, __, ___) => _placeholder(),
      loadingBuilder: (_, child, loading) {
        if (loading == null) return child;
        return _placeholder(loading: true);
      },
    );
  }

  Widget _placeholder({bool loading = false}) => Container(
    decoration: const BoxDecoration(gradient: LinearGradient(
      begin: Alignment.topLeft, end: Alignment.bottomRight,
      colors: [Color(0xFFF1F8FF), Color(0xFFE8F1FF), Color(0xFFF8FCFF)],
    )),
    child: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
      Container(padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(color: Colors.white.withValues(alpha: .76), borderRadius: BorderRadius.circular(24)),
        child: Icon(loading ? Icons.hourglass_top_rounded : Icons.shopping_bag_outlined,
          color: AppColors.primary.withValues(alpha: .60), size: 39)),
      if (!loading) ...[
        const SizedBox(height: 10),
        const PpeeText('PPee', style: TextStyle(color: AppColors.gray500, fontSize: 10, letterSpacing: .6, fontWeight: FontWeight.w800)),
      ],
    ])),
  );
}
