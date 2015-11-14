package controllers

import java.net.URI
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import java.util.TimeZone

import com.github.macpersia.planty_jira_view.{ConnectionConfig, WorklogEntry, WorklogFilter, WorklogReporter}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

import play.api.libs.concurrent.Execution.Implicits._

case class ReportParams(
                         baseUrl: String,
                         username: String,
                         password: String,
                         jiraQuery: String,
                         author: Option[String],
                         fromDate: LocalDate,
                         toDate: LocalDate,
                         tzOffsetMinutes: Int)

class Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def worklogs = Action {
    Ok(views.html.worklogs())
  }

/*
  val paramsForm = Form(
    mapping(
      "baseUrl" -> nonEmptyText,
      "username" -> nonEmptyText,
      "password" -> nonEmptyText,
      "jiraQuery" -> text,
      "author" -> text,
      "fromDate" -> jodaLocalDate,
      "toDate" -> jodaLocalDate
    )(ReportParams.apply)(ReportParams.unapply)
  )

  def worklogs = Action {
    Ok(views.html.worklogs(paramsForm))
  }
*/

  def initParams = Action {
    Ok(Json.toJson(new ReportParams(
      "https://jira02.jirahosting.de/jira", null, null,
      "project = BICM AND labels = 2015 AND labels IN ('#7', '#8') AND summary ~ 'Project Management'",
      None,
      (ZonedDateTime.now minusWeeks 1).toLocalDate,
      (ZonedDateTime.now plusDays 1).toLocalDate,
      0
    )))
  }

  def extractConnectionConfig(params: ReportParams): ConnectionConfig =
    ConnectionConfig(new URI(params.baseUrl), params.username, params.password)

  def extractWorklogFilter(params: ReportParams): WorklogFilter = {
    val tzOffsetSeconds = (-1) * params.tzOffsetMinutes * 60
    val timeZone        = TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(tzOffsetSeconds))
    WorklogFilter(params.jiraQuery, params.author, params.fromDate, params.toDate, timeZone)
  }
  
  def constructReportParams(connConfig: ConnectionConfig, filter: WorklogFilter) = {
    val timeZone            = filter.timeZone
    val zoneId              = timeZone.toZoneId
    val instantAtStartOfDay = filter.fromDate.atStartOfDay.atZone(zoneId).toInstant
    val tzOffsetMillis      = timeZone.getOffset(instantAtStartOfDay.toEpochMilli)
    val tzOffsetMinutes     = tzOffsetMillis / 60 / 1000 / (-1)
    ReportParams(
      connConfig.baseUri.toString, connConfig.username, connConfig.password,
      filter.jiraQuery, filter.author, filter.fromDate, filter.toDate, tzOffsetMinutes)
  }

  def retrieveWorklogs = Action(BodyParsers.parse.json) { request =>
    val paramsResult = request.body.validate[ReportParams]
    paramsResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> JsString("KO"), "message" -> JsError.toJson(errors)))
      },
      params => {
        val entries = new WorklogReporter(
          extractConnectionConfig(params),
          extractWorklogFilter(params)
        ).retrieveWorklogs()
        // Ok(Json.toJson(entries)
        Ok(Json.obj("status" -> JsString("OK"), "entries" -> entries))
      })
  }

/*
  def retrieveWorklogs = Action { implicit request =>
    paramsForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.worklogs(formWithErrors))
      },
      params => {
        // binding success, you get the actual value.
        val entries = new WorklogReporter(extractConnectionConfig(params), extractWorklogFilter(params)).retrieveWorklogs()
        Ok(Json.obj("status" -> JsString("OK"), "entries" -> entries))
      }
    )
  }
*/
  implicit val worklogWrites: Writes[WorklogEntry] = (
    (JsPath \ "date").write[LocalDate] and
    (JsPath \ "description").write[String] and
    (JsPath \ "duration").write[Double]
  )(unlift(WorklogEntry.unapply))

  implicit val paramWrites: Writes[ReportParams] = (
    (JsPath \ "baseUrl").write[String] and
    (JsPath \ "username").write[String] and
    (JsPath \ "password").write[String] and
    (JsPath \ "jiraQuery").write[String] and
    (JsPath \ "author").writeNullable[String] and
    (JsPath \ "fromDate").write[LocalDate] and
    (JsPath \ "toDate").write[LocalDate] and
    (JsPath \ "tzOffsetMinutes").write[Int]
  )(unlift(ReportParams.unapply))

  implicit val paramsReads: Reads[ReportParams] = (
    (JsPath \ "baseUrl").read[String] and
    (JsPath \ "username").read[String] and
    (JsPath \ "password").read[String] and
    (JsPath \ "jiraQuery").read[String] and
    (JsPath \ "author").readNullable[String] and
    (JsPath \ "fromDate").read[LocalDate] and
    (JsPath \ "toDate").read[LocalDate] and
    (JsPath \ "tzOffsetMinutes").read[Int]
  )(ReportParams.apply _)
}
