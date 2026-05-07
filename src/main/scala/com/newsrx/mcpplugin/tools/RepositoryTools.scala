package com.newsrx.mcpplugin.tools

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.newsrx.mcpplugin.mcp.ToolDef
import gitbucket.core.model.Account
import gitbucket.core.service.{AccountService, RepositoryService}
import javax.servlet.http.HttpServletRequest

private[tools] trait RepoService
    extends RepositoryService
    with AccountService
    with SessionSupport

// ── list_repositories ───────────────────────────────────────────────────────

class ListRepositoriesTool extends ToolDef with RepoService {
  val name        = "list_repositories"
  val description = "List repositories accessible to a GitBucket user"

  def inputSchema(mapper: ObjectMapper) = objectSchema(
    mapper,
    required = Seq("owner"),
    "owner"          -> strNode(mapper, "User or organization name"),
    "without_forked" -> boolNode(mapper, "Exclude forked repositories (default: false)")
  )

  def execute(args: JsonNode, account: Account, request: HttpServletRequest, mapper: ObjectMapper): JsonNode = {
    val owner        = str(args, "owner")
    val withoutForked = bool(args, "without_forked", default = false)

    implicit val session = blockingSession(request)

    val repos = getUserRepositories(owner, withoutForked)
    val arr   = mapper.createArrayNode()
    repos.foreach { info =>
      val r = info.repository
      val n = mapper.createObjectNode()
      n.put("name",          r.repositoryName)
      n.put("owner",         r.userName)
      n.put("description",   r.description.getOrElse(""))
      n.put("is_private",    r.isPrivate)
      n.put("default_branch", r.defaultBranch)
      n.put("issue_count",   info.issueCount)
      n.put("pr_count",      info.pullCount)
      n.put("fork_count",    info.forkedCount)
      arr.add(n)
    }

    val root = mapper.createObjectNode()
    root.put("count", repos.size)
    root.set[ObjectNode]("repositories", arr)
    jsonContent(mapper, root)
  }
}

// ── get_repository ───────────────────────────────────────────────────────────

class GetRepositoryTool extends ToolDef with RepoService {
  val name        = "get_repository"
  val description = "Get details of a specific GitBucket repository"

  def inputSchema(mapper: ObjectMapper) = objectSchema(
    mapper,
    required = Seq("owner", "repo"),
    "owner" -> strNode(mapper, "Repository owner"),
    "repo"  -> strNode(mapper, "Repository name")
  )

  def execute(args: JsonNode, account: Account, request: HttpServletRequest, mapper: ObjectMapper): JsonNode = {
    val owner = str(args, "owner")
    val repo  = str(args, "repo")

    implicit val session = blockingSession(request)

    getRepository(owner, repo) match {
      case None =>
        throw new NoSuchElementException(s"repository $owner/$repo not found")
      case Some(info) =>
        val r = info.repository
        val n = mapper.createObjectNode()
        n.put("name",           r.repositoryName)
        n.put("owner",          r.userName)
        n.put("description",    r.description.getOrElse(""))
        n.put("is_private",     r.isPrivate)
        n.put("default_branch", r.defaultBranch)
        n.put("issue_count",    info.issueCount)
        n.put("pr_count",       info.pullCount)
        n.put("fork_count",     info.forkedCount)

        val branches = mapper.createArrayNode()
        info.branchList.foreach(branches.add)
        n.set[ObjectNode]("branches", branches)

        val tags = mapper.createArrayNode()
        info.tags.foreach(t => tags.add(t.name))
        n.set[ObjectNode]("tags", tags)

        jsonContent(mapper, n)
    }
  }
}
