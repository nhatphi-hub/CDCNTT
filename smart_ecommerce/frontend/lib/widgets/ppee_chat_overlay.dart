import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/network/api_client.dart';
import '../core/network/api_exception.dart';
import '../core/utils/formatters.dart';
import '../core/routes/app_routes.dart';
import '../providers/auth_provider.dart';
import 'product_image.dart';

/// Keeps the floating assistant above all routes while providing a real
/// Overlay ancestor for tooltips, text selection and popup controls.
///
/// MaterialApp.builder is ABOVE Navigator's own Overlay. Inserting the chat
/// directly into a Stack there makes IconButton tooltips throw
/// "No Overlay widget found". This host supplies an outer Overlay without
/// replacing the existing Navigator or changing named routes.
class PpeeChatHost extends StatefulWidget {
  const PpeeChatHost({super.key, required this.api, required this.child});

  final ApiClient api;
  final Widget? child;

  @override
  State<PpeeChatHost> createState() => _PpeeChatHostState();
}

class _PpeeChatHostState extends State<PpeeChatHost> {
  late final OverlayEntry _rootEntry;

  @override
  void initState() {
    super.initState();
    _rootEntry = OverlayEntry(builder: (_) => Stack(
      fit: StackFit.expand,
      children: [
        if (widget.child != null) widget.child!,
        PpeeChatOverlay(api: widget.api),
      ],
    ));
  }

  @override
  void didUpdateWidget(covariant PpeeChatHost oldWidget) {
    super.didUpdateWidget(oldWidget);
    // MaterialApp can rebuild when the active language changes.
    // Refresh the embedded Navigator without resetting the chat instance.
    _rootEntry.markNeedsBuild();
  }

  @override
  void dispose() {
    // The parent Overlay may already have detached its entry during teardown.
    if (_rootEntry.mounted) _rootEntry.remove();
    _rootEntry.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Overlay(initialEntries: [_rootEntry]);
}

/// One chat instance above all named routes; no parallel chat page.
/// Chat is read-only. All product/order facts originate from the backend.
class PpeeChatOverlay extends StatefulWidget {
  static final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();
  const PpeeChatOverlay({super.key, required this.api});
  final ApiClient api;

  @override
  State<PpeeChatOverlay> createState() => _PpeeChatOverlayState();
}

class _ChatMessage {
  const _ChatMessage(this.text, {this.fromUser = false, this.products = const []});
  final String text;
  final bool fromUser;
  final List<Map<String, dynamic>> products;
}

class _PpeeChatOverlayState extends State<PpeeChatOverlay> {
  final TextEditingController _controller = TextEditingController();
  final ScrollController _scroll = ScrollController();
  bool _open = false;
  bool _sending = false;
  int _version = 0;
  String? _accountId;
  final List<_ChatMessage> _messages = [
    const _ChatMessage('Xin chào! Mình là Trợ lý PPee. Bạn có thể hỏi về sản phẩm, giá, '
        'cách đặt hàng hoặc trạng thái đơn mua của mình nhé.'),
  ];

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final userId = context.watch<AuthProvider>().user?.id;
    if (_accountId != userId) {
      _accountId = userId;
      _version++;
      _messages
        ..clear()
        ..add(const _ChatMessage('Chào bạn! Mình có thể giúp tìm sản phẩm, tư vấn mua sắm '
            'và kiểm tra đơn hàng thuộc tài khoản đang đăng nhập.'));
      _sending = false;
      _controller.clear();
    }
  }

  @override
  void dispose() {
    _version++;
    _controller.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _toBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 220), curve: Curves.easeOut);
      }
    });
  }

  Future<void> _send([String? text]) async {
    if (_sending) return;
    final message = (text ?? _controller.text).trim();
    if (message.isEmpty) return;
    final version = _version;
    final account = _accountId;
    // Only customer-authored turns. The chat is reset on account switch, and
    // this transient context is used for clarifications such as "áo mà".
    final recentUserMessages = _messages
        .where((m) => m.fromUser)
        .map((m) => m.text)
        .toList()
        .reversed
        .take(4)
        .toList()
        .reversed
        .toList();
    _controller.clear();
    setState(() {
      _sending = true;
      _messages.add(_ChatMessage(message, fromUser: true));
    });
    _toBottom();
    try {
      final reply = await widget.api.post('/api/assistant/chat',
          authorized: context.read<AuthProvider>().isAuthenticated,
          body: {'message': message, 'recentUserMessages': recentUserMessages});
      if (!mounted || version != _version || account != _accountId) return;
      final data = Map<String, dynamic>.from(reply as Map);
      final productRows = (data['products'] as List? ?? const [])
          .whereType<Map>()
          .map((p) => Map<String, dynamic>.from(p)).toList();
      setState(() => _messages.add(_ChatMessage(
          data['answer']?.toString() ?? 'Chưa có câu trả lời từ PPee.',
          products: productRows)));
    } on ApiException catch (e) {
      if (!mounted || version != _version || account != _accountId) return;
      setState(() => _messages.add(_ChatMessage(
          'Không thể kết nối trợ lý lúc này (${e.message}). Bạn có thể thử lại sau.')));
    } catch (_) {
      if (!mounted || version != _version || account != _accountId) return;
      setState(() => _messages.add(const _ChatMessage(
          'Trợ lý đang tạm thời không phản hồi. Vui lòng thử lại.')));
    } finally {
      if (mounted && version == _version && account == _accountId) {
        setState(() => _sending = false);
        _toBottom();
      }
    }
  }

  String _displayPrice(Object? value) {
    final price = num.tryParse(value?.toString() ?? '');
    return price == null ? 'Xem giá trong chi tiết' : Formatters.vnd(price);
  }

  Widget _bubble(_ChatMessage message) {
    final mine = message.fromUser;
    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 320),
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 5),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: mine ? const Color(0xFF2563EB) : const Color(0xFFF1F5FD),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(message.text, style: TextStyle(
                color: mine ? Colors.white : const Color(0xFF172B4D), height: 1.4)),
            for (final product in message.products) Padding(
              padding: const EdgeInsets.only(top: 9),
              child: Material(
                color: Colors.white,
                borderRadius: BorderRadius.circular(10),
                child: InkWell(
                  borderRadius: BorderRadius.circular(10),
                  onTap: () {
                    setState(() => _open = false);
                    PpeeChatOverlay.navigatorKey.currentState?.pushNamed(
                        '${AppRoutes.product}/${product['id']}');
                  },
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Row(mainAxisSize: MainAxisSize.min, children: [
                      SizedBox(width: 48, height: 48,
                          child: ProductImage(url: product['imageUrl']?.toString())),
                      const SizedBox(width: 8),
                      Flexible(child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('${product['name'] ?? ''}',
                              maxLines: 2, overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                          const SizedBox(height: 4),
                          Text(_displayPrice(product['price']),
                              style: const TextStyle(fontSize: 12,
                                  color: Color(0xFF155EEF), fontWeight: FontWeight.w700)),
                        ],
                      )),
                      const Icon(Icons.chevron_right, size: 17),
                    ]),
                  ),
                ),
              ),
            ),
          ]),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final screen = MediaQuery.sizeOf(context);
    final insets = MediaQuery.viewInsetsOf(context);
    final safeTop = MediaQuery.paddingOf(context).top;
    final safeBottom = MediaQuery.paddingOf(context).bottom;
    final keyboardOpen = insets.bottom > 0;
    final bottomGap = keyboardOpen ? insets.bottom + 8 : safeBottom + 14;
    final availableHeight = screen.height - safeTop - bottomGap - 8;
    final panelHeight = availableHeight.clamp(0.0, 525.0).toDouble();
    final panelWidth = (screen.width - 24).clamp(0.0, 390.0).toDouble();

    return Positioned(
      right: 12,
      bottom: bottomGap,
      child: _open
          ? Material(
              elevation: 16,
              borderRadius: BorderRadius.circular(20),
              clipBehavior: Clip.antiAlias,
              child: SizedBox(
                width: panelWidth,
                height: panelHeight,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    border: Border.all(color: const Color(0xFFDCE6F6)),
                  ),
                  child: Column(children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      color: const Color(0xFF1646A3),
                      child: Row(children: [
                        const CircleAvatar(
                          radius: 18,
                          backgroundColor: Colors.white24,
                          child: Icon(Icons.support_agent_rounded, color: Colors.white),
                        ),
                        const SizedBox(width: 8),
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('Trợ lý PPee', style: TextStyle(
                                fontWeight: FontWeight.bold, color: Colors.white)),
                              Text('Hỗ trợ sản phẩm và đơn hàng',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(fontSize: 11, color: Colors.white70)),
                            ],
                          ),
                        ),
                        IconButton(
                          tooltip: 'Thu nhỏ',
                          constraints: const BoxConstraints(minWidth: 38, minHeight: 38),
                          icon: const Icon(Icons.remove, color: Colors.white),
                          onPressed: () => setState(() => _open = false),
                        ),
                        IconButton(
                          tooltip: 'Đóng và xóa cuộc trò chuyện',
                          constraints: const BoxConstraints(minWidth: 38, minHeight: 38),
                          icon: const Icon(Icons.close, color: Colors.white),
                          onPressed: () => setState(() {
                            _open = false;
                            _version++;
                            _sending = false;
                            _messages
                              ..clear()
                              ..add(const _ChatMessage('Xin chào! Bạn cần PPee hỗ trợ gì?'));
                          }),
                        ),
                      ]),
                    ),
                    Expanded(
                      child: ListView.builder(
                        controller: _scroll,
                        padding: const EdgeInsets.all(12),
                        itemCount: _messages.length + (_sending ? 1 : 0) +
                            (_messages.length == 1 ? 1 : 0),
                        itemBuilder: (context, index) {
                          if (index < _messages.length) {
                            return _bubble(_messages[index]);
                          }
                          if (_sending && index == _messages.length) {
                            return const Padding(
                              padding: EdgeInsets.all(12),
                              child: Text('Trợ lý đang tìm thông tin...',
                                style: TextStyle(fontSize: 12, color: Colors.black54)),
                            );
                          }
                          // Suggestions scroll together with chat messages instead
                          // of increasing the fixed panel height on small screens.
                          return Padding(
                            padding: const EdgeInsets.only(top: 6),
                            child: Wrap(spacing: 5, runSpacing: 5, children: [
                              ActionChip(label: const Text('Tìm sách'),
                                onPressed: () => _send('Tìm sách')),
                              ActionChip(label: const Text('Đơn của tôi'),
                                onPressed: () => _send('Trạng thái đơn hàng của tôi')),
                              ActionChip(label: const Text('Cách đặt hàng'),
                                onPressed: () => _send('Cách đặt hàng và thanh toán')),
                            ]),
                          );
                        },
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(8),
                      child: Row(children: [
                        Expanded(child: TextField(
                          controller: _controller,
                          enabled: !_sending,
                          maxLength: 500,
                          minLines: 1,
                          maxLines: 2,
                          textInputAction: TextInputAction.send,
                          decoration: InputDecoration(
                            counterText: '',
                            hintText: 'Nhập câu hỏi của bạn...',
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 13, vertical: 11),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12)),
                          ),
                          onSubmitted: (_) => _send(),
                        )),
                        const SizedBox(width: 6),
                        IconButton.filled(
                          tooltip: 'Gửi',
                          onPressed: _sending ? null : () => _send(),
                          icon: const Icon(Icons.send_rounded),
                        ),
                      ]),
                    ),
                  ]),
                ),
              ),
            )
          : SizedBox(
              width: 52,
              height: 52,
              child: FloatingActionButton(
                heroTag: 'ppee-global-chat',
                tooltip: 'Mở Trợ lý PPee',
                onPressed: () => setState(() => _open = true),
                backgroundColor: const Color(0xFF235BE9),
                foregroundColor: Colors.white,
                elevation: 4,
                shape: const CircleBorder(),
                child: const Icon(Icons.chat_bubble_outline_rounded, size: 24),
              ),
            ),
    );
  }

}
