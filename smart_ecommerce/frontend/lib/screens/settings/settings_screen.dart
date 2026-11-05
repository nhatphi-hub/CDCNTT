import '../../core/l10n/language_controller.dart';
import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:provider/provider.dart';

import '../../core/network/api_exception.dart';
import '../../core/routes/app_routes.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/ppee_ui.dart';
import '../../providers/auth_provider.dart';
import '../../services/google_sign_in_bridge.dart';
import '../../widgets/google_sign_in_button.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _googleReady = false;
  bool _loadingStatus = true;
  bool _linking = false;
  bool _linked = false;
  String? _message;
  StreamSubscription<GoogleSignInAuthenticationEvent>? _events;

  @override
  void initState() {
    super.initState();
    unawaited(_loadGoogleLink());
  }

  Future<void> _loadGoogleLink() async {
    try {
      final linked = await context.read<AuthProvider>().isGoogleLinked();
      if (!mounted) return;
      setState(() { _linked = linked; _loadingStatus = false; });
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() { _message = error.message; _loadingStatus = false; });
    } catch (_) {
      if (!mounted) return;
      setState(() { _message = 'Không tải được trạng thái liên kết Google.'; _loadingStatus = false; });
    }
    if (!mounted || !kIsWeb) return;
    try {
      await GoogleSignInBridge.ensureInitialized();
      if (!mounted) return;
      _events = GoogleSignIn.instance.authenticationEvents.listen(
        (event) {
          if (event is GoogleSignInAuthenticationEventSignIn && !_linked) {
            unawaited(_linkAccount(event.user));
          }
        },
        onError: (Object _) {
          if (mounted) setState(() => _message = 'Google Sign-In thất bại. Hãy thử lại.');
        },
      );
      setState(() => _googleReady = true);
    } catch (_) {
      if (mounted) setState(() => _message = 'Không tải được Google Sign-In. Kiểm tra Client ID và mạng.');
    }
  }

  Future<void> _linkAccount(GoogleSignInAccount account) async {
    if (_linking || !mounted) return;
    final idToken = account.authentication.idToken;
    if (idToken == null || idToken.isEmpty) {
      setState(() => _message = 'Google không cung cấp ID Token.');
      return;
    }
    setState(() { _linking = true; _message = null; });
    try {
      await context.read<AuthProvider>().linkGoogle(idToken);
      if (!mounted) return;
      setState(() { _linked = true; _message = 'Đã liên kết Google thành công. Bạn có thể dùng Google để đăng nhập PPee.'; });
    } on ApiException catch (error) {
      if (mounted) setState(() => _message = error.message);
    } catch (_) {
      if (mounted) setState(() => _message = 'Không thể liên kết Google. Vui lòng thử lại.');
    } finally {
      if (mounted) setState(() => _linking = false);
    }
  }

  @override
  void dispose() {
    _events?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthProvider>().user!;
    return Scaffold(
      appBar: AppBar(title: const PpeeText('Cài đặt tài khoản')),
      body: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 850),
        child: ListView(padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 26), children: [
          PpeeText('Trung tâm tài khoản', style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 7),
          PpeeText('Theo dõi bảo mật, kết nối và địa chỉ giao hàng.',
            style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 22),
          PpeeSurface(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const Icon(Icons.badge_outlined, color: AppColors.primary, size: 23),
              const SizedBox(width: 10),
              PpeeText('Thông tin tài khoản', style: Theme.of(context).textTheme.titleLarge),
            ]),
            const SizedBox(height: 18),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(17),
              decoration: BoxDecoration(color: AppColors.gray100,
                borderRadius: BorderRadius.circular(15)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const PpeeText('Họ và tên', style: TextStyle(fontSize: 12, color: AppColors.gray700)),
                const SizedBox(height: 5),
                PpeeText(user.fullName, style: const TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 14),
                const PpeeText('Địa chỉ email', style: TextStyle(fontSize: 12, color: AppColors.gray700)),
                const SizedBox(height: 5),
                SelectableText(user.email, style: const TextStyle(fontWeight: FontWeight.w600)),
              ]),
            ),
          ])),
          const SizedBox(height: 16),
          PpeeSurface(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const Icon(Icons.verified_user_outlined, color: AppColors.primary, size: 23),
              const SizedBox(width: 10),
              Expanded(child: PpeeText('Liên kết đăng nhập Google',
                style: Theme.of(context).textTheme.titleLarge)),
            ]),
            const SizedBox(height: 10),
            PpeeText('Kết nối đúng tài khoản Google để đăng nhập thuận tiện. '
                'Lịch sử đơn hàng và vai trò PPee vẫn được giữ nguyên.',
              style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 17),
            if (_loadingStatus)
              const PpeeLoadingView(message: 'Đang kiểm tra trạng thái Google...')
            else if (_linked)
              const Row(children: [
                Icon(Icons.check_circle_rounded, color: AppColors.success),
                SizedBox(width: 9),
                Expanded(child: PpeeText('Tài khoản đã liên kết Google',
                  style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.success))),
              ])
            else if (kIsWeb && _googleReady)
              Center(child: PpeeGoogleSignInButton(disabled: _linking))
            else if (!kIsWeb)
              const PpeeNotice(message: 'Liên kết Google hiện chỉ hỗ trợ Flutter Web.'),
            if (_linking) ...[const SizedBox(height: 13), const LinearProgressIndicator()],
            if (_message != null) ...[
              const SizedBox(height: 15),
              PpeeNotice(message: _message!,
                isError: !_linked || !_message!.contains('thành công')),
            ],
          ])),
          const SizedBox(height: 16),
          PpeeSurface(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            PpeeText('Địa chỉ và bảo mật', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.location_on_outlined),
              title: const PpeeText('Địa chỉ giao hàng'),
              subtitle: const PpeeText('Thêm, sửa và chọn địa chỉ mặc định'),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => Navigator.pushNamed(context, AppRoutes.addresses),
            ),
            const Divider(height: 14),
            const ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.lock_outline),
              title: PpeeText('Đổi mật khẩu'),
              subtitle: PpeeText('Chưa có API đổi mật khẩu nên tính năng chưa được mở.'),
              enabled: false,
            ),
            const ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.edit_outlined),
              title: PpeeText('Chỉnh sửa hồ sơ'),
              subtitle: PpeeText('Chưa có API cập nhật hồ sơ nên tính năng chưa được mở.'),
              enabled: false,
            ),
          ])),
          const SizedBox(height: 28),
        ]),
      )),
    );
  }
}
