package com.duyanh.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duyanh.audit.AuditActions;
import com.duyanh.audit.Auditable;
import com.duyanh.dto.membership.PackageRequest;
import com.duyanh.dto.membership.PackageResponse;
import com.duyanh.entity.MembershipPackage;
import com.duyanh.exception.ApiException;
import com.duyanh.mapper.MembershipMapper;
import com.duyanh.repository.MembershipPackageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Quản lý gói tập mẫu. Không xóa gói (đã có hội viên mua), chỉ ngừng bán bằng active = false.
 */
@Service
@RequiredArgsConstructor
public class PackageService {

	private final MembershipPackageRepository packageRepository;
	private final MembershipMapper mapper;

	@Transactional(readOnly = true)
	public List<PackageResponse> list(boolean includeInactive) {
		List<MembershipPackage> packages = includeInactive
				? packageRepository.findAllByOrderByDurationDaysAsc()
				: packageRepository.findByActiveTrueOrderByDurationDaysAsc();
		return packages.stream().map(mapper::toResponse).toList();
	}

	@Auditable(action = AuditActions.PACKAGE_CREATE, entityType = "PACKAGE", entityId = "#result.id()", details = "#req")
	@Transactional
	public PackageResponse create(PackageRequest req) {
		if (packageRepository.existsByCode(req.code())) {
			throw ApiException.conflict("PACKAGE_CODE_EXISTS", "Mã gói đã tồn tại");
		}
		MembershipPackage pkg = new MembershipPackage();
		mapper.updateEntity(req, pkg);
		return mapper.toResponse(packageRepository.save(pkg));
	}

	/**
	 * Đổi giá không ảnh hưởng gói hội viên đã mua (subscription lưu price_paid riêng).
	 */
	@Auditable(action = AuditActions.PACKAGE_UPDATE, entityType = "PACKAGE", entityId = "#id", details = "#req")
	@Transactional
	public PackageResponse update(Long id, PackageRequest req) {
		MembershipPackage pkg = packageRepository.findById(id)
				.orElseThrow(() -> ApiException.notFound("PACKAGE_NOT_FOUND", "Không tìm thấy gói tập"));
		if (packageRepository.existsByCodeAndIdNot(req.code(), id)) {
			throw ApiException.conflict("PACKAGE_CODE_EXISTS", "Mã gói đã tồn tại");
		}
		mapper.updateEntity(req, pkg);
		return mapper.toResponse(pkg);
	}
}
