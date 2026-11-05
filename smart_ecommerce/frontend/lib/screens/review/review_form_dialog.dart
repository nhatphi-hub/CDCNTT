import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/review.dart';
import '../../providers/review_provider.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/ppee_ui.dart';

class ReviewFormDialog extends StatefulWidget {
  const ReviewFormDialog({super.key, required this.productId, this.review});
  final String productId;
  final Review? review;

  @override
  State<ReviewFormDialog> createState() => _ReviewFormDialogState();
}

class _ReviewFormDialogState extends State<ReviewFormDialog> {
  late int _rating;
  late final TextEditingController _commentController;

  @override
  void initState() {
    super.initState();
    _rating = widget.review?.rating ?? 5;
    _commentController = TextEditingController(text: widget.review?.comment ?? '');
  }

  @override
  void dispose() {
    _commentController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final busy = context.watch<ReviewProvider>().mutating;
    return AlertDialog(
      title: PpeeText(widget.review == null ? 'Viết đánh giá' : 'Sửa đánh giá'),
      content: SizedBox(
        width: (MediaQuery.sizeOf(context).width - 90).clamp(240.0, 460.0).toDouble(),
        child: SingleChildScrollView(child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const PpeeNotice(message: 'Chia sẻ trải nghiệm thực tế để giúp người mua khác lựa chọn tốt hơn.'),
            const SizedBox(height: 18),
            const PpeeText('Mức độ hài lòng', style: TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            Wrap(
              spacing: 1,
              children: List.generate(5, (index) {
                final value = index + 1;
                return IconButton(
                  onPressed: busy ? null : () => setState(() => _rating = value),
                  tooltip: tr(context, '$value sao'),
                  icon: Icon(value <= _rating ? Icons.star_rounded : Icons.star_outline_rounded,
                    color: value <= _rating ? const Color(0xFFFFB342) : AppColors.gray500, size: 30),
                );
              }),
            ),
            const SizedBox(height: 12),
            PpeeFormField(
              controller: _commentController,
              enabled: !busy,
              label: 'Nhận xét (không bắt buộc)',
              maxLength: 2000,
              maxLines: 5,
              hint: 'Chất lượng, đóng gói, thời gian giao hàng...',
            ),
          ],
        )),
      ),
      actions: [
        TextButton(onPressed: busy ? null : () => Navigator.pop(context, false), child: const PpeeText('Hủy')),
        FilledButton(
          onPressed: busy
              ? null
              : () async {
                  final provider = context.read<ReviewProvider>();
                  final ok = widget.review == null
                      ? await provider.create(widget.productId, _rating, _commentController.text)
                      : await provider.update(widget.review!, _rating, _commentController.text);
                  if (!context.mounted) return;
                  if (ok) {
                    Navigator.pop(context, true);
                  } else {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: PpeeText(provider.error ?? 'Không thể lưu đánh giá')));
                  }
                },
          child: busy
              ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const PpeeText('Lưu'),
        ),
      ],
    );
  }
}
