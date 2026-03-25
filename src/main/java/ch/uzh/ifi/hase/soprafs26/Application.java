package ch.uzh.ifi.hase.soprafs26;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RestController
@SpringBootApplication
public class Application {

	private static final String[] DEFAULT_ALLOWED_ORIGINS = {
			"http://localhost:3000",
			"http://127.0.0.1:3000",
			"https://sopra-fs26-group-09-client.vercel.app"
	};

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public String helloWorld() {
		return "The application is running.";
	}

	@Bean
	public WebMvcConfigurer corsConfigurer() {
		final String[] allowedOrigins = getAllowedOrigins();
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/**")
						.allowedOrigins(allowedOrigins)
						.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
						.allowedHeaders("*");
			}
		};
	}

	String[] getAllowedOrigins() {
		String rawValue = resolveAllowedOriginsRawValue();
		if (rawValue == null || rawValue.trim().isEmpty()) {
			return DEFAULT_ALLOWED_ORIGINS;
		}
		String[] parsedOrigins = parseAllowedOrigins(rawValue);
		return parsedOrigins.length == 0 ? DEFAULT_ALLOWED_ORIGINS : parsedOrigins;
	}

	String resolveAllowedOriginsRawValue() {
		String configuredOrigins = System.getProperty("CORS_ALLOWED_ORIGINS");
		if (configuredOrigins != null && !configuredOrigins.trim().isEmpty()) {
			return configuredOrigins;
		}
		return System.getenv("CORS_ALLOWED_ORIGINS");
	}

	static String[] parseAllowedOrigins(String rawValue) {
		String sanitized = rawValue.trim();
		java.util.List<String> origins = new java.util.ArrayList<>();
		int start = 0;
		for (int i = 0; i <= sanitized.length(); i++) {
			boolean atSeparator = i == sanitized.length() || sanitized.charAt(i) == ',';
			if (atSeparator) {
				String candidate = sanitized.substring(start, i).trim();
				if (!candidate.isEmpty()) {
					origins.add(candidate);
				}
				start = i + 1;
			}
		}
		return origins.toArray(new String[0]);
	}
}
