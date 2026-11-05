import '../core/l10n/language_controller.dart';
import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/routes/app_routes.dart';
import '../core/theme/app_colors.dart';
import '../core/utils/formatters.dart';
import '../models/recommendation.dart';
import '../providers/auth_provider.dart';
import '../providers/recommendation_provider.dart';
import '../providers/product_provider.dart';
import 'product_image.dart';

class RecommendationSection extends StatefulWidget {
  const RecommendationSection({
    super.key,
    required this.title,
    required this.type,
    required this.items,
  });

  final String title;
  final String type;
  final List<RecommendationItem> items;

  @override
  State<RecommendationSection> createState() => _RecommendationSectionState();
}

class _RecommendationSectionState extends State<RecommendationSection> {
  // Cards are 208 px wide, separated by 10 px, with 12 px of list padding.
  static const double _cardWidth = 208;
  static const double _cardStride = 218;
  final ScrollController _scrollController = ScrollController();
  ScrollPosition? _parentScrollPosition;
  double _viewportWidth = 0;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_trackVisibleImpressions);
    WidgetsBinding.instance.addPostFrameCallback((_) => _trackVisibleImpressions());
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // A card is not an impression until its section has entered the visible
    // page, including when the parent page scrolls vertically.
    final parent = Scrollable.maybeOf(context)?.position;
    if (parent != _parentScrollPosition) {
      _parentScrollPosition?.removeListener(_trackVisibleImpressions);
      _parentScrollPosition = parent;
      _parentScrollPosition?.addListener(_trackVisibleImpressions);
    }
  }

  @override
  void didUpdateWidget(covariant RecommendationSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.type != widget.type || oldWidget.items != widget.items) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _trackVisibleImpressions());
    }
  }

  @override
  void dispose() {
    _parentScrollPosition?.removeListener(_trackVisibleImpressions);
    _scrollController.removeListener(_trackVisibleImpressions);
    _scrollController.dispose();
    super.dispose();
  }

  void _trackVisibleImpressions() {
    if (!mounted || widget.items.isEmpty || !_scrollController.hasClients ||
        _viewportWidth <= 0) return;
    final renderObject = context.findRenderObject();
    if (renderObject is! RenderBox || !renderObject.hasSize) return;
    final y = renderObject.localToGlobal(Offset.zero).dy;
    final height = renderObject.size.height;
    final screenHeight = MediaQuery.sizeOf(context).height;
    final verticalOverlap = math.max(0.0,
        math.min(y + height, screenHeight) - math.max(y, 0.0));
    if (verticalOverlap < math.min(height, screenHeight) * 0.25) return;
    final auth = context.read<AuthProvider>();
    // An ADMIN/STAFF session is not permitted to create customer events.
    if (auth.isAuthenticated && !auth.isCustomer) return;
    final provider = context.read<RecommendationProvider>();
    final visibleStart = _scrollController.offset;
    final visibleEnd = visibleStart + _viewportWidth;
    for (var index = 0; index < widget.items.length; index++) {
      final left = 12.0 + index * _cardStride;
      final right = left + _cardWidth;
      final overlap = math.max(0.0,
          math.min(right, visibleEnd) - math.max(left, visibleStart));
      // Count an impression only after at least half the card is visible.
      if (overlap >= _cardWidth / 2) {
        unawaited(provider.track(
          widget.type,
          widget.items[index],
          'IMPRESSION',
          index + 1,
          authenticated: auth.isCustomer,
        ));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.items.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 18, 14, 0),
      child: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1270),
        // Use the same rounded, bordered surface as other home sections.
        // The inner ListView remains horizontally scrollable; clipping applies
        // to the card frame, not the scrolling axis.
        child: Container(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: AppColors.white,
            borderRadius: BorderRadius.circular(21),
            border: Border.all(color: AppColors.gray300.withValues(alpha: .80)),
            boxShadow: [BoxShadow(
              color: AppColors.primaryDeep.withValues(alpha: .03),
              blurRadius: 18,
              offset: const Offset(0, 6),
            )],
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(padding: const EdgeInsets.fromLTRB(22, 20, 18, 18), child: Row(children: [
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(
                  color: AppColors.primarySoft,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(Icons.auto_awesome_rounded,
                    color: AppColors.primary, size: 19),
              ),
              const SizedBox(width: 11),
              Expanded(child: PpeeText(widget.title.toUpperCase(),
                style: const TextStyle(color: AppColors.black, fontSize: 19,
                    fontWeight: FontWeight.w800))),
              TextButton.icon(
                onPressed: () => Navigator.pushNamed(context, AppRoutes.products),
                iconAlignment: IconAlignment.end,
                icon: const Icon(Icons.arrow_forward_ios_rounded, size: 12),
                label: const PpeeText('Xem tất cả',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
              ),
            ])),
            const Divider(height: 1),
            SizedBox(height: 302, child: LayoutBuilder(
              builder: (context, constraints) {
                _viewportWidth = constraints.maxWidth;
                WidgetsBinding.instance.addPostFrameCallback((_) => _trackVisibleImpressions());
                return ListView.separated(
                  controller: _scrollController,
                  padding: const EdgeInsets.all(12),
                  scrollDirection: Axis.horizontal,
                  itemCount: widget.items.length + 1,
                  separatorBuilder: (_, __) => const SizedBox(width: 10),
                  itemBuilder: (context, index) => index == widget.items.length
                    ? const _MoreRecommendationsCard()
                    : _RecommendationCard(
                        item: widget.items[index], type: widget.type,
                        position: index + 1,
                      ),
                );
              },
            )),
          ]),
        ),
      )),
    );
  }
}

class _RecommendationCard extends StatelessWidget {
  const _RecommendationCard({
    required this.item,
    required this.type,
    required this.position,
  });

  final RecommendationItem item;
  final String type;
  final int position;

  @override
  Widget build(BuildContext context) {
    final sold = context.watch<ProductProvider>().salesCounts[item.productId];
    return SizedBox(
        width: 208,
        child: InkWell(
          onTap: () {
            final auth = context.read<AuthProvider>();
            // Click tracking must not block navigation on a slow network.
            if (!auth.isAuthenticated || auth.isCustomer) {
              unawaited(context.read<RecommendationProvider>().track(
                    type,
                    item,
                    'CLICK',
                    position,
                    authenticated: auth.isCustomer,
                  ));
            }
            Navigator.pushNamed(
              context,
              '${AppRoutes.product}/${item.productId}',
              arguments: RecommendationSource(
                type: type,
                item: item,
                position: position,
              ),
            );
          },
          child: Card(
            clipBehavior: Clip.antiAlias,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: ProductImage(url: item.imageUrl)),
                Padding(
                  padding: const EdgeInsets.all(11),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      PpeeText(
                        item.productName,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 8),
                      PpeeText(
                        Formatters.vnd(item.displayPrice),
                        style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.primary, fontSize: 17),
                      ),
                      if (sold != null) ...[
                        const SizedBox(height: 5),
                        PpeeText('Đã bán $sold', style: const TextStyle(
                          fontSize: 11, color: AppColors.gray700)),
                      ],
                      if (item.reason.isNotEmpty) ...[
                        const SizedBox(height: 6),
                        PpeeText(
                          _reasonVi(item.reason),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 11, color: Colors.black54),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      );
  }
}


/// Visual end-cap; not counted as a recommendation impression or click.
class _MoreRecommendationsCard extends StatelessWidget {
  const _MoreRecommendationsCard();
  @override
  Widget build(BuildContext context) => SizedBox(width: 210,
    child: Material(color: AppColors.primarySoft, borderRadius: BorderRadius.circular(10),
      child: InkWell(borderRadius: BorderRadius.circular(10),
        onTap: () => Navigator.pushNamed(context, AppRoutes.products),
        child: Padding(padding: const EdgeInsets.all(16), child: Column(
          mainAxisAlignment: MainAxisAlignment.center, children: [
            Container(padding: const EdgeInsets.all(15),
              decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
              child: const Icon(Icons.explore_outlined, size: 36, color: AppColors.primary)),
            const SizedBox(height: 14),
            const PpeeText('Khám phá thêm', textAlign: TextAlign.center,
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900)),
            const SizedBox(height: 7),
            const PpeeText('Xem những mặt hàng khác trên PPee',
              textAlign: TextAlign.center, style: TextStyle(fontSize: 11,
                color: AppColors.gray700, height: 1.45)),
            const SizedBox(height: 13),
            const Icon(Icons.arrow_forward_rounded, color: AppColors.primary, size: 22),
          ])),
      ),
    ),
  );
}

String _reasonVi(String reason) => switch (reason) {
  'Frequently bought together' => 'Thường được mua cùng',
  'Same category' => 'Cùng danh mục',
  'Similar product type' => 'Sản phẩm tương tự',
  'Similar audience' => 'Phù hợp nhu cầu',
  'Popular product' => 'Được quan tâm nhiều',
  'New arrival' => 'Sản phẩm mới',
  'Based on your purchase history' => 'Theo lịch sử mua hàng',
  'Based on your browsing history' => 'Theo sản phẩm bạn xem',
  'Based on your interests' => 'Theo sở thích của bạn',
  _ => reason,
};
