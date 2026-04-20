package kz.vainshtein.github;

import kz.vainshtein.github.model.GitHubIssue;
import kz.vainshtein.github.model.GitHubPullRequest;
import kz.vainshtein.github.model.GitHubPullRequestStatus;
import kz.vainshtein.github.model.PullRequestFeedback;
import kz.vainshtein.github.model.PullRequestFile;
import kz.vainshtein.github.model.PullRequestReviewTarget;

import java.util.List;

public interface GitHubOperations {

	List<GitHubIssue> unresolvedIssues(String owner, String repository, int limit);

	GitHubPullRequest createPullRequest(
			String owner,
			String repository,
			String title,
			String branch,
			String baseBranch,
			long issueNumber,
			String body);

	GitHubPullRequestStatus pullRequestStatus(String owner, String repository, long pullRequestNumber);

	List<GitHubPullRequest> openPullRequests(String owner, String repository);

	PullRequestReviewTarget pullRequestReviewTarget(String owner, String repository, long pullRequestNumber);

	List<PullRequestFile> pullRequestFiles(String owner, String repository, long pullRequestNumber);

	List<PullRequestFeedback> pullRequestFeedback(String owner, String repository, long pullRequestNumber);

	void addPullRequestComment(String owner, String repository, long pullRequestNumber, String body);

	void addPullRequestReview(String owner, String repository, long pullRequestNumber, String body);

	void addPullRequestFeedbackReply(
			String owner,
			String repository,
			long pullRequestNumber,
			PullRequestFeedback feedback,
			String body);
}
