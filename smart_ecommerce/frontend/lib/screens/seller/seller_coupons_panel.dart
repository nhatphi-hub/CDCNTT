import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../repositories/seller_coupon_repository.dart';
import '../../widgets/ppee_ui.dart';
import '../../widgets/seller_ui.dart';

class SellerCouponsPanel extends StatefulWidget {
  const SellerCouponsPanel({super.key});
  @override State<SellerCouponsPanel> createState() => _SellerCouponsPanelState();
}

class _SellerCouponsPanelState extends State<SellerCouponsPanel> {
  late Future<List<Map<String, dynamic>>> _future;
  String _query = '';
  bool _onlyActive = false;
  @override void initState() { super.initState(); _load(); }
  void _load() => _future = context.read<SellerCouponRepository>().mine();

  Future<void> _create([Map<String, dynamic>? initial]) async {
    final changed = await showDialog<bool>(context: context,
      builder: (_) => _CouponDialog(initial: initial));
    if (mounted && changed == true) setState(_load);
  }

  Future<void> _toggle(Map<String, dynamic> c) async {
    try {
      await context.read<SellerCouponRepository>()
        .changeActive('${c['id']}', c['active'] != true);
      if (mounted) setState(_load);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: PpeeText(_msg(e))));
    }
  }

  String _msg(Object e) => e is ApiException ? e.message : 'Không thể kết nối API: $e';
  String _discount(Map<String, dynamic> c) => c['discountType'] == 'PERCENT'
    ? 'Giảm ${c['discountValue']}%' : 'Giảm ${c['discountValue']}đ';
  String _usage(Map<String, dynamic> c) {
    final used = c['usedCount'] ?? 0;
    final limit = c['usageLimit'];
    return limit == null ? 'Đã dùng $used lượt' : 'Đã dùng $used/$limit lượt';
  }
  bool _expired(Map<String, dynamic> c) {
    final end = DateTime.tryParse('${c['endAt'] ?? ''}');
    return end != null && end.isBefore(DateTime.now());
  }
  bool _upcoming(Map<String, dynamic> c) {
    final start = DateTime.tryParse('${c['startAt'] ?? ''}');
    return start != null && start.isAfter(DateTime.now());
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      SellerPageIntro(title: 'Khuyến mãi & ưu đãi',
        subtitle: 'Quản lý mã giảm giá dành riêng cho sản phẩm của gian hàng.',
        icon: Icons.local_offer_rounded,
        actions: [
          OutlinedButton.icon(onPressed: () => setState(_load),
            icon: const Icon(Icons.refresh_rounded, size: 18),
            label: const PpeeText('Làm mới')),
          FilledButton.icon(onPressed: () => _create(),
            icon: const Icon(Icons.add_rounded), label: const PpeeText('Tạo mã mới')),
        ]),
      const SizedBox(height: 17),
      FutureBuilder<List<Map<String, dynamic>>>(future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const SellerPanel(child: Center(child: Padding(
              padding: EdgeInsets.all(42), child: CircularProgressIndicator())));
          }
          if (snapshot.hasError) return SellerEmptyState(
            title: 'Không tải được khuyến mãi', message: _msg(snapshot.error!),
            icon: Icons.wifi_off_outlined,
            action: OutlinedButton(onPressed: () => setState(_load),
              child: const PpeeText('Thử lại')));
          final coupons = snapshot.data!;
          final active = coupons.where((c) => c['active'] == true && !_expired(c) && !_upcoming(c)).length;
          final upcoming = coupons.where((c) => c['active'] == true && _upcoming(c)).length;
          final expired = coupons.where(_expired).length;
          final filtered = coupons.where((c) {
            final match = '${c['code'] ?? ''} ${c['description'] ?? ''}'
              .toLowerCase().contains(_query.toLowerCase().trim());
            return match && (!_onlyActive || c['active'] == true);
          }).toList();
          return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SellerMetricGrid(children: [
              SellerMetric(label: 'Tổng mã giảm giá', value: '${coupons.length}',
                icon: Icons.confirmation_number_outlined),
              SellerMetric(label: 'Đang hiệu lực', value: '$active',
                icon: Icons.verified_outlined, color: AppColors.success),
              SellerMetric(label: 'Sắp diễn ra', value: '$upcoming',
                icon: Icons.schedule_rounded, color: AppColors.accent),
              SellerMetric(label: 'Đã hết hạn', value: '$expired',
                icon: Icons.event_busy_outlined, color: AppColors.gray700),
            ]),
            const SizedBox(height: 15),
            SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const SellerSectionTitle(title: 'Danh sách chương trình',
                detail: 'Tìm kiếm theo mã hoặc tên chương trình',
                icon: Icons.view_list_rounded),
              const SizedBox(height: 15),
              LayoutBuilder(builder: (context, size) => Wrap(spacing: 12,
                runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
                  SizedBox(width: size.maxWidth < 450 ? size.maxWidth : 370,
                    child: TextField(
                      onChanged: (v) => setState(() => _query = v),
                      decoration: InputDecoration(
                        hintText: tr(context, 'Tìm mã giảm giá...'),
                        prefixIcon: Icon(Icons.search_rounded)),
                    )),
                  FilterChip(label: const PpeeText('Đang bật'),
                    selected: _onlyActive,
                    onSelected: (v) => setState(() => _onlyActive = v)),
                  SellerStatusBadge(label: '${filtered.length} mã'),
                ])),
            ])),
            const SizedBox(height: 13),
            if (coupons.isEmpty)
              SellerEmptyState(title: 'Chưa có mã giảm giá',
                message: 'Tạo ưu đãi đầu tiên cho khách hàng của gian hàng.',
                icon: Icons.local_offer_outlined,
                action: FilledButton.icon(onPressed: () => _create(),
                  icon: const Icon(Icons.add), label: const PpeeText('Tạo mã')))
            else if (filtered.isEmpty)
              const SellerEmptyState(title: 'Không tìm thấy chương trình',
                message: 'Thử đổi từ khóa hoặc bộ lọc.',
                icon: Icons.search_off_rounded)
            else
              for (final c in filtered) _couponCard(c),
          ]);
        }),
    ]);

  Widget _couponCard(Map<String, dynamic> c) {
    final enabled = c['active'] == true;
    final expired = _expired(c);
    final upcoming = _upcoming(c);
    final label = !enabled ? 'Đã tắt' : expired ? 'Hết hạn' : upcoming ? 'Sắp diễn ra' : 'Đang hiệu lực';
    final color = !enabled || expired ? AppColors.gray700 : upcoming ? AppColors.accent : AppColors.success;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: SellerPanel(padding: const EdgeInsets.all(18),
        child: LayoutBuilder(builder: (context, size) => Wrap(
          alignment: WrapAlignment.spaceBetween,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 13, runSpacing: 13, children: [
            SizedBox(width: size.maxWidth < 650 ? size.maxWidth : size.maxWidth * .63,
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Container(width: 58, height: 58,
                  decoration: BoxDecoration(
                    color: AppColors.highlightSoft,
                    borderRadius: BorderRadius.circular(16)),
                  child: const Icon(Icons.sell_outlined,
                    color: AppColors.highlight, size: 26)),
                const SizedBox(width: 13),
                Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Wrap(spacing: 8, runSpacing: 6, children: [
                      PpeeText('${c['code']}', style: const TextStyle(
                        color: AppColors.black, fontWeight: FontWeight.w900,
                        fontSize: 17)),
                      SellerStatusBadge(label: label, color: color),
                    ]),
                    const SizedBox(height: 6),
                    PpeeText(_discount(c), style: const TextStyle(
                      color: AppColors.primaryDark, fontWeight: FontWeight.w900,
                      fontSize: 15)),
                    const SizedBox(height: 5),
                    if ('${c['description'] ?? ''}'.trim().isNotEmpty)
                      PpeeText('${c['description']}', maxLines: 2,
                        overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 7),
                    PpeeText('${_usage(c)}  ·  ${c['startAt'] ?? '—'} → ${c['endAt'] ?? '—'}',
                      style: const TextStyle(color: AppColors.gray700,
                        height: 1.45, fontSize: 11.5)),
                  ])),
              ])),
            Wrap(spacing: 7, crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                OutlinedButton.icon(onPressed: () => _create(c),
                  icon: const Icon(Icons.edit_outlined, size: 17),
                  label: const PpeeText('Chỉnh sửa')),
                Tooltip(message: tr(context, enabled ? 'Tắt mã giảm giá' : 'Bật mã giảm giá'),
                  child: Switch.adaptive(value: enabled, onChanged: (_) => _toggle(c))),
              ]),
          ]))),
    );
  }
}

class _CouponDialog extends StatefulWidget {
  const _CouponDialog({this.initial});
  final Map<String,dynamic>? initial;
  @override State<_CouponDialog> createState()=>_CouponDialogState();
}
class _CouponDialogState extends State<_CouponDialog> {
  final _form=GlobalKey<FormState>();
  final code=TextEditingController(), description=TextEditingController();
  final value=TextEditingController(), min=TextEditingController(), max=TextEditingController(), limit=TextEditingController();
  String type='PERCENT';
  DateTime start=DateTime.now(), end=DateTime.now().add(const Duration(days:30));
  bool busy=false; String? error;
  @override void initState() {
    super.initState();
    final c=widget.initial;
    if(c!=null){
      code.text='${c['code']}';description.text='${c['description'] ?? ''}';
      value.text='${c['discountValue']}';min.text=c['minOrderAmount']?.toString() ?? '';
      max.text=c['maxDiscountAmount']?.toString() ?? '';
      limit.text=c['usageLimit']?.toString() ?? '';
      type='${c['discountType']}';
      start=DateTime.tryParse('${c['startAt']}')??start;
      end=DateTime.tryParse('${c['endAt']}')??end;
    }
  }
  @override void dispose(){for(final c in [code,description,value,min,max,limit]){c.dispose();}super.dispose();}
  Future<void> _date(bool startDate) async {
    final date=await showDatePicker(context:context,initialDate:startDate?start:end,
      firstDate:DateTime(2020),lastDate:DateTime(2100));
    if(date==null || !mounted) return;
    setState(() {if(startDate){start=date;}else{end=date.add(const Duration(hours:23,minutes:59));}});
  }
  String _iso(DateTime date)=>date.toIso8601String().split('.').first;
  Future<void> _save() async {
    if(!_form.currentState!.validate())return;
    if(end.isBefore(start)){setState(()=>error='Ngày kết thúc phải sau ngày bắt đầu');return;}
    setState(() {busy=true;error=null;});
    try {
      final data={
        'code':code.text.trim(), 'description':description.text.trim(),
        'discountType':type,'discountValue':double.parse(value.text),
        'minOrderAmount':min.text.isEmpty?null:double.parse(min.text),
        'maxDiscountAmount':max.text.isEmpty?null:double.parse(max.text),
        'usageLimit':limit.text.isEmpty?null:int.parse(limit.text),
        'startAt':_iso(start),'endAt':_iso(end),'active':widget.initial?['active']!=false,
      };
      final repo=context.read<SellerCouponRepository>();
      if(widget.initial==null){await repo.create(data);}else{await repo.update('${widget.initial!['id']}',data);}
      if(mounted) Navigator.pop(context,true);
    } catch(e) {
      if(mounted) setState(()=>error=e is ApiException?e.message:'Lỗi: $e');
    } finally {if(mounted) setState(()=>busy=false);}
  }
  Widget number(String label, TextEditingController ctrl, {bool mandatory = false}) => PpeeFormField(
    label: label,
    controller: ctrl,
    requiredField: mandatory,
    keyboardType: const TextInputType.numberWithOptions(decimal: true),
    validator: (s) {
      if ((s ?? '').isEmpty) return mandatory ? 'Bắt buộc' : null;
      final v = double.tryParse(s!);
      return v == null || v <= 0 ? 'Nhập số lớn hơn 0' : null;
    },
  );

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: PpeeText(widget.initial == null ? 'Tạo khuyến mãi theo shop' : 'Sửa khuyến mãi'),
    content: SizedBox(
      width: (MediaQuery.sizeOf(context).width - 90).clamp(240.0, 650.0).toDouble(),
      child: SingleChildScrollView(child: Form(
        key: _form,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min, children: [
          const PpeeNotice(message: 'Mã được áp dụng với sản phẩm của gian hàng. Vui lòng kiểm tra giới hạn và thời gian hiệu lực.'),
          const SizedBox(height: 19),
          PpeeFormGrid(children: [
            PpeeFormField(
              controller: code, label: 'Mã giảm giá', requiredField: true,
              prefixIcon: Icons.confirmation_number_outlined,
              hint: 'Mã dành cho khách hàng',
              validator: (v) => (v ?? '').trim().isEmpty ? 'Nhập mã' : null,
            ),
            DropdownButtonFormField<String>(
              initialValue: type,
              isExpanded: true,
              decoration: InputDecoration(labelText: tr(context, 'Kiểu khuyến mãi'),
                prefixIcon: Icon(Icons.percent_rounded)),
              items: const [
                DropdownMenuItem(value: 'PERCENT', child: PpeeText('Giảm theo %')),
                DropdownMenuItem(value: 'FIXED', child: PpeeText('Giảm tiền cố định')),
              ],
              onChanged: (v) => setState(() => type = v ?? 'PERCENT'),
            ),
          ]),
          const SizedBox(height: 16),
          PpeeFormField(controller: description, label: 'Mô tả khuyến mãi',
            maxLines: 2, hint: 'Nội dung hiển thị của chương trình'),
          const SizedBox(height: 16),
          PpeeFormGrid(children: [
            number('Giá trị giảm', value, mandatory: true),
            number('Giá trị đơn tối thiểu', min),
            number('Giảm tối đa (không bắt buộc)', max),
            PpeeFormField(
              controller: limit, label: 'Giới hạn số lượt sử dụng',
              keyboardType: TextInputType.number,
              validator: (s) {
                if ((s ?? '').isEmpty) return null;
                final n = int.tryParse(s!);
                return n == null || n <= 0 ? 'Nhập số nguyên dương' : null;
              },
            ),
          ]),
          const SizedBox(height: 17),
          PpeeText('Thời gian áp dụng', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(spacing: 9, runSpacing: 8, children: [
            OutlinedButton.icon(
              onPressed: busy ? null : () => _date(true),
              icon: const Icon(Icons.calendar_today_outlined, size: 18),
              label: PpeeText('Từ ${start.day}/${start.month}/${start.year}')),
            OutlinedButton.icon(
              onPressed: busy ? null : () => _date(false),
              icon: const Icon(Icons.event_available_outlined, size: 18),
              label: PpeeText('Đến ${end.day}/${end.month}/${end.year}')),
          ]),
          if (error != null) ...[
            const SizedBox(height: 14),
            PpeeNotice(message: error!, isError: true),
          ],
        ]),
      )),
    ),
    actionsPadding: const EdgeInsets.fromLTRB(20, 0, 20, 18),
    actions: [
      TextButton(onPressed: busy ? null : () => Navigator.pop(context),
        child: const PpeeText('Hủy')),
      FilledButton.icon(
        onPressed: busy ? null : _save,
        icon: const Icon(Icons.save_outlined, size: 18),
        label: PpeeText(busy ? 'Đang lưu...' : 'Lưu mã'),
      ),
    ],
  );
}
