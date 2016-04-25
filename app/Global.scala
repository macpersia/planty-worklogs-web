import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.Logger
import play.api.mvc._
import play.api._

object Global extends WithFilters(AccessLoggingFilter) {

  override def onStart(app: Application) {
    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    Logger.info("Application has stopped")
  }
}

object AccessLoggingFilter extends Filter {

  val accessLogger = Logger("access")

  def apply(next: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
    val resultFuture = next(request)

    resultFuture.foreach(result => {
      val msg = s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress}" +
        s" status=${result.header.status}";
      accessLogger.info(msg)
    })

    resultFuture
  }
}

import javax.inject.Inject

import play.api.http.HttpFilters
import play.filters.cors.CORSFilter

class MyHttpFilters @Inject() (corsFilter: CORSFilter) extends HttpFilters {
  // TODO: We shouldn't need this!
  def filters = Seq(corsFilter)
}
