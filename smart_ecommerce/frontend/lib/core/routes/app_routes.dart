import '../l10n/language_controller.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../providers/auth_provider.dart';
import '../../repositories/payment_repository.dart';
import '../../screens/address/address_screen.dart';
import '../../screens/auth/login_screen.dart';
import '../../screens/auth/register_screen.dart';
import '../../screens/cart/cart_screen.dart';
import '../../screens/category/category_screen.dart';
import '../../screens/checkout/checkout_screen.dart';
import '../../screens/favorite/favorites_screen.dart';
import '../../screens/home/home_screen.dart';
import '../../screens/order/order_detail_screen.dart';
import '../../screens/order/order_list_screen.dart';
import '../../screens/order/order_success_screen.dart';
import '../../screens/product/product_detail_screen.dart';
import '../../screens/product/product_list_screen.dart';
import '../../screens/profile/profile_screen.dart';
import '../../screens/review/my_reviews_screen.dart';
import '../../screens/settings/settings_screen.dart';
import '../../screens/ai/activity_history_screen.dart';
import '../../screens/notifications/notification_center_screen.dart';
import '../../models/recommendation.dart';
import '../../screens/seller/seller_entry_screen.dart';
import '../../screens/seller/seller_setup_screen.dart';
import '../../screens/admin/admin_returns_screen.dart';
import '../../screens/admin/admin_dashboard_screen.dart';

class AppRoutes {
  AppRoutes._();

  static const root = '/';
  static const login = '/login';
  static const register = '/register';
  static const home = '/home';
  static const profile = '/profile';
  static const settings = '/settings';
  static const favorites = '/favorites';
  static const myReviews = '/reviews/mine';
  static const categories = '/categories';
  static const category = '/category';
  static const products = '/products';
  static const product = '/product';
  static const cart = '/cart';
  static const checkout = '/checkout';
  static const addresses = '/addresses';
  static const orders = '/orders';
  static const order = '/order';
  static const orderSuccess = '/order-success';
  static const activityHistory = '/activity-history';
  static const notifications = '/notifications';
  static const seller = '/seller';
  static const sellerSetup = '/seller/setup';
  static const sellerDashboard = '/seller/dashboard';
  static const adminReturns = '/admin/returns';
  static const adminDashboard = '/admin';

  static Route<dynamic> onGenerateRoute(RouteSettings routeSettings) {
    return MaterialPageRoute(
      settings: routeSettings,
      builder: (context) {
        final auth = context.watch<AuthProvider>();
        if (auth.status == AuthStatus.initializing) return const _Splash();

        final route = routeSettings.name ?? root;
        final protected = (route == adminDashboard || route == adminReturns) || route == profile ||
            route == AppRoutes.settings ||
            route == favorites ||
            route == myReviews ||
            route == cart ||
            route == checkout ||
            route == addresses ||
            route == orders ||
            route == activityHistory ||
            route == notifications ||
            route.startsWith('$order/') ||
            route.startsWith('$orderSuccess/');
        final customerOnly = route == favorites ||
            route == myReviews ||
            route == cart ||
            route == checkout ||
            route == addresses ||
            route == orders ||
            route == activityHistory ||
            route == notifications ||
            route.startsWith('$order/') ||
            route.startsWith('$orderSuccess/');

        if (protected && !auth.isAuthenticated) return const LoginScreen();
        if ((route == adminDashboard || route == adminReturns) && !(auth.user?.roles.contains('ADMIN') ?? false)) {
          return const _Forbidden();
        }
        if (route == adminReturns) return const AdminReturnsScreen();
        if (route == adminDashboard) return const AdminDashboardScreen();
        if (customerOnly && !auth.isCustomer) return const _Forbidden();
        if ((route == login || route == register) && auth.isAuthenticated) return const HomeScreen();

        if (route == seller || route == sellerDashboard) return const SellerEntryScreen();
        if (route == sellerSetup) {
          if (!auth.isAuthenticated) return const LoginScreen();
          return const SellerSetupScreen();
        }

        if (route == root || route == home) return const HomeScreen();
        if (route == login) return const LoginScreen();
        if (route == register) return const RegisterScreen();
        if (route == profile) return const ProfileScreen();
        if (route == settings) return const SettingsScreen();
        if (route == favorites) return const FavoritesScreen();
        if (route == myReviews) return const MyReviewsScreen();
        if (route == categories) return const CategoryScreen();
        if (route == products) return ProductListScreen(initialSearch: routeSettings.arguments as String?);
        if (route == cart) return const CartScreen();
        if (route == checkout) return CheckoutScreen(paymentRepository: context.read<PaymentRepository>());
        if (route == addresses) return AddressScreen(selectionMode: routeSettings.arguments == true);
        if (route == orders) return const OrderListScreen();
        if (route == activityHistory) return const ActivityHistoryScreen();
        if (route == notifications) return const NotificationCenterScreen();

        if (route.startsWith('$product/')) {
          final id = route.substring('$product/'.length);
          if (id.isNotEmpty) return ProductDetailScreen(productId: id, recommendationSource: routeSettings.arguments is RecommendationSource ? routeSettings.arguments as RecommendationSource : null);
        }
        if (route.startsWith('$category/')) {
          final id = route.substring('$category/'.length);
          if (id.isNotEmpty) return CategoryProductsScreen(categoryId: id, categoryName: routeSettings.arguments as String?);
        }
        if (route.startsWith('$orderSuccess/')) {
          final id = route.substring('$orderSuccess/'.length);
          if (id.isNotEmpty) return OrderSuccessScreen(orderId: id);
        }
        if (route.startsWith('$order/')) {
          final id = route.substring('$order/'.length);
          if (id.isNotEmpty) return OrderDetailScreen(orderId: id);
        }
        return const _NotFound();
      },
    );
  }
}

class _Splash extends StatelessWidget {
  const _Splash();
  @override
  Widget build(BuildContext context) => const Scaffold(body: Center(child: CircularProgressIndicator()));
}

class _NotFound extends StatelessWidget {
  const _NotFound();
  @override
  Widget build(BuildContext context) => const Scaffold(body: Center(child: PpeeText('Không tìm thấy trang yêu cầu.')));
}


class _Forbidden extends StatelessWidget {
  const _Forbidden();

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const PpeeText('Không có quyền truy cập')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.lock_outline, size: 48),
                const SizedBox(height: 16),
                const PpeeText(
                  'Tài khoản của bạn không có quyền truy cập trang này.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => Navigator.pushNamedAndRemoveUntil(
                    context,
                    AppRoutes.home,
                    (_) => false,
                  ),
                  child: const PpeeText('Về trang chủ'),
                ),
              ],
            ),
          ),
        ),
      );
}
