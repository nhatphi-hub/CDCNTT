import '../core/network/api_client.dart';

class SellerOrderRepository {
  SellerOrderRepository(this._api);
  final ApiClient _api;
  static const path = '/api/seller/orders';

  Future<Map<String, dynamic>> list({String? status, int page = 0}) async {
    final data = await _api.get(path, query: {
      'page': page,
      'size': 20,
      if (status != null && status.isNotEmpty) 'status': status,
    });
    return Map<String, dynamic>.from(data as Map);
  }

  Future<Map<String, dynamic>> detail(String id) async =>
      Map<String, dynamic>.from(await _api.get('$path/$id') as Map);

  Future<Map<String, dynamic>> changeStatus(String id, String status) async =>
      Map<String, dynamic>.from(await _api.patch('$path/$id/status', body: {'status': status}) as Map);

  Future<Map<String, dynamic>> analytics({int days = 30}) async =>
      Map<String, dynamic>.from(await _api.get('$path/analytics', query: {'days': days}) as Map);

  Future<Map<String, dynamic>> stats() async =>
      Map<String, dynamic>.from(await _api.get('$path/stats') as Map);
}
