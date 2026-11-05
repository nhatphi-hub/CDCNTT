import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../models/review.dart';
import '../../repositories/seller_review_repository.dart';
import '../../widgets/seller_ui.dart';

class SellerReviewsPanel extends StatefulWidget {
  const SellerReviewsPanel({super.key});
  @override
  State<SellerReviewsPanel> createState() => _SellerReviewsPanelState();
}

class _SellerReviewsPanelState extends State<SellerReviewsPanel> {
  bool loading = true;
  String? error;
  List<Review> reviews = const [];
  int _rating = 0;
  bool _unrepliedOnly = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => reload());
  }

  Future<void> reload() async {
    setState(() { loading = true; error = null; });
    try {
      final result = await context.read<SellerReviewRepository>().mine();
      if (mounted) setState(() => reviews = result);
    } catch (e) {
      if (mounted) setState(() => error = e is ApiException
        ? e.message : 'Không tải được đánh giá từ gian hàng');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _reply(Review review) async {
    final ctrl = TextEditingController(text: review.sellerReply ?? '');
    final message = await showDialog<String>(context: context,
      builder: (ctx) => AlertDialog(
        title: const PpeeText('Phản hồi đánh giá'),
        content: SizedBox(
          width: (MediaQuery.sizeOf(ctx).width - 100).clamp(240.0, 530.0).toDouble(),
          child: Column(mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start, children: [
              const SellerInfoNotice(message: 'Nội dung phản hồi sẽ được lưu cho đánh giá này.', compact: true),
              const SizedBox(height: 16),
              TextField(controller: ctrl, maxLines: 4, maxLength: 1000,
                decoration: InputDecoration(labelText: tr(context, 'Phản hồi của shop'),
                  hintText: tr(context, 'Cảm ơn bạn đã chia sẻ...'))),
            ])),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const PpeeText('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(ctx, ctrl.text),
            child: const PpeeText('Lưu phản hồi')),
        ],
      ));
    ctrl.dispose();
    if (message == null || !mounted) return;
    try {
      await context.read<SellerReviewRepository>().reply(review.id, message);
      if (mounted) await reload();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: PpeeText(e is ApiException ? e.message : 'Không lưu được phản hồi: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final average = reviews.isEmpty ? 0.0
      : reviews.fold<int>(0, (total, r) => total + r.rating) / reviews.length;
    final unanswered = reviews.where((r) => (r.sellerReply ?? '').trim().isEmpty).length;
    final filtered = reviews.where((r) =>
      (_rating == 0 || r.rating == _rating) &&
      (!_unrepliedOnly || (r.sellerReply ?? '').trim().isEmpty)).toList();
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SellerPageIntro(title: 'Đánh giá khách hàng',
        subtitle: 'Lắng nghe phản hồi, xem chất lượng dịch vụ và trả lời đánh giá sản phẩm.',
        icon: Icons.reviews_rounded,
        actions: [OutlinedButton.icon(onPressed: loading ? null : reload,
          icon: const Icon(Icons.refresh_rounded, size: 18),
          label: const PpeeText('Làm mới'))]),
      const SizedBox(height: 16),
      if (loading)
        const SellerPanel(child: Padding(padding: EdgeInsets.all(45),
          child: Center(child: CircularProgressIndicator())))
      else if (error != null)
        SellerEmptyState(title: 'Không thể tải đánh giá',
          message: error!, icon: Icons.wifi_off_outlined,
          action: OutlinedButton(onPressed: reload,
            child: const PpeeText('Thử lại')))
      else ...[
        SellerMetricGrid(children: [
          SellerMetric(label: 'Tổng lượt đánh giá', value: '${reviews.length}',
            icon: Icons.rate_review_outlined),
          SellerMetric(label: 'Điểm đánh giá trung bình',
            value: reviews.isEmpty ? '—' : '${average.toStringAsFixed(1)} / 5',
            icon: Icons.star_rounded, color: AppColors.warning),
          SellerMetric(label: 'Chờ shop phản hồi', value: '$unanswered',
            icon: Icons.mark_chat_unread_outlined, color: AppColors.highlight),
        ]),
        const SizedBox(height: 15),
        SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SellerSectionTitle(title: 'Lọc đánh giá',
              detail: 'Theo số sao và tình trạng phản hồi',
              icon: Icons.filter_list_rounded),
            const SizedBox(height: 15),
            Wrap(spacing: 8, runSpacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center, children: [
                for (var star = 0; star <= 5; star++)
                  ChoiceChip(label: PpeeText(star == 0 ? 'Tất cả' : '$star ★'),
                    selected: _rating == star,
                    onSelected: (_) => setState(() => _rating = star)),
                FilterChip(label: const PpeeText('Chưa trả lời'),
                  selected: _unrepliedOnly,
                  onSelected: (value) => setState(() => _unrepliedOnly = value)),
              ]),
          ])),
        const SizedBox(height: 15),
        if (reviews.isEmpty)
          const SellerEmptyState(title: 'Chưa có đánh giá',
            message: 'Các đánh giá của khách hàng sẽ xuất hiện tại đây.',
            icon: Icons.star_border_rounded)
        else if (filtered.isEmpty)
          const SellerEmptyState(title: 'Không có kết quả phù hợp',
            message: 'Thử chọn số sao khác hoặc tắt bộ lọc.',
            icon: Icons.filter_alt_off_outlined)
        else
          for (final r in filtered) _reviewCard(r),
      ],
    ]);
  }

  Widget _reviewCard(Review r) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: SellerPanel(child: Column(
      crossAxisAlignment: CrossAxisAlignment.start, children: [
        Wrap(alignment: WrapAlignment.spaceBetween, spacing: 12,
          runSpacing: 10, crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Row(mainAxisSize: MainAxisSize.min, children: [
              Container(width: 40, height: 40,
                alignment: Alignment.center,
                decoration: BoxDecoration(color: AppColors.primarySoft,
                  borderRadius: BorderRadius.circular(13)),
                child: const Icon(Icons.person_outline,
                  color: AppColors.primary, size: 22)),
              const SizedBox(width: 10),
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PpeeText(r.userName, style: const TextStyle(
                  fontWeight: FontWeight.w900, color: AppColors.black)),
                if (r.createdAt != null)
                  PpeeText('${r.createdAt!.day}/${r.createdAt!.month}/${r.createdAt!.year}',
                    style: const TextStyle(fontSize: 11, color: AppColors.gray700)),
              ]),
            ]),
            SellerStatusBadge(
              icon: (r.sellerReply ?? '').trim().isNotEmpty
                ? Icons.mark_chat_read_outlined : Icons.mark_chat_unread_outlined,
              label: (r.sellerReply ?? '').trim().isNotEmpty
                ? 'Đã phản hồi' : 'Chờ phản hồi',
              color: (r.sellerReply ?? '').trim().isNotEmpty
                ? AppColors.success : AppColors.highlight),
          ]),
        const SizedBox(height: 15),
        PpeeText(r.productName, style: const TextStyle(
          color: AppColors.black, fontWeight: FontWeight.w800, fontSize: 15)),
        const SizedBox(height: 5),
        Wrap(spacing: 3, children: [
          for (var star = 1; star <= 5; star++)
            Icon(star <= r.rating ? Icons.star_rounded : Icons.star_border_rounded,
              color: AppColors.warning, size: 19),
        ]),
        if ((r.comment ?? '').trim().isNotEmpty) ...[
          const SizedBox(height: 11),
          PpeeText(r.comment!, style: const TextStyle(height: 1.5)),
        ],
        if ((r.sellerReply ?? '').trim().isNotEmpty) ...[
          const SizedBox(height: 14),
          Container(width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(color: AppColors.gray100,
              borderRadius: BorderRadius.circular(13)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const PpeeText('Phản hồi của gian hàng', style: TextStyle(
                  fontWeight: FontWeight.w800, color: AppColors.primaryDark,
                  fontSize: 12)),
                const SizedBox(height: 6),
                PpeeText(r.sellerReply!, style: const TextStyle(height: 1.5)),
              ])),
        ],
        const SizedBox(height: 12),
        Align(alignment: Alignment.centerRight,
          child: OutlinedButton.icon(onPressed: () => _reply(r),
            icon: const Icon(Icons.reply_outlined, size: 18),
            label: PpeeText(r.sellerReply == null ? 'Trả lời đánh giá' : 'Sửa phản hồi'))),
      ])),
  );
}
