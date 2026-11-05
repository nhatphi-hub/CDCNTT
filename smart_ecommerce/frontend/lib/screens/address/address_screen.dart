import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_colors.dart';
import '../../models/address.dart';
import '../../providers/address_provider.dart';
import '../../widgets/catalog_header.dart';
import '../../widgets/ppee_ui.dart';

class AddressScreen extends StatefulWidget {
  const AddressScreen({super.key, this.selectionMode = false});
  final bool selectionMode;

  @override
  State<AddressScreen> createState() => _AddressScreenState();
}

class _AddressScreenState extends State<AddressScreen> {
  @override
  void initState() {
    super.initState();
    final provider = context.read<AddressProvider>();
    Future.microtask(provider.load);
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AddressProvider>();
    return Scaffold(
      appBar: const CatalogHeader(),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: provider.loading ? null : () => _showAddressForm(context, provider),
        icon: const Icon(Icons.add_location_alt_outlined),
        label: const PpeeText('Thêm địa chỉ'),
      ),
      body: _buildBody(context, provider),
    );
  }

  Widget _buildBody(BuildContext context, AddressProvider provider) {
    if (provider.loading && provider.addresses.isEmpty) {
      return const PpeeLoadingView(message: 'Đang tải sổ địa chỉ...');
    }
    if (provider.error != null && provider.addresses.isEmpty) {
      return PpeeStateView(
        title: 'Không tải được địa chỉ', message: provider.error!,
        icon: Icons.wifi_off_rounded, actionLabel: 'Thử lại', onAction: provider.load,
      );
    }
    if (provider.addresses.isEmpty) {
      return PpeeStateView(
        title: 'Chưa có địa chỉ giao hàng',
        message: 'Thêm địa chỉ đầu tiên để thanh toán thuận tiện và chính xác hơn.',
        icon: Icons.location_on_outlined,
        actionLabel: 'Thêm địa chỉ', onAction: () => _showAddressForm(context, provider),
      );
    }
    return Center(child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 1080),
      child: LayoutBuilder(builder: (context, constraints) {
        final columns = constraints.maxWidth >= 750 ? 2 : 1;
        const gap = 16.0;
        final cardWidth = (constraints.maxWidth - 40 - (columns - 1) * gap) / columns;
        return ListView(padding: const EdgeInsets.fromLTRB(20, 26, 20, 110), children: [
          PpeeText(widget.selectionMode ? 'Chọn địa chỉ giao hàng' : 'Sổ địa chỉ',
            style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 7),
          PpeeText('Thông tin nhận hàng được lưu trong tài khoản PPee của bạn.',
            style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 24),
          if (provider.error != null) ...[
            PpeeNotice(message: provider.error!, isError: true),
            const SizedBox(height: 16),
          ],
          Wrap(spacing: gap, runSpacing: gap, children: [
            for (final address in provider.addresses)
              SizedBox(width: cardWidth, child: _addressCard(context, provider, address)),
          ]),
        ]);
      }),
    ));
  }

  Widget _addressCard(BuildContext context, AddressProvider provider, AddressModel address) =>
      PpeeSurface(padding: const EdgeInsets.all(20), child: InkWell(
        onTap: widget.selectionMode ? () => Navigator.pop(context, address) : null,
        borderRadius: BorderRadius.circular(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Container(width: 42, height: 42,
              decoration: const BoxDecoration(color: AppColors.primarySoft, shape: BoxShape.circle),
              child: Icon(address.isDefault ? Icons.home_rounded : Icons.location_on_outlined,
                color: AppColors.primary, size: 22)),
            const SizedBox(width: 11),
            Expanded(child: PpeeText(address.recipientName,
              style: Theme.of(context).textTheme.titleMedium)),
            if (address.isDefault) Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(color: AppColors.successSoft,
                borderRadius: BorderRadius.circular(8)),
              child: const PpeeText('Mặc định', style: TextStyle(color: AppColors.success,
                fontSize: 11, fontWeight: FontWeight.w800))),
            if (!widget.selectionMode) PopupMenuButton<String>(
              tooltip: tr(context, 'Tùy chọn địa chỉ'), enabled: !provider.loading,
              onSelected: (value) async {
                if (value == 'default') {
                  await provider.setDefault(address.id);
                } else if (value == 'edit' && context.mounted) {
                  await _showAddressForm(context, provider, address);
                } else if (value == 'delete') {
                  await provider.remove(address.id);
                }
              },
              itemBuilder: (_) => [
                if (!address.isDefault) const PopupMenuItem(
                  value: 'default', child: PpeeText('Đặt làm mặc định')),
                const PopupMenuItem(value: 'edit', child: PpeeText('Chỉnh sửa')),
                const PopupMenuItem(value: 'delete', child: PpeeText('Xóa địa chỉ')),
              ],
            ),
          ]),
          const SizedBox(height: 16),
          Row(children: [
            const Icon(Icons.call_outlined, color: AppColors.gray700, size: 18),
            const SizedBox(width: 9),
            Expanded(child: PpeeText(address.phone,
              style: const TextStyle(fontWeight: FontWeight.w600))),
          ]),
          const SizedBox(height: 10),
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Icon(Icons.place_outlined, color: AppColors.gray700, size: 18),
            const SizedBox(width: 9),
            Expanded(child: PpeeText(address.fullAddress,
              style: const TextStyle(color: AppColors.charcoal, height: 1.5))),
          ]),
          if (widget.selectionMode) ...[
            const SizedBox(height: 17),
            const Row(mainAxisAlignment: MainAxisAlignment.end, children: [
              PpeeText('Chọn địa chỉ này', style: TextStyle(color: AppColors.primary,
                fontWeight: FontWeight.w700)),
              SizedBox(width: 6),
              Icon(Icons.arrow_forward_rounded, size: 17, color: AppColors.primary),
            ]),
          ],
        ]),
      ));

  Future<void> _showAddressForm(BuildContext context, AddressProvider provider, [AddressModel? address]) async {
    final formKey = GlobalKey<FormState>();
    final recipientController = TextEditingController(text: address?.recipientName);
    final phoneController = TextEditingController(text: address?.phone);
    final provinceController = TextEditingController(text: address?.province);
    final districtController = TextEditingController(text: address?.district);
    final wardController = TextEditingController(text: address?.ward);
    final detailController = TextEditingController(text: address?.addressDetail);
    var isDefault = address?.isDefault ?? false;
    var saving = false;

    String? requiredText(String? value) => value == null || value.trim().isEmpty
        ? 'Vui lòng nhập thông tin này' : null;

    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(builder: (dialogContext, setDialogState) => AlertDialog(
        title: PpeeText(address == null ? 'Thêm địa chỉ nhận hàng' : 'Chỉnh sửa địa chỉ'),
        content: SizedBox(
          width: (MediaQuery.sizeOf(dialogContext).width - 86).clamp(240.0, 630.0).toDouble(),
          child: SingleChildScrollView(child: Form(
            key: formKey,
            child: Column(crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min, children: [
                const PpeeNotice(message: 'Hãy nhập chính xác thông tin để quá trình giao hàng thuận lợi.'),
                const SizedBox(height: 22),
                PpeeFormGrid(children: [
                  PpeeFormField(label: 'Người nhận', requiredField: true,
                    prefixIcon: Icons.person_outline_rounded,
                    hint: 'Họ và tên người nhận', controller: recipientController,
                    validator: requiredText),
                  PpeeFormField(label: 'Số điện thoại', requiredField: true,
                    prefixIcon: Icons.call_outlined, hint: 'Số điện thoại liên hệ',
                    keyboardType: TextInputType.phone, controller: phoneController,
                    validator: requiredText),
                  PpeeFormField(label: 'Tỉnh / Thành phố', requiredField: true,
                    controller: provinceController, hint: 'Tỉnh hoặc thành phố',
                    validator: requiredText),
                  PpeeFormField(label: 'Quận / Huyện', requiredField: true,
                    controller: districtController, hint: 'Quận hoặc huyện',
                    validator: requiredText),
                  PpeeFormField(label: 'Phường / Xã', requiredField: true,
                    controller: wardController, hint: 'Phường hoặc xã',
                    validator: requiredText),
                  PpeeFormField(label: 'Địa chỉ chi tiết', requiredField: true,
                    controller: detailController, hint: 'Số nhà, tên đường, tòa nhà...',
                    validator: requiredText),
                ]),
                const SizedBox(height: 16),
                CheckboxListTile(
                  value: isDefault,
                  contentPadding: EdgeInsets.zero,
                  controlAffinity: ListTileControlAffinity.leading,
                  onChanged: saving ? null : (value) => setDialogState(() => isDefault = value ?? false),
                  title: const PpeeText('Đặt làm địa chỉ mặc định',
                    style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5)),
                ),
              ]),
          )),
        ),
        actionsPadding: const EdgeInsets.fromLTRB(20, 4, 20, 18),
        actions: [
          TextButton(onPressed: saving ? null : () => Navigator.pop(dialogContext), child: const PpeeText('Hủy')),
          FilledButton.icon(
            onPressed: saving ? null : () async {
              if (!(formKey.currentState?.validate() ?? false)) return;
              setDialogState(() => saving = true);
              final success = await provider.save(AddressDraft(
                recipientName: recipientController.text.trim(),
                phone: phoneController.text.trim(),
                province: provinceController.text.trim(),
                district: districtController.text.trim(),
                ward: wardController.text.trim(),
                addressDetail: detailController.text.trim(),
                isDefault: isDefault,
              ), id: address?.id);
              if (success && dialogContext.mounted) {
                Navigator.pop(dialogContext);
              } else if (dialogContext.mounted) {
                setDialogState(() => saving = false);
              }
            },
            icon: saving
                ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(
                    strokeWidth: 2, color: Colors.white))
                : const Icon(Icons.save_outlined, size: 18),
            label: PpeeText(saving ? 'Đang lưu...' : 'Lưu địa chỉ'),
          ),
        ],
      )),
    );

    for (final controller in [recipientController, phoneController, provinceController,
      districtController, wardController, detailController]) {
      controller.dispose();
    }
  }
}
