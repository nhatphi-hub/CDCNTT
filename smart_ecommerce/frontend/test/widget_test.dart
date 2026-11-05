import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/constants/app_constants.dart';
import 'package:frontend/core/responsive/shop_layout.dart';
import 'package:frontend/core/theme/app_theme.dart';

import 'support/localized_test_app.dart';

void main() {
  testWidgets('PPee renders its branded layout on a small screen', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(
      localizedTestApp(
        theme: AppTheme.light,
        home: const Scaffold(
          body: SafeArea(
            child: ShopHeading(
              title: AppConstants.appName,
              description: 'Sàn thương mại điện tử thông minh',
            ),
          ),
        ),
      ),
    );
    expect(renderedText('PPee'), findsOneWidget);
    expect(renderedText('Sàn thương mại điện tử thông minh'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  test('responsive catalog columns follow common breakpoints', () {
    expect(ShopLayout.gridColumns(390), 2);
    expect(ShopLayout.gridColumns(800), 3);
    expect(ShopLayout.gridColumns(1100), 4);
    expect(ShopLayout.gridColumns(1400), 5);
  });
}
