import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/catalog/variant_matrix.dart';
import '../../core/l10n/language_controller.dart';
import '../../core/network/api_exception.dart';
import '../../repositories/seller_product_repository.dart';
import '../../widgets/product_image.dart';
import '../../widgets/ppee_ui.dart';

/// Create actual image × size variants in ONE transaction. Uses existing
/// seller-uploaded photos; never substitutes fabricated color/product photos.
class SellerVariantMatrixDialog extends StatefulWidget {
  const SellerVariantMatrixDialog({super.key, required this.product, required this.existing});
  final Map<String, dynamic> product;
  final List<Map<String, dynamic>> existing;

  @override
  State<SellerVariantMatrixDialog> createState() => _SellerVariantMatrixDialogState();
}

class _SellerVariantMatrixDialogState extends State<SellerVariantMatrixDialog> {
  static const suggestions = ['S', 'M', 'L', 'XL', 'XXL', '3XL', 'Free size'];
  List<Map<String, dynamic>> photos = [];
  final Set<String> selectedPhotos = {};
  final Set<String> selectedSizes = {};
  final quantity = TextEditingController(text: '10');
  final price = TextEditingController();
  final salePrice = TextEditingController();
  final otherSize = TextEditingController();
  bool loading = true;
  bool saving = false;
  String? error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    try {
      final result = await context.read<SellerProductRepository>()
          .images('${widget.product['id']}');
      if (mounted) setState(() {
        photos = result;
        loading = false;
      });
    } catch (e) {
      if (mounted) setState(() { loading = false; error = _message(e); });
    }
  }

  @override
  void dispose() {
    quantity.dispose(); price.dispose(); salePrice.dispose(); otherSize.dispose();
    super.dispose();
  }

  String _message(Object e) => e is ApiException ? e.message : '$e';

  List<String> get sizes => selectedSizes.toList()..sort((a, b) {
    final ai = suggestions.indexOf(a), bi = suggestions.indexOf(b);
    if (ai == -1 && bi == -1) return a.compareTo(b);
    if (ai == -1) return 1;
    if (bi == -1) return -1;
    return ai.compareTo(bi);
  });

  int get newCombinations {
    final existing = widget.existing.map((v) =>
        '${v['imageId'] ?? ''}|${(v['size'] ?? '').toString().trim().toLowerCase()}').toSet();
    var count = 0;
    for (final photo in selectedPhotos) {
      for (final size in sizes) {
        if (!existing.contains('$photo|${size.toLowerCase()}')) count++;
      }
    }
    return count;
  }

  Future<void> _submit() async {
    final qty = int.tryParse(quantity.text.trim());
    final inputPrice = price.text.trim();
    final inputSale = salePrice.text.trim();
    final normal = inputPrice.isEmpty ? null : num.tryParse(inputPrice);
    final reduced = inputSale.isEmpty ? null : num.tryParse(inputSale);
    if (selectedPhotos.isEmpty || selectedSizes.isEmpty) {
      setState(() => error = 'Hãy chọn ít nhất một ảnh và một kích cỡ.'); return;
    }
    if (qty == null || qty < 0 || (inputPrice.isNotEmpty && normal == null) ||
        (inputSale.isNotEmpty && reduced == null) ||
        (normal != null && normal < 0) ||
        (reduced != null && (normal == null || reduced < 0 || reduced > normal))) {
      setState(() => error = 'Giá hoặc số lượng không hợp lệ.'); return;
    }
    final photoIds = photos.map((p) => '${p['id']}').toList();
    final candidates = VariantMatrix.make(
      productId: '${widget.product['id']}', productSku: '${widget.product['sku'] ?? ''}',
      selectedImageIds: photoIds.where(selectedPhotos.contains).toList(),
      sizes: sizes, galleryImageIds: photoIds, quantity: qty,
      price: normal, salePrice: reduced,
    );
    final existing = widget.existing.map((v) =>
        '${v['imageId'] ?? ''}|${(v['size'] ?? '').toString().trim().toLowerCase()}').toSet();
    final entries = candidates.where((row) => !existing.contains(
        '${row['imageId']}|${row['size'].toString().toLowerCase()}')).toList();
    if (entries.isEmpty) {
      setState(() => error = 'Các mẫu và kích cỡ đã được tạo trước đó.'); return;
    }
    if (entries.length > 100) {
      setState(() => error = 'Chỉ tạo tối đa 100 biến thể một lần.'); return;
    }
    setState(() { saving = true; error = null; });
    try {
      await context.read<SellerProductRepository>()
          .saveVariantsBulk('${widget.product['id']}', entries);
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => error = _message(e));
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    return AlertDialog(
      title: const PpeeText('Tạo nhanh biến thể theo ảnh × kích cỡ'),
      content: SizedBox(width: (width - 72).clamp(250.0, 760.0).toDouble(),
        child: SingleChildScrollView(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min,
          children: [
            const PpeeNotice(message:
              '1. Thêm ảnh từng mẫu ở mục Hình ảnh. 2. Chọn ảnh đại diện cho mỗi mẫu. '
              '3. Chọn tất cả kích cỡ. Hệ thống tự tạo SKU và tồn kho theo từng tổ hợp.'),
            const SizedBox(height: 18),
            const PpeeText('1  Chọn ảnh đại diện mẫu',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
            const SizedBox(height: 12),
            if (loading) const Center(child: CircularProgressIndicator())
            else if (photos.isEmpty)
              const PpeeNotice(message: 'Chưa có ảnh. Đóng hộp thoại và vào mục Hình ảnh để tải ảnh lên trước.')
            else Wrap(spacing: 10, runSpacing: 10, children: [
              for (var i = 0; i < photos.length; i++)
                InkWell(
                  onTap: saving ? null : () => setState(() {
                    final id = '${photos[i]['id']}';
                    selectedPhotos.contains(id) ? selectedPhotos.remove(id) : selectedPhotos.add(id);
                  }),
                  borderRadius: BorderRadius.circular(12),
                  child: Container(width: 112, padding: const EdgeInsets.all(5),
                    decoration: BoxDecoration(borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: selectedPhotos.contains('${photos[i]['id']}')
                          ? Theme.of(context).colorScheme.primary : Colors.black12,
                        width: selectedPhotos.contains('${photos[i]['id']}') ? 2.5 : 1)),
                    child: Column(children: [
                      SizedBox(height: 88, width: 98,
                        child: ClipRRect(borderRadius: BorderRadius.circular(8),
                          child: ProductImage(url: '${photos[i]['imageUrl'] ?? ''}'))),
                      const SizedBox(height: 5),
                      PpeeText('Mẫu ${i + 1}', style: const TextStyle(fontWeight: FontWeight.w700)),
                      Icon(selectedPhotos.contains('${photos[i]['id']}')
                          ? Icons.check_circle_rounded : Icons.circle_outlined, size: 20),
                    ]),
                  ),
                ),
            ]),
            const SizedBox(height: 20),
            const PpeeText('2  Chọn kích cỡ',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
            const SizedBox(height: 10),
            Wrap(spacing: 8, runSpacing: 8, children: [
              for (final s in suggestions)
                FilterChip(label: PpeeText(s), selected: selectedSizes.contains(s),
                  onSelected: saving ? null : (v) => setState(() {
                    v ? selectedSizes.add(s) : selectedSizes.remove(s);
                  })),
            ]),
            Row(children: [
              Expanded(child: TextField(controller: otherSize, maxLength: 30,
                decoration: const InputDecoration(labelText: 'Kích cỡ khác (nếu có)'),
                onSubmitted: (_) => _addCustomSize())),
              IconButton(onPressed: saving ? null : _addCustomSize,
                icon: const Icon(Icons.add_circle_outline_rounded), tooltip: 'Thêm kích cỡ'),
            ]),
            if (selectedSizes.any((s) => !suggestions.contains(s))) ...[
              const SizedBox(height: 8),
              Wrap(spacing: 7, children: [
                for (final extra in sizes.where((s) => !suggestions.contains(s)))
                  InputChip(label: PpeeText(extra), onDeleted: saving ? null :
                    () => setState(() => selectedSizes.remove(extra))),
              ]),
            ],
            const SizedBox(height: 12),
            const PpeeText('3  Giá và tồn kho áp dụng cho tất cả tổ hợp',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
            const SizedBox(height: 12),
            Wrap(spacing: 12, runSpacing: 12, children: [
              SizedBox(width: 148, child: TextField(controller: quantity,
                keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Tồn kho / tổ hợp'))),
              SizedBox(width: 185, child: TextField(controller: price,
                keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Giá riêng (tùy chọn)'))),
              SizedBox(width: 185, child: TextField(controller: salePrice,
                keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Giá ưu đãi (tùy chọn)'))),
            ]),
            const SizedBox(height: 13),
            PpeeNotice(message: 'Sẽ tạo $newCombinations SKU mới. '
              'Các tổ hợp đã có được bỏ qua; SKU và tồn kho vẫn riêng biệt.'),
            if (error != null) Padding(padding: const EdgeInsets.only(top: 12),
              child: PpeeNotice(message: error!, isError: true)),
          ],
        )),
      ),
      actions: [
        TextButton(onPressed: saving ? null : () => Navigator.of(context).pop(),
          child: const PpeeText('Hủy')),
        FilledButton.icon(onPressed: saving || loading || photos.isEmpty ? null : _submit,
          icon: saving ? const SizedBox(width: 17, height: 17,
            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
            : const Icon(Icons.auto_awesome_rounded),
          label: PpeeText(saving ? 'Đang tạo...' : 'Tạo $newCombinations biến thể')),
      ],
    );
  }

  void _addCustomSize() {
    final value = otherSize.text.trim();
    if (value.isEmpty || value.length > 30) return;
    setState(() => selectedSizes.add(value));
    otherSize.clear();
  }
}
