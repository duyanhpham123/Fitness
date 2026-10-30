package com.duyanh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.dto.workout.BodyMetricRequest;
import com.duyanh.dto.workout.BodyMetricResponse;
import com.duyanh.entity.BodyMetric;
import com.duyanh.exception.ApiException;
import com.duyanh.repository.BodyMetricRepository;
import com.duyanh.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Chỉ số cơ thể theo ngày để vẽ biểu đồ tiến độ. Mỗi hội viên tối đa 1 dòng / ngày.
 */
@Service
@RequiredArgsConstructor
public class BodyMetricService {

	private final BodyMetricRepository metricRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<BodyMetricResponse> listMine(Long userId) {
		return metricRepository.findByUserIdOrderByMeasuredAtAsc(userId).stream().map(BodyMetricService::toResponse)
				.toList();
	}

	/**
	 * Ghi chỉ số của 1 ngày; ngày đó đã có thì cập nhật (giữ nguyên các số đo không gửi lên).
	 */
	@Transactional
	public BodyMetricResponse record(Long userId, BodyMetricRequest req) {
		if (req.weightKg() == null && req.heightCm() == null && req.bodyFatPct() == null
				&& req.muscleMassKg() == null) {
			throw ApiException.badRequest("EMPTY_METRIC", "Cần nhập ít nhất 1 chỉ số");
		}
		LocalDate date = req.measuredAt() != null ? req.measuredAt() : LocalDate.now();
		BodyMetric m = metricRepository.findByUserIdAndMeasuredAt(userId, date).orElseGet(() -> {
			BodyMetric created = new BodyMetric();
			created.setUser(userRepository.getReferenceById(userId));
			created.setMeasuredAt(date);
			return created;
		});
		if (req.weightKg() != null) {
			m.setWeightKg(req.weightKg());
		}
		if (req.heightCm() != null) {
			m.setHeightCm(req.heightCm());
		}
		if (req.bodyFatPct() != null) {
			m.setBodyFatPct(req.bodyFatPct());
		}
		if (req.muscleMassKg() != null) {
			m.setMuscleMassKg(req.muscleMassKg());
		}
		if (req.note() != null) {
			m.setNote(req.note().isBlank() ? null : req.note().trim());
		}
		return toResponse(metricRepository.save(m));
	}

	private static BodyMetricResponse toResponse(BodyMetric m) {
		return new BodyMetricResponse(m.getId(), m.getMeasuredAt(), m.getWeightKg(), m.getHeightCm(),
				m.getBodyFatPct(), m.getMuscleMassKg(), bmi(m.getWeightKg(), m.getHeightCm()), m.getNote());
	}

	// BMI = cân nặng (kg) / chiều cao (m)²
	static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
		if (weightKg == null || heightCm == null || heightCm.signum() == 0) {
			return null;
		}
		BigDecimal heightM = heightCm.movePointLeft(2);
		return weightKg.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
	}
}
