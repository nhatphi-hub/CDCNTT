import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';

import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/seller_ui.dart';

/// Public introduction for accounts without a shop.
class SellerLandingScreen extends StatelessWidget {
  const SellerLandingScreen({super.key});

  @override
  Widget build(BuildContext context) => SellerScaffold(
    child: SingleChildScrollView(
      child: Column(children: [
        _SellerHero(onStart: () => Navigator.pushNamed(context, AppRoutes.sellerSetup)),
        Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1180),
          child: Padding(padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 38),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const SellerPill(label: 'Dành cho đối tác bán hàng', icon: Icons.handshake_outlined),
              const SizedBox(height: 15),
              PpeeText('Một nơi để bắt đầu kinh doanh trực tuyến',
                style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 8),
              const PpeeText('PPee hướng tới sàn kết nối nhiều gian hàng. Tìm hiểu các công cụ dành cho người bán.',
                style: TextStyle(color: AppColors.gray700, fontSize: 14)),
              const SizedBox(height: 22),
              LayoutBuilder(builder: (context, c) {
                const benefits = [
                  _Benefit(Icons.storefront_outlined, 'Gian hàng riêng',
                    'Tạo hồ sơ thương hiệu và giới thiệu sản phẩm tới khách hàng.'),
                  _Benefit(Icons.inventory_2_outlined, 'Quản lý sản phẩm',
                    'Không gian quản lý danh sách hàng hóa và tình trạng tồn kho.'),
                  _Benefit(Icons.local_shipping_outlined, 'Theo dõi đơn bán',
                    'Xem và xác nhận phần đơn hàng thuộc gian hàng của bạn.'),
                  _Benefit(Icons.insights_outlined, 'Báo cáo kinh doanh',
                    'Xem số đơn và doanh thu thực tế của gian hàng.'),
                ];
                final count = c.maxWidth < 560 ? 1 : c.maxWidth < 940 ? 2 : 4;
                const gap = 14.0;
                final w = (c.maxWidth - (count - 1) * gap) / count;
                return Wrap(spacing: gap, runSpacing: gap, children: [
                  for (final b in benefits) SizedBox(width: w, child: _BenefitCard(benefit: b)),
                ]);
              }),
              const SizedBox(height: 40),
              PpeeText('Bắt đầu chỉ với 3 bước', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 8),
              const PpeeText('Đăng ký gian hàng được thiết kế thành luồng đơn giản, dễ thao tác.',
                style: TextStyle(color: AppColors.gray700)),
              const SizedBox(height: 18),
              LayoutBuilder(builder: (context, c) {
                final steps = [
                  _Step('01', 'Tạo tài khoản PPee', 'Sử dụng tài khoản hiện tại hoặc đăng ký tài khoản mới.',
                    Icons.person_add_alt_1_outlined),
                  _Step('02', 'Điền thông tin gian hàng', 'Đặt tên shop và chuẩn bị thông tin liên hệ.',
                    Icons.edit_note_outlined),
                  _Step('03', 'Kích hoạt bán hàng', 'Chờ quản trị viên phê duyệt trước khi bán hàng.',
                    Icons.verified_outlined),
                ];
                return c.maxWidth < 750
                  ? Column(children: [for (final s in steps) Padding(
                      padding: const EdgeInsets.only(bottom: 12), child: _StepCard(step: s))])
                  : Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      for (var i = 0; i < steps.length; i++) ...[
                        if (i > 0) const SizedBox(width: 14),
                        Expanded(child: _StepCard(step: steps[i])),
                      ],
                    ]);
              }),
              const SizedBox(height: 24),
              const SellerInfoNotice(message:
                'Đăng nhập và đăng ký gian hàng để bắt đầu. Gian hàng sẽ được kích hoạt sau khi quản trị viên phê duyệt.'),
              const SizedBox(height: 25),
              Center(child: Wrap(alignment: WrapAlignment.center, spacing: 12, runSpacing: 10,
                children: [
                  FilledButton.icon(
                    onPressed: () => Navigator.pushNamed(context, AppRoutes.sellerSetup),
                    icon: const Icon(Icons.arrow_forward_rounded),
                    label: const PpeeText('Thiết lập gian hàng'),
                  ),
                ])),
            ])))),
        const _SellerFooter(),
      ]),
    ),
  );
}

class _SellerHero extends StatelessWidget {
  const _SellerHero({required this.onStart});
  final VoidCallback onStart;
  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    decoration: const BoxDecoration(gradient: LinearGradient(
      begin: Alignment.topLeft, end: Alignment.bottomRight,
      colors: [Color(0xFF073885), AppColors.primary, Color(0xFF51A6FA)])),
    child: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 1180),
      child: Padding(padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 54),
        child: LayoutBuilder(builder: (context, c) {
          final wide = c.maxWidth >= 760;
          final intro = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Container(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: .16),
                borderRadius: BorderRadius.circular(28)),
              child: const PpeeText('PPee SELLER CENTER', style: TextStyle(
                color: Colors.white, fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 1.7))),
            const SizedBox(height: 20),
            PpeeText('Bắt đầu bán hàng\ncùng PPee',
              style: TextStyle(color: Colors.white,
                fontSize: wide ? 47 : 33, height: 1.12,
                letterSpacing: -1.1, fontWeight: FontWeight.w900)),
            const SizedBox(height: 15),
            const PpeeText('Tạo dấu ấn thương hiệu của bạn trên một sàn mua sắm đa dạng. '
              'Khám phá không gian dành riêng cho nhà bán hàng.',
              style: TextStyle(color: Color(0xFFE1EEFF), height: 1.65, fontSize: 15)),
            const SizedBox(height: 24),
            Wrap(spacing: 10, runSpacing: 10, children: [
              FilledButton.icon(onPressed: onStart,
                style: FilledButton.styleFrom(backgroundColor: Colors.white,
                  foregroundColor: AppColors.primaryDark),
                icon: const Icon(Icons.storefront_outlined),
                label: const PpeeText('Trở thành người bán')),
            ]),
          ]);
          return wide ? Row(children: [
            Expanded(flex: 6, child: intro), const SizedBox(width: 32),
            const Expanded(flex: 4, child: _StoreIllustration()),
          ]) : Column(children: [intro, const SizedBox(height: 28),
              const _StoreIllustration()]);
        }),
      ),
    )),
  );
}

class _StoreIllustration extends StatelessWidget {
  const _StoreIllustration();
  @override
  Widget build(BuildContext context) => Container(
    height: 278,
    decoration: BoxDecoration(color: Colors.white.withValues(alpha: .11),
      border: Border.all(color: Colors.white24), borderRadius: BorderRadius.circular(25)),
    child: Center(child: Container(
      margin: const EdgeInsets.all(22), padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(18)),
      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Row(children: [Container(width: 49, height: 49,
          decoration: BoxDecoration(color: AppColors.primarySoft, borderRadius: BorderRadius.circular(14)),
          child: const Icon(Icons.storefront_rounded, color: AppColors.primary, size: 29)),
          const SizedBox(width: 13), const Expanded(child: Column(
            crossAxisAlignment: CrossAxisAlignment.start, children: [
              PpeeText('Gian hàng của bạn', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
              SizedBox(height: 5), PpeeText('Seller Center · PPee',
                style: TextStyle(color: AppColors.gray700, fontSize: 12)),
            ])),
          const Icon(Icons.verified_rounded, color: AppColors.primary),
        ]),
        const SizedBox(height: 21),
        const Divider(),
        const SizedBox(height: 13),
        Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: const [
          _IllustrationFeature(Icons.inventory_2_outlined, 'Sản phẩm'),
          _IllustrationFeature(Icons.receipt_long_outlined, 'Đơn hàng'),
          _IllustrationFeature(Icons.bar_chart_rounded, 'Thống kê'),
        ]),
      ]),
    )),
  );
}

class _IllustrationFeature extends StatelessWidget {
  const _IllustrationFeature(this.icon, this.label);
  final IconData icon;
  final String label;
  @override
  Widget build(BuildContext context) => Column(children: [
    Container(padding: const EdgeInsets.all(13),
      decoration: BoxDecoration(color: AppColors.primarySoft,
        borderRadius: BorderRadius.circular(12)),
      child: Icon(icon, color: AppColors.primary)),
    const SizedBox(height: 8), PpeeText(label, style: const TextStyle(fontSize: 11,
      fontWeight: FontWeight.w700, color: AppColors.charcoal)),
  ]);
}

class _Benefit {
  const _Benefit(this.icon, this.title, this.description);
  final IconData icon;
  final String title;
  final String description;
}

class _BenefitCard extends StatelessWidget {
  const _BenefitCard({required this.benefit});
  final _Benefit benefit;
  @override
  Widget build(BuildContext context) => SellerPanel(child: SizedBox(height: 175,
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(
        color: AppColors.primarySoft, borderRadius: BorderRadius.circular(12)),
        child: Icon(benefit.icon, color: AppColors.primary)),
      const SizedBox(height: 16),
      PpeeText(benefit.title, style: const TextStyle(fontWeight: FontWeight.w800,
        color: AppColors.black, fontSize: 15)),
      const SizedBox(height: 7),
      PpeeText(benefit.description, maxLines: 3, overflow: TextOverflow.ellipsis,
        style: const TextStyle(color: AppColors.gray700, fontSize: 12, height: 1.5)),
    ]),
  ));
}

class _Step {
  const _Step(this.number, this.title, this.body, this.icon);
  final String number;
  final String title;
  final String body;
  final IconData icon;
}

class _StepCard extends StatelessWidget {
  const _StepCard({required this.step});
  final _Step step;
  @override
  Widget build(BuildContext context) => SellerPanel(child: Row(
    crossAxisAlignment: CrossAxisAlignment.start, children: [
      Container(width: 45, height: 45,
        decoration: BoxDecoration(color: AppColors.primarySoft,
          borderRadius: BorderRadius.circular(14)),
        child: Center(child: PpeeText(step.number, style: const TextStyle(color: AppColors.primary,
          fontWeight: FontWeight.w900, fontSize: 18)))),
      const SizedBox(width: 14),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        PpeeText(step.title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        PpeeText(step.body, style: const TextStyle(color: AppColors.gray700, height: 1.5,
          fontSize: 12)),
      ])),
    ],
  ));
}

class _SellerFooter extends StatelessWidget {
  const _SellerFooter();
  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity, color: const Color(0xFF0C2C5E),
    padding: const EdgeInsets.all(25),
    child: const Center(child: PpeeText('PPee Seller Center · Đăng ký gian hàng và quản lý sản phẩm',
      textAlign: TextAlign.center, style: TextStyle(color: Colors.white70, fontSize: 12))),
  );
}
