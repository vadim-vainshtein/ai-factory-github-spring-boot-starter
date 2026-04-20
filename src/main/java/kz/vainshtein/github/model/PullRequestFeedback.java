package kz.vainshtein.github.model;

public record PullRequestFeedback(
		String key,
		String summary,
		Long replyToCommentId
) {
	public PullRequestFeedback(String key, String summary) {
		this(key, summary, null);
	}

	public boolean supportsThreadReply() {
		return replyToCommentId != null;
	}
}
