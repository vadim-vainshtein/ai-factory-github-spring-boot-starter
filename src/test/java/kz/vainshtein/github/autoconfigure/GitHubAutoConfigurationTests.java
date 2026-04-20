package kz.vainshtein.github.autoconfigure;

import kz.vainshtein.github.GitHubOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, GitHubAutoConfiguration.class));

	@Test
	void registersGitHubOperationsWithDefaultProperties() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(GitHubOperations.class);
			assertThat(context).hasSingleBean(GitHubProperties.class);
			assertThat(context.getBean(GitHubProperties.class).apiBaseUrl())
					.isEqualTo(URI.create("https://api.github.com"));
		});
	}

	@Test
	void bindsGitHubProperties() {
		contextRunner
				.withPropertyValues(
						"ai-factory.github.token=test-token",
						"ai-factory.github.api-base-url=https://github.enterprise.test/api/v3",
						"ai-factory.github.connect-timeout=5s")
				.run(context -> {
					var properties = context.getBean(GitHubProperties.class);

					assertThat(properties.token()).isEqualTo("test-token");
					assertThat(properties.apiBaseUrl()).isEqualTo(URI.create("https://github.enterprise.test/api/v3"));
					assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
				});
	}
}
