package com.newsrx.mcpplugin.tools

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.newsrx.mcpplugin.mcp.ToolDef
import gitbucket.core.model.{Account, Issue, PullRequest}
import gitbucket.core.service.{AccountService, ActivityService, CommitsService, IssuesService, LabelsService, MergeService, MilestonesService, PrioritiesService, PullRequestService, RepositoryService, RequestCache, SystemSettingsService, WebHookPullRequestReviewCommentService, WebHookPullRequestService, WebHookService}
import gitbucket.core.service.IssuesService.IssueSearchCondition
import gitbucket.core.util.JGitUtil.DiffInfo
import javax.servlet.http.HttpServletRequest

// ── Shared helpers ──────────────────────────────────────────────────────────

private[tools] trait PrService
    extends IssuesService
    with PullRequestService
    with RepositoryService
    with AccountService
    with LabelsService
    with PrioritiesService
    with MilestonesService
    with CommitsService
    with WebHookService
    with WebHookPullRequestService
    with WebHookPullRequestReviewCommentService
    with MergeService
    with ActivityService
    with RequestCache
    with SystemSettingsService
    with SessionSupport

private[tools] object PrJson {
  def prNode(mapper: ObjectMapper, issue: Issue, pr: PullRequest): ObjectNode = {
    val n = mapper.createObjectNode()
    n.put("number",      issue.issueId)
    n.put("title",       issue.title)
    n.put("body",        issue.content.getOrElse(""))
    n.put("state",       if (issue.closed) "closed" else "open")
    n.put("author",      issue.openedUserName)
    n.put("base_branch", pr.branch)
    n.put("head_branch", pr.requestBranch)
    n.put("head_user",   pr.requestUserName)
    n.put("is_draft",    pr.isDraft)
    n.put("created_at",  issue.registeredDate.toString)
    n.put("updated_at",  issue.updatedDate.toString)
    n
  }
}

// ── list_pull_requests ──────────────────────────────────────────────────────

class ListPullRequestsTool extends ToolDef with PrService {
  val name        = "list_pull_requests"
  val description = "List pull requests in a GitBucket repository"

  def inputSchema(mapper: ObjectMapper) = objectSchema(
    mapper,
    required = Seq("owner", "repo"),
    "owner" -> strNode(mapper, "Repository owner (user or organization)"),
    "repo"  -> strNode(mapper, "Repository name"),
    "state" -> strEnum(mapper, "Filter by state (default: open)", "open", "closed", "all"),
    "limit" -> intNode(mapper, "Maximum number of results (default: 30, max: 100)")
  )

  def execute(args: JsonNode, account: Account, request: HttpServletRequest, mapper: ObjectMapper): JsonNode = {
    val owner = str(args, "owner")
    val repo  = str(args, "repo")
    val state = strOpt(args, "state").getOrElse("open")
    val limit = math.min(intOpt(args, "limit", 30), 100)

    implicit val session = blockingSession

    val condition = new IssueSearchCondition(
      labels = Set.empty, milestone = None, priority = None,
      author = None, assigned = None, mentioned = None,
      state = state, sort = "created", direction = "desc",
      visibility = None, groups = Set.empty, others = Nil
    )

    val prs = searchPullRequestByApi(condition, 0, limit, (owner, repo))
    val arr = mapper.createArrayNode()
    prs.foreach { t =>
      arr.add(PrJson.prNode(mapper, t._1, t._4))
    }

    val root = mapper.createObjectNode()
    root.put("count", prs.size)
    root.set[ObjectNode]("pull_requests", arr)
    jsonContent(mapper, root)
  }
}

// ── get_pull_request ────────────────────────────────────────────────────────

class GetPullRequestTool extends ToolDef with PrService {
  val name        = "get_pull_request"
  val description = "Get details of a specific pull request"

  def inputSchema(mapper: ObjectMapper) = objectSchema(
    mapper,
    required = Seq("owner", "repo", "number"),
    "owner"  -> strNode(mapper, "Repository owner"),
    "repo"   -> strNode(mapper, "Repository name"),
    "number" -> intNode(mapper, "Pull request number")
  )

  def execute(args: JsonNode, account: Account, request: HttpServletRequest, mapper: ObjectMapper): JsonNode = {
    val owner  = str(args, "owner")
    val repo   = str(args, "repo")
    val number = int(args, "number")

    implicit val session = blockingSession

    getPullRequest(owner, repo, number) match {
      case None =>
        throw new NoSuchElementException(s"pull request #$number not found in $owner/$repo")
      case Some((issue, pr)) =>
        val node = PrJson.prNode(mapper, issue, pr)
        node.put("comment_count", getComments(owner, repo, number).size)
        jsonContent(mapper, node)
    }
  }
}

// ── add_pr_comment ──────────────────────────────────────────────────────────

class AddPrCommentTool extends ToolDef with PrService {
  val name        = "add_pr_comment"
  val description = "Add a comment to a pull request"

  def inputSchema(mapper: ObjectMapper) = objectSchema(
    mapper,
    required = Seq("owner", "repo", "number", "body"),
    "owner"  -> strNode(mapper, "Repository owner"),
    "repo"   -> strNode(mapper, "Repository name"),
    "number" -> intNode(mapper, "Pull request number"),
    "body"   -> strNode(mapper, "Comment text (Markdown supported)")
  )

  def execute(args: JsonNode, account: Account, request: HttpServletRequest, mapper: ObjectMapper): JsonNode = {
    val owner  = str(args, "owner")
    val repo   = str(args, "repo")
    val number = int(args, "number")
    val body   = str(args, "body")

    implicit val session = blockingSession

    getPullRequest(owner, repo, number).getOrElse(
      throw new NoSuchElementException(s"pull request #$number not found in $owner/$repo")
    )

    val commentId = createComment(owner, repo, account.userName, number, body, "comment")

    val result = mapper.createObjectNode()
    result.put("comment_id", commentId)
    result.put("pr_number",  number)
    result.put("author",     account.userName)
    result.put("body",       body)
    jsonContent(mapper, result)
  }
}

// ── close_pull_request ──────────────────────────────────────────────────────

class ClosePullRequestTool extends ToolDef with PrService {
  val name        = "close_pull_request"
  val description = "Close a pull request without merging"

  def inputSchema(mapper: ObjectMapper) = objectSchema(
    mapper,
    required = Seq("owner", "repo", "number"),
    "owner"  -> strNode(mapper, "Repository owner"),
    "repo"   -> strNode(mapper, "Repository name"),
    "number" -> intNode(mapper, "Pull request number"),
    "reason" -> strNode(mapper, "Optional comment to post when closing")
  )

  def execute(args: JsonNode, account: Account, request: HttpServletRequest, mapper: ObjectMapper): JsonNode = {
    val owner  = str(args, "owner")
    val repo   = str(args, "repo")
    val number = int(args, "number")
    val reason = strOpt(args, "reason")

    implicit val session = blockingSession

    getPullRequest(owner, repo, number).getOrElse(
      throw new NoSuchElementException(s"pull request #$number not found in $owner/$repo")
    )

    reason.foreach(r => createComment(owner, repo, account.userName, number, r, "close"))
    updateClosed(owner, repo, number, closed = true)

    textContent(mapper, s"Pull request #$number in $owner/$repo closed")
  }
}

// ── reopen_pull_request ─────────────────────────────────────────────────────

class ReopenPullRequestTool extends ToolDef with PrService {
  val name        = "reopen_pull_request"
  val description = "Reopen a closed pull request"

  def inputSchema(mapper: ObjectMapper) = objectSchema(
    mapper,
    required = Seq("owner", "repo", "number"),
    "owner"  -> strNode(mapper, "Repository owner"),
    "repo"   -> strNode(mapper, "Repository name"),
    "number" -> intNode(mapper, "Pull request number")
  )

  def execute(args: JsonNode, account: Account, request: HttpServletRequest, mapper: ObjectMapper): JsonNode = {
    val owner  = str(args, "owner")
    val repo   = str(args, "repo")
    val number = int(args, "number")

    implicit val session = blockingSession

    getPullRequest(owner, repo, number).getOrElse(
      throw new NoSuchElementException(s"pull request #$number not found in $owner/$repo")
    )

    createComment(owner, repo, account.userName, number, "", "reopen")
    updateClosed(owner, repo, number, closed = false)

    textContent(mapper, s"Pull request #$number in $owner/$repo reopened")
  }
}

// ── get_pr_diff ─────────────────────────────────────────────────────────────

class GetPrDiffTool extends ToolDef with PrService {
  val name        = "get_pr_diff"
  val description = "Get the list of files changed in a pull request"

  def inputSchema(mapper: ObjectMapper) = objectSchema(
    mapper,
    required = Seq("owner", "repo", "number"),
    "owner"  -> strNode(mapper, "Repository owner"),
    "repo"   -> strNode(mapper, "Repository name"),
    "number" -> intNode(mapper, "Pull request number")
  )

  def execute(args: JsonNode, account: Account, request: HttpServletRequest, mapper: ObjectMapper): JsonNode = {
    val owner  = str(args, "owner")
    val repo   = str(args, "repo")
    val number = int(args, "number")

    implicit val session = blockingSession

    val (_, pr) = getPullRequest(owner, repo, number).getOrElse(
      throw new NoSuchElementException(s"pull request #$number not found in $owner/$repo")
    )

    val settings = loadSystemSettings()
    val (_, diffs) = getRequestCompareInfo(
      pr.userName, pr.repositoryName, pr.commitIdFrom,
      pr.requestUserName, pr.requestRepositoryName, pr.requestBranch,
      settings
    )

    val root  = mapper.createObjectNode()
    val files = mapper.createArrayNode()
    diffs.foreach { diff =>
      val f = mapper.createObjectNode()
      f.put("path",        if (diff.newPath.nonEmpty) diff.newPath else diff.oldPath)
      f.put("old_path",    diff.oldPath)
      f.put("new_path",    diff.newPath)
      f.put("change_type", diff.changeType.name())
      diff.patch.foreach(p => f.put("patch", p))
      files.add(f)
    }
    root.put("pr_number",  number)
    root.put("file_count", diffs.size)
    root.set[ObjectNode]("files", files)
    jsonContent(mapper, root)
  }
}
