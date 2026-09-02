# Deploy to a GitBucket instance — verified runbook

Tested 2026-09-03 against a local GitBucket 4.44.0 instance (JDK 21, `~/Downloads/gitbucket.war`
run standalone with Jetty). Full round trip confirmed: build, hot-deploy, MCP handshake, read tool
call, write tool call.

## 1. Build
```bash
./gradlew shadowJar
```
Produces `build/libs/gitbucket-mcp-plugin-<version>.jar`.

## 2. Deploy
Drop the jar into the target GitBucket's plugin directory:
```bash
cp build/libs/gitbucket-mcp-plugin-<version>.jar $GITBUCKET_HOME/plugins/
```
GitBucket runs a `PluginWatchThread` that watches that directory and **hot-reloads on file create** —
no GitBucket restart needed. Confirmed in logs:
```
INFO  g.core.plugin.PluginWatchThread - ENTRY_CREATE: gitbucket-mcp-plugin-<version>.jar
INFO  g.core.plugin.PluginWatchThread - Reloading plugins...
INFO  g.core.plugin.PluginRegistry - Initialize gitbucket-mcp-plugin-<version>.jar
INFO  g.core.plugin.PluginWatchThread - Reloading finished.
```
Reload took under 100ms in testing.

## 3. Verify
```bash
curl http://<host>/plugin-mcp/health
# {"status":"ok","protocol":"MCP","transport":"Streamable HTTP"}
```

MCP JSON-RPC handshake (Basic auth, GitBucket username + password or PAT):
```bash
curl -u <user>:<pass> -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' \
  http://<host>/plugin-mcp/
```
Returned in testing:
```json
{"jsonrpc":"2.0","result":{"capabilities":{"tools":{"listChanged":false},"resources":{"subscribe":false,"listChanged":false},"prompts":{"listChanged":false}},"serverInfo":{"name":"gitbucket-mcp-plugin","version":"0.1.0"},"protocolVersion":"2025-03-26"},"id":1}
```

`tools/list` returned all 14 documented tools (list/get/create/close/reopen for issues and PRs, plus
repo tools). Round-tripped both a read (`list_repositories`) and a write (`create_issue`) tool call
against a real test repo — both worked and reflected actual GitBucket state.

## 4. Known gaps / things to check on the target instance
- Auth tested here as HTTP Basic with the GitBucket account's own password (`root:root` on the test
  instance). GitBucket's `/api/v3/user/access_tokens` endpoint returned 404 on 4.44.0 — if the prod
  instance is on a different version, verify whether PAT creation via API works there, or just use
  the account password / an existing PAT the same way `git` already authenticates against it.
- GitBucket version compatibility per this plugin's own README: 4.42.0–4.46.0, Java 17 or 21. The
  4.44.0 test instance is inside that range.
- Not tested: TLS termination in front of GitBucket, concurrent multi-plugin interaction beyond the
  bundled gist/emoji/notifications/pages plugins, or behavior under GitBucket's private-repo/LDAP auth
  modes.
- This was validated against a disposable local instance, not the shared corporate GitBucket at
  `192.168.214.202` — deploying there is a separate, deliberate infra change for whoever owns that
  box.
