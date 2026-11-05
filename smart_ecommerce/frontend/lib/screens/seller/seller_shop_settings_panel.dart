import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/routes/app_routes.dart';
import '../../repositories/seller_shop_repository.dart';
import '../../widgets/seller_ui.dart';
import '../../widgets/ppee_ui.dart';

/// Shop profile is read from PostgreSQL through /api/seller/shop.
class SellerShopSettingsPanel extends StatefulWidget {
  const SellerShopSettingsPanel({super.key});

  @override
  State<SellerShopSettingsPanel> createState() => _SellerShopSettingsPanelState();
}

class _SellerShopSettingsPanelState extends State<SellerShopSettingsPanel> {
  late Future<Map<String, dynamic>> _future;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  void _reload() => _future = context.read<SellerShopRepository>().mine();

  Future<void> _edit() async {
    await Navigator.pushNamed(context, AppRoutes.sellerSetup);
    if (mounted) setState(_reload);
  }

  Widget _row(String label, dynamic value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 12),
    child: LayoutBuilder(builder: (context, box) {
      final horizontal = box.maxWidth >= 500;
      if (!horizontal) {
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          PpeeText(label, style: const TextStyle(color: Colors.blueGrey, fontSize: 12)),
          const SizedBox(height: 5), PpeeText('$value'),
        ]);
      }
      return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        SizedBox(width: 195, child: PpeeText(label, style: const TextStyle(color: Colors.blueGrey))),
        Expanded(child: PpeeText('$value', style: const TextStyle(fontWeight: FontWeight.w600))),
      ]);
    }),
  );

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Row(children: [
        Expanded(child: PpeeText('Thông tin gian hàng', style: Theme.of(context).textTheme.titleLarge)),
        IconButton(tooltip: tr(context, 'Làm mới'), onPressed: () => setState(_reload),
          icon: const Icon(Icons.refresh)),
      ]),
      const SizedBox(height: 12),
      FutureBuilder<Map<String, dynamic>>(future: _future, builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const PpeeLoadingView(message: 'Đang tải thông tin gian hàng...');
        }
        if (snapshot.hasError) {
          final e = snapshot.error;
          return SellerPanel(child: Column(children: [
            PpeeNotice(message: e is ApiException ? e.message : 'Không tải được gian hàng', isError: true),
            TextButton(onPressed: () => setState(_reload), child: const PpeeText('Thử lại')),
          ]));
        }
        final shop = snapshot.data!;
        return SellerPanel(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Wrap(spacing: 12, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
            PpeeText('${shop['name']}', style: Theme.of(context).textTheme.titleLarge),
            Chip(label: PpeeText('${shop['status']}')),
          ]),
          const SizedBox(height: 12),
          _row('Ngành hàng', shop['primaryCategory'] ?? '—'),
          _row('Giới thiệu', shop['description'] ?? '—'),
          const Divider(),
          _row('Người liên hệ', shop['contactName'] ?? '—'),
          _row('Điện thoại', shop['contactPhone'] ?? '—'),
          _row('Email', shop['contactEmail'] ?? '—'),
          _row('Địa chỉ lấy hàng', shop['pickupAddress'] ?? '—'),
          const SizedBox(height: 16),
          FilledButton.icon(onPressed: _edit, icon: const Icon(Icons.edit_outlined),
            label: const PpeeText('Chỉnh sửa thông tin')),
        ]));
      }),
    ],
  );
}
