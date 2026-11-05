import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';

import '../core/constants/app_constants.dart';
import '../core/theme/app_colors.dart';

class AuthShell extends StatelessWidget {
  const AuthShell({super.key, required this.title, required this.subtitle, required this.child});
  final String title;
  final String subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: LayoutBuilder(builder: (context, layout) {
      final desktop = layout.maxWidth >= 900 && layout.maxHeight >= 640;
      final form = Center(child: SingleChildScrollView(
        padding: EdgeInsets.symmetric(horizontal: desktop ? 34 : 18, vertical: 28),
        child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 475), child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Row(children: [
              Container(width: 38, height: 38,
                decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(12)),
                child: const Icon(Icons.shopping_bag_rounded, color: Colors.white, size: 21)),
              const SizedBox(width: 10),
              PpeeText(AppConstants.appName, style: const TextStyle(fontSize: 17, letterSpacing: .5, fontWeight: FontWeight.w800)),
            ]),
            const SizedBox(height: 25),
            Card(child: Padding(
              padding: EdgeInsets.all(layout.maxWidth < 480 ? 22 : 35),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Container(alignment: Alignment.centerLeft, child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
                  decoration: BoxDecoration(color: AppColors.primarySoft, borderRadius: BorderRadius.circular(10)),
                  child: const PpeeText('CHÀO MỪNG BẠN', style: TextStyle(fontSize: 10, letterSpacing: 1,
                    color: AppColors.primary, fontWeight: FontWeight.w800)))),
                const SizedBox(height: 17),
                PpeeText(title, style: Theme.of(context).textTheme.headlineMedium),
                const SizedBox(height: 9),
                PpeeText(subtitle, style: const TextStyle(color: AppColors.gray700, height: 1.5)),
                const SizedBox(height: 28),
                child,
              ]),
            )),
            const SizedBox(height: 19),
            const PpeeText('© PPee · Smart E-Commerce',
              textAlign: TextAlign.center, style: TextStyle(color: AppColors.gray500, fontSize: 12)),
          ]),
        ),
      ));
      if (!desktop) return form;
      return Row(children: [
        Expanded(flex: 10, child: Container(
          padding: const EdgeInsets.all(58),
          decoration: const BoxDecoration(gradient: LinearGradient(
            begin: Alignment.topLeft, end: Alignment.bottomRight,
            colors: [Color(0xFF092D75), Color(0xFF135DCF), Color(0xFF28A0F0)],
          )),
          child: Stack(children: [
            Positioned(top: -160, right: -150, child: _GlowOrb(diameter: 340)),
            Positioned(bottom: -120, left: -150, child: _GlowOrb(diameter: 310)),
            Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 470),
              child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Icon(Icons.auto_awesome_rounded, color: Color(0xFFBBF0FF), size: 34),
                const SizedBox(height: 28),
                const PpeeText('Khám phá thế giới\nmua sắm thông minh.',
                  style: TextStyle(color: Colors.white, fontSize: 43, fontWeight: FontWeight.w800, height: 1.13, letterSpacing: -1)),
                const SizedBox(height: 18),
                const PpeeText('Đa dạng sản phẩm. Tìm kiếm dễ dàng. Trải nghiệm cá nhân hóa ngay trong một ứng dụng.',
                  style: TextStyle(color: Color(0xFFE0EEFF), fontSize: 16, height: 1.7)),
                const SizedBox(height: 27),
                Container(padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(color: Colors.white.withValues(alpha: .12),
                    border: Border.all(color: Colors.white24), borderRadius: BorderRadius.circular(19)),
                  child: const Row(children: [
                    Icon(Icons.lightbulb_outline_rounded, color: Color(0xFFBBF0FF), size: 29),
                    SizedBox(width: 13),
                    Expanded(child: PpeeText('Chọn đúng sản phẩm, tiết kiệm thời gian',
                      style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600, height: 1.4))),
                  ])),
              ]),
            )),
          ]),
        )),
        Expanded(flex: 11, child: form),
      ]);
    })),
  );
}

class _GlowOrb extends StatelessWidget {
  const _GlowOrb({required this.diameter});
  final double diameter;
  @override
  Widget build(BuildContext context) => Container(
    width: diameter, height: diameter,
    decoration: BoxDecoration(shape: BoxShape.circle,
      border: Border.all(color: Colors.white.withValues(alpha: .13), width: 42)),
  );
}
