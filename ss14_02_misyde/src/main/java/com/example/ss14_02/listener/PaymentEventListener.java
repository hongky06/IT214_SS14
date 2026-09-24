package com.example.ss14_02.listener;

import com.example.ss14_02.dto.PaymentResponseEvent;
import com.example.ss14_02.entity.Order;
import com.example.ss14_02.entity.OrderStatus;
import com.example.ss14_02.exception.OrderNotFoundException;
import com.example.ss14_02.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderRepository orderRepository;

    @EventListener
    @Transactional
    public void handlePaymentResponse(PaymentResponseEvent event) {
        log.info("Nhận PaymentResponseEvent: OrderId={}, Trạng thái={}", event.getOrderId(), event.getStatus());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(event.getOrderId()));

        // Kiểm tra tính lũy thừa (Idempotency): nếu đơn không còn PENDING thì không xử lý đè
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Đơn hàng ID={} đã ở trạng thái {}. Bỏ qua cập nhật trùng lặp.", order.getId(), order.getStatus());
            return;
        }

        switch (event.getStatus()) {
            case "SUCCESS":
                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);
                log.info("Cập nhật thành công Order ID={} sang trạng thái PAID", order.getId());
                // Gọi tiếp luồng đóng gói, xuất kho hoặc thông báo cho khách hàng
                break;

            case "REJECTED":
                order.setStatus(OrderStatus.CANCELED);
                orderRepository.save(order);
                log.warn("Thanh toán bị từ chối. Đơn hàng ID={} chuyển sang CANCELED", order.getId());
                // Thực hiện hoàn trả số lượng kho (Compensating transaction) nếu cần
                break;

            case "FAILED":
                order.setStatus(OrderStatus.FAILED);
                orderRepository.save(order);
                log.warn("Thanh toán lỗi kỹ thuật. Đơn hàng ID={} chuyển sang FAILED", order.getId());
                // Thực hiện hoàn trả kho hoặc gửi cảnh báo lỗi kỹ thuật
                break;

            default:
                // Không gắn UNKNOWN bừa bãi, giữ PENDING và ghi log để Job đối soát tự động xử lý
                log.error("Trạng thái thanh toán không hợp lệ: '{}' cho Order ID={}. Giữ nguyên PENDING để đối soát.",
                        event.getStatus(), order.getId());
                break;
        }
    }
}