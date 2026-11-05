import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/routes/app_routes.dart';
import '../../core/responsive/shop_layout.dart';
import '../../core/theme/app_colors.dart';
import '../../core/utils/formatters.dart';
import '../../models/address.dart';
import '../../providers/address_provider.dart';
import '../../providers/cart_provider.dart';
import '../../providers/checkout_provider.dart';
import '../../providers/order_provider.dart';
import '../../repositories/payment_repository.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/ppee_ui.dart';

class CheckoutScreen extends StatefulWidget {
  const CheckoutScreen({super.key, required this.paymentRepository});
  final PaymentRepository paymentRepository;

  @override
  State<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends State<CheckoutScreen> {
  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    final cart = context.read<CartProvider>();
    final addresses = context.read<AddressProvider>();
    await Future.wait([cart.load(), addresses.load()]);
    if (!mounted) return;
    final defaultAddress = addresses.defaultAddress;
    if (defaultAddress != null) {
      context.read<CheckoutProvider>().selectAddress(defaultAddress);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cartProvider = context.watch<CartProvider>();
    final addressProvider = context.watch<AddressProvider>();
    final checkout = context.watch<CheckoutProvider>();
    final orders = context.watch<OrderProvider>();

    if ((cartProvider.loading && cartProvider.cart == null) ||
        (addressProvider.loading && addressProvider.addresses.isEmpty)) {
      return const Scaffold(
        appBar: CatalogHeader(),
        body: PpeeLoadingView(message: 'Đang chuẩn bị thanh toán...'),
      );
    }

    final loadError = cartProvider.cart == null
        ? cartProvider.error
        : addressProvider.addresses.isEmpty
            ? addressProvider.error
            : null;
    if (loadError != null) {
      return Scaffold(
        appBar: const CatalogHeader(),
        body: _CheckoutError(message: loadError, onRetry: _load),
      );
    }

    final cart = cartProvider.cart;
    if (cart == null || cart.items.isEmpty || cartProvider.selectedItems.isEmpty) {
      return Scaffold(
        appBar: const CatalogHeader(),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const PpeeText('Giỏ hàng đang trống.'),
              const SizedBox(height: 12),
              OutlinedButton(
                onPressed: () => Navigator.pushNamedAndRemoveUntil(
                  context,
                  AppRoutes.products,
                  (route) => route.settings.name == AppRoutes.home,
                ),
                child: const PpeeText('Tiếp tục mua sắm'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: const CatalogHeader(),
      body: SingleChildScrollView(
        padding: EdgeInsets.all(ShopLayout.padding(context)),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 1000),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const ShopHeading(title: 'Thanh toán', description: 'Kiểm tra thông tin trước khi hoàn tất đơn hàng'),
                const SizedBox(height: 20),
                const _CheckoutProgress(),
                const SizedBox(height: 17),
                Card(
                  child: ListTile(
                    title: const PpeeText('Địa chỉ giao hàng', style: TextStyle(fontWeight: FontWeight.w700)),
                    leading: const Icon(Icons.location_on_outlined, color: AppColors.primary),
                    subtitle: PpeeText(
                      checkout.address == null
                          ? 'Chưa chọn địa chỉ'
                          : '${checkout.address!.recipientName} • ${checkout.address!.phone}\n${checkout.address!.fullAddress}',
                    ),
                    trailing: TextButton(
                      onPressed: () async {
                        final selected = await Navigator.pushNamed(
                          context,
                          AppRoutes.addresses,
                          arguments: true,
                        );
                        if (selected is AddressModel && context.mounted) {
                          checkout.selectAddress(selected);
                        }
                      },
                      child: PpeeText(addressProvider.addresses.isEmpty ? 'Thêm' : 'Thay đổi'),
                    ),
                  ),
                ),
                if (addressProvider.addresses.isEmpty) ...[
                  const SizedBox(height: 8),
                  const PpeeText('Bạn cần thêm địa chỉ giao hàng trước khi đặt hàng.'),
                ],
                const SizedBox(height: 12),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Row(children: [Icon(Icons.credit_card_outlined, color: AppColors.primary), SizedBox(width: 10), PpeeText('Phương thức thanh toán', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16))]),
                        const SizedBox(height: 12),
                        RadioGroup<String>(
                          groupValue: checkout.paymentMethod,
                          onChanged: checkout.submitting
                              ? (_) {}
                              : (value) {
                                  if (value != null) checkout.selectPayment(value);
                                },
                          child: Column(
                            children: [
                              for (final method in const ['COD', 'BANK_TRANSFER', 'VNPAY', 'MOMO'])
                                Container(
                                  margin: const EdgeInsets.only(bottom: 10),
                                  decoration: BoxDecoration(
                                    color: checkout.paymentMethod == method
                                        ? AppColors.primarySoft : AppColors.gray100,
                                    borderRadius: BorderRadius.circular(14),
                                    border: Border.all(
                                      color: checkout.paymentMethod == method
                                          ? AppColors.primary : AppColors.gray300,
                                      width: checkout.paymentMethod == method ? 1.4 : 1,
                                    ),
                                  ),
                                  child: RadioListTile<String>(
                                    value: method,
                                    enabled: !checkout.submitting,
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(14)),
                                    title: PpeeText(_paymentLabel(method),
                                      style: const TextStyle(fontWeight: FontWeight.w700)),
                                    secondary: Icon(_paymentIcon(method), color: AppColors.primary),
                                  ),
                                ),
                            ],
                          ),
                        ),
                        if (checkout.paymentMethod != 'COD')
                          const PpeeNotice(
                            message: 'Thanh toán trực tuyến đang ở chế độ mô phỏng cho mục đích trình diễn đồ án.'),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const PpeeText('Tóm tắt thanh toán', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                        const SizedBox(height: 12),
                        PpeeText('Tạm tính sản phẩm đã chọn: ${Formatters.vnd(cartProvider.selectedTotal)}', style: const TextStyle(color: AppColors.primary, fontSize: 18, fontWeight: FontWeight.w800)),
                        const SizedBox(height: 8),
                        const PpeeText('Phí vận chuyển, giảm giá và tổng thanh toán cuối cùng được xác định khi tạo đơn.', style: TextStyle(color: AppColors.gray700, fontSize: 12)),
                        const SizedBox(height: 16),
                        TextFormField(
                          initialValue:checkout.couponCode,
                          maxLength:50,
                          textCapitalization:TextCapitalization.characters,
                          decoration:InputDecoration(
                            labelText:tr(context, 'Mã giảm giá (nếu có)'),
                            hintText: tr(context, 'Nhập mã ưu đãi'),
                            helperText:tr(context, 'Mã shop chỉ giảm trên sản phẩm của shop đó; tổng tiền xác nhận tại bước tạo đơn.'),
                            prefixIcon:Icon(Icons.local_offer_outlined),
                          ),
                          onChanged:checkout.setCouponCode,
                        ),
                        const SizedBox(height: 16),
                        if (orders.error != null) ...[
                          PpeeNotice(message: orders.error!, isError: true),
                          const SizedBox(height: 12),
                        ],
                        FilledButton(
                          onPressed: checkout.submitting || checkout.address == null
                              ? null
                              : () => _placeOrder(checkout, orders),
                          child: checkout.submitting
                              ? const SizedBox.square(
                                  dimension: 20,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                )
                              : const PpeeText('Xác nhận đặt hàng'),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _placeOrder(
    CheckoutProvider checkout,
    OrderProvider orders,
  ) async {
    if (checkout.submitting || checkout.address == null) return;
    checkout.setSubmitting(true);
    try {
      final order = await orders.create(
        addressId: checkout.address!.id,
        paymentMethod: checkout.paymentMethod,
        couponCode:checkout.couponCode.isEmpty?null:checkout.couponCode,
        selectedCartItemIds: context.read<CartProvider>().selectedIds.toList(),
      );
      if (order == null || !mounted) return;

      if (checkout.paymentMethod != 'COD') {
        final idempotencyKey = '${order.id}-${DateTime.now().microsecondsSinceEpoch}';
        try {
          await widget.paymentRepository.create(
            order.id,
            checkout.paymentMethod,
            idempotencyKey,
          );
        } on ApiException catch (e) {
          if (!mounted) return;
          await showDialog<void>(
            context: context,
            builder: (dialogContext) => AlertDialog(
              title: const PpeeText('Đơn hàng đã được tạo'),
              content: PpeeText(
                'Không thể khởi tạo thanh toán: ${e.message}\n\nBạn có thể kiểm tra trạng thái trong chi tiết đơn hàng.',
              ),
              actions: [
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext),
                  child: const PpeeText('Tiếp tục'),
                ),
              ],
            ),
          );
        } catch (_) {
          if (!mounted) return;
          await showDialog<void>(
            context: context,
            builder: (dialogContext) => AlertDialog(
              title: const PpeeText('Đơn hàng đã được tạo'),
              content: const PpeeText(
                'Không thể khởi tạo thanh toán. Vui lòng kiểm tra trạng thái trong chi tiết đơn hàng.',
              ),
              actions: [
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext),
                  child: const PpeeText('Tiếp tục'),
                ),
              ],
            ),
          );
        }
      }

      if (!mounted) return;
      Navigator.pushNamedAndRemoveUntil(
        context,
        '${AppRoutes.orderSuccess}/${order.id}',
        (route) => route.settings.name == AppRoutes.home,
      );
    } finally {
      checkout.setSubmitting(false);
    }
  }
}

class _CheckoutError extends StatelessWidget {
  const _CheckoutError({required this.message, required this.onRetry});
  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => PpeeStateView(
    title: 'Không thể tải thông tin thanh toán',
    message: message,
    icon: Icons.wifi_off_rounded,
    actionLabel: 'Thử lại',
    onAction: () { onRetry(); },
  );
}


String _paymentLabel(String method) => switch (method) {
  'COD' => 'Thanh toán khi nhận hàng (COD)',
  'BANK_TRANSFER' => 'Chuyển khoản ngân hàng',
  'VNPAY' => 'Ví điện tử VNPAY',
  'MOMO' => 'Ví điện tử MoMo',
  _ => method,
};

IconData _paymentIcon(String method) => switch (method) {
  'COD' => Icons.payments_outlined,
  'BANK_TRANSFER' => Icons.account_balance_outlined,
  'VNPAY' => Icons.qr_code_rounded,
  'MOMO' => Icons.account_balance_wallet_outlined,
  _ => Icons.payment_rounded,
};

class _CheckoutProgress extends StatelessWidget {
  const _CheckoutProgress();
  @override
  Widget build(BuildContext context) => Card(child: Padding(
    padding: const EdgeInsets.all(15),
    child: Wrap(spacing: 12, runSpacing: 10, alignment: WrapAlignment.spaceBetween,
      children: const [
        _CheckoutStep('1', 'Địa chỉ'),
        _CheckoutStep('2', 'Thanh toán'),
        _CheckoutStep('3', 'Xác nhận'),
      ],
    ),
  ));
}

class _CheckoutStep extends StatelessWidget {
  const _CheckoutStep(this.number, this.label);
  final String number;
  final String label;
  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
    CircleAvatar(radius: 14, backgroundColor: AppColors.primarySoft,
      child: PpeeText(number, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: AppColors.primary))),
    const SizedBox(width: 8),
    PpeeText(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
  ]);
}
