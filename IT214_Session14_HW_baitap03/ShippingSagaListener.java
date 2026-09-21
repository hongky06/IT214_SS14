package com.storex.saga.shipping;

import com.storex.saga.event.SagaEvents.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Shipping Service tham gia vào Choreography Saga:
 * - Lắng nghe PaymentSuccessEvent -> Kiểm tra địa chỉ giao hàng và tạo vận đơn
 * - Nếu địa chỉ không hỗ trợ -> Phát ShippingFailedEvent
 * - Nếu tạo vận đơn thành công -> Phát ShippingSuccessEvent
 */
@Service
public class ShippingSagaListener {

    private static final Logger log = LoggerFactory.getLogger(ShippingSagaListener.class);
    private final ApplicationEventPublisher eventPublisher;

    public ShippingSagaListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Nhận sự kiện PaymentSuccessEvent: Kiểm tra tuyến giao hàng và tạo vận đơn
     */
    @EventListener
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        log.info("[Shipping-Service] Nhận PaymentSuccess cho đơn {}. Kiểm tra địa chỉ: {}", 
                event.orderId(), event.shippingAddress());

        // Nghiệp vụ: Nếu địa chỉ chứa "UNSUPPORTED" hoặc "UNKNOWN" -> Bị lỗi địa chỉ không hỗ trợ
        if (event.shippingAddress() != null && event.shippingAddress().toUpperCase().contains("UNSUPPORTED")) {
            log.error("[Shipping-Service] Địa chỉ '{}' nằm ngoài vùng phủ sóng của đơn vị vận chuyển!", 
                    event.shippingAddress());
            
            // Bắn sự kiện Shipping thất bại
            eventPublisher.publishEvent(new ShippingFailedEvent(
                    event.orderId(), 
                    "Địa chỉ giao hàng không được hỗ trợ (Out of delivery coverage)"
            ));
            return;
        }

        // Tạo vận đơn thành công
        String trackingNumber = "GHN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[Shipping-Service] Tạo vận đơn thành công cho đơn {}. Mã vận đơn: {}", 
                event.orderId(), trackingNumber);

        // Bắn sự kiện Shipping thành công
        eventPublisher.publishEvent(new ShippingSuccessEvent(event.orderId(), trackingNumber));
    }
}
