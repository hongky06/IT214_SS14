1. Sơ Đồ Chuyển Trạng Thái (Order State Machine)                       [ Bắt đầu: Tạo đơn ]
   │
   ▼
   ┌──────────────┐
   │   PENDING    │
   └──────┬───────┘
   │
   ┌───────────────────────┼──────────────────────┐
   │                       │                      │
   Payment = SUCCESS       Payment = REJECTED     Quá hạn (Timeout 5m)
   │                       │               & Payment = NOT_FOUND/FAIL
   ▼                       ▼                      ▼
   ┌──────────────┐        ┌──────────────┐       ┌──────────────┐
   │     PAID     │        │   CANCELED   │       │    FAILED    │
   └──────┬───────┘        └──────────────┘       └──────────────┘
   │
   Đóng gói & Giao hàng
   │
   ▼
   ┌──────────────┐
   │   SHIPPED    │
   └──────┬───────┘
   │
   Khách nhận hàng
   │
   ▼
   ┌──────────────┐
   │  COMPLETED   │
   └──────────────┘
   Giải thích điều kiện chuyển trạng thái:PENDING $\rightarrow$ PAID: Payment Service trừ tiền thành công, gửi event SUCCESS. Đơn hàng đủ điều kiện sang khâu xuất kho/vận chuyển.PENDING $\rightarrow$ CANCELED: Payment Service gửi event REJECTED (thẻ hết hạn, sai OTP, tài khoản không đủ số dư, người dùng hủy thao tác).PENDING $\rightarrow$ FAILED: Đơn hàng bị treo quá 5 phút mà không nhận được phản hồi (Network Timeout / Event bị thất lạc), và sau khi đối soát (reconcile) kiểm tra thấy thanh toán chưa hoàn tất hoặc thất bại.PAID $\rightarrow$ SHIPPED $\rightarrow$ COMPLETED: Các bước tiếp theo trong vòng đời đơn hàng khi giao dịch tiền tệ đã hoàn tất hợp lệ.2. Phân Tích Điểm Cải Tiến Trong Xử Lý Lắng Nghe Sự KiệnIdempotency (Tính lũy thừa): Tránh trường hợp message broker gửi lại event nhiều lần (At-least-once delivery) dẫn đến việc ghi đè trạng thái của đơn hàng đã được xử lý xong.Loại bỏ trạng thái mù mờ UNKNOWN: Giữ nguyên trạng thái PENDING và ghi log cảnh báo để hệ thống tự động đối soát thay vì đánh dấu bừa bãi.Cơ chế Active Polling / Reconciliation: Sử dụng một Scheduled Job định kỳ quét các đơn hàng PENDING bị "bỏ quên" do rớt mạng, chủ động gọi sang Payment Service truy vấn trạng thái thực tế trước khi ra quyết định chuyển PAID hoặc FAILED.