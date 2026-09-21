package com.storex.saga.order;

import com.storex.saga.event.SagaEvents.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order Service tham gia vào Choreography Saga:
 * - Khởi tạo đơn hàng PENDING và phát sinh OrderCreatedEvent
 * - Xử lý ShippingSuccess -> COMPLETED
 * - Xử lý ShippingFailed -> Gửi yêu cầu bù trừ CompensatePaymentEvent
 * - Xử lý RefundSuccess -> CANCELED
 * - Xử lý Timeout (30s): Tự động kích hoạt bù trừ nếu Shipping phản hồi chậm
 */
@Service
public class OrderSagaCoordinator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaCoordinator.class);

    private final ApplicationEventPublisher eventPublisher;

    // Lưu trữ đơn hàng mô phỏng trong bộ nhớ
    public enum SagaOrderStatus { PENDING, PAID_WAITING_SHIPPING, COMPLETED, CANCELED }
    
    public record OrderEntity(
            String orderId,
            String customerId,
            BigDecimal amount,
            String shippingAddress,
            SagaOrderStatus status,
            Instant updatedAt
    ) {}

    private final Map<String, OrderEntity> orderStore = new ConcurrentHashMap<>();

    public OrderSagaCoordinator(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Bước 1: Khách đặt hàng -> Tạo đơn PENDING -> Gửi OrderCreatedEvent
     */
    public String createOrder(String customerId, BigDecimal amount, String shippingAddress, String itemSku, int quantity) {
        String orderId = "ORD-" + System.currentTimeMillis();
        OrderEntity order = new OrderEntity(orderId, customerId, amount, shippingAddress, 
                SagaOrderStatus.PENDING, Instant.now());
        orderStore.put(orderId, order);

        log.info("[Order-Service] Đã tạo đơn hàng: {}, Trạng thái: PENDING", orderId);
        
        // Phát sự kiện bắt đầu Saga
        eventPublisher.publishEvent(new OrderCreatedEvent(orderId, customerId, amount, shippingAddress, itemSku, quantity));
        return orderId;
    }

    /**
     * Bước 2b: Nhận PaymentSuccess -> Cập nhật sang PAID_WAITING_SHIPPING
     */
    @EventListener
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        OrderEntity order = orderStore.get(event.orderId());
        if (order != null && order.status() == SagaOrderStatus.PENDING) {
            orderStore.put(event.orderId(), new OrderEntity(order.orderId(), order.customerId(), 
                    order.amount(), order.shippingAddress(), SagaOrderStatus.PAID_WAITING_SHIPPING, Instant.now()));
            log.info("[Order-Service] Nhận PaymentSuccess cho đơn {}. Chờ Shipping phản hồi.", event.orderId());
        }
    }

    /**
     * Bước 3 (Happy Path): Nhận ShippingSuccess -> Hoàn tất đơn hàng COMPLETED
     */
    @EventListener
    public void handleShippingSuccess(ShippingSuccessEvent event) {
        OrderEntity order = orderStore.get(event.orderId());
        if (order != null) {
            orderStore.put(event.orderId(), new OrderEntity(order.orderId(), order.customerId(), 
                    order.amount(), order.shippingAddress(), SagaOrderStatus.COMPLETED, Instant.now()));
            log.info("[Order-Service] Nhận ShippingSuccess (Vận đơn: {}). Đơn hàng {} hoàn tất thành công (COMPLETED)!", 
                    event.trackingNumber(), event.orderId());
        }
    }

    /**
     * Bước 3 (Failure Path): Nhận ShippingFailed -> Kích hoạt Bù trừ (Compensate Payment)
     */
    @EventListener
    public void handleShippingFailed(ShippingFailedEvent event) {
        OrderEntity order = orderStore.get(event.orderId());
        if (order != null) {
            log.warn("[Order-Service] Nhận ShippingFailed cho đơn {}. Lý do: {}. Bắt đầu luồng bù trừ...", 
                    event.orderId(), event.reason());

            // Gửi sự kiện yêu cầu Payment Service hoàn tiền
            eventPublisher.publishEvent(new CompensatePaymentEvent(order.orderId(), order.amount(), event.reason()));
        }
    }

    /**
     * Bước 4 (Compensation Completed): Nhận RefundSuccess -> Hủy đơn hàng CANCELED
     */
    @EventListener
    public void handleRefundSuccess(RefundSuccessEvent event) {
        OrderEntity order = orderStore.get(event.orderId());
        if (order != null) {
            orderStore.put(event.orderId(), new OrderEntity(order.orderId(), order.customerId(), 
                    order.amount(), order.shippingAddress(), SagaOrderStatus.CANCELED, Instant.now()));
            log.info("[Order-Service] Nhận RefundSuccess cho đơn {}. Đã hoàn tiền: {}. Đơn hàng chính thức bị CANCELED.", 
                    event.orderId(), event.refundedAmount());
        }
    }

    /**
     * Yêu cầu d: Xử lý Timeout (Saga Deadline) sau 30 giây nếu Shipping Service không phản hồi
     */
    @Scheduled(fixedRate = 5000)
    public void checkShippingTimeout() {
        Instant now = Instant.now();
        orderStore.values().stream()
                .filter(order -> order.status() == SagaOrderStatus.PAID_WAITING_SHIPPING)
                .filter(order -> now.isAfter(order.updatedAt().plusSeconds(30)))
                .forEach(order -> {
                    log.error("[Order-Service] Đơn hàng {} bị TIMEOUT sau 30s không nhận được phản hồi từ Shipping Service! Kích hoạt bù trừ khẩn cấp.", 
                            order.orderId());
                    
                    // Phát sinh bù trừ do Shipping Timeout
                    eventPublisher.publishEvent(new CompensatePaymentEvent(order.orderId(), order.amount(), "SHIPPING_SERVICE_TIMEOUT_30S"));
                });
    }

    public OrderEntity getOrder(String orderId) {
        return orderStore.get(orderId);
    }
}
