import '../../core/l10n/language_controller.dart';
import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:provider/provider.dart';

import '../../core/constants/app_spacing.dart';
import '../../core/routes/app_routes.dart';
import '../../core/utils/validators.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/auth_shell.dart';
import '../../widgets/ppee_ui.dart';
import '../../widgets/google_sign_in_button.dart';
import '../../services/google_sign_in_bridge.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;
  bool _googleReady = false;
  bool _googleFailed = false;
  StreamSubscription<GoogleSignInAuthenticationEvent>? _googleEvents;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<AuthProvider>().clearError();
    });
    if (kIsWeb) {
      GoogleSignInBridge.ensureInitialized().then((_) {
        if (!mounted) return;
        _googleEvents = GoogleSignIn.instance.authenticationEvents.listen(
          (event) {
            if (event is GoogleSignInAuthenticationEventSignIn) {
              unawaited(_handleGoogleAccount(event.user));
            }
          },
          onError: (Object error) {
            if (mounted) {
              context.read<AuthProvider>().showGoogleError('Không thể đăng nhập Google. Vui lòng thử lại.');
            }
          },
        );
        setState(() => _googleReady = true);
      }).catchError((Object error) {
        if (mounted) {
          setState(() => _googleFailed = true);
          context.read<AuthProvider>().showGoogleError('Không tải được Google Sign-In. Kiểm tra kết nối hoặc Client ID.');
        }
      });
    }
  }

  Future<void> _handleGoogleAccount(GoogleSignInAccount account) async {
    if (!mounted || context.read<AuthProvider>().isBusy) return;
    final token = account.authentication.idToken;
    if (token == null || token.isEmpty) {
      context.read<AuthProvider>().showGoogleError('Google không cung cấp ID Token. Hãy thử lại.');
      return;
    }
    final auth = context.read<AuthProvider>();
    final success = await auth.loginWithGoogle(idToken: token);
    if (!mounted) return;
    if (success) {
      Navigator.of(context).pushNamedAndRemoveUntil(
        auth.user?.roles.contains('ADMIN') == true ? AppRoutes.adminDashboard : AppRoutes.home,
        (_) => false,
      );
    }
  }


  @override
  void dispose() {
    _googleEvents?.cancel();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    final auth = context.read<AuthProvider>();
    final ok = await auth.login(email: _emailController.text, password: _passwordController.text);
    if (!mounted) return;
    if (ok) {
      Navigator.of(context).pushNamedAndRemoveUntil(
        auth.user?.roles.contains('ADMIN') == true ? AppRoutes.adminDashboard : AppRoutes.home,
        (_) => false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    return AuthShell(
      title: 'Chào mừng trở lại',
      subtitle: 'Đăng nhập để tiếp tục hành trình mua sắm cùng PPee.',
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (auth.errorMessage != null) ...[
              PpeeNotice(message: auth.errorMessage!, isError: true),
              const SizedBox(height: AppSpacing.lg),
            ],
            PpeeFormField(
              label: 'Địa chỉ email',
              controller: _emailController,
              requiredField: true,
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.email],
              prefixIcon: Icons.alternate_email_rounded,
              hint: 'tenban@example.com',
              validator: Validators.email,
              enabled: !auth.isBusy,
            ),
            const SizedBox(height: AppSpacing.lg),
            PpeeFormField(
              label: 'Mật khẩu',
              controller: _passwordController,
              requiredField: true,
              prefixIcon: Icons.lock_outline_rounded,
              hint: 'Nhập mật khẩu của bạn',
              obscureText: _obscurePassword,
              textInputAction: TextInputAction.done,
              autofillHints: const [AutofillHints.password],
              suffixIcon: IconButton(
                tooltip: _obscurePassword ? 'Hiện mật khẩu' : 'Ẩn mật khẩu',
                onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                icon: Icon(_obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
              ),
              validator: Validators.loginPassword,
              enabled: !auth.isBusy,
              onFieldSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: 26),
            FilledButton.icon(
              onPressed: auth.isBusy ? null : _submit,
              icon: auth.isBusy
                  ? const SizedBox(width: 19, height: 19, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.login_rounded, size: 19),
              label: PpeeText(auth.isBusy ? 'Đang đăng nhập...' : 'Đăng nhập'),
            ),
            if (kIsWeb) ...[
              const SizedBox(height: AppSpacing.lg),
              Row(children: [
                const Expanded(child: Divider()),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: PpeeText('Hoặc tiếp tục với', style: Theme.of(context).textTheme.bodySmall),
                ),
                const Expanded(child: Divider()),
              ]),
              const SizedBox(height: AppSpacing.md),
              if (_googleReady)
                Center(child: PpeeGoogleSignInButton(disabled: auth.isBusy))
              else if (_googleFailed)
                const PpeeNotice(message: 'Không tải được đăng nhập Google. Bạn vẫn có thể sử dụng email và mật khẩu.', isError: true)
              else
                const Center(child: Padding(
                  padding: EdgeInsets.all(12),
                  child: PpeeText('Đang chuẩn bị đăng nhập Google...'),
                )),
            ],
            const SizedBox(height: AppSpacing.lg),
            Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Flexible(child: PpeeText('Chưa có tài khoản?')),
              TextButton(
                onPressed: auth.isBusy ? null : () => Navigator.pushNamed(context, AppRoutes.register),
                child: const PpeeText('Đăng ký ngay'),
              ),
            ]),
            const SizedBox(height: 5),
            OutlinedButton.icon(
              onPressed: () => Navigator.pushNamed(context, AppRoutes.seller),
              icon: const Icon(Icons.storefront_outlined, size: 18),
              label: const PpeeText('Khám phá Kênh Người Bán'),
            ),
          ],
        ),
      ),
    );
  }
}
