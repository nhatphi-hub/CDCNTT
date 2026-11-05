import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/responsive/shop_layout.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/formatters.dart';
import '../../models/order.dart';
import '../../providers/order_provider.dart';
import '../../providers/review_provider.dart';
import '../../repositories/shop_return_repository.dart';
import '../review/review_form_dialog.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/order_status_pill.dart';
import '../../widgets/product_image.dart';

class OrderDetailScreen extends StatefulWidget {
  const OrderDetailScreen({super.key, required this.orderId});
  final String orderId;
  @override
  State<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends State<OrderDetailScreen> {
  late Future<OrderModel> _future;
  late Future<List<Map<String,dynamic>>> _returns;

  @override
  void initState() {
    super.initState();
    _future = _load();
    _returns = context.read<ShopReturnRepository>().customer();
  }

  Future<OrderModel> _load() => context.read<OrderProvider>().get(widget.orderId);

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: const CatalogHeader(),
    body: FutureBuilder<OrderModel>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
        if (snapshot.hasError) return ShopEmptyState(icon: Icons.wifi_off_outlined,
          title: 'Không thể tải chi tiết đơn hàng', message: _message(snapshot.error),
          action: OutlinedButton(onPressed: () => setState(() { _future = _load(); }), child: const PpeeText('Thử lại')));
        return _content(snapshot.data!);
      },
    ),
  );

  Future<void> _requestReturn(String orderId,String productId) async {
    final controller=TextEditingController();
    final reason=await showDialog<String>(context:context,builder:(dialogContext)=>AlertDialog(
      title:const PpeeText('Yêu cầu trả hàng'),
      content:Column(mainAxisSize:MainAxisSize.min,children:[
        const PpeeText('Yêu cầu sẽ được chuyển đến shop. Việc hoàn tiền cần xử lý riêng, không thực hiện tự động.'),
        TextField(controller:controller,maxLength:1000,maxLines:3,
          decoration:InputDecoration(labelText:tr(context, 'Lý do (từ 10 ký tự)'))),
      ]),
      actions:[TextButton(onPressed:()=>Navigator.pop(dialogContext),child:const PpeeText('Hủy')),
        FilledButton(onPressed:()=>Navigator.pop(dialogContext,controller.text),child:const PpeeText('Gửi'))],
    ));
    controller.dispose();
    if(reason==null || !mounted)return;
    try {
      await context.read<ShopReturnRepository>().request(orderId,productId,reason);
      if(mounted) {
        final nextReturns = context.read<ShopReturnRepository>().customer();
        setState(() {
          _returns = nextReturns;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content:PpeeText('Đã gửi yêu cầu trả hàng đến shop')));
      }
    } catch(e) {
      if(mounted)ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content:PpeeText(e is ApiException?e.message:'Không gửi được yêu cầu: $e')));
    }
  }

  Widget _content(OrderModel order) {
    final compact = ShopLayout.isMobile(context);
    return Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1030),
      child: ListView(padding: EdgeInsets.all(ShopLayout.padding(context)), children: [
        Wrap(alignment: WrapAlignment.spaceBetween, spacing: 20, runSpacing: 12, crossAxisAlignment: WrapCrossAlignment.center, children: [
          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            PpeeText('Chi tiết đơn hàng', style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 5),
            SelectableText(order.orderCode, style: const TextStyle(color: AppColors.gray700)),
          ]),
          OrderStatusPill(status: order.status),
        ]),
        const SizedBox(height: 22),
        _section('Theo dõi đơn hàng', Icons.local_shipping_outlined, _timeline(order.status)),
        const SizedBox(height: 15),
        if(order.status=='DELIVERED' && order.paymentStatus=='PAID')
          FutureBuilder<List<Map<String,dynamic>>>(future:_returns,builder:(context,s){
            if(!s.hasData) return const SizedBox.shrink();
            final matches=s.data!.where((r)=>'${r['orderId']}'==order.id).toList();
            if(matches.isEmpty)return const SizedBox.shrink();
            return _section('Yêu cầu trả hàng',Icons.assignment_return_outlined,
              Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
                for(final r in matches) Padding(padding:const EdgeInsets.only(bottom:8),
                  child:PpeeText('${r['shopName']}: ${r['status']} · ${r['reason']}')),
              ]));
          }),

        _section('Sản phẩm đã đặt', Icons.shopping_bag_outlined, Column(children: [
          for (final item in order.items) ...[
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: ClipRRect(borderRadius: BorderRadius.circular(11),
                child: SizedBox(width: 67, height: 73, child: ProductImage(url: item.productImage))),
              title: PpeeText(item.productName, style: const TextStyle(fontWeight: FontWeight.w700)),
              subtitle: PpeeText([
                if ((item.variantName ?? '').isNotEmpty) item.variantName!,
                if ((item.size ?? '').isNotEmpty) 'Size: ${item.size}',
                if ((item.color ?? '').isNotEmpty) 'Màu: ${item.color}',
                'Số lượng: ${item.quantity}',
              ].join(' · ')),
              trailing: compact ? null : PpeeText(Formatters.vnd(item.subtotal), style: const TextStyle(fontWeight: FontWeight.w700)),
              onTap: () => Navigator.pushNamed(context, '${AppRoutes.product}/${item.productId}'),
            ),
            if (order.status == 'DELIVERED' && order.paymentStatus == 'PAID')
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () => _reviewProduct(item.productId),
                  icon: const Icon(Icons.rate_review_outlined, size: 19),
                  label: const PpeeText('Đánh giá sản phẩm'),
                ),
              ),
            if (order.status == 'DELIVERED' && order.paymentStatus == 'PAID')
              Align(alignment:Alignment.centerRight,child:TextButton.icon(
                onPressed:()=>_requestReturn(order.id,item.productId),
                icon:const Icon(Icons.assignment_return_outlined,size:18),
                label:const PpeeText('Yêu cầu trả hàng'))),
            if (compact) Align(alignment: Alignment.centerRight,
              child: PpeeText(Formatters.vnd(item.subtotal), style: const TextStyle(fontWeight: FontWeight.w700))),
            const Divider(height: 15),
          ],
        ])),
        const SizedBox(height: 15),
        LayoutBuilder(builder: (context, c) {
          final address = _section('Địa chỉ nhận hàng', Icons.location_on_outlined,
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              PpeeText(order.shippingRecipientName, style: const TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 5),
              PpeeText(order.shippingPhone),
              const SizedBox(height: 5),
              PpeeText(order.shippingAddress),
            ]));
          final payment = _section('Thanh toán', Icons.credit_card_rounded,
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              _labelValue('Phương thức', order.paymentMethod),
              const SizedBox(height: 10),
              _labelValue('Trạng thái', order.paymentStatus),
            ]));
          if (c.maxWidth < 700) return Column(children: [address, const SizedBox(height: 15), payment]);
          return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(child: address), const SizedBox(width: 15), Expanded(child: payment),
          ]);
        }),
        const SizedBox(height: 15),
        _section('Chi tiết thanh toán', Icons.receipt_long_outlined, Column(children: [
          _labelValue('Tạm tính', Formatters.vnd(order.subtotal)),
          const SizedBox(height: 13),
          _labelValue('Giảm giá', Formatters.vnd(order.discountAmount)),
          const SizedBox(height: 13),
          _labelValue('Phí giao hàng', Formatters.vnd(order.shippingFee)),
          const Divider(height: 30),
          Row(children: [
            const Expanded(child: PpeeText('Tổng thanh toán', style: TextStyle(fontWeight: FontWeight.w800))),
            PpeeText(Formatters.vnd(order.totalAmount),
              style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800, color: AppColors.primary)),
          ]),
        ])),
        if (order.status == 'PENDING' && order.paymentStatus != 'PAID') ...[
          const SizedBox(height: 17),
          OutlinedButton.icon(
            onPressed: context.watch<OrderProvider>().loading ? null : () => _cancel(order),
            icon: const Icon(Icons.cancel_outlined, color: AppColors.error),
            label: const PpeeText('Hủy đơn hàng', style: TextStyle(color: AppColors.error)),
          ),
        ],
        const SizedBox(height: 22),
      ]),
    ));
  }

  Widget _section(String title, IconData icon, Widget content) => Card(child: Padding(
    padding: const EdgeInsets.all(20), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [Icon(icon, size: 20, color: AppColors.primary), const SizedBox(width: 10),
        PpeeText(title, style: Theme.of(context).textTheme.titleMedium)]),
      const Divider(height: 25), content,
    ]),
  ));

  Widget _timeline(String status) {
    if (status == 'CANCELLED') return const Row(children: [
      Icon(Icons.cancel_rounded, color: AppColors.error, size: 20),
      SizedBox(width: 10), Expanded(child: PpeeText('Đơn hàng đã hủy', style: TextStyle(color: AppColors.error, fontWeight: FontWeight.w700))),
    ]);
    const states = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED'];
    final currentIndex = states.indexOf(status);
    return LayoutBuilder(builder: (context, c) {
      final compact = c.maxWidth < 480;
      if (compact) return Column(children: [
        for (int i = 0; i < states.length; i++) ...[
          Row(children: [
            Icon(i <= currentIndex ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
              color: i <= currentIndex ? AppColors.success : AppColors.gray300, size: 22),
            const SizedBox(width: 10),
            PpeeText(OrderStatusPill.labelFor(states[i]), style: TextStyle(
              fontWeight: i == currentIndex ? FontWeight.w800 : FontWeight.w500)),
          ]),
          if (i < states.length - 1) const Padding(padding: EdgeInsets.only(left: 10, top: 3, bottom: 3),
            child: Align(alignment: Alignment.centerLeft, child: SizedBox(height: 15, child: VerticalDivider(width: 1, thickness: 2)))),
        ],
      ]);
      return Row(children: [
        for (int i = 0; i < states.length; i++) ...[
          Expanded(child: Column(children: [
            Icon(i <= currentIndex ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
              color: i <= currentIndex ? AppColors.success : AppColors.gray300, size: 24),
            const SizedBox(height: 8),
            PpeeText(OrderStatusPill.labelFor(states[i]), maxLines: 1, textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: i <= currentIndex ? AppColors.black : AppColors.gray700,
                fontWeight: i == currentIndex ? FontWeight.w800 : FontWeight.w500)),
          ])),
          if (i < states.length - 1) const SizedBox(width: 13, child: Divider(thickness: 2)),
        ],
      ]);
    });
  }

  Widget _labelValue(String key, String value) => Row(children: [
    Expanded(child: PpeeText(key, style: const TextStyle(color: AppColors.gray700, fontSize: 13))),
    Flexible(child: PpeeText(value, textAlign: TextAlign.end,
      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13))),
  ]);

  Future<void> _reviewProduct(String productId) async {
    final provider = context.read<ReviewProvider>();
    final eligibility = await provider.loadEligibility(productId, force: true);
    if (!mounted) return;
    if (eligibility == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: PpeeText(provider.error ?? 'Không kiểm tra được điều kiện đánh giá.')));
      return;
    }
    if (eligibility.alreadyReviewed) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: PpeeText('Bạn đã đánh giá sản phẩm này. Vào trang chi tiết sản phẩm để sửa đánh giá.')));
      Navigator.pushNamed(context, '${AppRoutes.product}/$productId');
      return;
    }
    if (!eligibility.canReview) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: PpeeText('Chỉ có thể đánh giá sản phẩm đã nhận và thanh toán.')));
      return;
    }
    await showDialog<bool>(
      context: context,
      builder: (_) => ChangeNotifierProvider.value(
        value: provider,
        child: ReviewFormDialog(productId: productId),
      ),
    );
  }

  Future<void> _cancel(OrderModel order) async {
    final provider = context.read<OrderProvider>();
    final ok = await provider.cancel(order.id);
    if (!mounted) return;
    if (ok) {
      setState(() { _future = _load(); });
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: PpeeText(provider.error ?? 'Không thể hủy đơn hàng.')));
    }
  }

  String _message(Object? error) {
    if (error is ApiException) return error.message;
    return 'Không thể tải chi tiết đơn hàng.';
  }
}
