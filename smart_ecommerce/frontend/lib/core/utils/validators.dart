class Validators {
  Validators._();

  static String? requiredField(String? value, String label) {
    if (value == null || value.trim().isEmpty) return '$label không được để trống';
    return null;
  }

  static String? email(String? value) {
    final requiredError = requiredField(value, 'Email');
    if (requiredError != null) return requiredError;
    final emailRegex = RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$');
    if (!emailRegex.hasMatch(value!.trim())) return 'Email không hợp lệ';
    if (value.trim().length > 150) return 'Email không được vượt quá 150 ký tự';
    return null;
  }

  static String? loginPassword(String? value) => requiredField(value, 'Mật khẩu');

  static String? registerPassword(String? value) {
    final requiredError = requiredField(value, 'Mật khẩu');
    if (requiredError != null) return requiredError;
    if (value!.length < 6 || value.length > 100) return 'Mật khẩu phải từ 6 đến 100 ký tự';
    if (value.codeUnits.length > 72) return 'Mật khẩu quá dài đối với BCrypt';
    return null;
  }

  static String? fullName(String? value) {
    final requiredError = requiredField(value, 'Họ tên');
    if (requiredError != null) return requiredError;
    if (value!.trim().length > 150) return 'Họ tên không được vượt quá 150 ký tự';
    return null;
  }
}
