# Tài nguyên database PPee

`smart_ecommerce.sql` có tên đuôi `.sql` nhưng nội dung là **PostgreSQL custom archive** (magic `PGDMP`), không phải SQL dạng văn bản. `pg_restore --list` đã đọc được mục lục offline gồm 24 bảng và các mục TABLE DATA. Chưa restore hoặc kết nối database để xác minh schema hiện hành.

Giữ nguyên tên, đường dẫn và toàn bộ byte của bản lưu vì đây là dữ liệu dự án. Không đưa vào `_REVIEW_BEFORE_DELETE`, không dùng `psql -f` với file này và không mở rồi lưu lại như văn bản. Mục lục archive không chứng minh dữ liệu bên trong tương ứng với database đang vận hành.

Trong lúc audit, file trên đĩa được thay thế ngoài phạm vi chỉnh sửa đã khai báo: baseline 85.848 byte, bản quan sát sau đó 74.356 byte, last-write 16:45:46 ngày 03/11/2026 (UTC+07). Cả hai lần đọc đều nhận định dạng custom archive và 24 bảng. Không xác định tác nhân thay thế; công việc audit chỉ đọc file và giữ bản hiện tại, không ghi/restore đè. Hash trước/sau được ghi riêng trong [bằng chứng](../PROJECT_AUDIT_REPORT/evidence/concurrent-file-change.json); chưa so sánh dữ liệu bên trong hai bản.

Các script bổ sung schema nằm trong [backend/src/main/resources/database](../backend/src/main/resources/database/). Bao gồm coupon, payment, shipping, recommendation tracking, Google login, các giai đoạn seller, order enhancements và schema alignment. Script coupon đã được chuyển khỏi `src/main/java` về thư mục này với SHA-256 giữ nguyên.

Đây là script thủ công, chưa có Flyway/Liquibase hoặc thứ tự migration do build quản lý. Không chạy tất cả theo thứ tự tên file. Trước thao tác database cần đối chiếu schema, lịch sử thực thi, dữ liệu và kế hoạch khôi phục; đợt tái cấu trúc không thực thi bất kỳ script nào.

Nếu đổi đuôi archive thành `.dump` hoặc `.backup` sau này, cần cập nhật hướng dẫn và các công cụ vận hành liên quan. Đợt này chỉ ghi nhận định dạng để tránh phá hỏng bản lưu. Xem [báo cáo](../PROJECT_AUDIT_REPORT/VALIDATION_REPORT.md).
