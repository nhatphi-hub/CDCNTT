# Backend PPee

Java 21, Spring Boot 4.1.1, Spring Security/JWT, Spring Data JPA và PostgreSQL. Entry point là `SmartEcommerceApplication`; component scan bắt đầu tại `com.smart_ecommerce.backend`.

## Phân lớp

| Package/thư mục | Trách nhiệm |
|---|---|
| `controller/` | Endpoint REST và DTO đầu vào/đầu ra |
| `service/` | Nghiệp vụ, transaction, kiểm tra chủ sở hữu và điều phối truy vấn |
| `service/payment/` | Hợp đồng payment provider và triển khai mock hiện có |
| `repository/` | JPA, truy vấn catalog, analytics và data mining |
| `entity/` | Ánh xạ database; không dùng thay DTO public |
| `dto/<chức năng>/` | Hợp đồng request/response và validation đầu vào |
| `mapper/` | Ánh xạ thuần entity → DTO, không truy vấn DB hoặc kiểm tra quyền |
| `config/`, `security/` | Cấu hình, kiểm tra môi trường, JWT filter, CORS và phân quyền |
| `exception/` | Chuẩn hóa lỗi API |
| `src/main/resources/database/` | Script SQL thủ công, gồm `coupon-module.sql` đã chuyển khỏi cây Java |
| `src/main/resources/META-INF/spring.factories` | Đăng ký bộ kiểm tra môi trường; phải giữ khi đóng gói |
| `src/test/` | Unit test, MockMvc security test và integration test có guard database riêng |
| `uploads/products/` | Ảnh thực của ứng dụng; dữ liệu vận hành, không phải cache |

Recommendation hiện nằm trong `RecommendationEngine`, `RuleBasedRecommendationEngine`, `ImplicitPreferenceModel` và các service tracking/analytics. Analytics và data mining dùng service + repository riêng. Giữ chung module backend để component scan, transaction và các lời gọi hiện tại không đổi.

## Kiểm tra

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Trong môi trường đã có Maven/dependencies, có thể thêm `-o` để chạy offline. Tham số local repository chỉ phục vụ máy hiện tại, không cần đưa vào `pom.xml`.

Lượt kiểm tra 03/11/2026: đóng gói khi bỏ test thành công; bộ mặc định có 215/219 test đạt và 4 lỗi cấu hình có sẵn. Xem [báo cáo kiểm tra](../PROJECT_AUDIT_REPORT/VALIDATION_REPORT.md); việc bỏ test chỉ chứng minh khả năng compile/package.

Vai trò người mua trong code là `CUSTOMER`, cùng `ADMIN`, `STAFF`, `SELLER`. Không đổi `CUSTOMER` thành `USER` khi tổ chức package. `TestController` phục vụ endpoint public `/api/test` đang được security test và tài liệu demo sử dụng; giữ trong source chính.

Tài liệu liên quan: [cấu hình bảo mật](docs/security-configuration.md), [payment API](docs/payment-api.md), [chuẩn bị môi trường local](../docs/demo/LOCAL-RUN-SETUP.md), [bản lưu database](../database/README.md). Khởi động backend có thể ghi role hoặc bootstrap account qua `RoleDataInitializer`; không dùng startup để thay thế kiểm tra compile trên database hiện có.
