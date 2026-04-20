package kz.vainshtein.github;

import kz.vainshtein.github.autoconfigure.GitHubProperties;
import kz.vainshtein.github.model.GitHubIssue;
import kz.vainshtein.github.model.GitHubPullRequest;
import kz.vainshtein.github.model.GitHubPullRequestStatus;
import kz.vainshtein.github.model.PullRequestFeedback;
import kz.vainshtein.github.model.PullRequestFile;
import kz.vainshtein.github.model.PullRequestReviewTarget;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GitHubClient implements GitHubOperations {

	public static final String ORCHESTRATOR_COMMENT_MARKER = "<!-- orchestrator-review-update -->";

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final GitHubProperties properties;
	private final String apiBaseUrl;

	public GitHubClient(ObjectMapper objectMapper, GitHubProperties properties, HttpClient httpClient) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.httpClient = httpClient;
		this.apiBaseUrl = properties.apiBaseUrl().toString().replaceAll("/+$", "");
	}

	@Override
	public List<GitHubIssue> unresolvedIssues(String owner, String repository, int limit) {
		int requestedIssues = Math.max(1, limit);
		List<GitHubIssue> result = new ArrayList<>();
		Set<Long> includedIssueNumbers = new HashSet<>();
		int readyIssues = 0;
		for (int page = 1; ; page++) {
			String uri = "%s/repos/%s/%s/issues?state=open&per_page=100&page=%d".formatted(
					apiBaseUrl,
					url(owner),
					url(repository),
					page);
			JsonNode issues = get(uri);
			int count = 0;
			for (JsonNode issueNode : issues) {
				count++;
				if (issueNode.has("pull_request")) {
					continue;
				}
				GitHubIssue issue = issue(issueNode);
				if (issue.readyForImplementation()) {
					readyIssues++;
				}
				if ((result.size() < requestedIssues || issue.readyForImplementation())
						&& includedIssueNumbers.add(issue.number())) {
					result.add(issue);
				}
			}
			if (count < 100 || readyIssues >= requestedIssues) {
				return result;
			}
		}
	}

	@Override
	public GitHubPullRequest createPullRequest(
			String owner,
			String repository,
			String title,
			String branch,
			String baseBranch,
			long issueNumber,
			String body
	) {
		JsonNode requestBody = objectMapper.createObjectNode()
				.put("title", title)
				.put("head", branch)
				.put("base", baseBranch)
				.put("body", pullRequestBody(issueNumber, body));
		JsonNode response = send(jsonRequest(
				URI.create("%s/repos/%s/%s/pulls".formatted(apiBaseUrl, url(owner), url(repository))),
				"POST",
				requestBody));
		return new GitHubPullRequest(
				response.path("number").asLong(),
				response.path("title").asString(),
				branch,
				response.path("html_url").asString());
	}

	@Override
	public GitHubPullRequestStatus pullRequestStatus(String owner, String repository, long pullRequestNumber) {
		JsonNode response = get("%s/repos/%s/%s/pulls/%d".formatted(apiBaseUrl, url(owner), url(repository), pullRequestNumber));
		return new GitHubPullRequestStatus(
				response.path("number").asLong(),
				response.path("state").asString(),
				response.path("merged").asBoolean(false),
				response.path("html_url").asString());
	}

	@Override
	public List<GitHubPullRequest> openPullRequests(String owner, String repository) {
		List<GitHubPullRequest> pullRequests = new ArrayList<>();
		for (int page = 1; ; page++) {
			String uri = "%s/repos/%s/%s/pulls?state=open&per_page=100&page=%d".formatted(
					apiBaseUrl,
					url(owner),
					url(repository),
					page);
			JsonNode response = get(uri);
			int count = 0;
			for (JsonNode pullRequest : response) {
				count++;
				pullRequests.add(new GitHubPullRequest(
						pullRequest.path("number").asLong(),
						pullRequest.path("title").asString(),
						pullRequest.path("head").path("ref").asString(""),
						pullRequest.path("html_url").asString()));
			}
			if (count < 100) {
				return pullRequests;
			}
		}
	}

	@Override
	public PullRequestReviewTarget pullRequestReviewTarget(String owner, String repository, long pullRequestNumber) {
		JsonNode response = get("%s/repos/%s/%s/pulls/%d".formatted(apiBaseUrl, url(owner), url(repository), pullRequestNumber));
		return new PullRequestReviewTarget(
				response.path("number").asLong(),
				response.path("title").asString(),
				response.path("state").asString(),
				response.path("head").path("sha").asString(""),
				response.path("base").path("ref").asString(""),
				response.path("head").path("ref").asString(""),
				response.path("html_url").asString());
	}

	@Override
	public List<PullRequestFile> pullRequestFiles(String owner, String repository, long pullRequestNumber) {
		List<PullRequestFile> files = new ArrayList<>();
		for (int page = 1; ; page++) {
			String uri = "%s/repos/%s/%s/pulls/%d/files?per_page=100&page=%d".formatted(
					apiBaseUrl,
					url(owner),
					url(repository),
					pullRequestNumber,
					page);
			JsonNode response = get(uri);
			int count = 0;
			for (JsonNode file : response) {
				count++;
				files.add(new PullRequestFile(
						file.path("filename").asString(),
						file.path("status").asString(),
						file.path("additions").asInt(),
						file.path("deletions").asInt(),
						file.path("changes").asInt(),
						file.path("patch").asString("")));
			}
			if (count < 100) {
				return files;
			}
		}
	}

	@Override
	public List<PullRequestFeedback> pullRequestFeedback(String owner, String repository, long pullRequestNumber) {
		List<PullRequestFeedback> feedback = new ArrayList<>();
		addIssueComments(owner, repository, pullRequestNumber, feedback);
		addReviewComments(owner, repository, pullRequestNumber, feedback);
		addReviews(owner, repository, pullRequestNumber, feedback);
		return feedback;
	}

	@Override
	public void addPullRequestComment(String owner, String repository, long pullRequestNumber, String body) {
		JsonNode requestBody = objectMapper.createObjectNode().put("body", "%s\n%s".formatted(ORCHESTRATOR_COMMENT_MARKER, body));
		send(jsonRequest(
				URI.create("%s/repos/%s/%s/issues/%d/comments".formatted(apiBaseUrl, url(owner), url(repository), pullRequestNumber)),
				"POST",
				requestBody));
	}

	@Override
	public void addPullRequestReview(String owner, String repository, long pullRequestNumber, String body) {
		JsonNode requestBody = objectMapper.createObjectNode()
				.put("body", body)
				.put("event", "COMMENT");
		send(jsonRequest(
				URI.create("%s/repos/%s/%s/pulls/%d/reviews".formatted(apiBaseUrl, url(owner), url(repository), pullRequestNumber)),
				"POST",
				requestBody));
	}

	@Override
	public void addPullRequestFeedbackReply(
			String owner,
			String repository,
			long pullRequestNumber,
			PullRequestFeedback feedback,
			String body
	) {
		if (feedback.supportsThreadReply()) {
			JsonNode requestBody = objectMapper.createObjectNode().put("body", "%s\n%s".formatted(ORCHESTRATOR_COMMENT_MARKER, body));
			send(jsonRequest(
					URI.create("%s/repos/%s/%s/pulls/%d/comments/%d/replies".formatted(
							apiBaseUrl,
							url(owner),
							url(repository),
							pullRequestNumber,
							feedback.replyToCommentId())),
					"POST",
					requestBody));
			return;
		}
		addPullRequestComment(owner, repository, pullRequestNumber, body);
	}

	private GitHubIssue issue(JsonNode issue) {
		return new GitHubIssue(
				issue.path("number").asLong(),
				issue.path("title").asString(),
				issue.path("body").asString(""),
				issue.path("html_url").asString(),
				labels(issue));
	}

	private Set<String> labels(JsonNode issue) {
		List<String> labels = new ArrayList<>();
		for (JsonNode label : issue.path("labels")) {
			String name = label.path("name").asString("");
			if (!name.isBlank()) {
				labels.add(name);
			}
		}
		return labels.stream().collect(Collectors.toUnmodifiableSet());
	}

	private void addIssueComments(String owner, String repository, long pullRequestNumber, List<PullRequestFeedback> feedback) {
		String uri = "%s/repos/%s/%s/issues/%d/comments?per_page=100&page=%%d".formatted(
				apiBaseUrl, url(owner), url(repository), pullRequestNumber);
		forEachPage(uri, comment -> {
			String body = comment.path("body").asString("");
			if (!body.isBlank() && !body.contains(ORCHESTRATOR_COMMENT_MARKER)) {
				feedback.add(new PullRequestFeedback(
						"issue-comment:%s:%s".formatted(comment.path("id").asString(), comment.path("updated_at").asString()),
						"Pull request comment from %s: %s".formatted(comment.path("user").path("login").asString("unknown"), body),
						null));
			}
		});
	}

	private void addReviewComments(String owner, String repository, long pullRequestNumber, List<PullRequestFeedback> feedback) {
		String uri = "%s/repos/%s/%s/pulls/%d/comments?per_page=100&page=%%d".formatted(
				apiBaseUrl, url(owner), url(repository), pullRequestNumber);
		forEachPage(uri, comment -> {
			String body = comment.path("body").asString("");
			if (!body.isBlank() && !body.contains(ORCHESTRATOR_COMMENT_MARKER)) {
				feedback.add(new PullRequestFeedback(
						"review-comment:%s:%s".formatted(comment.path("id").asString(), comment.path("updated_at").asString()),
						"Review comment on %s from %s: %s".formatted(
								comment.path("path").asString("unknown file"),
								comment.path("user").path("login").asString("unknown"),
								body),
						comment.path("id").asLong()));
			}
		});
	}

	private void addReviews(String owner, String repository, long pullRequestNumber, List<PullRequestFeedback> feedback) {
		String uri = "%s/repos/%s/%s/pulls/%d/reviews?per_page=100&page=%%d".formatted(
				apiBaseUrl, url(owner), url(repository), pullRequestNumber);
		forEachPage(uri, review -> {
			String state = review.path("state").asString("");
			String body = review.path("body").asString("");
			if (!body.isBlank() && !"APPROVED".equalsIgnoreCase(state)) {
				feedback.add(new PullRequestFeedback(
						"review:%s:%s".formatted(review.path("id").asString(), review.path("submitted_at").asString()),
						"Review %s from %s: %s".formatted(
								state,
								review.path("user").path("login").asString("unknown"),
								body),
						null));
			}
		});
	}

	private String pullRequestBody(long issueNumber, String body) {
		String summary = body == null || body.isBlank()
				? "Implementation completed by the orchestrator."
				: body.strip();
		return """
				Implements #%d.

				%s
				""".formatted(issueNumber, summary);
	}

	private HttpRequest.Builder authenticated(HttpRequest.Builder builder) {
		builder.header("Accept", "application/vnd.github+json");
		builder.header("X-GitHub-Api-Version", "2022-11-28");
		String token = properties.token();
		if (token != null && !token.isBlank()) {
			builder.header("Authorization", "Bearer " + token);
		}
		return builder;
	}

	private JsonNode get(String uri) {
		return send(HttpRequest.newBuilder(URI.create(uri)).GET());
	}

	private HttpRequest.Builder jsonRequest(URI uri, String method, JsonNode body) {
		return HttpRequest.newBuilder(uri)
				.header("Content-Type", "application/json")
				.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
	}

	private void forEachPage(String uriTemplate, Consumer<JsonNode> consumer) {
		for (int page = 1; ; page++) {
			JsonNode response = get(uriTemplate.formatted(page));
			int count = 0;
			for (JsonNode item : response) {
				count++;
				consumer.accept(item);
			}
			if (count < 100) {
				return;
			}
		}
	}

	private JsonNode send(HttpRequest.Builder requestBuilder) {
		HttpRequest request = authenticated(requestBuilder.timeout(properties.requestTimeout())).build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() > 299) {
				throw new IllegalStateException("GitHub API failed with status %d: %s".formatted(response.statusCode(), response.body()));
			}
			if (response.body() == null || response.body().isBlank()) {
				return objectMapper.createObjectNode();
			}
			return objectMapper.readTree(response.body());
		} catch (IOException e) {
			throw new IllegalStateException("GitHub API request failed: " + request.uri(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted during GitHub API request: " + request.uri(), e);
		}
	}

	private String url(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
