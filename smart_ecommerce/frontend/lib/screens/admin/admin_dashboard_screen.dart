import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/ppee_ui.dart';
import '../../providers/auth_provider.dart';
import '../../repositories/admin_console_repository.dart';
import 'admin_categories_panel.dart';
import 'admin_order_detail_screen.dart';

/// Dashboard nội bộ: không có nút sửa trạng thái thanh toán thủ công.
class AdminDashboardScreen extends StatelessWidget {
  const AdminDashboardScreen({super.key});

  Future<void> _logout(BuildContext context) async {
    final auth = context.read<AuthProvider>();
    await auth.logout();
    if (!context.mounted) return;
    Navigator.of(
      context,
    ).pushNamedAndRemoveUntil(AppRoutes.login, (_) => false);
  }

  @override
  Widget build(BuildContext context) => DefaultTabController(
    length: 4,
    child: Scaffold(
      appBar: AppBar(
        backgroundColor: AppColors.primaryDeep,
        foregroundColor: Colors.white,
        title: PpeeText(
          MediaQuery.sizeOf(context).width < 560
              ? 'PPee  •  ADMIN'
              : 'PPee  •  Trung tâm ADMIN',
          style: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.w800,
          ),
        ),
        actions: [
          if (MediaQuery.sizeOf(context).width < 620)
            IconButton(
              tooltip: tr(context, 'Quản lý hậu mãi'),
              onPressed: () =>
                  Navigator.pushNamed(context, AppRoutes.adminReturns),
              icon: const Icon(
                Icons.assignment_return_outlined,
                color: Colors.white,
              ),
            )
          else
            TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: Colors.white),
              onPressed: () =>
                  Navigator.pushNamed(context, AppRoutes.adminReturns),
              icon: const Icon(Icons.assignment_return_outlined),
              label: const PpeeText('Hậu mãi'),
            ),
          IconButton(
            tooltip: tr(context, 'Đăng xuất'),
            onPressed: () => _logout(context),
            icon: const Icon(Icons.logout, color: Colors.white),
          ),
        ],
        toolbarHeight: 71,
        flexibleSpace: const DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                AppColors.primaryDeep,
                AppColors.primaryDark,
                AppColors.primary,
              ],
            ),
          ),
        ),
        bottom: const TabBar(
          labelColor: Colors.white,
          unselectedLabelColor: Color(0xFFD9E9FF),
          indicatorColor: Colors.white,
          indicatorWeight: 3,
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          tabs: [
            Tab(icon: Icon(Icons.storefront_outlined), text: 'Gian hàng'),
            Tab(icon: Icon(Icons.category_outlined), text: 'Loại sản phẩm'),
            Tab(icon: Icon(Icons.receipt_long_outlined), text: 'Đơn hàng'),
            Tab(
              icon: Icon(Icons.account_balance_wallet_outlined),
              text: 'Thanh toán',
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 15),
            decoration: const BoxDecoration(
              color: Colors.white,
              border: Border(bottom: BorderSide(color: AppColors.gray300)),
            ),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1100),
                child: Row(
                  children: [
                    Container(
                      width: 43,
                      height: 43,
                      decoration: BoxDecoration(
                        color: AppColors.primarySoft,
                        borderRadius: BorderRadius.circular(13),
                      ),
                      child: const Icon(
                        Icons.admin_panel_settings_rounded,
                        color: AppColors.primary,
                      ),
                    ),
                    const SizedBox(width: 13),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          PpeeText(
                            'Bảng điều hành PPee',
                            style: TextStyle(
                              fontSize: 17,
                              fontWeight: FontWeight.w900,
                              color: AppColors.black,
                            ),
                          ),
                          PpeeText(
                            'Quản lý gian hàng, loại sản phẩm, đơn hàng và thanh toán trên dữ liệu thực.',
                            maxLines: 2,
                            style: TextStyle(
                              fontSize: 12,
                              color: AppColors.gray700,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const Icon(
                      Icons.verified_user_outlined,
                      color: AppColors.success,
                    ),
                  ],
                ),
              ),
            ),
          ),
          const Expanded(
            child: TabBarView(
              children: [
                _AdminListPanel(resource: 'shops'),
                AdminCategoriesPanel(),
                _AdminListPanel(resource: 'orders'),
                _AdminListPanel(resource: 'payments'),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _AdminListPanel extends StatefulWidget {
  const _AdminListPanel({required this.resource});
  final String resource;

  @override
  State<_AdminListPanel> createState() => _AdminListPanelState();
}

class _AdminListPanelState extends State<_AdminListPanel> {
  late Future<AdminPage> _future;
  String _status = '';
  int _page = 0;
  bool _busy = false;

  List<String> get _statuses => switch (widget.resource) {
    'shops' => const ['PENDING', 'ACTIVE', 'SUSPENDED', 'REJECTED'],
    'orders' => const [
      'PENDING',
      'CONFIRMED',
      'SHIPPING',
      'DELIVERED',
      'CANCELLED',
    ],
    _ => const ['PENDING', 'PAID', 'FAILED', 'EXPIRED', 'CANCELLED'],
  };

  String get _title => switch (widget.resource) {
    'shops' => 'Quản lý gian hàng',
    'orders' => 'Quản lý và theo dõi đơn hàng',
    _ => 'Giám sát thanh toán',
  };

  String get _help => switch (widget.resource) {
    'shops' =>
      'Duyệt đăng ký, đình chỉ và kích hoạt shop. Backend cấp hoặc thu hồi quyền SELLER.',
    'orders' =>
      'Xem đơn hàng toàn hệ thống; xử lý vận chuyển mô phỏng ở trang chi tiết.',
    _ =>
      'Chỉ xem dữ liệu thanh toán thật trong PostgreSQL. Không đánh dấu PAID thủ công.',
  };

  @override
  void initState() {
    super.initState();
    _future = context.read<AdminConsoleRepository>().list(widget.resource);
  }

  void _refresh({String? status, int? page}) {
    final nextStatus = status ?? _status;
    final nextPage = page ?? _page;
    final future = context.read<AdminConsoleRepository>().list(
      widget.resource,
      status: nextStatus,
      page: nextPage,
    );
    setState(() {
      _status = nextStatus;
      _page = nextPage;
      _future = future;
    });
  }

  String _error(Object e) =>
      e is ApiException ? e.message : 'Không tải được dữ liệu: $e';

  Future<void> _changeShop(Map<String, dynamic> shop, String next) async {
    if (_busy) return;
    final id = '${shop['id']}';
    final confirm = await showDialog<bool>(
      context: context,
      builder: (dialog) => AlertDialog(
        title: PpeeText('Xác nhận chuyển thành $next?'),
        content: PpeeText(
          'Gian hàng: ${shop['name']}\n\n'
          'Thao tác này thay đổi quyền SELLER và trạng thái shop trên máy chủ.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialog, false),
            child: const PpeeText('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialog, true),
            child: const PpeeText('Xác nhận'),
          ),
        ],
      ),
    );
    if (confirm != true || !mounted) return;
    final repo = context.read<AdminConsoleRepository>();
    setState(() {
      _busy = true;
    });
    try {
      await repo.setShopStatus(id, next);
      if (!mounted) return;
      _refresh();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: PpeeText('Đã cập nhật ${shop['name']}: $next')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: PpeeText(_error(e))));
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
        });
      }
    }
  }

  Widget _statusTag(String status) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
    decoration: BoxDecoration(
      color: status == 'PAID' || status == 'ACTIVE' || status == 'DELIVERED'
          ? const Color(0xFFE5F5ED)
          : const Color(0xFFEAF2FF),
      borderRadius: BorderRadius.circular(8),
    ),
    child: PpeeText(
      status,
      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
    ),
  );

  Widget _identityHeader(IconData icon, String title, String status) => Row(
    children: [
      Container(
        width: 45,
        height: 45,
        decoration: BoxDecoration(
          color: AppColors.primarySoft,
          borderRadius: BorderRadius.circular(13),
        ),
        child: Icon(icon, color: AppColors.primary, size: 24),
      ),
      const SizedBox(width: 13),
      Expanded(
        child: PpeeText(
          title,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w900,
            color: AppColors.black,
          ),
        ),
      ),
      _statusTag(status),
    ],
  );

  Widget _dataLine(IconData icon, String text) => Padding(
    padding: const EdgeInsets.only(top: 9),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 16, color: AppColors.gray700),
        const SizedBox(width: 9),
        Expanded(
          child: PpeeText(
            text,
            style: const TextStyle(
              color: AppColors.charcoal,
              height: 1.4,
              fontSize: 12.5,
            ),
          ),
        ),
      ],
    ),
  );

  Widget _shopCard(Map<String, dynamic> item) {
    final status = '${item['status']}';
    final actions = <Widget>[];
    if (status == 'PENDING') {
      actions.add(
        OutlinedButton(
          onPressed: _busy ? null : () => _changeShop(item, 'REJECTED'),
          child: const PpeeText('Từ chối'),
        ),
      );
      actions.add(
        FilledButton(
          onPressed: _busy ? null : () => _changeShop(item, 'ACTIVE'),
          child: const PpeeText('Duyệt shop'),
        ),
      );
    } else if (status == 'ACTIVE') {
      actions.add(
        OutlinedButton(
          onPressed: _busy ? null : () => _changeShop(item, 'SUSPENDED'),
          child: const PpeeText('Đình chỉ'),
        ),
      );
    } else {
      actions.add(
        FilledButton.tonal(
          onPressed: _busy ? null : () => _changeShop(item, 'ACTIVE'),
          child: const PpeeText('Kích hoạt'),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: PpeeSurface(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _identityHeader(
              Icons.storefront_outlined,
              '${item['name']}',
              status,
            ),
            const SizedBox(height: 10),
            _dataLine(
              Icons.category_outlined,
              'Ngành hàng: ${item['primaryCategory'] ?? '—'}',
            ),
            _dataLine(
              Icons.person_outline_rounded,
              'Liên hệ: ${item['contactName'] ?? ''} · ${item['contactPhone'] ?? ''}',
            ),
            _dataLine(
              Icons.mail_outline_rounded,
              'Email: ${item['contactEmail'] ?? ''}',
            ),
            const Divider(height: 27),
            Wrap(spacing: 9, runSpacing: 9, children: actions),
          ],
        ),
      ),
    );
  }

  Widget _orderCard(Map<String, dynamic> item) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: PpeeSurface(
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _identityHeader(
            Icons.receipt_long_outlined,
            '${item['orderCode']}',
            '${item['status']}',
          ),
          const SizedBox(height: 11),
          _dataLine(
            Icons.payments_outlined,
            'Thanh toán: ${item['paymentStatus']} · ${item['paymentMethod']}',
          ),
          _dataLine(
            Icons.account_balance_wallet_outlined,
            'Giá trị đơn: ${item['totalAmount']} VND',
          ),
          const Divider(height: 27),
          OutlinedButton.icon(
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) =>
                    AdminOrderDetailScreen(orderId: '${item['id']}'),
              ),
            ),
            icon: const Icon(Icons.open_in_new_rounded),
            label: const PpeeText('Chi tiết và vận chuyển'),
          ),
        ],
      ),
    ),
  );

  Widget _paymentCard(Map<String, dynamic> item) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: PpeeSurface(
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _identityHeader(
            Icons.account_balance_wallet_outlined,
            '${item['orderCode']}',
            '${item['status']}',
          ),
          const SizedBox(height: 11),
          _dataLine(
            Icons.payments_outlined,
            '${item['paymentMethod']} · ${item['provider']} · ${item['amount']} ${item['currency']}',
          ),
          _dataLine(
            Icons.tag_rounded,
            'Provider TXN: ${item['providerTransactionId'] ?? 'Chưa ghi nhận'}',
          ),
          _dataLine(Icons.schedule_outlined, 'Tạo lúc: ${item['createdAt']}'),
          if (item['failureCode'] != null)
            _dataLine(
              Icons.error_outline_rounded,
              'Lỗi: ${item['failureCode']}',
            ),
        ],
      ),
    ),
  );

  @override
  Widget build(BuildContext context) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 1100),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            PpeeSurface(
              padding: const EdgeInsets.all(17),
              child: Wrap(
                spacing: 12,
                runSpacing: 10,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: AppColors.primarySoft,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      widget.resource == 'shops'
                          ? Icons.storefront_outlined
                          : widget.resource == 'orders'
                          ? Icons.receipt_long_outlined
                          : Icons.account_balance_wallet_outlined,
                      color: AppColors.primary,
                    ),
                  ),
                  PpeeText(
                    _title,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  SizedBox(
                    width: 238,
                    child: DropdownButtonFormField<String>(
                      initialValue: _status,
                      isExpanded: true,
                      decoration: InputDecoration(
                        labelText: tr(context, 'Lọc theo trạng thái'),
                        prefixIcon: Icon(Icons.tune_rounded),
                        contentPadding: EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 12,
                        ),
                      ),
                      items: ['', ..._statuses]
                          .map(
                            (s) => DropdownMenuItem<String>(
                              value: s,
                              child: PpeeText(
                                s.isEmpty ? 'Tất cả' : s,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          )
                          .toList(),
                      onChanged: _busy
                          ? null
                          : (s) => _refresh(status: s ?? '', page: 0),
                    ),
                  ),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _refresh,
                    icon: const Icon(Icons.refresh),
                    label: const PpeeText('Làm mới'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),
            PpeeText(
              _help,
              style: const TextStyle(fontSize: 12, color: Color(0xFF56647A)),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: FutureBuilder<AdminPage>(
                future: _future,
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  if (snapshot.hasError) {
                    return PpeeStateView(
                      title: 'Không thể tải dữ liệu',
                      message: _error(snapshot.error!),
                      icon: Icons.wifi_off_rounded,
                      actionLabel: 'Thử lại',
                      onAction: _refresh,
                    );
                  }
                  final data = snapshot.data!;
                  return Column(
                    children: [
                      Expanded(
                        child: data.items.isEmpty
                            ? const PpeeStateView(
                                title: 'Chưa có kết quả',
                                message:
                                    'Không có dữ liệu phù hợp với bộ lọc hiện tại.',
                                icon: Icons.filter_alt_off_outlined,
                              )
                            : ListView.builder(
                                itemCount: data.items.length,
                                itemBuilder: (context, index) {
                                  final item = data.items[index];
                                  return switch (widget.resource) {
                                    'shops' => _shopCard(item),
                                    'orders' => _orderCard(item),
                                    _ => _paymentCard(item),
                                  };
                                },
                              ),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: Wrap(
                          spacing: 12,
                          runSpacing: 6,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            PpeeText(
                              'Tổng ${data.totalElements} · Trang ${data.page + 1}/${data.totalPages == 0 ? 1 : data.totalPages}',
                            ),
                            OutlinedButton(
                              onPressed: _page > 0 && !_busy
                                  ? () => _refresh(page: _page - 1)
                                  : null,
                              child: const PpeeText('Trước'),
                            ),
                            OutlinedButton(
                              onPressed: _page + 1 < data.totalPages && !_busy
                                  ? () => _refresh(page: _page + 1)
                                  : null,
                              child: const PpeeText('Sau'),
                            ),
                          ],
                        ),
                      ),
                    ],
                  );
                },
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
