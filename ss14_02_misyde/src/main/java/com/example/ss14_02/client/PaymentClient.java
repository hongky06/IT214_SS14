package com.example.ss14_02.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentClient {

    /**
     * Chủ động truy vấn trạng thái thanh toán từ Payment Service khi nghi ngờ rớt mạng/mất event
     */
    public String checkPaymentStatus(Long orderId) {
        log.info("[PaymentClient] Đang truy vấn trạng thái thanh toán thực tế cho OrderId={}", orderId);
        // Giả lập kết quả trả về từ Payment Gateway: "SUCCESS", "REJECTED", hoặc "NOT_FOUND"
        return "SUCCESS";
    }
}