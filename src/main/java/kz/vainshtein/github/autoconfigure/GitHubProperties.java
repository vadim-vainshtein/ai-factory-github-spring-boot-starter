package kz.vainshtein.github.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("ai-factory.github")
public record GitHubProperties(
		String token,
		URI apiBaseUrl,
		Duration connectTimeout
) {
	private static final URI DEFAULT_API_BASE_URL = URI.create("https://api.github.com");
	private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(20);

	public GitHubProperties {
		apiBaseUrl = apiBaseUrl == null ? DEFAULT_API_BASE_URL : apiBaseUrl;
		connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
	}
}
