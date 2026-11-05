import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/review.dart';
import '../../providers/auth_provider.dart';
import '../../providers/review_provider.dart';
import '../review/review_form_dialog.dart';

class ProductReviewsSection extends StatefulWidget {
  const ProductReviewsSection({super.key, required this.productId});
  final String productId;

  @override
  State<ProductReviewsSection> createState() => _ProductReviewsSectionState();
}

class _ProductReviewsSectionState extends State<ProductReviewsSection> {
  String? _loadedForUserId;

  @override
  void initState() {
    super.initState();
    final reviews = context.read<ReviewProvider>();
    Future.microtask(() => reviews.loadProduct(widget.productId));
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final auth = context.watch<AuthProvider>();
    final userId = auth.isCustomer ? auth.user?.id : null;
    if (userId != null && userId != _loadedForUserId) {
      _loadedForUserId = userId;
      Future.microtask(() {
        if (mounted) context.read<ReviewProvider>().loadEligibility(widget.productId);
      });
    } else if (userId == null) {
      _loadedForUserId = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ReviewProvider>();
    final auth = context.watch<AuthProvider>();
    final reviews = provider.forProduct(widget.productId);
    final own = auth.user == null
        ? null
        : reviews.cast<Review?>().firstWhere((review) => review?.userId == auth.user!.id, orElse: () => null);
    final eligibility = provider.eligibilityFor(widget.productId);
    final average = reviews.isEmpty ? 0.0 : reviews.fold<int>(0, (sum, review) => sum + review.rating) / reviews.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Divider(height: 48),
        Row(
          children: [
            Expanded(child: PpeeText('Đánh giá', style: Theme.of(context).textTheme.titleLarge)),
            if (auth.isCustomer && own == null && eligibility?.canReview == true)
              OutlinedButton.icon(
                onPressed: () => _openForm(context),
                icon: const Icon(Icons.rate_review_outlined),
                label: const PpeeText('Viết đánh giá'),
              ),
          ],
        ),
        const SizedBox(height: 8),
        if (auth.isCustomer && own == null && eligibility?.purchasedAndDelivered == false)
          const PpeeText('Bạn có thể đánh giá sau khi đơn hàng được giao và đã thanh toán.'),
        if (auth.isCustomer && provider.checkingEligibility(widget.productId))
          const PpeeText('Đang kiểm tra điều kiện đánh giá...'),
        if (reviews.isNotEmpty) PpeeText('${average.toStringAsFixed(1)} / 5 • ${reviews.length} đánh giá'),
        const SizedBox(height: 16),
        if (provider.isLoadingProduct(widget.productId))
          const Center(child: CircularProgressIndicator())
        else if (provider.error != null && reviews.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                PpeeText(provider.error!),
                const SizedBox(height: 8),
                OutlinedButton(
                  onPressed: () => provider.loadProduct(widget.productId, force: true),
                  child: const PpeeText('Thử lại'),
                ),
              ],
            ),
          )
        else if (reviews.isEmpty)
          const Padding(padding: EdgeInsets.symmetric(vertical: 20), child: PpeeText('Chưa có đánh giá nào'))
        else
          ...reviews.map((review) => _ReviewTile(
                review: review,
                mine: auth.user?.id == review.userId,
                onEdit: () => _openForm(context, review),
                onDelete: () => _delete(context, review),
              )),
      ],
    );
  }

  Future<void> _openForm(BuildContext context, [Review? review]) async {
    await showDialog<bool>(
      context: context,
      builder: (_) => ChangeNotifierProvider.value(
        value: context.read<ReviewProvider>(),
        child: ReviewFormDialog(productId: widget.productId, review: review),
      ),
    );
  }

  Future<void> _delete(BuildContext context, Review review) async {
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

class _ReviewTile extends StatelessWidget {
  const _ReviewTile({required this.review, required this.mine, required this.onEdit, required this.onDelete});
  final Review review;
  final bool mine;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: PpeeText(review.userName, style: const TextStyle(fontWeight: FontWeight.w700))),
                PpeeText('${review.rating}/5 ★'),
              ],
            ),
            if ((review.comment ?? '').isNotEmpty) ...[const SizedBox(height: 6), PpeeText(review.comment!)],
            if ((review.sellerReply ?? '').trim().isNotEmpty) Padding(
              padding:const EdgeInsets.only(top:8,left:12),
              child:PpeeText('Phản hồi từ shop: ${review.sellerReply}',
                  style:const TextStyle(fontStyle:FontStyle.italic))),
            if (mine)
              Wrap(
                spacing: 8,
                children: [
                  TextButton(onPressed: onEdit, child: const PpeeText('Sửa')),
                  TextButton(onPressed: onDelete, child: const PpeeText('Xóa')),
                ],
              ),
            const Divider(),
          ],
        ),
      );
}
