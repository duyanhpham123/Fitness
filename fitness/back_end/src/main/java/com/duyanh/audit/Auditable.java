package com.duyanh.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method service cần ghi Audit Log khi chạy thành công.
 *
 * Các thuộc tính entityId / actorId / details là biểu thức SpEL, đọc được tham số theo tên
 * (vd. "#paymentId", "#req") và giá trị trả về ("#result.id").
 *
 * <pre>
 * &#64;Auditable(action = AuditActions.PAYMENT_CONFIRM, entityType = "PAYMENT", entityId = "#paymentId")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

	String action();

	String entityType() default "";

	// SpEL trả về Long, để trống nếu không có
	String entityId() default "";

	// SpEL trả về Long; để trống = người đang đăng nhập (dùng cho login, khi chưa có ai đăng nhập)
	String actorId() default "";

	// SpEL trả về object bất kỳ, được lưu vào cột new_value dạng JSON
	String details() default "";
}
