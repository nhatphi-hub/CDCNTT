import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/image_url.dart';
import '../../repositories/seller_product_repository.dart';
import '../../repositories/category_repository.dart';
import '../../models/category.dart';
import '../../models/category_tree.dart';
import '../../widgets/seller_ui.dart';
import '../../widgets/ppee_ui.dart';
import 'seller_product_images_panel.dart';
import 'seller_variant_matrix_dialog.dart';

/// Seller-scoped product CRUD; API calls and ownership checks stay unchanged.
class SellerProductsPanel extends StatefulWidget {
  const SellerProductsPanel({super.key});
  @override
  State<SellerProductsPanel> createState() => _SellerProductsPanelState();
}

class _SellerProductsPanelState extends State<SellerProductsPanel> {
  bool loading = true;
  String? error;
  List<Map<String, dynamic>> products = [];
  String _query = '';
  String _visibility = 'ALL';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => reload());
  }

  Future<void> reload() async {
    setState(() { loading = true; error = null; });
    try {
      final value = await context.read<SellerProductRepository>().list();
      if (mounted) setState(() => products = value);
    } catch (e) {
      if (mounted) setState(() => error = _message(e));
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  String _message(Object e) => e is ApiException ? e.message : 'Không thể kết nối backend: $e';
  void notice(String text) {
    if (mounted) ScaffoldMessenger.of(context)
      .showSnackBar(SnackBar(content: PpeeText(text)));
  }

  Future<void> edit([Map<String, dynamic>? product]) async {
    final success = await showDialog<bool>(context: context,
      builder: (ctx) => _ProductEditor(initial: product));
    if (!mounted) return;
    if (success == true) {
      notice('Đã lưu sản phẩm');
      await reload();
    }
  }

  Future<void> toggle(Map<String, dynamic> product) async {
    try {
      await context.read<SellerProductRepository>().visibility(
        product['id'].toString(), product['status'] != 'ACTIVE');
      notice('Đã cập nhật hiển thị');
      await reload();
    } catch (e) { notice(_message(e)); }
  }

  /// Delete on a commerce system must not break existing orders or reviews.
  /// The seller action below removes the item from public sale but preserves
  /// the record so it can be restored with the existing 'Hiện' action.
  Future<void> removeProduct(Map<String, dynamic> product) async {
    final answer = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const PpeeText('Ngừng bán sản phẩm?'),
        content: PpeeText('Sản phẩm "${product['name']}" sẽ bị gỡ khỏi trang mua sắm ngay. '
            'Đơn hàng, đánh giá và dữ liệu lịch sử vẫn được giữ nguyên. '
            'Bạn có thể khôi phục trong bộ lọc "Đã ẩn".'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false),
            child: const PpeeText('Hủy')),
          FilledButton.icon(onPressed: () => Navigator.pop(dialogContext, true),
            icon: const Icon(Icons.remove_shopping_cart_outlined),
            label: const PpeeText('Ngừng bán')),
        ],
      ),
    );
    if (answer != true || !mounted) return;
    try {
      await context.read<SellerProductRepository>()
          .removeProductFromSale(product['id'].toString());
      if (!mounted) return;
      notice('Đã ngừng bán. Sản phẩm được giữ trong danh sách Đã ẩn.');
      await reload();
    } catch (e) {
      if (mounted) notice(_message(e));
    }
  }

  Future<void> images(Map<String, dynamic> product) async {
    await showDialog<void>(context: context,
      builder: (_) => SellerProductImagesPanel(product: product));
  }

  Future<void> variants(Map<String, dynamic> product) async {
    await showDialog<void>(context: context,
      builder: (ctx) => _VariantsEditor(product: product));
    if (mounted) await reload();
  }

  String _money(dynamic raw) {
    final n = num.tryParse('$raw');
    if (n == null) return raw == null ? '—' : '$raw';
    final s = n.toStringAsFixed(0).replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.');
    return '$s đ';
  }

  @override
  Widget build(BuildContext context) {
    final active = products.where((p) => p['status'] == 'ACTIVE').length;
    final hidden = products.where((p) => p['status'] != 'ACTIVE').length;
    final lowStock = products.where((p) => p['status'] == 'ACTIVE' &&
      (num.tryParse('${p['quantity']}') ?? 0) <= 5).length;
    final visible = products.where((p) {
      final matches = '${p['name'] ?? ''} ${p['sku'] ?? ''} ${p['brand'] ?? ''}'
        .toLowerCase().contains(_query.toLowerCase().trim());
      final status = _visibility == 'ALL' ||
        (_visibility == 'ACTIVE' && p['status'] == 'ACTIVE') ||
        (_visibility == 'HIDDEN' && p['status'] != 'ACTIVE');
      return matches && status;
    }).toList();
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SellerPageIntro(title: 'Danh mục sản phẩm',
        subtitle: 'Quản lý mặt hàng, biến thể, hình ảnh và tồn kho của gian hàng.',
        icon: Icons.inventory_2_rounded,
        actions: [
          OutlinedButton.icon(onPressed: loading ? null : reload,
            icon: const Icon(Icons.refresh_rounded, size: 18),
            label: const PpeeText('Làm mới')),
          FilledButton.icon(onPressed: () => edit(),
            icon: const Icon(Icons.add_box_outlined, size: 19),
            label: const PpeeText('Thêm sản phẩm')),
        ]),
      const SizedBox(height: 16),
      if (loading)
        const SellerPanel(child: Center(child: Padding(
          padding: EdgeInsets.all(45), child: CircularProgressIndicator())))
      else if (error != null)
        SellerEmptyState(title: 'Không thể tải sản phẩm', message: error!,
          icon: Icons.wifi_off_rounded,
          action: OutlinedButton(onPressed: reload,
            child: const PpeeText('Thử lại')))
      else ...[
        SellerMetricGrid(children: [
          SellerMetric(label: 'Tổng sản phẩm', value: '${products.length}',
            icon: Icons.widgets_outlined),
          SellerMetric(label: 'Đang hiển thị', value: '$active',
            icon: Icons.storefront_rounded, color: AppColors.success),
          SellerMetric(label: 'Đã ẩn', value: '$hidden',
            icon: Icons.visibility_off_outlined, color: AppColors.gray700),
          SellerMetric(label: 'Tồn kho thấp (≤ 5)', value: '$lowStock',
            icon: Icons.warning_amber_outlined, color: AppColors.highlight),
        ]),
        const SizedBox(height: 15),
        SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SellerSectionTitle(title: 'Danh sách sản phẩm',
              detail: 'Tìm kiếm sản phẩm theo tên, SKU hoặc thương hiệu',
              icon: Icons.view_list_rounded),
            const SizedBox(height: 15),
            LayoutBuilder(builder: (context, size) => Wrap(spacing: 12,
              runSpacing: 10, crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                SizedBox(width: size.maxWidth < 440 ? size.maxWidth : 355,
                  child: TextField(onChanged: (v) => setState(() => _query = v),
                    decoration: InputDecoration(
                      prefixIcon: Icon(Icons.search_rounded),
                      hintText: tr(context, 'Tìm tên, SKU, thương hiệu...')))),
                for (final value in ['ALL', 'ACTIVE', 'HIDDEN'])
                  ChoiceChip(label: PpeeText(value == 'ALL' ? 'Tất cả'
                    : value == 'ACTIVE' ? 'Đang bán' : 'Đã ẩn'),
                    selected: _visibility == value,
                    onSelected: (_) => setState(() => _visibility = value)),
                SellerStatusBadge(label: '${visible.length} sản phẩm'),
              ])),
          ])),
        const SizedBox(height: 13),
        if (products.isEmpty)
          SellerEmptyState(title: 'Chưa có sản phẩm',
            message: 'Thêm sản phẩm đầu tiên để bắt đầu bán hàng.',
            icon: Icons.inventory_2_outlined,
            action: FilledButton.icon(onPressed: () => edit(),
              icon: const Icon(Icons.add), label: const PpeeText('Thêm sản phẩm')))
        else if (visible.isEmpty)
          const SellerEmptyState(title: 'Không tìm thấy sản phẩm',
            message: 'Thử thay đổi từ khóa hoặc bộ lọc.',
            icon: Icons.search_off_rounded)
        else
          for (final product in visible) _productTile(product),
      ],
    ]);
  }

  Widget _productTile(Map<String, dynamic> product) {
    final active = product['status'] == 'ACTIVE';
    final quantity = num.tryParse('${product['quantity']}') ?? 0;
    final image = ImageUrl.resolve('${product['primaryImageUrl'] ?? product['imageUrl'] ?? product['thumbnailUrl'] ?? ''}');
    return Padding(padding: const EdgeInsets.only(bottom: 12),
      child: SellerPanel(padding: const EdgeInsets.all(17),
        child: LayoutBuilder(builder: (context, size) {
          final info = Row(crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(borderRadius: BorderRadius.circular(15),
                child: SizedBox(width: 64, height: 64,
                  child: image.isEmpty
                    ? Container(color: AppColors.primarySoft,
                        child: const Icon(Icons.inventory_2_outlined,
                          color: AppColors.primary, size: 28))
                    : Image.network(image, fit: BoxFit.cover,
                        webHtmlElementStrategy: WebHtmlElementStrategy.prefer,
                        errorBuilder: (_, _, _) => Container(
                          color: AppColors.primarySoft,
                          child: const Icon(Icons.inventory_2_outlined,
                            color: AppColors.primary, size: 28))),
                )),
              const SizedBox(width: 14),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  PpeeText('${product['name'] ?? ''}', maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 15,
                      fontWeight: FontWeight.w900, color: AppColors.black)),
                  const SizedBox(height: 8),
                  Wrap(spacing: 8, runSpacing: 7, children: [
                    SellerStatusBadge(label: active ? 'Đang bán' : 'Đã ẩn',
                      icon: active ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                      color: active ? AppColors.success : AppColors.gray700),
                    if (active && quantity <= 5)
                      const SellerStatusBadge(label: 'Sắp hết hàng',
                        icon: Icons.warning_amber_rounded, color: AppColors.highlight),
                  ]),
                  const SizedBox(height: 9),
                  PpeeText('SKU: ${product['sku'] ?? '—'}  •  Tồn: ${product['quantity'] ?? 0}',
                    style: const TextStyle(color: AppColors.gray700, fontSize: 12)),
                  const SizedBox(height: 7),
                  PpeeText(_money(product['price']),
                    style: const TextStyle(color: AppColors.primaryDark,
                      fontSize: 16, fontWeight: FontWeight.w900)),
                ])),
            ]);
          final actions = Wrap(spacing: 7, runSpacing: 7, children: [
            OutlinedButton.icon(onPressed: () => edit(product),
              icon: const Icon(Icons.edit_outlined, size: 17),
              label: const PpeeText('Sửa')),
            OutlinedButton.icon(onPressed: () => variants(product),
              icon: const Icon(Icons.layers_outlined, size: 17),
              label: const PpeeText('Biến thể / tồn kho')),
            OutlinedButton.icon(onPressed: () => images(product),
              icon: const Icon(Icons.photo_library_outlined, size: 17),
              label: const PpeeText('Hình ảnh')),
            TextButton.icon(onPressed: () => toggle(product),
              icon: Icon(active ? Icons.visibility_off_outlined
                : Icons.visibility_outlined, size: 17),
              label: PpeeText(active ? 'Ẩn' : 'Hiện')),
            if (active)
              TextButton.icon(onPressed: () => removeProduct(product),
                icon: const Icon(Icons.delete_outline_rounded, size: 17),
                style: TextButton.styleFrom(foregroundColor: Colors.red.shade700),
                label: const PpeeText('Ngừng bán / gỡ')),
          ]);
          if (size.maxWidth < 800) return Column(
            crossAxisAlignment: CrossAxisAlignment.start, children: [
              info, const Divider(height: 25), actions,
            ]);
          return Row(crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(flex: 5, child: info),
              const SizedBox(width: 16),
              Expanded(flex: 5, child: actions),
            ]);
        })));
  }
}

class _ProductEditor extends StatefulWidget {
  const _ProductEditor({this.initial});
  final Map<String,dynamic>? initial;
  @override State<_ProductEditor> createState()=>_ProductEditorState();
}
class _ProductEditorState extends State<_ProductEditor> {
  final form=GlobalKey<FormState>();
  late final Map<String,TextEditingController> fields;
  bool saving=false;
  String? error;
  List<Map<String,dynamic>> categories=[];
  String? categoryId;
  bool categoriesLoading = true;
  String? categoriesError;
  String? gender;
  bool visible=true;
  @override void initState(){super.initState();
    final p=widget.initial??{};
    fields={for(final key in ['name','slug','sku','brand','description','price','salePrice','quantity'])
      key:TextEditingController(text:p[key]?.toString()??(key=='quantity'?'0':''))};
    categoryId=p['categoryId']?.toString();gender=p['gender']?.toString();visible=p['status']!='INACTIVE';
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadCategories());
  }
  Future<void> _loadCategories() async {
    if (!mounted) return;
    setState(() { categoriesLoading = true; categoriesError = null; });
    try {
      final items = await context.read<CategoryRepository>().getAll();
      if (!mounted) return;
      // Keep an inactive old selection visible when editing, without allowing
      // inactive categories as new choices.
      setState(() {
        final tree = CategoryTree(items);
        final sorted = <Map<String, dynamic>>[];
        final seen = <String>{};
        void visit(Category category, int depth) {
          if (!category.isActive || !seen.add(category.id)) return;
          final prefix = depth == 0 ? '' : '${List.filled(depth, '  ').join()}↳ ';
          sorted.add({
            'id': category.id, 'name': category.name,
            'label': '$prefix${category.name}',
          });
          for (final child in tree.childrenOf(category.id)) { visit(child, depth + 1); }
        }
        for (final root in tree.roots) { visit(root, 0); }
        // Existing legacy products can retain an inactive or orphaned category.
        for (final category in items.where((c) => c.id == categoryId && !seen.contains(c.id))) {
          sorted.add({'id': category.id, 'name': category.name,
            'label': '${category.name} (đã ẩn)'});
        }
        categories = sorted;
      });
    } catch (_) {
      if (mounted) setState(() => categoriesError = 'Không tải được loại sản phẩm từ máy chủ.');
    } finally {
      if (mounted) setState(() => categoriesLoading = false);
    }
  }
  @override void dispose(){for(final c in fields.values){c.dispose();}super.dispose();}
  String? requiredText(String? s)=>s==null||s.trim().isEmpty?'Không được bỏ trống':null;
  Widget field(String key, String label, {
    bool required = false, bool numeric = false, int lines = 1,
    String? hint, IconData? icon,
  }) => PpeeFormField(
    controller: fields[key]!,
    label: label,
    hint: hint,
    prefixIcon: icon,
    maxLines: lines,
    requiredField: required,
    keyboardType: numeric
        ? const TextInputType.numberWithOptions(decimal: true)
        : (lines > 1 ? TextInputType.multiline : TextInputType.text),
    validator: required ? requiredText : null,
  );

  Future<void> submit() async {
    if(!form.currentState!.validate()) return;
    final initialCategory = widget.initial?['categoryId']?.toString();
    final chosenFromDb = categories.any((c) => c['id'] == categoryId);
    // New products must choose an existing category. Editing an older product
    // without category should still work without forcing unrelated changes.
    if ((widget.initial == null && !chosenFromDb) ||
        (widget.initial != null && categoryId != initialCategory && !chosenFromDb)) {
      setState(() => error = 'Vui lòng chọn loại sản phẩm từ danh mục đã được quản trị viên tạo.');
      return;
    }
    final price=num.tryParse(fields['price']!.text.trim());
    final sale=fields['salePrice']!.text.trim().isEmpty?null:num.tryParse(fields['salePrice']!.text.trim());
    final qty=int.tryParse(fields['quantity']!.text.trim());
    if(price==null||price<0||qty==null||qty<0||sale!=null&&(sale<0||sale>price)) {
      setState(()=>error='Giá hoặc số lượng không hợp lệ');return;
    }
    if(fields['salePrice']!.text.trim().isNotEmpty&&sale==null){setState(()=>error='Giá giảm không hợp lệ');return;}
    final selectedName = chosenFromDb
        ? categories.firstWhere((c) => c['id'] == categoryId)['name']?.toString() ?? ''
        : '';
    // products.category_id is authoritative; products.product_type is VARCHAR(50).
    // Do not discard old legacy data or send an overlong string on updates.
    final oldType = widget.initial?['productType']?.toString();
    final newType = initialCategory == categoryId && widget.initial != null
        ? oldType : (selectedName.length <= 50 ? selectedName : null);
    final body={for(final e in fields.entries)e.key:e.value.text.trim(),
      'price':price,'salePrice':sale,'quantity':qty,'categoryId':categoryId,
      'productType':newType, 'gender':gender,
      'status':visible?'ACTIVE':'INACTIVE'};
    for(final k in ['brand','description']) {if(body[k]=='') body[k]=null;}
    setState(() {saving=true;error=null;});
    try {
      final repo=context.read<SellerProductRepository>();
      if(widget.initial==null) {await repo.create(body);} else {await repo.update(widget.initial!['id'].toString(),body);}
      if(mounted) Navigator.pop(context,true);
    } catch(e) {if(mounted)setState(()=>error=e is ApiException?e.message:'Không thể lưu sản phẩm: $e');}
    finally {if(mounted)setState(()=>saving=false);}
  }
  @override
  Widget build(BuildContext context) => AlertDialog(
    title: PpeeText(widget.initial == null ? 'Thêm sản phẩm mới' : 'Chỉnh sửa sản phẩm'),
    content: SizedBox(
      width: (MediaQuery.sizeOf(context).width - 90).clamp(240.0, 740.0).toDouble(),
      child: SingleChildScrollView(child: Form(
        key: form,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min, children: [
          const PpeeNotice(message: 'Thông tin sản phẩm sẽ được lưu vào gian hàng của bạn. Các trường có dấu * là bắt buộc.'),
          const SizedBox(height: 22),
          PpeeFormGrid(children: [
            field('name', 'Tên sản phẩm', required: true,
              hint: 'Nhập tên hiển thị', icon: Icons.inventory_2_outlined),
            field('slug', 'Đường dẫn (slug)', required: true,
              hint: 'duong-dan-san-pham', icon: Icons.link_rounded),
            field('sku', 'Mã sản phẩm (SKU)', required: true,
              hint: 'Mã SKU duy nhất', icon: Icons.qr_code_rounded),
            field('brand', 'Thương hiệu', hint: 'Thương hiệu sản phẩm',
              icon: Icons.workspace_premium_outlined),
          ]),
          const SizedBox(height: 18),
          field('description', 'Mô tả sản phẩm', lines: 4,
            hint: 'Điểm nổi bật, thông số, hướng dẫn sử dụng...'),
          const SizedBox(height: 18),
          PpeeFormGrid(children: [
            field('price', 'Giá bán (VND)', required: true, numeric: true,
              icon: Icons.payments_outlined),
            field('salePrice', 'Giá khuyến mãi (VND)', numeric: true,
              icon: Icons.local_offer_outlined),
            field('quantity', 'Số lượng tồn kho', required: true, numeric: true,
              icon: Icons.warehouse_outlined),

          ]),
          const SizedBox(height: 18),
          if (categoriesLoading)
            const Padding(padding: EdgeInsets.symmetric(vertical: 10),
              child: LinearProgressIndicator()),
          if (categoriesError != null) ...[
            PpeeNotice(message: categoriesError!, isError: true),
            TextButton.icon(onPressed: _loadCategories,
              icon: const Icon(Icons.refresh_rounded),
              label: const PpeeText('Tải lại danh mục')),
          ],
          PpeeFormGrid(children: [
            DropdownButtonFormField<String>(
              key: ValueKey('product-category-$categoryId-${categories.length}'),
              initialValue: categories.any((c) => c['id'].toString() == categoryId)
                  ? categoryId : null,
              isExpanded: true,
              decoration: InputDecoration(
                labelText: tr(context, 'Loại sản phẩm *'),
                helperText: tr(context, 'Do quản trị viên tạo và lưu trong database'),
                prefixIcon: const Icon(Icons.category_outlined)),
              items: categories.map((c) => DropdownMenuItem<String>(
                value: c['id'].toString(), child: PpeeText(c['label'].toString(),
                  overflow: TextOverflow.ellipsis))).toList(),
              onChanged: categoriesLoading || categoriesError != null
                  ? null : (v) => setState(() { categoryId = v; error = null; }),
              validator: (v) => widget.initial == null && v == null
                  ? tr(context, 'Vui lòng chọn loại sản phẩm') : null,
            ),
            DropdownButtonFormField<String>(
              initialValue: gender, isExpanded: true,
              decoration: InputDecoration(
                labelText: tr(context, 'Đối tượng'), prefixIcon: Icon(Icons.groups_outlined)),
              items: const [
                DropdownMenuItem(value: 'MEN', child: PpeeText('Nam')),
                DropdownMenuItem(value: 'WOMEN', child: PpeeText('Nữ')),
                DropdownMenuItem(value: 'UNISEX', child: PpeeText('Unisex')),
                DropdownMenuItem(value: 'KIDS', child: PpeeText('Trẻ em')),
              ],
              onChanged: (v) => setState(() => gender = v),
            ),
          ]),
          const SizedBox(height: 14),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            value: visible,
            onChanged: (v) => setState(() => visible = v),
            title: const PpeeText('Hiển thị trên trang mua sắm',
              style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5)),
            subtitle: const PpeeText('Có thể bật/tắt sau khi tạo sản phẩm.'),
          ),
          if (error != null) ...[
            const SizedBox(height: 12),
            PpeeNotice(message: error!, isError: true),
          ],
        ]),
      )),
    ),
    actionsPadding: const EdgeInsets.fromLTRB(20, 0, 20, 18),
    actions: [
      TextButton(onPressed: saving ? null : () => Navigator.pop(context),
        child: const PpeeText('Hủy')),
      FilledButton.icon(
        onPressed: saving ? null : submit,
        icon: saving
            ? const SizedBox(width: 16, height: 16,
                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
            : const Icon(Icons.save_outlined, size: 18),
        label: PpeeText(saving ? 'Đang lưu...' : 'Lưu sản phẩm'),
      ),
    ],
  );
}

class _VariantsEditor extends StatefulWidget {
  const _VariantsEditor({required this.product});
  final Map<String,dynamic> product;
  @override State<_VariantsEditor> createState()=>_VariantsEditorState();
}
class _VariantsEditorState extends State<_VariantsEditor> {
  bool loading=true;
  String? error;
  List<Map<String,dynamic>> list=[];
  @override void initState(){super.initState();WidgetsBinding.instance.addPostFrameCallback((_)=>reload());}
  Future<void> reload() async {
    setState((){loading=true;error=null;});
    try{final items=await context.read<SellerProductRepository>().variants(widget.product['id'].toString());
      if(mounted)setState(()=>list=items);
    }catch(e){if(mounted)setState(()=>error=e is ApiException?e.message:'Không thể tải biến thể: $e');}
    finally{if(mounted)setState(()=>loading=false);}
  }
  Future<void> bulk() async {
    final result = await showDialog<bool>(context: context,
      builder: (_) => SellerVariantMatrixDialog(product: widget.product, existing: list));
    if (result == true) await reload();
  }
  Future<void> edit([Map<String,dynamic>? item]) async {
    final result=await showDialog<bool>(context:context,builder:(_)=>_VariantForm(productId:widget.product['id'].toString(),initial:item));
    if(result==true) await reload();
  }
  Future<void> stock(Map<String,dynamic> v) async {
    final c=TextEditingController(text:v['quantity'].toString());
    final repo = context.read<SellerProductRepository>();
    final quantity=await showDialog<int>(context:context,builder:(ctx)=>AlertDialog(title:PpeeText('Tồn kho • ${v['sku']}'),
      content:TextField(controller:c,keyboardType:TextInputType.number,decoration:InputDecoration(labelText:tr(context, 'Số lượng mới'))),
      actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const PpeeText('Hủy')),
        FilledButton(onPressed:()=>Navigator.pop(ctx,int.tryParse(c.text)),child:const PpeeText('Cập nhật'))]));
    c.dispose();
    if (!mounted || quantity == null) return;
    try{await repo.stock(widget.product['id'].toString(),v['id'].toString(),quantity);
      await reload();
    }catch(e){if(mounted)setState(()=>error=e is ApiException?e.message:'Cập nhật tồn kho thất bại: $e');}
  }
  Future<void> removeVariant(Map<String,dynamic> variant) async {
    final result = await showDialog<bool>(context: context,
      builder: (dialogContext) => AlertDialog(
        title: const PpeeText('Xóa biến thể?'),
        content: PpeeText('Biến thể ${variant['sku']} sẽ được xóa nếu chưa có đơn hàng/giỏ hàng. '
            'Nếu đã có tham chiếu, hệ thống sẽ ngừng bán biến thể để giữ nguyên lịch sử giao dịch.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false),
            child: const PpeeText('Hủy')),
          FilledButton.icon(onPressed: () => Navigator.pop(dialogContext, true),
            icon: const Icon(Icons.delete_outline),
            label: const PpeeText('Xác nhận')),
        ],
      ));
    if (result != true || !mounted) return;
    setState(() { loading = true; error = null; });
    try {
      final hardDeleted = await context.read<SellerProductRepository>()
          .deleteVariant(widget.product['id'].toString(), variant['id'].toString());
      if (!mounted) return;
      await reload();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:
        PpeeText(hardDeleted ? 'Đã xóa biến thể.'
          : 'Biến thể đã ngừng bán vì có trong đơn hàng hoặc giỏ hàng. '
            'Có thể mở lại bằng nút Sửa.')));
    } catch (e) {
      if (mounted) setState(() {
        loading = false;
        error = e is ApiException ? e.message : 'Không thể xóa biến thể: $e';
      });
    }
  }

  @override Widget build(BuildContext context)=>AlertDialog(
    title:PpeeText('Biến thể • ${widget.product['name']}'),
    content:SizedBox(width:(MediaQuery.sizeOf(context).width - 100).clamp(220.0, 630.0).toDouble(),child:SingleChildScrollView(child:Column(mainAxisSize:MainAxisSize.min,children:[
      if(loading) const CircularProgressIndicator(),
      if(error!=null) PpeeText(error!,style:const TextStyle(color:Colors.red)),
      if(!loading&&list.isEmpty) const PpeeText('Chưa có biến thể'),
      for (final v in list) Card(
        margin: const EdgeInsets.only(bottom: 10),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 13),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(child: PpeeText('${v['sku']}', maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13))),
              const SizedBox(width: 8),
              if (v['status'] != 'ACTIVE')
                const SellerStatusBadge(label: 'Ngừng bán', icon: Icons.block_outlined),
            ]),
            const SizedBox(height: 5),
            PpeeText('Size: ${v['size'] ?? '-'}  •  Mẫu: ${v['color'] ?? '-'}  •  Kho: ${v['quantity']}',
              style: const TextStyle(fontSize: 12, color: AppColors.gray700)),
            const SizedBox(height: 9),
            Wrap(spacing: 8, runSpacing: 3, children: [
              OutlinedButton.icon(onPressed: () => edit(v),
                icon: const Icon(Icons.edit_outlined, size: 16),
                label: const PpeeText('Sửa')),
              OutlinedButton.icon(onPressed: () => stock(v),
                icon: const Icon(Icons.inventory_2_outlined, size: 16),
                label: const PpeeText('Tồn kho')),
              if (v['status'] == 'ACTIVE')
                TextButton.icon(onPressed: () => removeVariant(v),
                  icon: const Icon(Icons.delete_outline_rounded, size: 16),
                  label: const PpeeText('Xóa'),
                  style: TextButton.styleFrom(foregroundColor: AppColors.error)),
            ]),
          ]),
        ),
      ),
    ]))),
    actionsOverflowButtonSpacing: 8,
    actions:[FilledButton.icon(onPressed:bulk,icon:const Icon(Icons.auto_awesome_rounded),label:const PpeeText('Tạo nhanh ảnh × size')),
      TextButton(onPressed:()=>edit(),child:const PpeeText('Thêm lẻ')),
      TextButton(onPressed:()=>Navigator.pop(context),child:const PpeeText('Đóng'))],
  );
}
class _VariantForm extends StatefulWidget {
  const _VariantForm({required this.productId,this.initial});
  final String productId;
  final Map<String,dynamic>? initial;
  @override State<_VariantForm> createState()=>_VariantFormState();
}
class _VariantFormState extends State<_VariantForm> {
  final form=GlobalKey<FormState>();
  late final Map<String,TextEditingController> f;
  bool saving=false;
  String? error;
  bool active=true;
  String? imageId;
  late Future<List<Map<String, dynamic>>> photoFuture;
  @override void initState(){super.initState();final p=widget.initial??{};
    imageId=p['imageId']?.toString();
    photoFuture=context.read<SellerProductRepository>().images(widget.productId);
    f={for(final k in ['sku','size','color','price','salePrice','quantity'])
      k:TextEditingController(text:p[k]?.toString()??(k=='quantity'?'0':''))};
    active=p['status']!='INACTIVE';
  }
  @override void dispose(){for(final c in f.values){c.dispose();}super.dispose();}
  Future<void> submit() async {
    if(!form.currentState!.validate())return;
    final price=f['price']!.text.trim().isEmpty?null:num.tryParse(f['price']!.text.trim());
    final sale=f['salePrice']!.text.trim().isEmpty?null:num.tryParse(f['salePrice']!.text.trim());
    final qty=int.tryParse(f['quantity']!.text.trim());
    if(qty==null||qty<0||price!=null&&price<0||sale!=null&&(price==null||sale<0||sale>price)
        ||f['price']!.text.trim().isNotEmpty&&price==null || f['salePrice']!.text.trim().isNotEmpty&&sale==null){
      setState(()=>error='Giá hoặc tồn kho không hợp lệ');return;
    }
    final data={'productId':widget.productId,'sku':f['sku']!.text.trim(),'size':f['size']!.text.trim().isEmpty?null:f['size']!.text.trim(),
      'color':f['color']!.text.trim().isEmpty?null:f['color']!.text.trim(),
      'price':price,'salePrice':sale,'quantity':qty,'status':active?'ACTIVE':'INACTIVE','imageId':imageId};
    setState((){saving=true;error=null;});
    try{await context.read<SellerProductRepository>().saveVariant(widget.productId,data,id:widget.initial?['id']?.toString());
      if(mounted)Navigator.pop(context,true);
    }catch(e){if(mounted)setState(()=>error=e is ApiException?e.message:'Lưu biến thể thất bại: $e');}
    finally{if(mounted)setState(()=>saving=false);}
  }
  String _label(String key) => switch (key) {
    'sku' => 'Mã biến thể (SKU)',
    'size' => 'Kích cỡ',
    'color' => 'Màu sắc',
    'price' => 'Giá bán (VND)',
    'salePrice' => 'Giá khuyến mãi (VND)',
    'quantity' => 'Số lượng tồn',
    _ => key,
  };

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: PpeeText(widget.initial == null ? 'Thêm biến thể' : 'Chỉnh sửa biến thể'),
    content: SizedBox(
      width: (MediaQuery.sizeOf(context).width - 90).clamp(240.0, 620.0).toDouble(),
      child: SingleChildScrollView(child: Form(
        key: form,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min, children: [
          const PpeeNotice(message: 'Kích cỡ, màu sắc, giá và tồn kho được quản lý riêng theo từng biến thể.'),
          const SizedBox(height: 22),
          PpeeFormGrid(children: [
            for (final key in ['sku', 'size', 'color', 'price', 'salePrice', 'quantity'])
              PpeeFormField(
                label: _label(key),
                controller: f[key]!,
                requiredField: key == 'sku' || key == 'quantity',
                hint: 'Nhập ${_label(key).toLowerCase()}',
                keyboardType: ['price', 'salePrice', 'quantity'].contains(key)
                    ? const TextInputType.numberWithOptions(decimal: true)
                    : TextInputType.text,
                validator: key == 'sku' || key == 'quantity'
                    ? (value) => value == null || value.trim().isEmpty
                        ? 'Vui lòng nhập thông tin này' : null
                    : null,
              ),
          ]),
          const SizedBox(height: 13),
          FutureBuilder<List<Map<String,dynamic>>>(future:photoFuture,
            builder:(context,snapshot) {
              if(!snapshot.hasData) return const SizedBox.shrink();
              final photos=snapshot.data!;
              if(photos.isEmpty) return const PpeeNotice(message:'Chưa có ảnh. Thêm ảnh trong mục Hình ảnh để gắn cho biến thể.');
              return DropdownButtonFormField<String>(
                value: photos.any((v)=>v['id'].toString()==imageId) ? imageId : null,
                decoration: InputDecoration(labelText: tr(context,'Ảnh mẫu (tùy chọn cho biến thể cũ)')),
                items: [
                  const DropdownMenuItem<String>(value:'',child:PpeeText('Không liên kết ảnh')),
                  for(var i=0;i<photos.length;i++) DropdownMenuItem<String>(
                    value: photos[i]['id'].toString(), child:PpeeText('Mẫu ảnh ${i+1}')),
                ],
                onChanged:(id)=>setState(()=>imageId=id==''?null:id),
              );
            }),
          const SizedBox(height: 13),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const PpeeText('Biến thể hoạt động',
              style: TextStyle(fontWeight: FontWeight.w700)),
            value: active,
            onChanged: (value) => setState(() => active = value),
          ),
          if (error != null) PpeeNotice(message: error!, isError: true),
        ]),
      )),
    ),
    actionsPadding: const EdgeInsets.fromLTRB(20, 0, 20, 18),
    actions: [
      TextButton(onPressed: saving ? null : () => Navigator.pop(context),
        child: const PpeeText('Hủy')),
      FilledButton.icon(
        onPressed: saving ? null : submit,
        icon: const Icon(Icons.save_outlined, size: 18),
        label: PpeeText(saving ? 'Đang lưu...' : 'Lưu biến thể'),
      ),
    ],
  );
}
