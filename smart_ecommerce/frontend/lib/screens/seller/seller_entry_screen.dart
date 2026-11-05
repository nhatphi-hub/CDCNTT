import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/routes/app_routes.dart';
import '../../providers/auth_provider.dart';
import '../../repositories/seller_shop_repository.dart';
import '../../widgets/seller_ui.dart';
import 'seller_dashboard_screen.dart';
import 'seller_landing_screen.dart';

/// Resolve seller entry from the actual authenticated shop, not from route arguments.
class SellerEntryScreen extends StatelessWidget {
  const SellerEntryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    if (!auth.isAuthenticated) return const SellerLandingScreen();
    return _SellerShopGate(key: ValueKey(auth.user!.id));
  }
}

class _SellerShopGate extends StatefulWidget {
  const _SellerShopGate({super.key});

  @override
  State<_SellerShopGate> createState() => _SellerShopGateState();
}

class _SellerShopGateState extends State<_SellerShopGate> {
  late Future<Map<String, dynamic>> _shopFuture;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  void _reload() {
    _shopFuture = context.read<SellerShopRepository>().mine();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Map<String, dynamic>>(
      future: _shopFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const SellerScaffold(
            child: Center(child: CircularProgressIndicator()),
          );
        }
        if (snapshot.hasError) {
          final error = snapshot.error;
          if (error is ApiException && error.statusCode == 404) {
            return const SellerLandingScreen();
          }
          return _message(
            error is ApiException ? error.message : 'Không thể tải thông tin gian hàng.',
            retry: true,
          );
        }
        final shop = snapshot.data!;
        final status = (shop['status'] ?? '').toString().toUpperCase();
        final roles = context.read<AuthProvider>().user?.roles ?? <String>{};
        if (status == 'ACTIVE' && roles.contains('SELLER')) {
          return SellerDashboardScreen(shopName: shop['name']?.toString());
        }
        if (status == 'ACTIVE') {
          return _message('Gian hàng đã được duyệt. Nhấn làm mới quyền để vào kênh người bán.', refreshRoles: true);
        }
        if (status == 'PENDING') {
          return _message('Gian hàng đang chờ quản trị viên phê duyệt. Bạn có thể cập nhật thông tin gian hàng trong thời gian chờ.', showSetup: true);
        }
        return _message('Trạng thái gian hàng: $status. Chức năng bán hàng hiện chưa được mở.', showSetup: true);
      },
    );
  }

  Widget _message(String message, {bool retry = false, bool showSetup = false, bool refreshRoles = false}) {
    return SellerScaffold(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.storefront_outlined, size: 46),
              const SizedBox(height: 16),
              PpeeText(message, textAlign: TextAlign.center),
              const SizedBox(height: 20),
              if (refreshRoles) FilledButton(
                onPressed: () async {
                  final auth = context.read<AuthProvider>();
                  final success = await auth.refreshCurrentUser();
                  if (!mounted) return;
                  if (success) setState(_reload);
                },
                child: const PpeeText('Làm mới quyền tài khoản'),
              ),
              if (retry) FilledButton(
                onPressed: () => setState(_reload),
                child: const PpeeText('Thử lại'),
              ),
              if (showSetup) FilledButton(
                onPressed: () => Navigator.pushNamed(context, AppRoutes.sellerSetup),
                child: const PpeeText('Thông tin gian hàng'),
              ),
              TextButton(
                onPressed: () => Navigator.pushNamedAndRemoveUntil(context, AppRoutes.home, (_) => false),
                child: const PpeeText('Về PPee'),
              ),
            ]),
          ),
        ),
      ),
    );
  }
}
