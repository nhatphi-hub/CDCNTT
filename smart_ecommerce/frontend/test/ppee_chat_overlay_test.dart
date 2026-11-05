import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/providers/auth_provider.dart';
import 'package:frontend/repositories/auth_repository.dart';
import 'package:frontend/services/auth_service.dart';
import 'package:frontend/services/token_storage.dart';
import 'package:frontend/widgets/ppee_chat_overlay.dart';

void main() {
  testWidgets('floating chat opens, answers from API and minimizes', (tester) async {
    final client = MockClient((request) async => http.Response(
        jsonEncode({'answer': 'Đây là hướng dẫn từ PPee.', 'products': []}), 200,
        headers: {'content-type': 'application/json'}));
    final api = ApiClient(client: client);
    final auth = AuthProvider(AuthRepository(
        authService: AuthService(api),
        tokenStorage: TokenStorage(),
        apiClient: api));
    await tester.pumpWidget(ChangeNotifierProvider<AuthProvider>.value(
      value: auth,
      child: MaterialApp(
        home: const Scaffold(body: Text('Trang chủ')),
        navigatorKey: PpeeChatOverlay.navigatorKey,
        builder: (context, child) => PpeeChatHost(api: api, child: child),
      ),
    ));
    expect(tester.takeException(), isNull);
    expect(find.byTooltip('Mở Trợ lý PPee'), findsOneWidget);
    expect(tester.getSize(find.byType(FloatingActionButton)), const Size(52, 52));
    await tester.tap(find.byTooltip('Mở Trợ lý PPee'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Xin chào'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.enterText(find.byType(TextField).last, 'tìm sách');
    await tester.tap(find.byIcon(Icons.send_rounded));
    await tester.pumpAndSettle();
    expect(find.textContaining('hướng dẫn từ PPee'), findsOneWidget);
    await tester.tap(find.byTooltip('Thu nhỏ'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Mở Trợ lý PPee'), findsOneWidget);
  });

  testWidgets('chat uses an Overlay and fits a narrow viewport', (tester) async {
    tester.view.physicalSize = const Size(320, 460);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    final api = ApiClient(client: MockClient((request) async =>
        http.Response(jsonEncode({'answer': 'OK', 'products': []}), 200,
            headers: {'content-type': 'application/json'})));
    final auth = AuthProvider(AuthRepository(
        authService: AuthService(api), tokenStorage: TokenStorage(), apiClient: api));
    await tester.pumpWidget(ChangeNotifierProvider<AuthProvider>.value(
      value: auth,
      child: MaterialApp(
        navigatorKey: PpeeChatOverlay.navigatorKey,
        home: const Scaffold(body: Text('PPee')),
        builder: (context, child) => PpeeChatHost(api: api, child: child),
      ),
    ));
    await tester.tap(find.byTooltip('Mở Trợ lý PPee'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('chat sends recent user request to interpret short corrections', (tester) async {
    final requests = <Map<String, dynamic>>[];
    final api = ApiClient(client: MockClient((request) async {
      requests.add(Map<String, dynamic>.from(jsonDecode(request.body) as Map));
      return http.Response(jsonEncode({'answer': 'Tìm sản phẩm theo yêu cầu.', 'products': []}),
          200, headers: {'content-type': 'application/json'});
    }));
    final auth = AuthProvider(AuthRepository(
        authService: AuthService(api), tokenStorage: TokenStorage(), apiClient: api));
    await tester.pumpWidget(ChangeNotifierProvider<AuthProvider>.value(
      value: auth,
      child: MaterialApp(
        home: const Scaffold(body: Text('Trang chủ')),
        navigatorKey: PpeeChatOverlay.navigatorKey,
        builder: (context, child) => PpeeChatHost(api: api, child: child),
      ),
    ));
    await tester.tap(find.byTooltip('Mở Trợ lý PPee'));
    await tester.pumpAndSettle();
    const first = 'Tôi muốn mua áo sơ mi tặng bạn trai, gợi ý cho tôi với';
    await tester.enterText(find.byType(TextField).last, first);
    await tester.tap(find.byIcon(Icons.send_rounded));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, 'áo mà');
    await tester.tap(find.byIcon(Icons.send_rounded));
    await tester.pumpAndSettle();
    expect(requests.length, 2);
    expect(requests.first['message'], first);
    expect(requests.last['recentUserMessages'], [first]);
    expect(requests.last['message'], 'áo mà');
  });

}
