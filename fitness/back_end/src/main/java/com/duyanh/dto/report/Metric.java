package com.duyanh.dto.report;

import java.math.BigDecimal;

/**
 * Một chỉ số trên dashboard.
 *
 * @param changePercent % thay đổi so với tháng trước (làm tròn 1 chữ số), null nếu tháng trước bằng 0
 */
public record Metric(BigDecimal value, Double changePercent) {
}
