import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'english_catalog.dart';

enum PpeeLanguage { vi, en }

/// Local-only language preference. No API, auth, or database changes.
class LanguageController extends ChangeNotifier {
  static const _storageKey = 'ppee.ui.language.v1';
  PpeeLanguage _language = PpeeLanguage.vi;

  PpeeLanguage get language => _language;
  Locale get locale => Locale(_language.name);
  bool get english => _language == PpeeLanguage.en;

  Future<void> initialize() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _language = prefs.getString(_storageKey) == 'en'
          ? PpeeLanguage.en
          : PpeeLanguage.vi;
    } catch (_) {
      _language = PpeeLanguage.vi;
    }
    notifyListeners();
  }

  Future<void> select(PpeeLanguage next) async {
    if (next == _language) return;
    _language = next;
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_storageKey, next.name);
    } catch (_) {
      // Do not interrupt browsing when browser storage is unavailable.
    }
  }

  String translate(String source) => english ? translateToEnglish(source) : source;
}

/// Call only from a widget's build method (or a widget builder).
String tr(BuildContext context, String text) =>
    context.watch<LanguageController>().translate(text);

/// Drop-in Text for localized UI copy. Dynamic product/shop/user names are not
/// translated unless they happen to match a registered interface label.
class PpeeText extends Text {
  const PpeeText(
    super.data, {
    super.key,
    super.style,
    super.strutStyle,
    super.textAlign,
    super.textDirection,
    super.locale,
    super.softWrap,
    super.overflow,
    super.textScaler,
    super.maxLines,
    super.semanticsLabel,
    super.textWidthBasis,
    super.textHeightBehavior,
    super.selectionColor,
  });

  @override
  Widget build(BuildContext context) {
    final value = tr(context, data ?? '');
    return Text(
      value,
      style: style,
      strutStyle: strutStyle,
      textAlign: textAlign,
      textDirection: textDirection,
      locale: locale,
      softWrap: softWrap,
      overflow: overflow,
      textScaler: textScaler,
      maxLines: maxLines,
      semanticsLabel: semanticsLabel,
      textWidthBasis: textWidthBasis,
      textHeightBehavior: textHeightBehavior,
      selectionColor: selectionColor,
    );
  }
}
