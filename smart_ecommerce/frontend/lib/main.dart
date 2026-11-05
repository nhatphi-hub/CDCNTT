import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'core/l10n/language_controller.dart';
import 'repositories/seller_coupon_repository.dart';
import 'repositories/seller_activity_repository.dart';
import 'repositories/shop_return_repository.dart';
import 'repositories/admin_return_repository.dart';
import 'repositories/admin_console_repository.dart';
import 'package:provider/provider.dart';

import 'core/constants/app_constants.dart';
import 'core/network/api_client.dart';
import 'core/routes/app_routes.dart';
import 'core/routes/ppee_route_observer.dart';
import 'core/theme/app_theme.dart';
import 'providers/address_provider.dart';
import 'providers/auth_provider.dart';
import 'providers/cart_provider.dart';
import 'providers/category_provider.dart';
import 'providers/checkout_provider.dart';
import 'providers/favorite_provider.dart';
import 'providers/order_provider.dart';
import 'providers/product_provider.dart';
import 'providers/product_variant_provider.dart';
import 'providers/review_provider.dart';
import 'providers/recommendation_provider.dart';
import 'providers/behavior_provider.dart';
import 'repositories/address_repository.dart';
import 'repositories/auth_repository.dart';
import 'repositories/cart_repository.dart';
import 'repositories/category_repository.dart';
import 'repositories/favorite_repository.dart';
import 'repositories/order_repository.dart';
import 'repositories/payment_repository.dart';
import 'repositories/product_repository.dart';
import 'repositories/product_variant_repository.dart';
import 'repositories/review_repository.dart';
import 'repositories/shipping_repository.dart';
import 'repositories/seller_shop_repository.dart';
import 'repositories/seller_product_repository.dart';
import 'repositories/seller_order_repository.dart';
import 'repositories/seller_review_repository.dart';
import 'repositories/recommendation_repository.dart';
import 'repositories/behavior_repository.dart';
import 'services/auth_service.dart';
import 'services/token_storage.dart';
import 'widgets/ppee_chat_overlay.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final language = LanguageController();
  await language.initialize();
  final api = ApiClient();
  final auth = AuthProvider(
    AuthRepository(authService: AuthService(api), tokenStorage: TokenStorage(), apiClient: api),
  );
  api.onUnauthorized = auth.handleUnauthorized;
  runApp(BoutiqueApp(api: api, auth: auth, language: language));
  auth.initialize();
}

class BoutiqueApp extends StatelessWidget {
  const BoutiqueApp({super.key, required this.api, required this.auth, required this.language});
  final ApiClient api;
  final AuthProvider auth;
  final LanguageController language;

  @override
  Widget build(BuildContext context) => MultiProvider(
        providers: [
          ChangeNotifierProvider.value(value: language),
          ChangeNotifierProvider.value(value: auth),
          ChangeNotifierProvider(create: (_) => ProductProvider(ProductRepository(api))),
          Provider<CategoryRepository>(create: (_) => CategoryRepository(api)),
          ChangeNotifierProvider(create: (context) => CategoryProvider(context.read<CategoryRepository>())),
          ChangeNotifierProxyProvider<AuthProvider, FavoriteProvider>(
            create: (_) => FavoriteProvider(FavoriteRepository(api)),
            update: (_, auth, provider) {
              final value = provider ?? FavoriteProvider(FavoriteRepository(api));
              value.syncUser(auth.user?.id);
              return value;
            },
          ),
          ChangeNotifierProxyProvider<AuthProvider, ReviewProvider>(
            create: (_) => ReviewProvider(ReviewRepository(api)),
            update: (_, auth, provider) {
              final value = provider ?? ReviewProvider(ReviewRepository(api));
              value.syncUser(auth.user?.id);
              return value;
            },
          ),
          ChangeNotifierProxyProvider<AuthProvider, CartProvider>(
            create: (_) => CartProvider(CartRepository(api)),
            update: (_, auth, provider) {
              final value = provider ?? CartProvider(CartRepository(api));
              value.syncUser(auth.user?.id);
              return value;
            },
          ),
          ChangeNotifierProxyProvider<AuthProvider, AddressProvider>(
            create: (_) => AddressProvider(AddressRepository(api)),
            update: (_, auth, provider) {
              final value = provider ?? AddressProvider(AddressRepository(api));
              value.syncUser(auth.user?.id);
              return value;
            },
          ),
          ChangeNotifierProxyProvider<AuthProvider, OrderProvider>(
            create: (_) => OrderProvider(OrderRepository(api)),
            update: (_, auth, provider) {
              final value = provider ?? OrderProvider(OrderRepository(api));
              value.syncUser(auth.user?.id);
              return value;
            },
          ),
          ChangeNotifierProxyProvider<AuthProvider, CheckoutProvider>(
            create: (_) => CheckoutProvider(),
            update: (_, auth, provider) {
              final value = provider ?? CheckoutProvider();
              value.syncUser(auth.user?.id);
              return value;
            },
          ),
          ChangeNotifierProvider(create: (_) => ProductVariantProvider(ProductVariantRepository(api))),
          ChangeNotifierProxyProvider<AuthProvider, RecommendationProvider>(
            create: (_) => RecommendationProvider(RecommendationRepository(api)),
            update: (_, auth, provider) {
              final value = provider ?? RecommendationProvider(RecommendationRepository(api));
              value.syncUser(auth.user?.id);
              return value;
            },
          ),
          ChangeNotifierProxyProvider<AuthProvider, BehaviorProvider>(
            create: (_) => BehaviorProvider(BehaviorRepository(api)),
            update: (_, auth, provider) {
              final value = provider ?? BehaviorProvider(BehaviorRepository(api));
              value.syncUser(auth.user?.id);
              return value;
            },
          ),
          Provider.value(value: PaymentRepository(api)),
          Provider.value(value: ShippingRepository(api)),
          Provider.value(value: SellerShopRepository(api)),
          Provider.value(value: SellerProductRepository(api)),
          Provider.value(value: SellerOrderRepository(api)),
          Provider.value(value: SellerReviewRepository(api)),
          Provider.value(value: SellerCouponRepository(api)),
          Provider.value(value: SellerActivityRepository(api)),
          Provider.value(value: ShopReturnRepository(api)),
          Provider.value(value: AdminReturnRepository(api)),
          Provider.value(value: AdminConsoleRepository(api)), 
        ],
        child: Builder(builder: (context) => MaterialApp(
          locale: context.watch<LanguageController>().locale,
          supportedLocales: const [Locale('vi'), Locale('en')],
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          title: AppConstants.appName,
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light,
          navigatorKey: PpeeChatOverlay.navigatorKey,
          navigatorObservers: [ppeeRouteObserver],
          initialRoute: AppRoutes.root,
          onGenerateRoute: AppRoutes.onGenerateRoute,
          builder: (context, child) => PpeeChatHost(api: api, child: child),
        )),
      );
}
