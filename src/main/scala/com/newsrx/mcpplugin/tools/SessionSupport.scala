package com.newsrx.mcpplugin.tools

import gitbucket.core.plugin.Sessions

trait SessionSupport {
  protected def blockingSession = Sessions.session
}
