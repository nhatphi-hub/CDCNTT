import '../core/l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/routes/app_routes.dart';
import '../providers/auth_provider.dart';
import '../providers/favorite_provider.dart';
import '../providers/recommendation_provider.dart';

class FavoriteButton extends StatefulWidget {
  const FavoriteButton({super.key, required this.productId});
  final String productId;

  @override
  State<FavoriteButton> createState() => _FavoriteButtonState();
}

class _FavoriteButtonState extends State<FavoriteButton> {
  String? _requestedForUser;

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final favorites = context.watch<FavoriteProvider>();

    if (auth.isCustomer && _requestedForUser != auth.user?.id) {
      _requestedForUser = auth.user?.id;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        context.read<FavoriteProvider>().ensure(widget.productId);
      });
    } else if (!auth.isAuthenticated) {
      _requestedForUser = null;
    }

    final selected = favorites.isFavorite(widget.productId);
    final loading = favorites.isLoading(widget.productId);

    return IconButton(
      tooltip: selected ? 'Bỏ yêu thích' : 'Yêu thích',
      onPressed: loading
          ? null
          : () async {
              if (!auth.isAuthenticated) {
                Navigator.pushNamed(context, AppRoutes.login);
                return;
              }
              if (!auth.isCustomer) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: PpeeText('Yêu thích chỉ dành cho tài khoản CUSTOMER.'),
                  ),
                );
                return;
              }
              final ok = await context.read<FavoriteProvider>().toggle(widget.productId);
              if (ok && context.mounted) {
                // A favorite changes the profile; refresh personalized picks.
                context.read<RecommendationProvider>()
                    .loadHome(personalizedAllowed: true);
              }
              if (!ok && context.mounted) {
                final message = context.read<FavoriteProvider>().error ??
                    'Không thể cập nhật yêu thích.';
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: PpeeText(message)),
                );
              }
            },
      icon: loading
          ? const SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(selected ? Icons.favorite : Icons.favorite_border),
    );
  }
}
