import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/widgets/catalog_header.dart';
import 'package:frontend/core/l10n/english_catalog.dart';

void main() {
  test('Header retains the compact original utility and search rows', () {
    expect(const CatalogHeader().preferredSize.height, 103);
  });

  test('Utility bar labels support English without changing language choices', () {
    expect(translateToEnglish('Thông báo'), 'Notifications');
    expect(translateToEnglish('Hỗ trợ'), 'Help');
    expect(translateToEnglish('Tài khoản'), 'Account');
  });
}
