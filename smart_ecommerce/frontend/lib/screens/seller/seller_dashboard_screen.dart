import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../widgets/seller_ui.dart';
import 'seller_analytics_panel.dart';
import 'seller_coupons_panel.dart';
import 'seller_notifications_panel.dart';
import 'seller_orders_panel.dart';
import 'seller_overview_panel.dart';
import 'seller_products_panel.dart';
import 'seller_returns_panel.dart';
import 'seller_reviews_panel.dart';
import 'seller_shop_settings_panel.dart';

/// Seller Center shell. Navigation and business panels are unchanged.
class SellerDashboardScreen extends StatefulWidget {
  const SellerDashboardScreen({super.key, this.shopName});
  final String? shopName;

  @override
  State<SellerDashboardScreen> createState() => _SellerDashboardScreenState();
}

class _SellerDashboardScreenState extends State<SellerDashboardScreen> {
  int _selected = 0;

  static const _tabs = <_SellerTab>[
    _SellerTab('Tổng quan', Icons.space_dashboard_rounded,
      'Điều hành gian hàng của bạn'),
    _SellerTab('Sản phẩm', Icons.inventory_2_outlined,
      'Quản lý sản phẩm và tồn kho'),
    _SellerTab('Đơn bán', Icons.receipt_long_outlined,
      'Theo dõi và xử lý đơn hàng'),
    _SellerTab('Khuyến mãi', Icons.local_offer_outlined,
      'Các chương trình ưu đãi'),
    _SellerTab('Phân tích', Icons.bar_chart_outlined,
      'Thống kê hoạt động kinh doanh'),
    _SellerTab('Đánh giá', Icons.star_border_rounded,
      'Phản hồi của khách hàng'),
    _SellerTab('Thông báo', Icons.notifications_none_rounded,
      'Đơn hàng và đánh giá gần đây'),
    _SellerTab('Trả hàng', Icons.assignment_return_outlined,
      'Quản lý yêu cầu trả hàng'),
    _SellerTab('Thiết lập shop', Icons.tune_rounded,
      'Cập nhật thông tin gian hàng'),
  ];

  void _openTab(int i) => setState(() => _selected = i);

  @override
  Widget build(BuildContext context) => SellerScaffold(
    maxWidth: 1600,
    shopName: widget.shopName,
    onOrders: () => _openTab(2),
    onProducts: () => _openTab(1),
    child: LayoutBuilder(builder: (context, c) {
      final desktop = c.maxWidth >= 900;
      return Row(children: [
        if (desktop) ...[
          Container(
            width: 250,
            decoration: const BoxDecoration(color: Colors.white,
              border: Border(right: BorderSide(color: AppColors.gray300))),
            child: ListView(padding: const EdgeInsets.fromLTRB(13, 23, 13, 22),
              children: [
                _shopBadge(),
                const SizedBox(height: 24),
                _sectionLabel('KHÔNG GIAN LÀM VIỆC'),
                for (var i = 0; i < 5; i++) _navItem(i),
                const SizedBox(height: 19),
                _sectionLabel('CHĂM SÓC KHÁCH HÀNG'),
                for (var i = 5; i < 8; i++) _navItem(i),
                const SizedBox(height: 19),
                _sectionLabel('GIAN HÀNG'),
                _navItem(8),
                const SizedBox(height: 23),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.primarySoft,
                    borderRadius: BorderRadius.circular(15)),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Icon(Icons.tips_and_updates_outlined,
                      color: AppColors.primary, size: 22),
                    const SizedBox(height: 10),
                    const PpeeText('Mẹo vận hành', style: TextStyle(
                      fontWeight: FontWeight.w800, color: AppColors.black)),
                    const SizedBox(height: 5),
                    const PpeeText('Theo dõi đơn chờ xác nhận và sản phẩm sắp hết hàng ngay tại Tổng quan.',
                      style: TextStyle(fontSize: 11.5, height: 1.5,
                        color: AppColors.charcoal)),
                    const SizedBox(height: 10),
                    TextButton.icon(onPressed: () => _openTab(0),
                      icon: const Icon(Icons.arrow_forward, size: 16),
                      label: const PpeeText('Xem tổng quan')),
                  ]),
                ),
              ]),
          ),
        ],
        Expanded(child: SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(desktop ? 32 : 16, 27,
            desktop ? 32 : 16, 44),
          child: Center(child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 1190),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              _pageHeading(desktop),
              if (!desktop) ...[
                const SizedBox(height: 20),
                SingleChildScrollView(scrollDirection: Axis.horizontal,
                  child: Row(children: [
                    for (var i = 0; i < _tabs.length; i++)
                      Padding(padding: const EdgeInsets.only(right: 8),
                        child: ChoiceChip(
                          avatar: Icon(_tabs[i].icon, size: 17,
                            color: _selected == i ? AppColors.primary : AppColors.gray700),
                          label: PpeeText(_tabs[i].title),
                          selected: _selected == i,
                          onSelected: (_) => _openTab(i),
                        ),
                      ),
                  ])),
              ],
              const SizedBox(height: 25),
              if (_selected == 0)
                SellerOverviewPanel(
                  openOrders: () => _openTab(2),
                  openProducts: () => _openTab(1))
              else if (_selected == 1)
                const SellerProductsPanel()
              else if (_selected == 2)
                const SellerOrdersPanel()
              else if (_selected == 3)
                const SellerCouponsPanel()
              else if (_selected == 4)
                const SellerAnalyticsPanel()
              else if (_selected == 5)
                const SellerReviewsPanel()
              else if (_selected == 6)
                const SellerNotificationsPanel()
              else if (_selected == 7)
                const SellerReturnsPanel()
              else
                const SellerShopSettingsPanel(),
            ]),
          )),
        )),
      ]);
    }),
  );

  Widget _shopBadge() => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: AppColors.gray100,
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: AppColors.gray300)),
    child: Row(children: [
      Container(width: 43, height: 43,
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [AppColors.primary, AppColors.accent]),
          borderRadius: BorderRadius.circular(13)),
        child: const Icon(Icons.storefront_rounded, color: Colors.white, size: 24)),
      const SizedBox(width: 10),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        PpeeText((widget.shopName?.trim().isNotEmpty ?? false)
            ? widget.shopName! : 'Gian hàng PPee',
          maxLines: 1, overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 13, color: AppColors.black,
            fontWeight: FontWeight.w900)),
        const SizedBox(height: 3),
        const PpeeText('SELLER WORKSPACE', style: TextStyle(
          color: AppColors.gray700, fontWeight: FontWeight.w700,
          fontSize: 9, letterSpacing: .7)),
      ])),
    ]),
  );

  Widget _sectionLabel(String text) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 0, 8, 12),
    child: PpeeText(text, style: const TextStyle(fontSize: 10,
      color: AppColors.gray700, letterSpacing: 1.15,
      fontWeight: FontWeight.w900)),
  );

  Widget _pageHeading(bool desktop) => LayoutBuilder(
    builder: (context, box) => Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      alignment: WrapAlignment.spaceBetween,
      spacing: 12, runSpacing: 16, children: [
        SizedBox(width: box.maxWidth < 570 ? box.maxWidth : box.maxWidth * .66,
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const SellerPill(label: 'TRUNG TÂM NGƯỜI BÁN',
              icon: Icons.verified_outlined),
            const SizedBox(height: 13),
            PpeeText(_selected == 0
                ? ((widget.shopName?.trim().isNotEmpty ?? false)
                    ? widget.shopName! : 'Quản lý gian hàng PPee')
                : _tabs[_selected].title,
              maxLines: 2, overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 7),
            PpeeText(_selected == 0 ? 'Quản lý mọi hoạt động bán hàng tại một nơi.'
                : _tabs[_selected].subtitle,
              style: const TextStyle(fontSize: 13, color: AppColors.gray700)),
          ])),
        if (desktop)
          OutlinedButton.icon(
            onPressed: () => _openTab(8),
            icon: const Icon(Icons.settings_outlined, size: 18),
            label: const PpeeText('Thiết lập gian hàng'),
          ),
      ],
    ),
  );

  Widget _navItem(int i) {
    final selected = _selected == i;
    return Padding(padding: const EdgeInsets.only(bottom: 5),
      child: Material(
        color: selected ? AppColors.primarySoft : Colors.transparent,
        borderRadius: BorderRadius.circular(13),
        child: InkWell(
          borderRadius: BorderRadius.circular(13),
          onTap: () => _openTab(i),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
            child: Row(children: [
              Icon(_tabs[i].icon, size: 20,
                color: selected ? AppColors.primary : AppColors.gray700),
              const SizedBox(width: 12),
              Expanded(child: PpeeText(_tabs[i].title,
                style: TextStyle(fontWeight: selected
                    ? FontWeight.w900 : FontWeight.w600, fontSize: 13,
                  color: selected ? AppColors.primaryDark : AppColors.charcoal))),
              if (selected)
                Container(width: 6, height: 6,
                  decoration: const BoxDecoration(
                    color: AppColors.primary, shape: BoxShape.circle)),
            ]),
          ),
        ),
      ),
    );
  }
}

class _SellerTab {
  const _SellerTab(this.title, this.icon, this.subtitle);
  final String title;
  final IconData icon;
  final String subtitle;
}
