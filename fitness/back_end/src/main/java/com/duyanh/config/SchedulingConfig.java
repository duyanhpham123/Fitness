package com.duyanh.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật các job định kỳ (gói hết hạn, hủy đơn online quá hạn).
 * Test đặt app.scheduling.enabled=false để job không tự chạy và ghi thật vào DB giữa chừng.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
