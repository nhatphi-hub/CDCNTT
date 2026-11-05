import 'category.dart';

/// A cycle-safe, read-only view of the category tree returned by PostgreSQL.
class CategoryTree {
  CategoryTree(List<Category> categories)
      : categories = List<Category>.unmodifiable(categories),
        byId = {for (final category in categories) category.id: category};

  final List<Category> categories;
  final Map<String, Category> byId;

  List<Category> get roots => categories
      .where((c) => c.isActive && c.parentId == null).toList()
    ..sort((a, b) => a.name.compareTo(b.name));

  List<Category> childrenOf(String id) => categories
      .where((c) => c.isActive && c.parentId == id).toList()
    ..sort((a, b) => a.name.compareTo(b.name));

  Category? rootFor(String id) {
    Category? current = byId[id];
    final seen = <String>{};
    while (current != null && seen.add(current.id)) {
      if (current.parentId == null) return current;
      current = byId[current.parentId];
    }
    return null;
  }

  /// Includes the selected node itself, plus all active descendants. Products
  /// under children are shown when browsing a root category.
  Set<String> subtreeIds(String id) {
    final ids = <String>{};
    final toVisit = <String>[id];
    while (toVisit.isNotEmpty) {
      final current = toVisit.removeLast();
      if (!ids.add(current)) continue;
      for (final child in childrenOf(current)) {
        if (!ids.contains(child.id)) toVisit.add(child.id);
      }
    }
    return ids;
  }
}
