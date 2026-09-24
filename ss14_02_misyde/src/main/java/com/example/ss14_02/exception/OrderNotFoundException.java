package com.example.ss14_02.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("Không tìm thấy đơn hàng với Order ID: " + orderId);
    }
}