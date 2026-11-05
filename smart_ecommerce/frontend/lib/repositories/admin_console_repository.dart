import '../core/network/api_client.dart';
import '../core/network/api_exception.dart';

class AdminPage {
  AdminPage(this.items, this.page, this.totalPages, this.totalElements);
  final List<Map<String, dynamic>> items;
  final int page;
  final int totalPages;
  final int totalElements;

  factory AdminPage.fromJson(dynamic raw) {
    final json = Map<String, dynamic>.from(raw as Map);
    final content = json['content'] as List? ?? const [];
    return AdminPage(
      content.map((e) => Map<String, dynamic>.from(e as Map)).toList(),
      (json['number'] as num?)?.toInt() ?? 0,
      (json['totalPages'] as num?)?.toInt() ?? 0,
      (json['totalElements'] as num?)?.toInt() ?? 0,
    );
  }
}

class AdminConsoleRepository {
  AdminConsoleRepository(this._api);
  final ApiClient _api;

  Future<AdminPage> list(String resource, {String status = '', int page = 0}) async {
    if (!const {'shops', 'orders', 'payments'}.contains(resource)) {
      throw ArgumentError.value(resource, 'resource');
    }
    return AdminPage.fromJson(await _api.get('/api/admin/console/$resource',
      query: {'status': status, 'page': page, 'size': 20}));
  }

  Future<void> setShopStatus(String shopId, String status) async {
    await _api.put('/api/admin/shops/$shopId/status', body: {'status': status});
  }

  Future<Map<String, dynamic>> orderDetail(String id) async =>
      Map<String, dynamic>.from(await _api.get('/api/orders/management/$id') as Map);

  Future<Map<String, dynamic>?> shipping(String orderId) async {
    try {
      return Map<String, dynamic>.from(await _api.get('/api/shipping/orders/$orderId') as Map);
    } on Exception catch (e) {
      // A non-existent shipping is normal for a new order; other API failures must surface.
      if (e is ApiException && e.statusCode == 404) return null;
      rethrow;
    }
  }

  Future<Map<String, dynamic>> createShipping(String orderId) async =>
      Map<String, dynamic>.from(await _api.post('/api/shipping/orders/$orderId', body: {}) as Map);

  Future<Map<String, dynamic>> setShippingStatus(String shippingId, String status) async =>
      Map<String, dynamic>.from(await _api.patch('/api/shipping/$shippingId/status',
          body: {'shippingStatus': status}) as Map);
}
