class ApiConfig {
  ApiConfig._();

  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration requestTimeout = Duration(seconds: 30);

  static const String login = '/api/users/login';
  static const String register = '/api/users/register';
  static const String googleLogin = '/api/users/google';
  static const String currentUser = '/api/users/me';
  static const String googleLink = '/api/users/google/link';
  static const String products = '/api/products';
  static const String categories = '/api/categories';
  static const String favorites = '/api/favorites';
  static const String reviews = '/api/reviews';
  static const String myReviews = '/api/reviews/mine';

  static String product(String id) => '$products/$id';
  static String productImages(String id) => '$products/$id/images';
  static String productsByCategory(String categoryId) => '$products/category/$categoryId';
  static String productSearch = '$products/search';
  static String category(String id) => '$categories/$id';
  static String categoryChildren(String id) => '$categories/$id/children';
  static String productReviews(String productId) => '/api/reviews/products/$productId';
  static String reviewEligibility(String productId) => '/api/reviews/products/$productId/eligibility';
  static String favorite(String productId) => '$favorites/$productId';
  static String favoriteCheck(String productId) => '$favorites/check/$productId';
  static String review(String reviewId) => '$reviews/$reviewId';
}
