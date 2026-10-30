package com.duyanh.dto.report;

import java.math.BigDecimal;

/**
 * @param percent tỷ lệ số lượt bán so với tổng (làm tròn 1 chữ số)
 */
public record TopPackageResponse(Long packageId, String packageName, long sold, BigDecimal revenue, double percent) {
}
