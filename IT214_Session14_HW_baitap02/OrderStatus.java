package com.storex.order.model;

/**
 * Định nghĩa tập hợp các trạng thái trong vòng đời đơn hàng (State Machine).
 */
public enum OrderStatus {
    PENDING,    // Đơn hàng vừa tạo, đang chờ phản hồi thanh toán
    PAID,       // Thanh toán thành công
    CANCELED,   // Bị từ chối thanh toán (REJECTED) hoặc khách chủ động hủy
    FAILED,     // Thanh toán thất bại (FAILED) hoặc quá hạn (TIMEOUT)
    PROCESSING, // Đang đóng gói / chuẩn bị hàng
    SHIPPED,    // Đã giao cho đơn vị vận chuyển
    DELIVERED   // Giao hàng thành công đến tay khách
}
