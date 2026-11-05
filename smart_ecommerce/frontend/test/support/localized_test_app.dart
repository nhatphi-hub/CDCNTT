import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/l10n/language_controller.dart';
import 'package:provider/provider.dart';

/// Matches the language provider supplied by the real application bootstrap.
Widget localizedTestApp({required Widget home, ThemeData? theme}) =>
    ChangeNotifierProvider(
      create: (_) => LanguageController(),
      child: MaterialApp(theme: theme, home: home),
    );

/// PpeeText extends Text and builds another Text; match the rendered label once.
Finder renderedText(String value) => find.byWidgetPredicate(
  (widget) => widget is Text && widget is! PpeeText && widget.data == value,
  description: 'rendered text "$value"',
);
