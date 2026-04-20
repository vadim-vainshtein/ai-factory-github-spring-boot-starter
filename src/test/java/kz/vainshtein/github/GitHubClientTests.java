package kz.vainshtein.github;

import kz.vainshtein.github.autoconfigure.GitHubProperties;
import kz.vainshtein.github.model.GitHubIssue;
import kz.vainshtein.github.model.PullRequestFeedback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
	void continuesPastInitialStatusPageUntilReadyIssuesAreFound() throws Exception {
		when(firstPageResponse.statusCode()).thenReturn(200);
		when(firstPageResponse.body()).thenReturn(issuesJson(1, 100, "triage"));
		when(secondPageResponse.statusCode()).thenReturn(200);
		when(secondPageResponse.body()).thenReturn("[%s]".formatted(issueJson(101, GitHubIssue.READY_FOR_IMPLEMENTATION_LABEL)));
		when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
				.thenReturn(firstPageResponse)
				.thenReturn(secondPageResponse);

		var issues = service().unresolvedIssues("owner", "repo", 100);

		assertThat(issues)
				.hasSize(101)
				.extracting(GitHubIssue::number)
				.endsWith(101L);
		assertThat(issues.getLast().readyForImplementation()).isTrue();

		ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient, org.mockito.Mockito.times(2))
				.send(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
		assertThat(requests.getAllValues())
				.extracting(request -> request.uri().toString())
				.containsExactly(
						"https://api.github.test/repos/owner/repo/issues?state=open&per_page=100&page=1",
						"https://api.github.test/repos/owner/repo/issues?state=open&per_page=100&page=2");
		assertThat(requests.getAllValues().getFirst().headers().firstValue("Authorization"))
				.contains("Bearer test-token");
		assertThat(requests.getAllValues().getFirst().timeout())
				.contains(Duration.ofSeconds(60));
	}

	@Test
	void pullRequestFeedbackPaginatesAndSkipsGeneratedFeedback() throws Exception {
		HttpResponse<String> issueCommentsPage1 = response(commentsJson(1, 100, GitHubClient.ORCHESTRATOR_COMMENT_MARKER + "\ngenerated"));
		HttpResponse<String> issueCommentsPage2 = response("[%s]".formatted(issueCommentJson(101, "needs a README")));
		HttpResponse<String> reviewCommentsPage1 = response(commentsJson(201, 100, GitHubClient.ORCHESTRATOR_COMMENT_MARKER + "\ngenerated reply"));
		HttpResponse<String> reviewCommentsPage2 = response("[%s]".formatted(reviewCommentJson(301, "src/Main.java", "fix the null case")));
		HttpResponse<String> reviewsPage1 = response("[%s,%s]".formatted(
				reviewJson(401, "APPROVED", "looks good"),
				reviewJson(402, "CHANGES_REQUESTED", "add tests")));
		when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
				.thenReturn(issueCommentsPage1)
				.thenReturn(issueCommentsPage2)
				.thenReturn(reviewCommentsPage1)
				.thenReturn(reviewCommentsPage2)
				.thenReturn(reviewsPage1);

		List<PullRequestFeedback> feedback = service().pullRequestFeedback("owner", "repo", 42);

		assertThat(feedback)
				.extracting(PullRequestFeedback::summary)
				.containsExactly(
						"Pull request comment from reviewer: needs a README",
						"Review comment on src/Main.java from reviewer: fix the null case",
						"Review CHANGES_REQUESTED from reviewer: add tests");
		assertThat(feedback)
				.extracting(PullRequestFeedback::key)
				.containsExactly(
						"issue-comment:101:2026-04-20T00:00:00Z",
						"review-comment:301:2026-04-20T00:00:00Z",
						"review:402:2026-04-20T00:00:00Z");
		assertThat(feedback.get(1).replyToCommentId()).isEqualTo(301L);

		ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient, org.mockito.Mockito.times(5))
				.send(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
		assertThat(requests.getAllValues())
				.extracting(request -> request.uri().toString())
				.containsExactly(
						"https://api.github.test/repos/owner/repo/issues/42/comments?per_page=100&page=1",
						"https://api.github.test/repos/owner/repo/issues/42/comments?per_page=100&page=2",
						"https://api.github.test/repos/owner/repo/pulls/42/comments?per_page=100&page=1",
						"https://api.github.test/repos/owner/repo/pulls/42/comments?per_page=100&page=2",
						"https://api.github.test/repos/owner/repo/pulls/42/reviews?per_page=100&page=1");
	}

	private GitHubClient service() {
		return new GitHubClient(
				new ObjectMapper(),
				new GitHubProperties("test-token", URI.create("https://api.github.test/"), Duration.ofSeconds(1), null),
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

	@SuppressWarnings("unchecked")
	private HttpResponse<String> response(String body) {
		HttpResponse<String> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(body);
		return response;
	}

	private String commentsJson(long firstId, long count, String body) {
		return LongStream.range(firstId, firstId + count)
				.mapToObj(id -> issueCommentJson(id, body))
				.collect(Collectors.joining(",", "[", "]"));
	}

	private String issueCommentJson(long id, String body) {
		return """
				{
					"id": %d,
					"updated_at": "2026-04-20T00:00:00Z",
					"user": {"login": "reviewer"},
					"body": "%s"
				}
				""".formatted(id, json(body));
	}

	private String reviewCommentJson(long id, String path, String body) {
		return """
				{
					"id": %d,
					"updated_at": "2026-04-20T00:00:00Z",
					"path": "%s",
					"user": {"login": "reviewer"},
					"body": "%s"
				}
				""".formatted(id, path, json(body));
	}

	private String reviewJson(long id, String state, String body) {
		return """
				{
					"id": %d,
					"submitted_at": "2026-04-20T00:00:00Z",
					"state": "%s",
					"user": {"login": "reviewer"},
					"body": "%s"
				}
				""".formatted(id, state, json(body));
	}

	private String json(String value) {
		return value.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n");
	}
}
