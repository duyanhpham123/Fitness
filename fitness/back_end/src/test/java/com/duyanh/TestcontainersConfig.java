package com.duyanh;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL + Redis tạm trong Docker cho test (cần Docker đang chạy).
 *
 * @ServiceConnection tự trỏ datasource / redis của app vào 2 container này; Flyway dựng schema + dữ liệu mẫu
 * từ đầu, nên test không phụ thuộc database trên máy dev.
 * Container là static: mọi test class (kể cả context khác nhau) dùng chung 1 cặp, chỉ khởi động 1 lần.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

	@SuppressWarnings("resource")
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("fitness_db")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	@SuppressWarnings("resource")
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

	@Bean
	@ServiceConnection
	MySQLContainer<?> mysqlContainer() {
		return MYSQL;
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return REDIS;
	}
}
