package ch.uzh.ifi.hase.soprafs26;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class ApplicationCorsTest {

	@AfterEach
	void cleanupProperty() {
		System.clearProperty("CORS_ALLOWED_ORIGINS");
	}

	@Test
	void helloWorld_returnsExpectedText() {
		Application application = new Application();
		assertEquals("The application is running.", application.helloWorld());
	}

	@Test
	void getAllowedOrigins_returnsDefaults_whenNoConfigurationExists() {
		Application application = new Application();
		String[] origins = application.getAllowedOrigins();

		assertArrayEquals(new String[] {
				"http://localhost:3000",
				"http://127.0.0.1:3000",
				"https://sopra-fs26-group-09-client.vercel.app"
		}, origins);
	}

	@Test
	void getAllowedOrigins_usesConfiguredSystemProperty() {
		System.setProperty("CORS_ALLOWED_ORIGINS", "https://example.org, https://example.com");
		Application application = new Application();
		String[] origins = application.getAllowedOrigins();

		assertArrayEquals(new String[] { "https://example.org", "https://example.com" }, origins);
	}

	@Test
	void parseAllowedOrigins_ignoresEmptyEntries_andTrimsValues() {
		String[] origins = Application.parseAllowedOrigins(" https://a.com, ,https://b.com ,, ");
		assertArrayEquals(new String[] { "https://a.com", "https://b.com" }, origins);
	}

	@Test
	void corsConfigurer_addCorsMappings_doesNotThrow() {
		Application application = new Application();
		WebMvcConfigurer configurer = application.corsConfigurer();

		assertNotNull(configurer);
		configurer.addCorsMappings(new CorsRegistry());
	}
}
