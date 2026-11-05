# Frontend PPee

Ứng dụng Flutter Web, dùng Provider cho state management và HTTP repositories để gọi backend. Tên package Dart vẫn là `frontend` để giữ nguyên import và cấu hình nền tảng.

## Tổ chức mã

| Thư mục | Vai trò |
|---|---|
| `lib/main.dart` | Khởi tạo ngôn ngữ, API client, auth và đăng ký providers/repositories |
| `lib/core/config/`, `constants/` | Base URL, endpoint dùng chung và hằng số |
| `lib/core/network/` | HTTP client, authorization và lỗi API |
| `lib/core/routes/` | Route public, người mua, người bán và quản trị |
| `lib/core/theme/`, `responsive/`, `l10n/`, `utils/` | Theme, bố cục, ngôn ngữ, định dạng và URL ảnh |
| `lib/screens/<chức năng>/` | Màn hình theo chức năng; chi tiết đơn quản trị có file riêng `admin_order_detail_screen.dart` |
| `lib/widgets/` | Thành phần UI dùng chung và Google Sign-In theo nền tảng |
| `lib/models/` | Mô hình JSON và dữ liệu màn hình |
| `lib/providers/` | State, tải dữ liệu và đồng bộ theo tài khoản |
| `lib/repositories/` | Truy cập API theo chức năng; auth repository còn điều phối phiên đăng nhập |
| `lib/services/` | Auth HTTP service, lưu token, JWT utility và cầu nối Google SDK |
| `test/` | Test hiện có; `support/localized_test_app.dart` cung cấp fixture ngôn ngữ dùng chung |
| `web/`, các thư mục nền tảng | Bootstrap, icon, cấu hình và tích hợp Flutter do từng nền tảng sử dụng |

`AuthRepository` và `AuthService` có trách nhiệm khác nhau, không phải hai phiên bản thay thế nhau. `google_sign_in_button_stub.dart` và `google_sign_in_button_web.dart` được chọn bằng conditional export; phải giữ cả hai.

## Lệnh làm việc

```powershell
# Khi chưa có dependencies: flutter pub get
flutter analyze --no-pub
flutter test --no-pub
flutter build web --no-pub
```

Base URL được khai báo trong `lib/core/config/api_config.dart` và nhận `API_BASE_URL` qua `--dart-define`. Mặc định là `http://localhost:8080`; backend phải cho phép đúng origin của frontend trong CORS. Xem [hướng dẫn môi trường](../docs/demo/ENVIRONMENT.md) và [báo cáo xác minh](../PROJECT_AUDIT_REPORT/VALIDATION_REPORT.md).

Giữ `pubspec.lock`, cấu hình icon/manifest và các file nền tảng. `.dart_tool/`, `build/`, `.firebase/`, generated plugin metadata là dữ liệu do công cụ quản lý, không chuyển vào thư mục chờ xóa. Ảnh sản phẩm đến từ backend/URL, không phải các file icon của ứng dụng. Không có thư mục assets riêng được khai báo trong pubspec hiện tại.
