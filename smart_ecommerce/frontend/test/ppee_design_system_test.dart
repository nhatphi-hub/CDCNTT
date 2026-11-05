import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/widgets/ppee_ui.dart';

import 'support/localized_test_app.dart';

void main() {
  testWidgets('PPee form giữ xác thực FormState và nhãn trường', (
    tester,
  ) async {
    final formKey = GlobalKey<FormState>();
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      localizedTestApp(
        theme: AppTheme.light,
        home: Scaffold(
          body: Form(
            key: formKey,
            child: PpeeFormField(
              label: 'Địa chỉ email',
              controller: controller,
              requiredField: true,
              hint: 'tenban@example.com',
              validator: (value) => value == null || !value.contains('@')
                  ? 'Email không hợp lệ'
                  : null,
            ),
          ),
        ),
      ),
    );
    expect(find.byType(PpeeFormField), findsOneWidget);
    await tester.enterText(find.byType(TextFormField), 'khong-hop-le');
    expect(formKey.currentState!.validate(), isFalse);
    await tester.pump();
    expect(find.text('Email không hợp lệ'), findsOneWidget);
    await tester.enterText(find.byType(TextFormField), 'hello@example.com');
    expect(formKey.currentState!.validate(), isTrue);
  });

  testWidgets('Thông báo lỗi không cần backend', (tester) async {
    await tester.pumpWidget(
      localizedTestApp(
        home: const Scaffold(
          body: PpeeNotice(message: 'Có lỗi kết nối', isError: true),
        ),
      ),
    );
    expect(renderedText('Có lỗi kết nối'), findsOneWidget);
    expect(find.byIcon(Icons.error_outline_rounded), findsOneWidget);
  });

  testWidgets('Trạng thái trống có thao tác chính', (tester) async {
    var tapped = false;
    await tester.pumpWidget(
      localizedTestApp(
        home: Scaffold(
          body: PpeeStateView(
            title: 'Chưa có địa chỉ',
            message: 'Thêm địa chỉ giao hàng',
            icon: Icons.place_outlined,
            actionLabel: 'Thêm ngay',
            onAction: () => tapped = true,
          ),
        ),
      ),
    );
    await tester.tap(renderedText('Thêm ngay'));
    expect(tapped, isTrue);
  });
}
