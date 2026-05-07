package com.newsrx.mcpplugin.mcp

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.{NullNode, ObjectNode}

/** Minimal JSON-RPC 2.0 over HTTP (MCP Streamable HTTP transport). */
object JsonRpc {

  // Standard JSON-RPC error codes
  val ParseError: Int     = -32700
  val InvalidRequest: Int = -32600
  val MethodNotFound: Int = -32601
  val InvalidParams: Int  = -32602
  val InternalError: Int  = -32603

  // MCP-specific error code range
  val McpError: Int = -32000

  case class Request(
    method: String,
    params: Option[JsonNode],
    id: Option[JsonNode]
  )

  /** Parse a JSON-RPC request body. Returns Left(errorMessage) on failure. */
  def parseRequest(mapper: ObjectMapper, body: String): Either[String, Request] =
    try {
      val node = mapper.readTree(body)
      if (!node.isObject) return Left("request must be a JSON object")
      val jsonrpc = node.path("jsonrpc").asText("")
      if (jsonrpc != "2.0") return Left(s"unsupported jsonrpc version: $jsonrpc")
      val method = node.path("method").asText("")
      if (method.isEmpty) return Left("missing or empty 'method'")
      val params = Option(node.get("params")).filterNot(_.isNull)
      val id     = Option(node.get("id"))
      Right(Request(method, params, id))
    } catch {
      case e: Exception => Left(e.getMessage)
    }

  def successResponse(mapper: ObjectMapper, id: Option[JsonNode], result: JsonNode): String = {
    val resp = mapper.createObjectNode()
    resp.put("jsonrpc", "2.0")
    resp.set[ObjectNode]("result", result)
    id match {
      case Some(i) => resp.set[ObjectNode]("id", i)
      case None    => resp.set[ObjectNode]("id", NullNode.getInstance())
    }
    mapper.writeValueAsString(resp)
  }

  def errorResponse(
    mapper: ObjectMapper,
    id: Option[JsonNode],
    code: Int,
    message: String,
    data: Option[String] = None
  ): String = {
    val resp  = mapper.createObjectNode()
    resp.put("jsonrpc", "2.0")
    val error = mapper.createObjectNode()
    error.put("code", code)
    error.put("message", message)
    data.foreach(d => error.put("data", d))
    resp.set[ObjectNode]("error", error)
    id match {
      case Some(i) => resp.set[ObjectNode]("id", i)
      case None    => resp.set[ObjectNode]("id", NullNode.getInstance())
    }
    mapper.writeValueAsString(resp)
  }
}
