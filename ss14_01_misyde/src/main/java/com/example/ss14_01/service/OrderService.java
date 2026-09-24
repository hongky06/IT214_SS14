package com.example.ss14_01.service;



import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final InventoryClient inventoryClient;
    private final OrderRepository orderRepository;

    public ResponseEntity<Order> createOrder(OrderRequest request) {
        // 1. Trừ tồn kho tại Inventory Service
        boolean stockDecreased = inventoryClient.decreaseStock(request.getProductId(), request.getQuantity());
        if (!stockDecreased) {
            log.warn("Trừ kho thất bại: Hết hàng hoặc ID không hợp lệ. ProductId={}", request.getProductId());
            return ResponseEntity.badRequest().build();
        }

        // 2. Khởi tạo đơn hàng
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        // 3. Lưu đơn hàng vào DB với cơ chế bù trừ khi lỗi
        try {
            Order savedOrder = orderRepository.save(order);
            return ResponseEntity.ok(savedOrder);
        } catch (Exception e) {
            log.error("Lưu đơn hàng thất bại cho ProductId={}. Bắt đầu thực hiện bù trừ (rollback kho)...",
                    request.getProductId(), e);

            // BÙ TRỪ: Gọi API cộng lại số lượng vào kho
            rollbackInventory(request.getProductId(), request.getQuantity());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void rollbackInventory(Long productId, Integer quantity) {
        try {
            boolean compensated = inventoryClient.increaseStock(productId, quantity);
            if (!compensated) {
                log.error("CRITICAL: Inventory Service từ chối lệnh hoàn kho! ProductId={}, Quantity={}",
                        productId, quantity);
            } else {
                log.info("Bù trừ thành công: Đã hoàn lại {} sản phẩm cho ProductId={}", quantity, productId);
            }
        } catch (Exception ex) {
            // Trường hợp mạng lag, timeout khi gọi hoàn kho
            log.error("CRITICAL: Gặp sự cố kết nối khi rollback kho! ProductId={}, Quantity={}",
                    productId, quantity, ex);
        }
    }
}