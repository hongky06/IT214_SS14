package com.example.ss14_02.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseEvent {
    private Long orderId;
    private String paymentTransactionId;
    private String status; // SUCCESS, REJECTED, FAILED
    private String message;
}