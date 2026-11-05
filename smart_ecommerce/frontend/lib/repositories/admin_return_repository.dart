import '../core/network/api_client.dart';

class AdminReturnRepository {
  AdminReturnRepository(this.api);
  final ApiClient api;

  Future<List<Map<String, dynamic>>> all() async =>
      (await api.get('/api/admin/returns') as List)
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();

  Future<void> confirmGoodsReceived(String id) async {
    await api.patch('/api/admin/returns/$id/receive');
  }

  Future<void> recordVerifiedRefund(String id, String amount,
      String externalReference, String evidenceNote) async {
    await api.post('/api/admin/returns/$id/manual-refund', body: {
      'amount': amount,
      'externalReference': externalReference,
      'evidenceNote': evidenceNote,
    });
  }
}
