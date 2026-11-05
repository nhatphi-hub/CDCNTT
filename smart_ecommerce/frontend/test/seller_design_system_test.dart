import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/widgets/seller_ui.dart';

import 'support/localized_test_app.dart';

void main() {
  Widget buildPage() => localizedTestApp(
    theme: AppTheme.light,
    home: Scaffold(
      body: SingleChildScrollView(
        child: Column(
          children: const [
            SellerPageIntro(
              title: 'Đơn bán',
              subtitle: 'Theo dõi đơn hàng thật của shop',
              icon: Icons.receipt_long_outlined,
            ),
            SizedBox(height: 10),
            SellerMetricGrid(
              children: [
                SellerMetric(
                  label: 'Tổng đơn',
                  value: '12',
                  icon: Icons.receipt_outlined,
                ),
                SellerMetric(
                  label: 'Chờ xử lý',
                  value: '3',
                  icon: Icons.schedule_outlined,
                ),
              ],
            ),
            SellerEmptyState(
              title: 'Chưa có đơn',
              message: 'Dữ liệu sẽ xuất hiện sau khi đặt hàng.',
              icon: Icons.inbox_outlined,
            ),
          ],
        ),
      ),
    ),
  );

  testWidgets('Seller widgets hiển thị trên desktop', (tester) async {
    tester.view.physicalSize = const Size(1280, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(buildPage());
    expect(renderedText('Đơn bán'), findsOneWidget);
    expect(renderedText('Tổng đơn'), findsOneWidget);
    expect(renderedText('Chưa có đơn'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Seller widgets thích ứng điện thoại', (tester) async {
    tester.view.physicalSize = const Size(375, 812);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(buildPage());
    expect(renderedText('Đơn bán'), findsOneWidget);
    expect(renderedText('Chờ xử lý'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
