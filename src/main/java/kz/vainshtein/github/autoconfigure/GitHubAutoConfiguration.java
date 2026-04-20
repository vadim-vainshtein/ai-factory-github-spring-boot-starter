package kz.vainshtein.github.autoconfigure;

import kz.vainshtein.github.GitHubClient;
import kz.vainshtein.github.GitHubOperations;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;

@AutoConfiguration
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	HttpClient githubHttpClient(GitHubProperties properties) {
		return HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
	}

	@Bean
	@ConditionalOnMissingBean
	GitHubOperations gitHubOperations(ObjectMapper objectMapper, GitHubProperties properties, HttpClient githubHttpClient) {
		return new GitHubClient(objectMapper, properties, githubHttpClient);
	}
}
