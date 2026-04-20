package kz.vainshtein.github.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("ai-factory.github")
public record GitHubProperties(
		String token,
		URI apiBaseUrl,
		Duration connectTimeout,
		Duration requestTimeout
) {
	private static final URI DEFAULT_API_BASE_URL = URI.create("https://api.github.com");
	private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

	public GitHubProperties {
		apiBaseUrl = apiBaseUrl == null ? DEFAULT_API_BASE_URL : apiBaseUrl;
		connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
		requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
		if (!apiBaseUrl.isAbsolute()) {
			throw new IllegalArgumentException("GitHub API base URL must be absolute");
		}
		if (connectTimeout.isNegative() || connectTimeout.isZero()) {
			throw new IllegalArgumentException("GitHub connect timeout must be positive");
		}
		if (requestTimeout.isNegative() || requestTimeout.isZero()) {
			throw new IllegalArgumentException("GitHub request timeout must be positive");
		}
	}
}
