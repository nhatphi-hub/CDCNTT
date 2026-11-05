import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:file_picker/file_picker.dart';
import '../../core/utils/image_url.dart';

import '../../core/network/api_exception.dart';
import '../../repositories/seller_product_repository.dart';

/// Manages image URLs and real file uploads to PPee backend.
class SellerProductImagesPanel extends StatefulWidget {
  const SellerProductImagesPanel({super.key, required this.product});
  final Map<String, dynamic> product;

  @override
  State<SellerProductImagesPanel> createState() => _SellerProductImagesPanelState();
}

class _SellerProductImagesPanelState extends State<SellerProductImagesPanel> {
  late Future<List<Map<String, dynamic>>> _future;
  bool _busy = false;
  String? _error;
  String get _productId => '${widget.product['id']}';

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _future = context.read<SellerProductRepository>().images(_productId);
  }

  // A setState callback must only update synchronous state, never return a Future.
  void _refresh() {
    final future = context.read<SellerProductRepository>().images(_productId);
    if (!mounted) return;
    setState(() {
      _error = null;
      _future = future;
    });
  }
  String _reason(Object error) => error is ApiException ? error.message : 'Không thể kết nối máy chủ: $error';

  Future<void> _edit([Map<String, dynamic>? image]) async {
    final changed = await showDialog<bool>(context: context,
      builder: (context) => _ImageForm(image: image, productId: _productId));
    if (!mounted) return;
    if (changed == true) _refresh();
  }

  Future<void> _upload() async {
    final selected=await FilePicker.platform.pickFiles(type:FileType.custom,
      allowedExtensions:['jpg','jpeg','png','webp'],withData:true);
    if(selected==null || !mounted)return;
    final file=selected.files.single;
    if(file.bytes==null){setState(()=>_error='Không đọc được dữ liệu ảnh');return;}
    setState(() {_busy=true;_error=null;});
    try {
      await context.read<SellerProductRepository>().uploadImage(
        _productId,file.bytes!,file.name,primary:true);
      if(mounted) _refresh();
    } catch(e){if(mounted)setState(()=>_error=_reason(e));}
    finally {if(mounted)setState(()=>_busy=false);}
  }

  Future<void> _primary(Map<String, dynamic> image) async {
    setState(() { _busy = true; _error = null; });
    try {
      await context.read<SellerProductRepository>().primaryImage(_productId, '${image['id']}');
      if (mounted) _refresh();
    } catch (error) {
      if (mounted) setState(() => _error = _reason(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _remove(Map<String, dynamic> image) async {
    final approved = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const PpeeText('Xóa ảnh sản phẩm?'),
      content: const PpeeText('Ảnh sẽ bị xóa khỏi danh sách hình ảnh của sản phẩm.'),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const PpeeText('Hủy')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const PpeeText('Xóa')),
      ],
    ));
    if (approved != true || !mounted) return;
    setState(() { _busy = true; _error = null; });
    try {
      await context.read<SellerProductRepository>().deleteImage(_productId, '${image['id']}');
      if (mounted) _refresh();
    } catch (error) {
      if (mounted) setState(() => _error = _reason(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: PpeeText('Hình ảnh • ${widget.product['name']}'),
    content: SizedBox(width: (MediaQuery.sizeOf(context).width - 100).clamp(220.0, 690.0).toDouble(), child: SingleChildScrollView(
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const PpeeText('Tải ảnh từ máy (khuyên dùng) hoặc nhập URL công khai. Ảnh chính xuất hiện ở trang mua hàng.',
            style: TextStyle(fontSize: 12)),
          if (_error != null) Padding(padding: const EdgeInsets.symmetric(vertical: 8),
            child: PpeeText(_error!, style: const TextStyle(color: Colors.red))),
          const SizedBox(height: 12),
          FutureBuilder<List<Map<String, dynamic>>>(future: _future, builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const Center(child: Padding(padding: EdgeInsets.all(28), child: CircularProgressIndicator()));
            }
            if (snapshot.hasError) {
              return Column(children: [
                PpeeText(_reason(snapshot.error!), style: const TextStyle(color: Colors.red)),
                TextButton(onPressed: () => _refresh(), child: const PpeeText('Thử lại')),
              ]);
            }
            final items = snapshot.data!;
            if (items.isEmpty) {
              return const Padding(padding: EdgeInsets.all(20),
                child: PpeeText('Sản phẩm chưa có ảnh. Hãy thêm ảnh đầu tiên.'));
            }
            return Column(children: [
              for (final image in items) Card(child: Padding(
                padding: const EdgeInsets.all(10), child: Row(children: [
                  ClipRRect(borderRadius: BorderRadius.circular(9), child: Image.network(
                    ImageUrl.resolve('${image['imageUrl']}'), width: 86, height: 86, fit: BoxFit.cover,
                    webHtmlElementStrategy: WebHtmlElementStrategy.prefer,
                    errorBuilder: (_, _, _) => const SizedBox(width: 86, height: 86,
                      child: Icon(Icons.broken_image_outlined)),
                  )),
                  const SizedBox(width: 12),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    PpeeText(image['isPrimary'] == true ? 'Ảnh chính' : 'Ảnh phụ',
                      style: const TextStyle(fontWeight: FontWeight.w700)),
                    PpeeText('${image['imageUrl']}', maxLines: 1, overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 12)),
                    Wrap(spacing: 2, children: [
                      TextButton(onPressed: _busy ? null : () => _edit(image), child: const PpeeText('Sửa URL')),
                      if (image['isPrimary'] != true) TextButton(
                        onPressed: _busy ? null : () => _primary(image), child: const PpeeText('Đặt làm ảnh chính')),
                      TextButton(onPressed: _busy ? null : () => _remove(image), child: const PpeeText('Xóa')),
                    ]),
                  ])),
                ]),
              )),
            ]);
          }),
        ],
      ),
    )),
    actions: [
      FilledButton.icon(onPressed:_busy?null:_upload,
        icon:const Icon(Icons.upload_file_outlined),label:const PpeeText('Tải ảnh từ máy')),
      OutlinedButton.icon(onPressed: _busy ? null : () => _edit(),
        icon: const Icon(Icons.add_photo_alternate_outlined), label: const PpeeText('Thêm ảnh')),
      TextButton(onPressed: _busy ? null : () => Navigator.pop(context), child: const PpeeText('Đóng')),
    ],
  );
}

class _ImageForm extends StatefulWidget {
  const _ImageForm({required this.productId, this.image});
  final String productId;
  final Map<String, dynamic>? image;

  @override
  State<_ImageForm> createState() => _ImageFormState();
}

class _ImageFormState extends State<_ImageForm> {
  final _key = GlobalKey<FormState>();
  late final TextEditingController _url;
  late final TextEditingController _sort;
  bool _primary = false;
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _url = TextEditingController(text: '${widget.image?['imageUrl'] ?? ''}');
    _sort = TextEditingController(text: '${widget.image?['sortOrder'] ?? 0}');
    _primary = widget.image?['isPrimary'] == true;
  }

  @override
  void dispose() {
    _url.dispose();
    _sort.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_key.currentState!.validate()) return;
    setState(() { _busy = true; _error = null; });
    try {
      final result = await context.read<SellerProductRepository>().saveImage(widget.productId, {
        'imageUrl': _url.text.trim(),
        'sortOrder': int.parse(_sort.text.trim()),
        'isPrimary': _primary,
      }, imageId: widget.image?['id']?.toString());
      if (!mounted) return;
      if (result['id'] != null) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() => _error = e is ApiException ? e.message : 'Không lưu được ảnh: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: PpeeText(widget.image == null ? 'Thêm hình ảnh' : 'Sửa hình ảnh'),
    content: SizedBox(width: (MediaQuery.sizeOf(context).width - 100).clamp(220.0, 480.0).toDouble(), child: Form(key: _key, child: Column(
      mainAxisSize: MainAxisSize.min, children: [
        TextFormField(controller: _url, maxLength: 2000,
          decoration: InputDecoration(labelText: tr(context, 'URL ảnh (https://... hoặc /api/media/products/...)')),
          validator: (value) {
            final text = (value ?? '').trim();
            final uri = Uri.tryParse(text);
            if (uri == null) return 'URL ảnh không hợp lệ';
            // Only PPee-generated media URLs may be relative.
            final local = RegExp(
              r'^/api/media/products/[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\.(?:jpg|png|webp)$',
            ).hasMatch(text);
            final remote = (uri.scheme == 'http' || uri.scheme == 'https') &&
                uri.host.isNotEmpty && uri.userInfo.isEmpty;
            return local || remote ? null : 'Nhập URL http/https hoặc đường dẫn ảnh PPee hợp lệ';
          }),
        TextFormField(controller: _sort, keyboardType: TextInputType.number,
          decoration: InputDecoration(labelText: tr(context, 'Thứ tự hiển thị')),
          validator: (value) {
            final n = int.tryParse(value?.trim() ?? '');
            return n == null || n < 0 ? 'Nhập số nguyên không âm' : null;
          }),
        CheckboxListTile(value: _primary, title: const PpeeText('Đặt làm ảnh chính'),
          onChanged: (v) => setState(() => _primary = v ?? false)),
        if (_error != null) PpeeText(_error!, style: const TextStyle(color: Colors.red)),
      ],
    ))),
    actions: [
      TextButton(onPressed: _busy ? null : () => Navigator.pop(context), child: const PpeeText('Hủy')),
      FilledButton(onPressed: _busy ? null : _submit, child: PpeeText(_busy ? 'Đang lưu...' : 'Lưu')),
    ],
  );
}
