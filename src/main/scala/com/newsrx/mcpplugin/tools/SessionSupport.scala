package com.newsrx.mcpplugin.tools

import gitbucket.core.servlet.Database
import javax.servlet.http.HttpServletRequest

trait SessionSupport {
  protected def blockingSession(implicit request: HttpServletRequest) =
    Database.getSession(request)
}
