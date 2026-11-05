import 'dart:math';

import 'package:flutter/foundation.dart';

import '../models/recommendation.dart';
import '../repositories/recommendation_repository.dart';

class RecommendationProvider extends ChangeNotifier {
  RecommendationProvider(this._repo);
  final RecommendationRepository _repo;

  List<RecommendationItem> popular = const [];
  List<RecommendationItem> personalized = const [];
  bool loading = false;
  String? error;
  final Set<String> _impressions = {};
  // One random identifier per in-memory anonymous app session. Never reused
  // across accounts and never used to authorize a private recommendation.
  String? _anonymousSessionId;
  String? _userId;
  int _requestVersion = 0;

  void syncUser(String? userId) {
    if (_userId == userId) return;
    _userId = userId;
    _requestVersion++;
    loading = false;
    personalized = const [];
    _impressions.clear();
    _anonymousSessionId = null;
    error = null;
    notifyListeners();
  }

  Future<void> loadHome({required bool personalizedAllowed}) async {
    // Never show a stale result from a previous account after log out/switch.
    final version = ++_requestVersion;
    // A new list exposure may legitimately produce another impression.
    _impressions.clear();
    loading = true;
    error = null;
    notifyListeners();
    try {
      final results = await _repo.popular();
      if (version != _requestVersion) return;
      popular = results;
    } catch (_) {
      if (version != _requestVersion) return;
      error = 'Không thể tải sản phẩm phổ biến.';
    }
    try {
      final results = personalizedAllowed
          ? await _repo.personalized()
          : <RecommendationItem>[];
      if (version != _requestVersion) return;
      personalized = results;
    } catch (_) {
      if (version != _requestVersion) return;
      personalized = const [];
      error = 'Không thể tải gợi ý cá nhân lúc này.';
    } finally {
      if (version == _requestVersion) {
        loading = false;
        notifyListeners();
      }
    }
  }

  Future<List<RecommendationItem>> related(String id) async {
    try {
      return await _repo.related(id);
    } catch (_) {
      return const [];
    }
  }

  Future<void> track(
    String type,
    RecommendationItem item,
    String event,
    int position, {
    required bool authenticated,
  }) async {
    // Anonymous users may track POPULAR/RELATED interactions with a sessionId.
    // PERSONALIZED belongs to an authenticated CUSTOMER only.
    if (!authenticated && type == 'PERSONALIZED') return;
    final key = '$type:$event:${item.productId}:$position';
    if (event == 'IMPRESSION' && !_impressions.add(key)) return;
    try {
      await _repo.track(
        recommendationType: type,
        productId: item.productId,
        eventType: event,
        authenticated: authenticated,
        sessionId: authenticated ? null : (_anonymousSessionId ??= _newUuidV4()),
        score: item.score,
        position: position,
      );
    } catch (_) {
      // Analytics must never interrupt shopping. Allow an impression retry on
      // the next exposure if its request failed.
      if (event == 'IMPRESSION') _impressions.remove(key);
    }
  }

  static String _newUuidV4() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }
}
