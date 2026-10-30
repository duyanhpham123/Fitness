package com.duyanh.dto.report;

/**
 * 4 ô số liệu đầu trang dashboard, so sánh với tháng trước.
 *
 * @param totalMembers        hội viên đang hoạt động
 * @param monthlyRevenue      doanh thu tháng này (giao dịch thành công)
 * @param monthlyBookings     lượt đặt lịch tạo trong tháng này
 * @param activeSubscriptions gói tập đang trong thời hạn sử dụng (kể cả đang bảo lưu)
 */
public record OverviewResponse(
		Metric totalMembers,
		Metric monthlyRevenue,
		Metric monthlyBookings,
		Metric activeSubscriptions) {
}
