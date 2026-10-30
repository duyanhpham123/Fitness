package com.duyanh.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param days mỗi ngày trong khoảng 1 phần tử (ngày không có doanh thu = 0), theo giờ Việt Nam
 */
public record RevenueReport(BigDecimal total, List<DailyAmount> days) {

	public record DailyAmount(LocalDate date, BigDecimal amount) {
	}
}
