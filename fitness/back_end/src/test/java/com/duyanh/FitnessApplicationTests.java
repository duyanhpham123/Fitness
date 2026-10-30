package com.duyanh;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class FitnessApplicationTests {

	@Test
	void contextLoads() {
	}

}
