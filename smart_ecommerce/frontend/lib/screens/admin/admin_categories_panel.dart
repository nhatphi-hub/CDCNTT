import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/l10n/language_controller.dart';
import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/image_url.dart';
import '../../models/category.dart';
import '../../providers/category_provider.dart';
import '../../repositories/category_repository.dart';
import '../../widgets/marketplace_category_tile.dart';
import '../../widgets/ppee_ui.dart';

/// Admin taxonomy manager: image URL, direct image upload, curated icon, and
/// parent/child hierarchy all use the existing categories.image column.
class AdminCategoriesPanel extends StatefulWidget {
  const AdminCategoriesPanel({super.key});
  @override
  State<AdminCategoriesPanel> createState() => _AdminCategoriesPanelState();
}

class _AdminCategoriesPanelState extends State<AdminCategoriesPanel> {
  bool _loading = true;
  bool _saving = false;
  String? _error;
  String _search = '';
  List<Category> _categories = const [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() { _loading = true; _error = null; });
    try {
      final items = await context.read<CategoryRepository>().getAll();
      if (mounted) setState(() => _categories = items);
    } catch (error) {
      if (mounted) setState(() => _error = error is ApiException ? error.message : '$error');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _edit([Category? original]) async {
    final draft = await showDialog<_CategoryDraft>(
      context: context,
      builder: (_) => _CategoryEditDialog(categories: _categories, original: original),
    );
    if (draft == null || !mounted || _saving) return;
    setState(() => _saving = true);
    try {
      final repo = context.read<CategoryRepository>();
      String? image = draft.image;
      if (draft.bytes != null && draft.filename != null) {
        image = await repo.uploadImage(draft.bytes!, draft.filename!);
        if (image.isEmpty) throw StateError('Máy chủ không trả đường dẫn ảnh.');
      }
      if (original == null) {
        await repo.create(
          name: draft.name, slug: draft.slug, description: draft.description,
          parentId: draft.parentId, isActive: draft.isActive, image: image,
        );
      } else {
        await repo.update(original.id,
          name: draft.name, slug: draft.slug, description: draft.description,
          parentId: draft.parentId, isActive: draft.isActive, image: image,
        );
      }
      if (!mounted) return;
      await context.read<CategoryProvider>().load(force: true);
      await _load();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: PpeeText(original == null ? 'Đã tạo danh mục.' : 'Đã cập nhật danh mục.'),
      ));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: PpeeText(e is ApiException ? e.message : 'Không thể lưu danh mục: $e'),
      ));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final query = _search.toLowerCase().trim();
    final visible = _categories.where((category) =>
      '${category.name} ${category.slug} ${category.description ?? ''}'
          .toLowerCase().contains(query)).toList()
      ..sort((a, b) {
        if ((a.parentId == null) != (b.parentId == null)) {
          return a.parentId == null ? -1 : 1;
        }
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });
    final roots = _categories.where((e) => e.parentId == null).length;
    final active = _categories.where((e) => e.isActive).length;
    return Center(child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 1140),
      child: ListView(padding: const EdgeInsets.fromLTRB(17, 22, 17, 30), children: [
        PpeeSurface(child: LayoutBuilder(builder: (context, constraints) => Wrap(
          alignment: WrapAlignment.spaceBetween,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 14, runSpacing: 12, children: [
            SizedBox(width: constraints.maxWidth < 640 ? constraints.maxWidth : constraints.maxWidth * .62,
              child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PpeeText('QUẢN LÝ DANH MỤC', style: TextStyle(color: AppColors.primary,
                  fontWeight: FontWeight.w800, letterSpacing: 1, fontSize: 11)),
                SizedBox(height: 8),
                PpeeText('Loại sản phẩm', style: TextStyle(fontWeight: FontWeight.w900,
                  color: AppColors.black, fontSize: 24)),
                SizedBox(height: 8),
                PpeeText('Quản lý loại gốc, loại con và ảnh/icon hiển thị trên trang chủ.',
                  style: TextStyle(color: AppColors.gray700, height: 1.45)),
              ])),
            FilledButton.icon(onPressed: _saving ? null : () => _edit(),
              icon: const Icon(Icons.add_circle_outline_rounded),
              label: const PpeeText('Thêm loại sản phẩm')),
          ],
        ))),
        const SizedBox(height: 14),
        Wrap(spacing: 12, runSpacing: 12, children: [
          _Metric(icon: Icons.category_outlined, label: 'Tổng loại', value: '${_categories.length}'),
          _Metric(icon: Icons.check_circle_outline_rounded, label: 'Đang sử dụng', value: '$active'),
          _Metric(icon: Icons.account_tree_outlined, label: 'Danh mục gốc', value: '$roots'),
        ]),
        const SizedBox(height: 16),
        PpeeSurface(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            const Expanded(child: PpeeText('Danh sách loại sản phẩm',
              style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800))),
            IconButton(onPressed: _load, tooltip: tr(context, 'Làm mới'),
              icon: const Icon(Icons.refresh_rounded, color: AppColors.primary)),
          ]),
          const SizedBox(height: 12),
          TextField(onChanged: (v) => setState(() => _search = v),
            decoration: InputDecoration(prefixIcon: const Icon(Icons.search_rounded),
              hintText: tr(context, 'Tìm theo tên, slug hoặc mô tả'))),
          const SizedBox(height: 16),
          if (_loading) const Padding(padding: EdgeInsets.all(30),
            child: Center(child: CircularProgressIndicator()))
          else if (_error != null) ...[
            PpeeNotice(message: _error!, isError: true),
            TextButton(onPressed: _load, child: const PpeeText('Thử lại')),
          ] else if (visible.isEmpty) const Padding(padding: EdgeInsets.all(22),
            child: Center(child: PpeeText('Chưa có loại sản phẩm phù hợp.')))
          else for (final category in visible) ...[
            Padding(padding: const EdgeInsets.symmetric(vertical: 9), child: Row(children: [
              CategoryArtwork(category: category, size: 58),
              const SizedBox(width: 12),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PpeeText(category.name, maxLines: 1, overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.black,
                    fontWeight: FontWeight.w800, fontSize: 14)),
                const SizedBox(height: 3),
                PpeeText('${category.slug}${category.parentId == null ? ' · Danh mục gốc' : ' · Danh mục con'}',
                  style: const TextStyle(fontSize: 11, color: AppColors.gray700)),
                if ((category.description ?? '').isNotEmpty)
                  PpeeText(category.description!, maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 11, color: AppColors.gray700)),
              ])),
              if (MediaQuery.sizeOf(context).width > 600)
                PpeeText(category.isActive ? 'Hoạt động' : 'Đã ẩn',
                  style: const TextStyle(fontSize: 11, color: AppColors.gray700)),
              const SizedBox(width: 9),
              IconButton(onPressed: _saving ? null : () => _edit(category),
                tooltip: tr(context, 'Sửa danh mục'),
                icon: const Icon(Icons.edit_outlined, color: AppColors.primary)),
            ])),
            const Divider(height: 1, color: AppColors.gray300),
          ],
        ])),
      ]),
    ));
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.icon, required this.label, required this.value});
  final IconData icon;
  final String label;
  final String value;
  @override
  Widget build(BuildContext context) => Container(
    width: 210, padding: const EdgeInsets.all(17),
    decoration: BoxDecoration(color: Colors.white,
      border: Border.all(color: AppColors.gray300), borderRadius: BorderRadius.circular(17)),
    child: Row(children: [
      Icon(icon, color: AppColors.primary, size: 29),
      const SizedBox(width: 12),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        PpeeText(value, style: const TextStyle(fontWeight: FontWeight.w900,
          color: AppColors.black, fontSize: 21)),
        PpeeText(label, style: const TextStyle(fontSize: 11, color: AppColors.gray700)),
      ])),
    ]),
  );
}

class _CategoryDraft {
  const _CategoryDraft({required this.name, required this.slug,
    required this.description, required this.isActive,
    this.parentId, this.image, this.bytes, this.filename});
  final String name;
  final String slug;
  final String description;
  final String? parentId;
  final bool isActive;
  final String? image;
  final Uint8List? bytes;
  final String? filename;
}

class _CategoryEditDialog extends StatefulWidget {
  const _CategoryEditDialog({required this.categories, this.original});
  final List<Category> categories;
  final Category? original;
  @override
  State<_CategoryEditDialog> createState() => _CategoryEditDialogState();
}

class _CategoryEditDialogState extends State<_CategoryEditDialog> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _slug = TextEditingController();
  final _description = TextEditingController();
  final _imageUrl = TextEditingController();
  String? _parentId;
  bool _active = true;
  bool _autoSlug = true;
  String _mode = 'none';
  String _icon = 'fashion';
  Uint8List? _fileBytes;
  String? _fileName;
  String? _fileError;

  @override
  void initState() {
    super.initState();
    final category = widget.original;
    if (category != null) {
      _name.text = category.name;
      _slug.text = category.slug;
      _description.text = category.description ?? '';
      _parentId = category.parentId;
      _active = category.isActive;
      _autoSlug = false;
      final image = category.image?.trim() ?? '';
      if (image.startsWith('icon:')) {
        _mode = 'icon';
        _icon = image.substring(5);
      } else if (image.isNotEmpty) {
        _mode = 'url';
        _imageUrl.text = image;
      }
    }
    _name.addListener(_updateSlug);
  }

  void _updateSlug() {
    if (!_autoSlug) return;
    final slug = slugify(_name.text);
    if (_slug.text != slug) _slug.text = slug;
  }

  static String slugify(String source) {
    var text = source.toLowerCase().trim();
    const accents = <String, String>{
      'à':'a','á':'a','ạ':'a','ả':'a','ã':'a','â':'a','ầ':'a','ấ':'a','ậ':'a','ẩ':'a','ẫ':'a','ă':'a','ằ':'a','ắ':'a','ặ':'a','ẳ':'a','ẵ':'a',
      'è':'e','é':'e','ẹ':'e','ẻ':'e','ẽ':'e','ê':'e','ề':'e','ế':'e','ệ':'e','ể':'e','ễ':'e',
      'ì':'i','í':'i','ị':'i','ỉ':'i','ĩ':'i',
      'ò':'o','ó':'o','ọ':'o','ỏ':'o','õ':'o','ô':'o','ồ':'o','ố':'o','ộ':'o','ổ':'o','ỗ':'o','ơ':'o','ờ':'o','ớ':'o','ợ':'o','ở':'o','ỡ':'o',
      'ù':'u','ú':'u','ụ':'u','ủ':'u','ũ':'u','ư':'u','ừ':'u','ứ':'u','ự':'u','ử':'u','ữ':'u',
      'ỳ':'y','ý':'y','ỵ':'y','ỷ':'y','ỹ':'y','đ':'d',
    };
    for (final pair in accents.entries) { text = text.replaceAll(pair.key, pair.value); }
    return text.replaceAll(RegExp(r'[^a-z0-9]+'), '-')
        .replaceAll(RegExp(r'^-+|-+$'), '');
  }

  bool _isDescendant(Category target, String id) {
    final byId = {for (final item in widget.categories) item.id: item};
    String? current = target.id;
    final seen = <String>{};
    while (current != null && seen.add(current)) {
      if (current == id) return true;
      current = byId[current]?.parentId;
    }
    return false;
  }

  Future<void> _pickFile() async {
    final selected = await FilePicker.platform.pickFiles(type: FileType.custom,
      allowedExtensions: const ['jpg', 'jpeg', 'png', 'webp'], withData: true);
    if (selected == null || !mounted) return;
    final file = selected.files.single;
    if (file.bytes == null || file.bytes!.isEmpty || file.bytes!.length > 5 * 1024 * 1024) {
      setState(() => _fileError = 'Ảnh phải là JPEG, PNG hoặc WebP, tối đa 5 MB.');
      return;
    }
    setState(() {
      _fileError = null;
      _fileBytes = file.bytes;
      _fileName = file.name;
      _mode = 'upload';
    });
  }

  @override
  void dispose() {
    _name.removeListener(_updateSlug);
    _name.dispose(); _slug.dispose(); _description.dispose(); _imageUrl.dispose();
    super.dispose();
  }

  String? _validateImage() {
    if (_mode == 'upload' && (_fileBytes == null || _fileName == null)) return 'Hãy chọn ảnh từ máy.';
    if (_mode == 'url') {
      final text = _imageUrl.text.trim();
      if (text.isEmpty) return 'Hãy nhập URL ảnh hoặc chọn nguồn khác.';
      if (text.length > 255) return 'Đường dẫn ảnh tối đa 255 ký tự.';
      final uri = Uri.tryParse(text);
      final external = uri != null && (uri.scheme == 'https' || uri.scheme == 'http') && uri.host.isNotEmpty && uri.userInfo.isEmpty;
      if (!external && !text.startsWith('/api/media/products/')) return 'Chỉ chấp nhận URL http(s) hoặc ảnh PPee đã tải lên.';
    }
    return null;
  }

  void _submit() {
    if (!_form.currentState!.validate()) return;
    final message = _validateImage();
    if (message != null) { setState(() => _fileError = message); return; }
    final image = switch (_mode) {
      'icon' => 'icon:$_icon',
      'url' => _imageUrl.text.trim(),
      _ => null,
    };
    Navigator.pop(context, _CategoryDraft(
      name: _name.text.trim(), slug: _slug.text.trim(),
      description: _description.text.trim(), parentId: _parentId,
      isActive: _active, image: image,
      bytes: _mode == 'upload' ? _fileBytes : null,
      filename: _mode == 'upload' ? _fileName : null,
    ));
  }

  Widget _preview() {
    if (_mode == 'upload' && _fileBytes != null) {
      return Image.memory(_fileBytes!, fit: BoxFit.cover, width: 78, height: 78);
    }
    if (_mode == 'icon') return Icon(categoryIconChoices[_icon] ?? Icons.category_outlined,
      size: 35, color: AppColors.primary);
    if (_mode == 'url' && _imageUrl.text.trim().isNotEmpty) {
      return Image.network(ImageUrl.resolve(_imageUrl.text),
        fit: BoxFit.cover, width: 78, height: 78,
        errorBuilder: (_, __, ___) => const Icon(Icons.broken_image_outlined));
    }
    return const Icon(Icons.add_photo_alternate_outlined, size: 34, color: AppColors.gray700);
  }

  @override
  Widget build(BuildContext context) {
    final original = widget.original;
    return AlertDialog(
      title: PpeeText(original == null ? 'Tạo loại sản phẩm' : 'Sửa danh mục'),
      content: SizedBox(width: 545, child: SingleChildScrollView(child: Form(
        key: _form,
        child: Column(mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start, children: [
          const PpeeNotice(message: 'Ảnh và icon sẽ được lưu cùng danh mục trong PostgreSQL và hiển thị ở trang chủ.'),
          const SizedBox(height: 16),
          TextFormField(controller: _name, maxLength: 100,
            decoration: InputDecoration(labelText: tr(context, 'Tên loại sản phẩm *'),
              prefixIcon: const Icon(Icons.category_outlined)),
            validator: (v) => (v?.trim().isEmpty ?? true) ? tr(context, 'Vui lòng nhập tên loại sản phẩm') : null),
          const SizedBox(height: 8),
          TextFormField(controller: _slug, maxLength: 120,
            onChanged: (_) => _autoSlug = false,
            decoration: InputDecoration(labelText: tr(context, 'Slug (đường dẫn) *'),
              prefixIcon: const Icon(Icons.link_rounded)),
            validator: (v) {
              final value = (v ?? '').trim();
              if (value.isEmpty) return tr(context, 'Vui lòng nhập slug');
              return RegExp(r'^[a-z0-9]+(?:-[a-z0-9]+)*$').hasMatch(value)
                ? null : tr(context, 'Slug chỉ gồm chữ thường, số và dấu gạch ngang');
            }),
          const SizedBox(height: 8),
          TextFormField(controller: _description, maxLength: 500, maxLines: 2,
            decoration: InputDecoration(labelText: tr(context, 'Mô tả'),
              prefixIcon: const Icon(Icons.notes_rounded))),
          const SizedBox(height: 8),
          DropdownButtonFormField<String?>(
            initialValue: _parentId, isExpanded: true,
            decoration: InputDecoration(labelText: tr(context, 'Danh mục cha (không bắt buộc)'),
              prefixIcon: const Icon(Icons.account_tree_outlined)),
            items: [
              const DropdownMenuItem<String?>(value: null, child: PpeeText('Không có – loại gốc')),
              for (final c in widget.categories.where((c) => c.isActive &&
                (original == null || !_isDescendant(c, original.id) || c.id == original.parentId)))
                DropdownMenuItem<String?>(value: c.id,
                  child: PpeeText(c.name, overflow: TextOverflow.ellipsis)),
            ],
            onChanged: (value) => setState(() => _parentId = value),
          ),
          const SizedBox(height: 16),
          const PpeeText('Ảnh / icon đại diện', style: TextStyle(
            fontSize: 14, fontWeight: FontWeight.w800, color: AppColors.black)),
          const SizedBox(height: 8),
          Wrap(spacing: 7, runSpacing: 7, children: [
            for (final choice in const [
              ('none', 'Không dùng ảnh'),
              ('upload', 'Tải ảnh lên'),
              ('url', 'Dán link ảnh'),
              ('icon', 'Chọn icon'),
            ])
              ChoiceChip(label: PpeeText(choice.$2), selected: _mode == choice.$1,
                onSelected: (_) => setState(() { _mode = choice.$1; _fileError = null; })),
          ]),
          const SizedBox(height: 12),
          if (_mode == 'upload') OutlinedButton.icon(onPressed: _pickFile,
            icon: const Icon(Icons.upload_file_outlined),
            label: PpeeText(_fileName == null ? 'Chọn ảnh từ thiết bị (tối đa 5 MB)' : _fileName!)),
          if (_mode == 'url') TextFormField(controller: _imageUrl,
            onChanged: (_) => setState(() => _fileError = null),
            decoration: InputDecoration(labelText: tr(context, 'URL ảnh danh mục'),
              hintText: 'https://example.com/category.jpg',
              prefixIcon: const Icon(Icons.link_rounded))),
          if (_mode == 'icon') Wrap(spacing: 6, runSpacing: 6, children: [
            for (final item in categoryIconChoices.entries)
              Tooltip(message: item.key, child: InkWell(
                onTap: () => setState(() => _icon = item.key),
                borderRadius: BorderRadius.circular(12),
                child: Container(width: 48, height: 48,
                  decoration: BoxDecoration(color: _icon == item.key
                    ? AppColors.primarySoft : AppColors.gray100,
                    border: Border.all(color: _icon == item.key
                      ? AppColors.primary : AppColors.gray300),
                    borderRadius: BorderRadius.circular(12)),
                  child: Icon(item.value, color: AppColors.primary)),
              )),
          ]),
          const SizedBox(height: 10),
          Row(children: [
            ClipRRect(borderRadius: BorderRadius.circular(13),
              child: Container(width: 78, height: 78, color: AppColors.primarySoft,
                alignment: Alignment.center, child: _preview())),
            const SizedBox(width: 12),
            const Expanded(child: PpeeText('Ảnh đại diện sẽ xuất hiện trong danh mục gốc trên trang chủ.',
              style: TextStyle(color: AppColors.gray700, fontSize: 12))),
          ]),
          if (_fileError != null) Padding(padding: const EdgeInsets.only(top: 8),
            child: PpeeText(_fileError!, style: const TextStyle(color: Colors.red, fontSize: 12))),
          const SizedBox(height: 12),
          SwitchListTile.adaptive(contentPadding: EdgeInsets.zero,
            value: _active, title: const PpeeText('Cho phép người bán lựa chọn'),
            onChanged: (value) => setState(() => _active = value)),
        ]),
      ))),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const PpeeText('Hủy')),
        FilledButton.icon(onPressed: _submit, icon: const Icon(Icons.save_outlined),
          label: const PpeeText('Lưu vào database')),
      ],
    );
  }
}
