package com.newsrx.mcpplugin

import gitbucket.core.plugin.{Plugin, PluginRegistry}
import gitbucket.core.service.SystemSettingsService.SystemSettings
import io.github.gitbucket.solidbase.model.Version
import javax.servlet.ServletContext

class MCPPlugin extends Plugin {
  override val pluginId   = "mcp-plugin"
  override val pluginName = "MCP Plugin"
  override val description = "MCP (Model Context Protocol) server for GitBucket"
  override val versions = List(new Version("0.1.0"))

  override def controllers(
    registry: PluginRegistry,
    context: ServletContext,
    settings: SystemSettings
  ): Seq[(String, MCPController)] =
    Seq("/*" -> new MCPController())
}
