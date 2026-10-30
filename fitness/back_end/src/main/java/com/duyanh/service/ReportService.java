package com.duyanh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.dto.booking.BookingResponse;
import com.duyanh.dto.report.AttendanceReport;
import com.duyanh.dto.report.Metric;
import com.duyanh.dto.report.NewMemberResponse;
import com.duyanh.dto.report.OverviewResponse;
import com.duyanh.dto.report.RevenueReport;
import com.duyanh.dto.report.TopPackageResponse;
import com.duyanh.entity.Subscription;
import com.duyanh.entity.User;
import com.duyanh.entity.enums.BookingStatus;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.BookingMapper;
import com.duyanh.repository.BookingRepository;
import com.duyanh.repository.CheckInRepository;
import com.duyanh.repository.PaymentRepository;
import com.duyanh.repository.SubscriptionRepository;
import com.duyanh.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Số liệu cho dashboard. Mọi khoảng ngày [from, to] tính trọn ngày theo giờ Việt Nam (giờ server).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

	private static final int MAX_RANGE_DAYS = 366;

	private final UserRepository userRepository;
	private final PaymentRepository paymentRepository;
	private final BookingRepository bookingRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final CheckInRepository checkInRepository;
	private final BookingMapper bookingMapper;

	/**
	 * 4 chỉ số đầu trang, so với tháng trước.
	 *
	 * Giới hạn trên là đầu tháng sau, không phải "bây giờ": cột DATETIME của MySQL làm tròn phần
	 * lẻ của giây, bản ghi vừa tạo có thể bị lưu muộn hơn "bây giờ" vài trăm mili giây và bị bỏ sót.
	 */
	public OverviewResponse overview() {
		LocalDate today = LocalDate.now();
		LocalDateTime thisMonth = today.withDayOfMonth(1).atStartOfDay();
		LocalDateTime lastMonth = thisMonth.minusMonths(1);
		LocalDateTime nextMonth = thisMonth.plusMonths(1);

		long members = userRepository.countActiveMembersCreatedBefore(nextMonth);
		long membersLastMonth = userRepository.countActiveMembersCreatedBefore(thisMonth);

		BigDecimal revenue = paymentRepository.sumRevenue(thisMonth, nextMonth);
		BigDecimal revenueLastMonth = paymentRepository.sumRevenue(lastMonth, thisMonth);

		long bookings = bookingRepository.countCreatedBetween(thisMonth, nextMonth);
		long bookingsLastMonth = bookingRepository.countCreatedBetween(lastMonth, thisMonth);

		long activeSubs = subscriptionRepository.countCovering(today);
		long activeSubsLastMonth = subscriptionRepository.countCovering(thisMonth.toLocalDate().minusDays(1));

		return new OverviewResponse(
				metric(BigDecimal.valueOf(members), BigDecimal.valueOf(membersLastMonth)),
				metric(revenue, revenueLastMonth),
				metric(BigDecimal.valueOf(bookings), BigDecimal.valueOf(bookingsLastMonth)),
				metric(BigDecimal.valueOf(activeSubs), BigDecimal.valueOf(activeSubsLastMonth)));
	}

	/**
	 * Doanh thu từng ngày trong [from, to], ngày không có giao dịch = 0 (vẽ biểu đồ đường).
	 */
	public RevenueReport revenue(LocalDate from, LocalDate to) {
		validateRange(from, to);
		Map<LocalDate, BigDecimal> byDay = emptyDays(from, to, BigDecimal.ZERO);
		for (Object[] row : paymentRepository.findPaidAmounts(from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
			LocalDate day = ((LocalDateTime) row[0]).toLocalDate();
			byDay.merge(day, (BigDecimal) row[1], BigDecimal::add);
		}
		BigDecimal total = byDay.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		return new RevenueReport(total, byDay.entrySet().stream()
				.map(e -> new RevenueReport.DailyAmount(e.getKey(), e.getValue()))
				.toList());
	}

	public List<TopPackageResponse> topPackages(LocalDate from, LocalDate to) {
		validateRange(from, to);
		List<Object[]> rows = paymentRepository.topPackages(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
		long totalSold = rows.stream().mapToLong(r -> (Long) r[2]).sum();
		return rows.stream().map(r -> new TopPackageResponse(
				(Long) r[0], (String) r[1], (Long) r[2], (BigDecimal) r[3],
				percent(BigDecimal.valueOf((Long) r[2]), BigDecimal.valueOf(totalSold))))
				.toList();
	}

	/**
	 * Tỷ lệ đi tập = số hội viên đã check-in / số hội viên có gói còn hiệu lực trong khoảng thời gian.
	 */
	public AttendanceReport attendance(LocalDate from, LocalDate to) {
		validateRange(from, to);
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime end = to.plusDays(1).atStartOfDay();

		long active = subscriptionRepository.countMembersCovering(from, to);
		long attended = checkInRepository.countDistinctMembers(start, end);
		Map<LocalDate, Long> byDay = emptyDays(from, to, 0L);
		checkInRepository.findCheckInTimes(start, end).forEach(t -> byDay.merge(t.toLocalDate(), 1L, Long::sum));

		Double rate = active == 0 ? null : percent(BigDecimal.valueOf(attended), BigDecimal.valueOf(active));
		return new AttendanceReport(active, attended, rate, byDay.entrySet().stream()
				.map(e -> new AttendanceReport.DailyCount(e.getKey(), e.getValue()))
				.toList());
	}

	/**
	 * Hội viên đăng ký gần đây kèm gói gần nhất (khối "Hội viên mới").
	 */
	public List<NewMemberResponse> newMembers(int limit) {
		List<User> users = userRepository.findNewestMembers(PageRequest.of(0, Math.clamp(limit, 1, 50)));
		Map<Long, String> packageByUser = new HashMap<>();
		if (!users.isEmpty()) {
			for (Subscription s : subscriptionRepository.findEffectiveOfUsers(users.stream().map(User::getId).toList())) {
				packageByUser.putIfAbsent(s.getUser().getId(), s.getMembershipPackage().getName());
			}
		}
		return users.stream()
				.map(u -> new NewMemberResponse(u.getId(), u.getFullName(), u.getAvatarUrl(),
						packageByUser.get(u.getId()), u.getCreatedAt()))
				.toList();
	}

	/**
	 * Lịch hẹn (PT và lớp) đang hiệu lực / đã hoàn thành trong 1 ngày (khối "Lịch hẹn hôm nay").
	 */
	public List<BookingResponse> bookingsOn(LocalDate date) {
		LocalDateTime start = date.atStartOfDay();
		return bookingRepository.findByStartAtBetweenAndStatusInOrderByStartAtAsc(start, start.plusDays(1).minusNanos(1),
				List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.COMPLETED))
				.stream().map(bookingMapper::toResponse).toList();
	}

	private static Metric metric(BigDecimal value, BigDecimal previous) {
		Double change = previous.signum() == 0 ? null
				: value.subtract(previous).multiply(BigDecimal.valueOf(100))
						.divide(previous, 1, RoundingMode.HALF_UP).doubleValue();
		return new Metric(value, change);
	}

	private static double percent(BigDecimal part, BigDecimal total) {
		return total.signum() == 0 ? 0
				: part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
	}

	// Map giữ thứ tự ngày, mỗi ngày trong khoảng có sẵn giá trị mặc định
	private static <V> Map<LocalDate, V> emptyDays(LocalDate from, LocalDate to, V zero) {
		Map<LocalDate, V> days = new LinkedHashMap<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			days.put(d, zero);
		}
		return days;
	}

	private static void validateRange(LocalDate from, LocalDate to) {
		if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw ApiException.badRequest("INVALID_RANGE",
					"Khoảng ngày không hợp lệ (from <= to, tối đa %d ngày)".formatted(MAX_RANGE_DAYS));
		}
	}
}
