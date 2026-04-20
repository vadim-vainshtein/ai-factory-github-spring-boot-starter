package kz.vainshtein.github.model;

public record GitHubPullRequest(
		long number,
		String title,
		String branch,
		String htmlUrl
) {
}
