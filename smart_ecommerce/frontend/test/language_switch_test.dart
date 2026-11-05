import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:frontend/core/l10n/language_controller.dart';

import 'support/localized_test_app.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'PPee switches its actual interface labels from Vietnamese to English',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final language = LanguageController();
      await language.initialize();
      expect(language.language, PpeeLanguage.vi);
      await tester.pumpWidget(
        ChangeNotifierProvider.value(
          value: language,
          child: const MaterialApp(
            home: Scaffold(
              body: Column(
                children: [
                  PpeeText('Trang chủ'),
                  PpeeText('Giỏ hàng'),
                  PpeeText('Đơn hàng'),
                ],
              ),
            ),
          ),
        ),
      );
      expect(renderedText('Trang chủ'), findsOneWidget);
      await language.select(PpeeLanguage.en);
      await tester.pump();
      expect(renderedText('Home'), findsOneWidget);
      expect(renderedText('Shopping cart'), findsOneWidget);
      expect(renderedText('Orders'), findsOneWidget);
    },
  );

  test('Selected language survives a controller reload', () async {
    SharedPreferences.setMockInitialValues({});
    final first = LanguageController();
    await first.initialize();
    await first.select(PpeeLanguage.en);
    final second = LanguageController();
    await second.initialize();
    expect(second.language, PpeeLanguage.en);
    await second.select(PpeeLanguage.vi);
    final third = LanguageController();
    await third.initialize();
    expect(third.language, PpeeLanguage.vi);
  });
}
