package com.example.ss14_02.scheduler;

import com.example.ss14_02.client.PaymentClient;
import com.example.ss14_02.entity.Order;
import com.example.ss14_02.entity.OrderStatus;
import com.example.ss14_02.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderTimeoutReconciliationJob {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    /**
     * Quét mỗi phút 1 lần để tìm các đơn hàng PENDING đã tạo quá 5 phút
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void reconcilePendingOrders() {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<Order> stuckOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, fiveMinutesAgo);

        if (stuckOrders.isEmpty()) {
            return;
        }

        log.info("Phát hiện {} đơn hàng PENDING bị quá hạn (quá 5 phút). Bắt đầu đối soát...", stuckOrders.size());

        for (Order order : stuckOrders) {
            try {
                // Chủ động hỏi Payment Service xem thực tế đã thanh toán chưa
                String actualPaymentStatus = paymentClient.checkPaymentStatus(order.getId());

                if ("SUCCESS".equalsIgnoreCase(actualPaymentStatus)) {
                    order.setStatus(OrderStatus.PAID);
                    orderRepository.save(order);
                    log.info("Đã đồng bộ lại thành công: Order ID={} -> PAID", order.getId());
                } else {
                    order.setStatus(OrderStatus.FAILED);
                    orderRepository.save(order);
                    log.warn("Đơn hàng ID={} không hoàn tất thanh toán sau 5 phút -> Chuyển sang FAILED", order.getId());
                }
            } catch (Exception e) {
                log.error("Lỗi khi đối soát tự động cho Order ID={}", order.getId(), e);
            }
        }
    }
}