import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/theme/app_colors.dart';

/// Consistent section surfaces used in the storefront and management consoles.
class PpeeSurface extends StatelessWidget {
  const PpeeSurface({super.key, required this.child, this.padding = const EdgeInsets.all(24), this.backgroundColor});
  final Widget child;
  final EdgeInsetsGeometry padding;
  final Color? backgroundColor;

  @override
  Widget build(BuildContext context) => Container(
    padding: padding,
    decoration: BoxDecoration(
      color: backgroundColor ?? AppColors.white,
      borderRadius: BorderRadius.circular(22),
      border: Border.all(color: AppColors.gray300.withValues(alpha: .85)),
      boxShadow: [BoxShadow(
        color: AppColors.primaryDeep.withValues(alpha: .035),
        blurRadius: 22, offset: const Offset(0, 7),
      )],
    ),
    child: child,
  );
}

/// Label and helper live outside the field, so focus/errors cannot obscure the label.
/// This wraps Flutter's native TextFormField and preserves native validation,
/// autofill, keyboard navigation, FormState, focus and controllers.
class PpeeFormField extends StatelessWidget {
  const PpeeFormField({
    super.key,
    required this.label,
    required this.controller,
    this.hint,
    this.helper,
    this.prefixIcon,
    this.suffixIcon,
    this.validator,
    this.keyboardType,
    this.textInputAction,
    this.obscureText = false,
    this.enabled = true,
    this.maxLines = 1,
    this.minLines,
    this.maxLength,
    this.textCapitalization = TextCapitalization.none,
    this.autofillHints,
    this.onFieldSubmitted,
    this.onChanged,
    this.requiredField = false,
    this.autovalidateMode = AutovalidateMode.onUserInteraction,
  });

  final String label;
  final TextEditingController controller;
  final String? hint;
  final String? helper;
  final IconData? prefixIcon;
  final Widget? suffixIcon;
  final FormFieldValidator<String>? validator;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final bool obscureText;
  final bool enabled;
  final int maxLines;
  final int? minLines;
  final int? maxLength;
  final TextCapitalization textCapitalization;
  final Iterable<String>? autofillHints;
  final ValueChanged<String>? onFieldSubmitted;
  final ValueChanged<String>? onChanged;
  final bool requiredField;
  final AutovalidateMode autovalidateMode;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Padding(
        padding: const EdgeInsets.only(bottom: 9),
        child: RichText(text: TextSpan(children: [
          TextSpan(text: tr(context, label), style: const TextStyle(
            color: AppColors.black, fontWeight: FontWeight.w700, fontSize: 13.5)),
          if (requiredField) const TextSpan(text: '  *', style: TextStyle(
            color: AppColors.error, fontWeight: FontWeight.w700)),
        ])),
      ),
      TextFormField(
        controller: controller,
        enabled: enabled,
        obscureText: obscureText,
        maxLines: obscureText ? 1 : maxLines,
        minLines: obscureText ? null : minLines,
        maxLength: maxLength,
        textCapitalization: textCapitalization,
        keyboardType: keyboardType,
        textInputAction: textInputAction,
        autofillHints: autofillHints,
        validator: validator == null ? null : (value) {
          final message = validator!(value);
          if (message == null) return null;
          return context.read<LanguageController>().translate(message);
        },
        autovalidateMode: autovalidateMode,
        onFieldSubmitted: onFieldSubmitted,
        onChanged: onChanged,
        decoration: InputDecoration(
          hintText: hint == null ? null : tr(context, hint!),
          helperText: helper == null ? null : tr(context, helper!),
          prefixIcon: prefixIcon == null ? null : Icon(prefixIcon, size: 21),
          suffixIcon: suffixIcon,
        ),
      ),
    ],
  );
}

/// Automatically uses one column in mobile dialogs and two on wider screens.
class PpeeFormGrid extends StatelessWidget {
  const PpeeFormGrid({super.key, required this.children, this.gap = 16, this.breakpoint = 580});
  final List<Widget> children;
  final double gap;
  final double breakpoint;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final columns = constraints.maxWidth >= breakpoint ? 2 : 1;
      final fieldWidth = columns == 1 ? constraints.maxWidth : (constraints.maxWidth - gap) / 2;
      return Wrap(
        spacing: gap,
        runSpacing: gap,
        children: children.map((child) => SizedBox(width: fieldWidth, child: child)).toList(),
      );
    },
  );
}

class PpeeNotice extends StatelessWidget {
  const PpeeNotice({super.key, required this.message, this.isError = false, this.icon});
  final String message;
  final bool isError;
  final IconData? icon;

  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: isError ? AppColors.errorSoft : AppColors.primarySoft,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: isError ? AppColors.error.withValues(alpha: .2) : AppColors.primary.withValues(alpha: .2)),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon ?? (isError ? Icons.error_outline_rounded : Icons.info_outline_rounded),
          color: isError ? AppColors.error : AppColors.primary, size: 21),
        const SizedBox(width: 11),
        Expanded(child: PpeeText(message, style: TextStyle(
          fontSize: 13.5, height: 1.5, fontWeight: FontWeight.w600,
          color: isError ? AppColors.error : AppColors.primaryDark))),
      ]),
    ),
  );
}

/// Unified empty/error/loading content for actual API-backed screens.
class PpeeStateView extends StatelessWidget {
  const PpeeStateView({
    super.key, required this.title, required this.message, required this.icon,
    this.actionLabel, this.onAction,
  });
  final String title;
  final String message;
  final IconData icon;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 390),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 35),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(
            width: 74, height: 74,
            decoration: const BoxDecoration(color: AppColors.primarySoft, shape: BoxShape.circle),
            child: Icon(icon, color: AppColors.primary, size: 33),
          ),
          const SizedBox(height: 19),
          PpeeText(title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          PpeeText(message, textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium),
          if (onAction != null && actionLabel != null) ...[
            const SizedBox(height: 20),
            FilledButton.icon(onPressed: onAction,
              icon: const Icon(Icons.arrow_forward_rounded, size: 18), label: PpeeText(actionLabel!)),
          ],
        ]),
      ),
    ),
  );
}

class PpeeLoadingView extends StatelessWidget {
  const PpeeLoadingView({super.key, this.message = 'Đang tải dữ liệu...'});
  final String message;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(30),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const SizedBox(width: 28, height: 28, child: CircularProgressIndicator(strokeWidth: 3)),
        const SizedBox(height: 16),
        PpeeText(message, style: Theme.of(context).textTheme.bodyMedium),
      ]),
    ),
  );
}
