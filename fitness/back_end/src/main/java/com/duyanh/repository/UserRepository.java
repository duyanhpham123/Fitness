package com.duyanh.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duyanh.entity.User;
import com.duyanh.entity.enums.RoleCode;
import com.duyanh.entity.enums.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);

	boolean existsByPhoneAndIdNot(String phone, Long id);

	// Id các tài khoản đang hoạt động, lọc theo role nếu có (gửi thông báo chung)
	@Query("""
			select distinct u.id from User u join u.roles r
			where u.status = 'ACTIVE' and (:role is null or r.code = :role)
			""")
	List<Long> findActiveIds(@Param("role") RoleCode role);

	// Tìm theo tên / email / SĐT, lọc theo role và trạng thái nếu có (màn quản lý tài khoản)
	@Query(value = """
			select u from User u
			where (:role is null or exists (select 1 from u.roles r where r.code = :role))
			  and (:status is null or u.status = :status)
			  and (:keyword is null
			       or lower(u.fullName) like lower(concat('%', :keyword, '%'))
			       or lower(u.email) like lower(concat('%', :keyword, '%'))
			       or u.phone like concat('%', :keyword, '%'))
			""")
	Page<User> search(@Param("role") RoleCode role, @Param("status") UserStatus status,
			@Param("keyword") String keyword, Pageable pageable);

	// Số hội viên đang hoạt động, tạo trước thời điểm cho trước (so sánh với tháng trước)
	@Query("""
			select count(distinct u) from User u join u.roles r
			where r.code = 'MEMBER' and u.status = 'ACTIVE' and u.createdAt < :before
			""")
	long countActiveMembersCreatedBefore(@Param("before") LocalDateTime before);

	// Hội viên mới nhất
	@Query("""
			select distinct u from User u join u.roles r
			where r.code = 'MEMBER' and u.status <> 'LOCKED'
			order by u.createdAt desc, u.id desc
			""")
	List<User> findNewestMembers(Pageable pageable);
}
