import '../config/api_config.dart';

class ImageUrl {
  ImageUrl._();

  static String resolve(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return '';
    final parsed = Uri.tryParse(trimmed);
    if (parsed != null && parsed.hasScheme) return trimmed;
    return Uri.parse('${ApiConfig.baseUrl}/').resolve(trimmed).toString();
  }
}
