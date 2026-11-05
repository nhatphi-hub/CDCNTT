import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/constants/app_spacing.dart';
import '../../core/routes/app_routes.dart';
import '../../models/review.dart';
import '../../providers/review_provider.dart';
import '../../widgets/catalog_header.dart';
import 'review_form_dialog.dart';

class MyReviewsScreen extends StatefulWidget {
  const MyReviewsScreen({super.key});

  @override
  State<MyReviewsScreen> createState() => _MyReviewsScreenState();
}

class _MyReviewsScreenState extends State<MyReviewsScreen> {
  @override
  void initState() {
    super.initState();
    final provider = context.read<ReviewProvider>();
    Future.microtask(provider.loadMine);
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ReviewProvider>();
    return Scaffold(
      appBar: const CatalogHeader(),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 900),
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                PpeeText('Đánh giá của tôi', style: Theme.of(context).textTheme.headlineMedium),
                const SizedBox(height: AppSpacing.lg),
                if (provider.myReviewsLoading)
                  const Expanded(child: Center(child: CircularProgressIndicator()))
                else if (provider.error != null && provider.myReviews.isEmpty)
                  Expanded(child: Center(child: PpeeText(provider.error!)))
                else if (provider.myReviews.isEmpty)
                  const Expanded(child: Center(child: PpeeText('Chưa có đánh giá nào')))
                else
                  Expanded(
                    child: RefreshIndicator(
                      onRefresh: provider.loadMine,
                      child: ListView.separated(
                        itemCount: provider.myReviews.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 12),
                        itemBuilder: (context, index) => _ReviewCard(review: provider.myReviews[index]),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ReviewCard extends StatelessWidget {
  const _ReviewCard({required this.review});
  final Review review;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(child: PpeeText(review.productName, style: const TextStyle(fontWeight: FontWeight.w700))),
                  PpeeText('${review.rating}/5 ★'),
                ],
              ),
              if ((review.comment ?? '').isNotEmpty) ...[
                const SizedBox(height: 8),
                PpeeText(review.comment!),
              ],
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pushNamed(context, '${AppRoutes.product}/${review.productId}'),
                    child: const PpeeText('Xem sản phẩm'),
                  ),
                  TextButton(
                    onPressed: () => showDialog<bool>(
                      context: context,
                      builder: (_) => ChangeNotifierProvider.value(
                        value: context.read<ReviewProvider>(),
                        child: ReviewFormDialog(productId: review.productId, review: review),
                      ),
                    ),
                    child: const PpeeText('Sửa'),
                  ),
                  TextButton(
                    onPressed: () => _confirmDelete(context),
                    child: const PpeeText('Xóa'),
                  ),
                ],
              ),
            ],
          ),
        ),
      );

  Future<void> _confirmDelete(BuildContext context) async {
    final confirmed = await showDialog<bool>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const PpeeText('Xóa đánh giá'),
            content: const PpeeText('Bạn có chắc muốn xóa đánh giá này?'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const PpeeText('Hủy')),
              FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const PpeeText('Xóa')),
            ],
          ),
        ) ??
        false;
    if (!confirmed || !context.mounted) return;
    final provider = context.read<ReviewProvider>();
    final ok = await provider.remove(review);
    if (!ok && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: PpeeText(provider.error ?? 'Không thể xóa đánh giá')));
    }
  }
}
