package com.example.ss14_02.repository;

import com.example.ss14_02.entity.Order;
import com.example.ss14_02.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Quét các đơn hàng PENDING tạo trước một mốc thời gian (dùng cho việc xử lý timeout)
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime timeThreshold);
}