import '../../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/constants/app_spacing.dart';
import '../../core/routes/app_routes.dart';
import '../../core/utils/validators.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/auth_shell.dart';
import '../../widgets/ppee_ui.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});
  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<AuthProvider>().clearError();
    });
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    final auth = context.read<AuthProvider>();
    final ok = await auth.register(
      fullName: _nameController.text,
      email: _emailController.text,
      password: _passwordController.text,
    );
    if (!mounted) return;
    if (ok) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: PpeeText('Đăng ký thành công. Vui lòng đăng nhập.')));
      Navigator.of(context).pushNamedAndRemoveUntil(AppRoutes.login, (_) => false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    return AuthShell(
      title: 'Tạo tài khoản PPee',
      subtitle: 'Mọi món đồ bạn yêu thích, giỏ hàng và đơn mua trong một nơi.',
      child: Form(
        key: _formKey,
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          if (auth.errorMessage != null) ...[
            PpeeNotice(message: auth.errorMessage!, isError: true),
            const SizedBox(height: AppSpacing.lg),
          ],
          PpeeFormField(
            label: 'Họ và tên', controller: _nameController,
            requiredField: true, prefixIcon: Icons.person_outline_rounded,
            hint: 'Tên đầy đủ của bạn', validator: Validators.fullName,
            enabled: !auth.isBusy, textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.name],
          ),
          const SizedBox(height: AppSpacing.lg),
          PpeeFormField(
            label: 'Email', controller: _emailController,
            requiredField: true, prefixIcon: Icons.alternate_email_rounded,
            keyboardType: TextInputType.emailAddress,
            hint: 'tenban@example.com', validator: Validators.email,
            textInputAction: TextInputAction.next, enabled: !auth.isBusy,
            autofillHints: const [AutofillHints.email],
          ),
          const SizedBox(height: AppSpacing.lg),
          PpeeFormField(
            label: 'Mật khẩu', controller: _passwordController,
            requiredField: true, prefixIcon: Icons.lock_outline_rounded,
            hint: 'Tạo mật khẩu bảo mật', helper: 'Từ 6 đến 100 ký tự',
            obscureText: _obscurePassword, validator: Validators.registerPassword,
            enabled: !auth.isBusy, textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.newPassword],
            suffixIcon: IconButton(
              tooltip: _obscurePassword ? 'Hiện mật khẩu' : 'Ẩn mật khẩu',
              onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
              icon: Icon(_obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          PpeeFormField(
            label: 'Xác nhận mật khẩu', controller: _confirmController,
            requiredField: true, prefixIcon: Icons.verified_user_outlined,
            hint: 'Nhập lại mật khẩu', obscureText: _obscurePassword,
            textInputAction: TextInputAction.done, enabled: !auth.isBusy,
            autofillHints: const [AutofillHints.newPassword],
            validator: (value) => value != _passwordController.text
                ? 'Mật khẩu xác nhận chưa khớp' : null,
            onFieldSubmitted: (_) => _submit(),
          ),
          const SizedBox(height: 26),
          FilledButton.icon(
            onPressed: auth.isBusy ? null : _submit,
            icon: auth.isBusy
                ? const SizedBox(width: 19, height: 19, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Icon(Icons.person_add_alt_1_rounded, size: 19),
            label: PpeeText(auth.isBusy ? 'Đang tạo tài khoản...' : 'Tạo tài khoản'),
          ),
          const SizedBox(height: AppSpacing.md),
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            const Flexible(child: PpeeText('Đã có tài khoản?')),
            TextButton(
              onPressed: auth.isBusy ? null : () => Navigator.pushReplacementNamed(context, AppRoutes.login),
              child: const PpeeText('Đăng nhập'),
            ),
          ]),
          OutlinedButton.icon(
            onPressed: () => Navigator.pushNamed(context, AppRoutes.seller),
            icon: const Icon(Icons.storefront_outlined, size: 18),
            label: const PpeeText('Tìm hiểu cách mở gian hàng'),
          ),
        ]),
      ),
    );
  }
}
