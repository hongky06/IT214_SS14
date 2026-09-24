# BÁO CÁO PHÂN TÍCH VÀ XỬ LÝ LỖ HỔNG "KHO TREO" (ROLLBACK LOGIC)
**Mã bài tập:** BÀI TẬP 1  
**Hệ thống:** E-Commerce Order & Inventory Processing System

---

## 1. Phân Tích Nguyên Nhân Gây Lỗi "Hàng Ảo" (Inconsistency)

### 1.1. Bản chất kiến trúc và vấn đề gặp phải
Trong kiến trúc Microservices, mỗi service (`Order Service` và `Inventory Service`) quản lý cơ sở dữ liệu riêng biệt (Database-per-service pattern). Do đó, không thể dùng giao thức ACID transaction truyền thống (`@Transactional`) để khóa và rollback dữ liệu đồng thời trên cả hai dịch vụ.

### 1.2. Phân tích luồng lỗi trong legacy code
Đoạn mã ban đầu thực thi theo thứ tự:
1. Gửi request sang `Inventory Service` qua `inventoryClient.decreaseStock(...)` thành công. Dữ liệu kho bị trừ và commit ngay lập tức tại database của `Inventory Service`.
2. Khởi tạo đối tượng `Order` và gọi `orderRepository.save(order)`.
3. Khi khối `save(order)` ném ra ngoại lệ (ví dụ: DB timeout, vi phạm constraint, ngắt kết nối):
   ```java
   catch (Exception e) {
       // LỖI: Chỉ trả về HTTP 500 mà không hề phát sinh hành động khôi phục dữ liệu
       return ResponseEntity.status(500).build();
   }
    ```

3. Giải Pháp Nâng Cao: Xử Lý Khi Compensatory Action Thất BạiPhương án gọi trực tiếp qua HTTP Client tiềm ẩn rủi ro: nếu đường truyền mạng ngắt quãng đúng lúc gọi rollback, giao dịch bù trừ sẽ thất bại và kho vẫn bị treo. Cần triển khai mô hình Saga kết hợp Transactional Outbox Pattern.[Client]
   │
   ▼
   [Order Service] ──(Tạo Đơn lỗi)──► Ghi Event vào [Outbox Table] (ACID cục bộ)
   │
   (CDC / Scheduler)
   ▼
   [Message Broker] (Kafka/RabbitMQ)
   │
   ▼
   [Inventory Service] ──► Rollback kho (Idempotent)
   3.1. Thiết kế Saga State & Outbox PatternThay vì gọi đồng bộ, áp dụng cơ chế bất đồng bộ đảm bảo gửi thành công ít nhất một lần (At-least-once delivery):Lưu trạng thái giao dịch cục bộ: Tạo bảng outbox_events hoặc saga_log cùng database với Order Service.Khi orderRepository.save(order) thất bại, ghi một bản ghi sự kiện bù trừ vào bảng outbox_events trong cùng một transaction cục bộ:JSON{
   "id": "evt-uuid-1234",
   "aggregateType": "INVENTORY_COMPENSATION",
   "payload": { "productId": 101, "quantity": 2 },
   "status": "PENDING_RETRY",
   "retryCount": 0
   }
   3.2. Quét định kỳ và cơ chế Retry (Worker / Scheduler)Background Worker: Một cron job (hoặc CDC tool như Debezium) đọc các event có trạng thái PENDING_RETRY từ bảng outbox và đẩy lên Message Broker (Kafka/RabbitMQ) hoặc gọi trực tiếp API của Inventory Service.Exponential Backoff: Nếu gọi thất bại, tăng retryCount và thử lại sau các khoảng thời gian dãn cách (ví dụ: $1s, 2s, 4s, 8s,...$).Dead Letter Queue (DLQ): Sau số lần retry tối đa (ví dụ: 5 lần) mà vẫn thất bại, event được đẩy vào DLQ để kích hoạt cảnh báo (PagerDuty/Slack/Telegram) cho kỹ sư can thiệp thủ công.3.3. Tính lũy thừa (Idempotency) tại Inventory ServiceKhi retry nhiều lần, Inventory Service có thể nhận cùng một yêu cầu hoàn kho nhiều hơn một lần. Dịch vụ kho phải triển khai kiểm tra transactionId hoặc eventId:Lưu lại các eventId đã xử lý thành công.Nếu nhận lại eventId cũ, bỏ qua việc cộng kho và trả về kết quả thành công ngay lập tức để tránh cộng dồn số lượng sai lệch.4. Bảng So Sánh Các Hướng Tiếp CậnTiêu chíTry-Catch Rollback Đồng BộSaga Orchestration / Choreography + OutboxĐộ phức tạpThấp, dễ triển khai ngayTrung bình - Cao, cần Message Broker / Outbox DBĐộ tin cậyKém khi mạng chập chờn / Service sậpRất cao, đảm bảo tính nhất quán sau cùngXử lý TimeoutDễ mất dữ liệu bù trừTự động retry thông qua Job / ConsumerMức độ phụ thuộcGhép nối chặt (Tightly coupled)Ghép nối lỏng (Loosely coupled, Asynchronous)