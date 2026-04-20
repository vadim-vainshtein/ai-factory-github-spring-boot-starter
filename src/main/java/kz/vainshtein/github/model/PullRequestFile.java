package kz.vainshtein.github.model;

public record PullRequestFile(
		String filename,
		String status,
		int additions,
		int deletions,
		int changes,
		String patch
) {
}
