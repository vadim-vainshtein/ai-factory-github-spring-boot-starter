package kz.vainshtein.github.model;

public record PullRequestReviewTarget(
		long number,
		String title,
		String state,
		String headSha,
		String baseRef,
		String headRef,
		String htmlUrl
) {

	public boolean open() {
		return "open".equalsIgnoreCase(state);
	}
}
