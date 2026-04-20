package kz.vainshtein.github.model;

import java.util.Set;

public record GitHubIssue(
		long number,
		String title,
		String body,
		String htmlUrl,
		Set<String> labels
) {
	public static final String READY_FOR_IMPLEMENTATION_LABEL = "ready-for-implementation";

	public GitHubIssue(long number, String title, String body, String htmlUrl) {
		this(number, title, body, htmlUrl, Set.of(READY_FOR_IMPLEMENTATION_LABEL));
	}

	public GitHubIssue {
		labels = labels == null ? Set.of() : Set.copyOf(labels);
	}

	public boolean readyForImplementation() {
		return labels.stream().anyMatch(READY_FOR_IMPLEMENTATION_LABEL::equalsIgnoreCase);
	}
}
