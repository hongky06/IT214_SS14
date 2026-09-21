package com.storex.saga.event;

import java.math.BigDecimal;

/**
 * Tập hợp các sự kiện (Events) luân chuyển trong vũ điệu Choreography Saga:
 * Order -> Payment -> Shipping (và luồng bù trừ Refund).
 */
public class SagaEvents {

    // 1. Sự kiện Order Service tạo đơn
    public record OrderCreatedEvent(
            String orderId,
            String customerId,
            BigDecimal amount,
            String shippingAddress,
            String itemSku,
            int quantity
    ) {}

    // 2. Sự kiện Payment Service trừ tiền thành công
    public record PaymentSuccessEvent(
            String orderId,
            String paymentId,
            BigDecimal amount,
            String shippingAddress
    ) {}

    // 3. Sự kiện Payment Service thất bại
    public record PaymentFailedEvent(
            String orderId,
            String reason
    ) {}

    // 4. Sự kiện Shipping Service tạo vận đơn thành công
    public record ShippingSuccessEvent(
            String orderId,
            String trackingNumber
    ) {}

    // 5. Sự kiện Shipping Service tạo vận đơn thất bại (VD: địa chỉ không hỗ trợ)
    public record ShippingFailedEvent(
            String orderId,
            String reason
    ) {}

    // 6. Sự kiện yêu cầu hoàn tiền (Bù trừ - Compensation)
    public record CompensatePaymentEvent(
            String orderId,
            BigDecimal amount,
            String reason
    ) {}

    // 7. Sự kiện Payment Service hoàn tiền thành công
    public record RefundSuccessEvent(
            String orderId,
            String refundId,
            BigDecimal refundedAmount
    ) {}
}
