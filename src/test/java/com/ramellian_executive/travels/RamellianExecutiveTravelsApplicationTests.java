package com.ramellian_executive.travels;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import io.swagger.v3.oas.models.OpenAPI;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RamellianExecutiveTravelsApplicationTests {

	@Autowired
	private OpenAPI openAPI;

	@Test
	void contextLoads() {
	}

	@Test
	void swaggerDocumentsBearerAuthentication() {
		assertNotNull(openAPI);
		assertNotNull(openAPI.getComponents().getSecuritySchemes().get("bearerAuth"));
		assertEquals("bearer", openAPI.getComponents().getSecuritySchemes()
				.get("bearerAuth").getScheme());
	}

}
