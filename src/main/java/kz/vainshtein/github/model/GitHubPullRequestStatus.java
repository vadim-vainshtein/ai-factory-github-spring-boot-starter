package kz.vainshtein.github.model;

public record GitHubPullRequestStatus(
		long number,
		String state,
		boolean merged,
		String htmlUrl
) {

	public boolean open() {
		return "open".equalsIgnoreCase(state);
	}
}
