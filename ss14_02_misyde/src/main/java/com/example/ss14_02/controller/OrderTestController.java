package com.example.ss14_02.controller;

import com.example.ss14_02.dto.PaymentResponseEvent;
import com.example.ss14_02.entity.Order;
import com.example.ss14_02.entity.OrderStatus;
import com.example.ss14_02.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderTestController {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    // API tạo thử 1 đơn hàng PENDING
    @PostMapping("/init")
    public ResponseEntity<Order> initOrder(@RequestParam Long productId, @RequestParam Integer quantity) {
        Order order = Order.builder()
                .productId(productId)
                .quantity(quantity)
                .status(OrderStatus.PENDING)
                .build();
        return ResponseEntity.ok(orderRepository.save(order));
    }

    // API giả lập Payment Gateway bắn event về
    @PostMapping("/mock-payment-event")
    public ResponseEntity<String> mockPaymentResponse(@RequestBody PaymentResponseEvent event) {
        eventPublisher.publishEvent(event);
        return ResponseEntity.ok("Event đã được publish vào EventListener thành công!");
    }
}