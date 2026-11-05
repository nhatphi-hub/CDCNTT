import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/models/product_view.dart';
import 'package:frontend/models/search_history.dart';
import 'package:frontend/providers/behavior_provider.dart';
import 'package:frontend/repositories/behavior_repository.dart';

class _FakeBehaviorRepository extends BehaviorRepository {
  _FakeBehaviorRepository() : super(ApiClient());
  final items = <SearchHistoryItem>[];
  Future<List<SearchHistoryItem>>? delayedSearch;

  @override
  Future<List<SearchHistoryItem>> searches() =>
      delayedSearch ?? Future.value(List<SearchHistoryItem>.from(items));

  @override
  Future<void> recordSearch(String keyword) async {
    items.insert(0, SearchHistoryItem(
      id: 'entry-${items.length + 1}',
      keyword: keyword,
      searchedAt: DateTime(2026, 11, 3),
    ));
  }

  @override
  Future<void> deleteSearch(String id) async {
    items.removeWhere((item) => item.id == id);
  }

  @override
  Future<void> clearSearches() async {
    items.clear();
  }

  @override
  Future<List<ProductViewModel>> myViews() async => [];

  @override
  Future<void> recordProductView(String id) async {}

  @override
  Future<void> clearViews() async {}
}

void main() {
  test('Lưu và xóa từ khóa được phản ánh trong lịch sử', () async {
    final repo = _FakeBehaviorRepository();
    final provider = BehaviorProvider(repo);
    addTearDown(provider.dispose);
    provider.syncUser('customer-a');

    await provider.recordSearch('Áo sơ mi');
    expect(provider.searchHistory.single.keyword, 'Áo sơ mi');

    final id = provider.searchHistory.single.id;
    expect(await provider.deleteSearch(id), isTrue);
    expect(provider.searchHistory, isEmpty);
    await provider.recordSearch('Quần jean');
    expect(await provider.clearSearches(), isTrue);
    expect(provider.searchHistory, isEmpty);
  });

  test('Không ghi lịch sử khi chưa đăng nhập', () async {
    final repo = _FakeBehaviorRepository();
    final provider = BehaviorProvider(repo);
    addTearDown(provider.dispose);

    await provider.recordSearch('Từ khóa khách vãng lai');
    expect(repo.items, isEmpty);
    expect(provider.searchHistory, isEmpty);
  });

  test('Kết quả tìm kiếm cũ không lọt sang tài khoản khác', () async {
    final repo = _FakeBehaviorRepository();
    final waiting = Completer<List<SearchHistoryItem>>();
    repo.delayedSearch = waiting.future;
    final provider = BehaviorProvider(repo);
    addTearDown(provider.dispose);

    provider.syncUser('customer-a');
    final loading = provider.loadSearches();
    provider.syncUser('customer-b');
    waiting.complete([SearchHistoryItem(
      id: 'account-a-entry', keyword: 'Riêng của A',
      searchedAt: DateTime(2026, 11, 3),
    )]);
    await loading;
    expect(provider.searchHistory, isEmpty);

    repo.delayedSearch = null;
    repo.items.add(SearchHistoryItem(
      id: 'account-b-entry', keyword: 'Riêng của B',
      searchedAt: DateTime(2026, 11, 3),
    ));
    await provider.loadSearches();
    expect(provider.searchHistory.single.keyword, 'Riêng của B');
  });
}
