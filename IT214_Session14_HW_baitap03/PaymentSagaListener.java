package com.storex.saga.payment;

import com.storex.saga.event.SagaEvents.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Payment Service tham gia vào Choreography Saga:
 * - Lắng nghe OrderCreatedEvent -> Trừ tiền ví -> Bắn PaymentSuccessEvent (hoặc PaymentFailedEvent)
 * - Lắng nghe CompensatePaymentEvent -> Hoàn tiền (Refund) -> Bắn RefundSuccessEvent
 */
@Service
public class PaymentSagaListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaListener.class);
    private final ApplicationEventPublisher eventPublisher;

    public PaymentSagaListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Nhận sự kiện OrderCreatedEvent: Tiến hành trừ tiền khách hàng
     */
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[Payment-Service] Nhận OrderCreated cho đơn {}. Bắt đầu trừ tiền ví: {}", 
                event.orderId(), event.amount());

        // Mô phỏng kiểm tra số dư và trừ tiền thành công
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[Payment-Service] Trừ tiền thành công cho đơn {}, Mã giao dịch: {}", event.orderId(), paymentId);

        // Phát sự kiện PaymentSuccess để kích hoạt bước tiếp theo
        eventPublisher.publishEvent(new PaymentSuccessEvent(
                event.orderId(), 
                paymentId, 
                event.amount(), 
                event.shippingAddress()
        ));
    }

    /**
     * Nhận sự kiện CompensatePaymentEvent: Thực hiện giao dịch bù trừ (Hoàn tiền)
     */
    @EventListener
    public void onCompensatePayment(CompensatePaymentEvent event) {
        log.warn("[Payment-Service] BẮT ĐẦU BÙ TRỪ: Nhận yêu cầu hoàn tiền cho đơn {}. Lý do: {}", 
                event.orderId(), event.reason());

        // Thực hiện hoàn tiền vào ví khách hàng
        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[Payment-Service] Đã hoàn tiền thành công số tiền: {} cho đơn {}. Mã hoàn tiền: {}", 
                event.amount(), event.orderId(), refundId);

        // Phát sự kiện RefundSuccess báo cho Order Service biết bù trừ đã hoàn tất
        eventPublisher.publishEvent(new RefundSuccessEvent(event.orderId(), refundId, event.amount()));
    }
}
