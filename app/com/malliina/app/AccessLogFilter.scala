package com.malliina.app

import play.api.Logger
import play.api.mvc.{EssentialAction, EssentialFilter}

import scala.concurrent.ExecutionContext

object AccessLogFilter {
  def apply(ec: ExecutionContext) = new AccessLogFilter()(ec)
}

class AccessLogFilter()(implicit ec: ExecutionContext) extends EssentialFilter {
  private val log = Logger(getClass)

  override def apply(next: EssentialAction): EssentialAction = { rh =>
    log.info(s"$rh")
    next(rh).map { r =>
      log.info(s"${r.header.status} $rh")
      r
    }
  }
}
