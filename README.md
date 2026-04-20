# AI Factory GitHub Spring Boot Starter

Spring Boot starter that auto-configures a small GitHub API client for AI Factory
workflows. The client can discover implementation-ready issues, create pull
requests, inspect pull request status and files, collect review feedback, and
post comments or review replies.

## Requirements

- Java 21
- Spring Boot 4.x
- A GitHub fine-grained token or GitHub App token when accessing private
  repositories or writing comments, reviews, and pull requests

## Installation

Add the library to a Spring Boot application as a dependency:

```gradle
dependencies {
    implementation "kz.vainshtein:ai-factory-github-spring-boot-starter:0.0.1-SNAPSHOT"
}
```

If the artifact is not published to your repository manager yet, include this
project as a composite build or publish it to your internal Maven repository.

## Configuration

The starter registers a `GitHubOperations` bean when one is not already present.
Configure it with the `ai-factory.github` prefix:

```yaml
ai-factory:
  github:
    token: ${GITHUB_TOKEN}
    api-base-url: https://api.github.com
    connect-timeout: 20s
    request-timeout: 60s
```

| Property | Default | Description |
| --- | --- | --- |
| `token` | none | Optional bearer token. Required for private repositories and write operations. |
| `api-base-url` | `https://api.github.com` | GitHub REST API base URL. Use an enterprise API URL for GitHub Enterprise Server. |
| `connect-timeout` | `20s` | Maximum time to establish an HTTP connection. Must be positive. |
| `request-timeout` | `60s` | Maximum time for an individual GitHub API request. Must be positive. |

## Usage

Inject `GitHubOperations` into an application service:

```java
import kz.vainshtein.github.GitHubOperations;
import org.springframework.stereotype.Service;

@Service
class GitHubWorkflowService {

    private final GitHubOperations gitHub;

    GitHubWorkflowService(GitHubOperations gitHub) {
        this.gitHub = gitHub;
    }

    void inspect(String owner, String repository) {
        var issues = gitHub.unresolvedIssues(owner, repository, 10);
        var pullRequests = gitHub.openPullRequests(owner, repository);
    }
}
```

`unresolvedIssues` excludes pull requests and marks issues with the
`ready-for-implementation` label as ready. Feedback collection skips comments
created by this client and paginates GitHub comment and review endpoints.

## Development

Run the verification suite with:

```bash
./gradlew test
```
