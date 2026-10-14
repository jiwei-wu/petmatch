package com.petmatch;

import com.petmatch.TestcontainersConfiguration;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PetmatchApplicationTests {

	@Test
	void contextLoads() {
	}

}
