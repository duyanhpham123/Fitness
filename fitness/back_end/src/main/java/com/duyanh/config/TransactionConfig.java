package com.duyanh.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Đặt transaction là lớp bọc ngoài cùng (order = 0), để AuditAspect chạy BÊN TRONG transaction:
 * log được ghi cùng lúc với thao tác nghiệp vụ, thao tác bị rollback thì log cũng rollback theo.
 *
 * Khai báo lại @EnableTransactionManagement thì Spring Boot bỏ cấu hình mặc định, nên phải giữ
 * proxyTargetClass = true như mặc định của Spring Boot.
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true, order = 0)
public class TransactionConfig {
}
