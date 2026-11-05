import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/widgets/seller_ui.dart';

import 'support/localized_test_app.dart';

void main() {
  testWidgets('Seller header shows only the back-to-store action', (
    tester,
  ) async {
    await tester.pumpWidget(
      localizedTestApp(
        home: SellerScaffold(
          shopName: 'Shop từ database',
          onProducts: () {},
          onOrders: () {},
          child: const SizedBox.expand(),
        ),
      ),
    );
    await tester.pump();
    expect(renderedText('Về trang mua sắm'), findsOneWidget);
    expect(renderedText('Đơn bán'), findsNothing);
    expect(renderedText('Sản phẩm'), findsNothing);
  });
}
