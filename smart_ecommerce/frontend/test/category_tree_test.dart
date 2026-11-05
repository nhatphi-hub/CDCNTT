import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/models/category.dart';
import 'package:frontend/models/category_tree.dart';

Category category(String id, String name, {String? parent, bool active = true}) =>
    Category(id: id, name: name, slug: name.toLowerCase(),
      parentId: parent, isActive: active);

void main() {
  test('root listing includes products assigned to nested subcategories', () {
    final tree = CategoryTree([
      category('fashion', 'Thời trang nam'),
      category('shirt', 'Áo thun', parent: 'fashion'),
      category('polo', 'Áo polo', parent: 'shirt'),
      category('jeans', 'Quần jeans', parent: 'fashion'),
      category('hidden', 'Danh mục ẩn', parent: 'fashion', active: false),
      category('phone', 'Điện thoại'),
    ]);
    expect(tree.roots.map((c) => c.id), containsAll(['fashion', 'phone']));
    expect(tree.subtreeIds('fashion'), containsAll(['fashion', 'shirt', 'polo', 'jeans']));
    expect(tree.subtreeIds('fashion'), isNot(contains('hidden')));
    expect(tree.rootFor('polo')?.id, 'fashion');
    expect(tree.subtreeIds('shirt'), containsAll(['shirt', 'polo']));
    expect(tree.subtreeIds('shirt'), isNot(contains('jeans')));
  });

  test('category traversal terminates if legacy data has a parent loop', () {
    final tree = CategoryTree([
      category('a', 'A', parent: 'b'),
      category('b', 'B', parent: 'a'),
    ]);
    expect(tree.subtreeIds('a'), {'a', 'b'});
    expect(tree.rootFor('a'), isNull);
  });
}
