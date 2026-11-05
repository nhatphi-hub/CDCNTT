# Dữ liệu đánh giá recommendation

Giữ nguyên năm file CSV hiện có; số dòng dưới đây không tính header:

| File | Số dòng | Cột |
|---|---:|---|
| `catalog.csv` | 3 | `product_id`, `category_id`, `product_type`, `created_at` |
| `events.csv` | 4 | `user_id`, `product_id`, `event_type`, `occurred_at` |
| `purchases.csv` | 4 | `user_id`, `product_id`, `order_id`, `occurred_at`, `quantity` |
| `views.csv` | 9 | `user_id`, `product_id`, `occurred_at` |
| `data-00.csv` | 5 | `metric`, `n` |

Các file là tài nguyên minh họa/đánh giá, chưa thấy pipeline tự động đọc CSV trong runtime/build hiện tại. Việc không có import không đủ để xác định chúng là file thừa. `data-00.csv` có tên chưa mô tả nội dung; chưa đổi tên vì cần xác nhận nguồn và cách sử dụng trong báo cáo đồ án.

Thuật toán chạy ở backend: `RecommendationEngine`, `RuleBasedRecommendationEngine`, `ImplicitPreferenceModel`, cùng repository/tracking/analytics liên quan. Không coi tập nhỏ này là bằng chứng hiệu quả mô hình; cần protocol chia dữ liệu và thước đo trước khi báo cáo kết quả định lượng. Xem [mô tả kiến trúc](../docs/demo/ARCHITECTURE-AND-FEATURES.md) và [khuyến nghị](../PROJECT_AUDIT_REPORT/FINAL_RECOMMENDATIONS.md).
