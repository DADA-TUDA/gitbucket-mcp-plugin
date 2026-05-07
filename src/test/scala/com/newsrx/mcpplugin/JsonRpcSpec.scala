package com.newsrx.mcpplugin

import com.fasterxml.jackson.databind.ObjectMapper
import com.newsrx.mcpplugin.mcp.JsonRpc
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

@RunWith(classOf[JUnitRunner])
class JsonRpcSpec extends AnyFunSuite with Matchers {

  val mapper = new ObjectMapper()

  // ── parseRequest ────────────────────────────────────────────────────────

  test("parseRequest parses a minimal valid request") {
    val body = """{"jsonrpc":"2.0","method":"ping","id":1}"""
    JsonRpc.parseRequest(mapper, body) match {
      case Right(req) =>
        req.method        shouldBe "ping"
        req.params        shouldBe None
        req.id.map(_.asInt()) shouldBe Some(1)
      case Left(err) => fail(s"unexpected error: $err")
    }
  }

  test("parseRequest parses a request with params object") {
    val body = """{"jsonrpc":"2.0","method":"tools/call","params":{"name":"list_issues","arguments":{"owner":"alice","repo":"myrepo"}},"id":42}"""
    JsonRpc.parseRequest(mapper, body) match {
      case Right(req) =>
        req.method shouldBe "tools/call"
        req.params.isDefined shouldBe true
        req.params.get.get("name").asText() shouldBe "list_issues"
      case Left(err) => fail(s"unexpected error: $err")
    }
  }

  test("parseRequest accepts null id (notification)") {
    val body = """{"jsonrpc":"2.0","method":"initialized","id":null}"""
    JsonRpc.parseRequest(mapper, body) match {
      case Right(req) =>
        req.method shouldBe "initialized"
      case Left(err) => fail(s"unexpected error: $err")
    }
  }

  test("parseRequest returns Left for missing method") {
    val body = """{"jsonrpc":"2.0","id":1}"""
    JsonRpc.parseRequest(mapper, body) shouldBe a[Left[_, _]]
  }

  test("parseRequest returns Left for wrong jsonrpc version") {
    val body = """{"jsonrpc":"1.0","method":"ping","id":1}"""
    JsonRpc.parseRequest(mapper, body) shouldBe a[Left[_, _]]
  }

  test("parseRequest returns Left for invalid JSON") {
    JsonRpc.parseRequest(mapper, "{not-json}") shouldBe a[Left[_, _]]
  }

  test("parseRequest returns Left for non-object JSON") {
    JsonRpc.parseRequest(mapper, """["a","b"]""") shouldBe a[Left[_, _]]
  }

  test("parseRequest returns Left for empty body") {
    JsonRpc.parseRequest(mapper, "") shouldBe a[Left[_, _]]
  }

  // ── successResponse ──────────────────────────────────────────────────────

  test("successResponse includes jsonrpc, result and id") {
    val id     = mapper.readTree("5")
    val result = mapper.createObjectNode()
    result.put("status", "ok")
    val json = mapper.readTree(JsonRpc.successResponse(mapper, Some(id), result))
    json.get("jsonrpc").asText() shouldBe "2.0"
    json.get("result").get("status").asText() shouldBe "ok"
    json.get("id").asInt() shouldBe 5
  }

  test("successResponse uses null id when id is None") {
    val result = mapper.createObjectNode()
    val json   = mapper.readTree(JsonRpc.successResponse(mapper, None, result))
    json.get("id").isNull shouldBe true
  }

  test("successResponse does not include error field") {
    val result = mapper.createObjectNode()
    val json   = mapper.readTree(JsonRpc.successResponse(mapper, None, result))
    json.has("error") shouldBe false
  }

  // ── errorResponse ────────────────────────────────────────────────────────

  test("errorResponse includes code and message") {
    val id   = mapper.readTree("3")
    val json = mapper.readTree(
      JsonRpc.errorResponse(mapper, Some(id), JsonRpc.MethodNotFound, "unknown method: foo")
    )
    json.get("jsonrpc").asText() shouldBe "2.0"
    json.get("id").asInt() shouldBe 3
    json.get("error").get("code").asInt() shouldBe JsonRpc.MethodNotFound
    json.get("error").get("message").asText() shouldBe "unknown method: foo"
  }

  test("errorResponse includes optional data field") {
    val json = mapper.readTree(
      JsonRpc.errorResponse(mapper, None, JsonRpc.InternalError, "oops", Some("stack trace"))
    )
    json.get("error").get("data").asText() shouldBe "stack trace"
  }

  test("errorResponse does not include result field") {
    val json = mapper.readTree(
      JsonRpc.errorResponse(mapper, None, JsonRpc.InternalError, "oops")
    )
    json.has("result") shouldBe false
  }

  // ── error codes ──────────────────────────────────────────────────────────

  test("error codes have correct values") {
    JsonRpc.ParseError      shouldBe -32700
    JsonRpc.InvalidRequest  shouldBe -32600
    JsonRpc.MethodNotFound  shouldBe -32601
    JsonRpc.InvalidParams   shouldBe -32602
    JsonRpc.InternalError   shouldBe -32603
    JsonRpc.McpError        shouldBe -32000
  }
}
