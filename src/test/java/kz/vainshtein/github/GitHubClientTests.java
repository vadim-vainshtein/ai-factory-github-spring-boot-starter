package kz.vainshtein.github;

import kz.vainshtein.github.autoconfigure.GitHubProperties;
import kz.vainshtein.github.model.GitHubIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubClientTests {

	@Mock
	private HttpClient httpClient;

	@Mock
	private HttpResponse<String> firstPageResponse;

	@Mock
	private HttpResponse<String> secondPageResponse;

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void continuesPastInitialStatusPageUntilReadyIssuesAreFound() throws Exception {
		when(firstPageResponse.statusCode()).thenReturn(200);
		when(firstPageResponse.body()).thenReturn(issuesJson(1, 100, "triage"));
		when(secondPageResponse.statusCode()).thenReturn(200);
		when(secondPageResponse.body()).thenReturn("[%s]".formatted(issueJson(101, GitHubIssue.READY_FOR_IMPLEMENTATION_LABEL)));
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn((HttpResponse) firstPageResponse)
				.thenReturn((HttpResponse) secondPageResponse);

		var issues = service().unresolvedIssues("owner", "repo", 100);

		assertThat(issues)
				.hasSize(101)
				.extracting(GitHubIssue::number)
				.endsWith(101L);
		assertThat(issues.getLast().readyForImplementation()).isTrue();

		ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient, org.mockito.Mockito.times(2))
				.send(requests.capture(), any(HttpResponse.BodyHandler.class));
		assertThat(requests.getAllValues())
				.extracting(request -> request.uri().toString())
				.containsExactly(
						"https://api.github.test/repos/owner/repo/issues?state=open&per_page=100&page=1",
						"https://api.github.test/repos/owner/repo/issues?state=open&per_page=100&page=2");
		assertThat(requests.getAllValues().getFirst().headers().firstValue("Authorization"))
				.contains("Bearer test-token");
	}

	private GitHubClient service() {
		return new GitHubClient(
				new ObjectMapper(),
				new GitHubProperties("test-token", URI.create("https://api.github.test/"), Duration.ZERO),
				httpClient);
	}

	private String issuesJson(long firstNumber, long count, String label) {
		return LongStream.range(firstNumber, firstNumber + count)
				.mapToObj(number -> issueJson(number, label))
				.collect(Collectors.joining(",", "[", "]"));
	}

	private String issueJson(long number, String label) {
		return """
				{
					"number": %d,
					"title": "Issue %d",
					"body": "body",
					"html_url": "https://example.test/issues/%d",
					"labels": [{"name": "%s"}]
				}
				""".formatted(number, number, number, label);
	}
}
