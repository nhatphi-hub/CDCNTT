import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../repositories/admin_return_repository.dart';
import '../../core/network/api_exception.dart';

class AdminReturnsScreen extends StatefulWidget {
  const AdminReturnsScreen({super.key});
  @override
  State<AdminReturnsScreen> createState() => _AdminReturnsScreenState();
}

class _AdminReturnsScreenState extends State<AdminReturnsScreen> {
  late Future<List<Map<String, dynamic>>> _future;
  bool _busy = false;
  @override
  void initState() { super.initState(); _reload(); }
  void _reload() { _future = context.read<AdminReturnRepository>().all(); }
  void _refresh() { final repo = context.read<AdminReturnRepository>();
    setState(() { _future = repo.all(); }); }

  Future<void> _received(String id) async {
    if (_busy) return;
    final repo = context.read<AdminReturnRepository>();
    setState(() => _busy = true);
    try {
      await repo.confirmGoodsReceived(id);
      if (!mounted) return;
      _refresh();
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: PpeeText('Đã xác nhận nhận hàng trả. Chưa ghi nhận hoàn tiền.')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: PpeeText(
          e is ApiException ? e.message : 'Không cập nhật được: $e')));
    } finally { if (mounted) setState(() => _busy = false); }
  }

  Future<void> _recordRefund(Map<String,dynamic> request) async {
    if (_busy) return;
    final amount = TextEditingController();
    final reference = TextEditingController();
    final evidence = TextEditingController();
    final input = await showDialog<bool>(context: context, builder: (dialogContext) =>
      AlertDialog(title: const PpeeText('Đối soát hoàn tiền đã thực hiện'),
        content: SizedBox(width: 500, child: SingleChildScrollView(child: Column(
          mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            PpeeText('Giới hạn tiền hàng của shop: ${request['maxGoodsRefund']} VND. '
                'Không bao gồm phí vận chuyển.'),
            const SizedBox(height: 8),
            const PpeeText('Chỉ ghi nhận sau khi đã chuyển tiền bên ngoài và có chứng từ. '
                'Thao tác này KHÔNG chuyển tiền tự động.',
                style: TextStyle(fontWeight: FontWeight.bold)),
            TextField(controller: amount, keyboardType: TextInputType.number,
              decoration: InputDecoration(labelText: tr(context, 'Số tiền đã hoàn (VND)'))),
            TextField(controller: reference,
              decoration: InputDecoration(labelText: tr(context, 'Mã giao dịch ngoài hệ thống'))),
            TextField(controller: evidence, minLines: 2, maxLines: 4,
              decoration: InputDecoration(labelText: tr(context, 'Bằng chứng/đối soát (>=10 ký tự)'))),
          ]))),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext,false),child: const PpeeText('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext,true),
            child: const PpeeText('Xác nhận đã hoàn bên ngoài')),
        ],
      ));
    final value = amount.text.trim();
    final ref = reference.text.trim();
    final note = evidence.text.trim();
    amount.dispose();reference.dispose();evidence.dispose();
    if (input != true || !mounted) return;
    final repo = context.read<AdminReturnRepository>();
    setState(() => _busy = true);
    try {
      await repo.recordVerifiedRefund('${request['id']}', value, ref, note);
      if (!mounted) return;
      _refresh();
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: PpeeText('Đã ghi sổ chứng từ hoàn tiền thủ công; Order và Payment gốc không đổi.')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: PpeeText(e is ApiException ? e.message : 'Đối soát thất bại: $e')));
    } finally { if (mounted) setState(() => _busy = false); }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const PpeeText('PPee ADMIN – Hậu mãi')),
    body: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1120),
      child: ListView(padding: const EdgeInsets.all(20),children: [
        Wrap(spacing: 10,runSpacing: 8,crossAxisAlignment: WrapCrossAlignment.center,children:[
          PpeeText('Yêu cầu trả hàng toàn hệ thống',style: Theme.of(context).textTheme.titleLarge),
          OutlinedButton.icon(onPressed: _busy ? null : _refresh,
            icon: const Icon(Icons.refresh), label: const PpeeText('Làm mới')),
        ]),
        const SizedBox(height: 8),
        const PpeeText('Chỉ quản trị viên được xác nhận hàng trả và ghi nhận chứng từ hoàn tiền thực hiện ngoài hệ thống. '
          'Không có API chuyển tiền tự động.'),
        const SizedBox(height: 18),
        FutureBuilder<List<Map<String,dynamic>>>(future:_future,builder:(context,snapshot){
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) return PpeeText('Không tải được: ${snapshot.error}');
          final items = snapshot.data ?? [];
          if (items.isEmpty) return const PpeeText('Chưa có yêu cầu trả hàng.');
          return Column(children: [for (final item in items) Card(
            child: Padding(padding: const EdgeInsets.all(14),child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,children:[
                PpeeText('${item['orderCode']} · ${item['shopName']}',
                  style:const TextStyle(fontWeight:FontWeight.bold)),
                PpeeText('Trạng thái: ${item['status']}'),
                PpeeText('Lý do: ${item['reason']}'),
                PpeeText('Tiền hàng shop sau giảm (tối đa): ${item['maxGoodsRefund']} VND'),
                if(item['automationStatus']!=null)
                  PpeeText('Xử lý tự động: ${item['automationStatus']} · ${item['automationExplanation'] ?? ''}'),
                if(item['recordedRefundAmount']!=null)
                  PpeeText('Đã ghi sổ: ${item['recordedRefundAmount']} VND · ${item['refundReference']}'),
                if(item['status']=='APPROVED')
                  FilledButton.tonal(onPressed:_busy?null:()=>_received('${item['id']}'),
                    child:const PpeeText('Xác nhận đã nhận hàng trả')),
                if(item['status']=='RETURN_RECEIVED')
                  FilledButton(onPressed:_busy?null:()=>_recordRefund(item),
                    child:const PpeeText('Ghi nhận đã hoàn tiền ngoài hệ thống')),
              ],)),
          )]);
        }),
      ]),
    )),
  );
}
