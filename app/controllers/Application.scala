package controllers

import java.net.URI
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import java.util
import java.util.{Comparator, TimeZone}

import com.github.macpersia.planty.views.cats.CatsWorklogReporter
import com.github.macpersia.planty.views.jira
import com.github.macpersia.planty.views.cats
import com.github.macpersia.planty.views.jira.JiraWorklogReporter

import com.github.macpersia.planty.views.jira.model.JiraWorklogFilter
import com.github.macpersia.planty.worklogs.model.{WorklogEntry, WorklogFilter}
import com.github.macpersia.planty.worklogs.WorklogReporting
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import resource._

import play.api.Logger

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._

case class ReportParams(fromDate: LocalDate,
                        toDate: LocalDate,
                        tzOffsetMinutes: Int,
                        jiraParams: JiraReportParams,
                        catsParams: CatsReportParams)

case class JiraReportParams(baseUrl: String,
                            username: String,
                            password: String,
                            jiraQuery: String,
                            author: Option[String])

case class CatsReportParams(baseUrl: Option[String],
                            username: Option[String],
                            password: Option[String])

case class WorklogMatch(date: LocalDate,
                        description: String,
                        durationInJira: Option[Double],
                        durationInCats: Option[Double])

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
    Ok(Json.toJson(ReportParams(
      (ZonedDateTime.now minusWeeks 1).toLocalDate,
      (ZonedDateTime.now plusDays 1).toLocalDate,
      0,
      JiraReportParams(
        "https://jira02.jirahosting.de/jira", null, null,
        // "project = BICM AND labels = 2015 AND labels IN ('#7', '#8') AND summary ~ 'Project Management'",
        "project = BICM",
        None
      ),
      CatsReportParams(
        Option("https://cats.arvato-systems.de/gui4cats-webapi"), None, None
      )
    )))
  }

  def extractConnectionConfig(params: JiraReportParams): jira.ConnectionConfig =
    jira.ConnectionConfig(new URI(params.baseUrl), params.username, params.password)

  def extractConnectionConfig(params: CatsReportParams): cats.ConnectionConfig =
    cats.ConnectionConfig(new URI(params.baseUrl.get), params.username.get, params.password.get)

  def extractWorklogFilter(params: ReportParams): JiraWorklogFilter = {
    val tzOffsetSeconds = (-1) * params.tzOffsetMinutes * 60
    val timeZone = TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(tzOffsetSeconds))
    val jiraParams = params.jiraParams
    new JiraWorklogFilter(jiraParams.author, params.fromDate, params.toDate, timeZone, jiraParams.jiraQuery)
  }

  def constructReportParams(connConfig: jira.ConnectionConfig, filter: JiraWorklogFilter) = {
    val timeZone = filter.timeZone
    val zoneId = timeZone.toZoneId
    val instantAtStartOfDay = filter.fromDate.atStartOfDay.atZone(zoneId).toInstant
    val tzOffsetMillis = timeZone.getOffset(instantAtStartOfDay.toEpochMilli)
    val tzOffsetMinutes = tzOffsetMillis / 60 / 1000 / (-1)
    JiraReportParams(
      connConfig.baseUri.toString, connConfig.username, connConfig.password,
      filter.jiraQuery, filter.author)
  }

  def retrieveWorklogs = Action(BodyParsers.parse.json) { request =>
    val paramsResult = request.body.validate[ReportParams]
    paramsResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> JsString("KO"), "message" -> JsError.toJson(errors)))
      },
      params => {
        Logger.info("Retrieving worklogs for: {" +
          s" jira-user: ${params.jiraParams.username}," +
          s" cats-user: ${params.catsParams.username} }")
        val jiraReporterMR = managed(new JiraWorklogReporter(
          extractConnectionConfig(params.jiraParams),
          extractWorklogFilter(params)
        ))
        val catsPassword = params.catsParams.password
        val resources =
          if (catsPassword.isEmpty || catsPassword.get.isEmpty)
            Seq(jiraReporterMR)
          else
            Seq(jiraReporterMR, managed(new CatsWorklogReporter(
              extractConnectionConfig(params.catsParams),
              extractWorklogFilter(params)
            )))
        join(resources).map(reporters => {
          val jiraEntries = reporters(0).retrieveWorklogs()
          val catsEntries = if (reporters.length > 1) reporters(1).retrieveWorklogs() else Nil
          Ok(Json.obj("status" -> JsString("OK"), "matches" -> pairUp(jiraEntries, catsEntries)))
        }).either match {
          case Left(errors) =>
            BadRequest(Json.obj("status" -> JsString("KO"), "message" -> errors.head.toString))
          case Right(result) => result
        }
      })
  }

  val worklogComparator = new Comparator[WorklogEntry] {
    def compare(w1: WorklogEntry, w2: WorklogEntry) = {
      Ordering[(Long, String)].compare(
        (w1.date.toEpochDay, w1.description),
        (w2.date.toEpochDay, w2.description)
      )
    }
  }

  def pairUp(jiraEntries: Seq[WorklogEntry], catsEntries: Seq[WorklogEntry]): Seq[WorklogMatch] = {
    val filteredCatsEntries = catsEntries.filter(ce => ce.description.startsWith("BICM"))
    var matches = mutable.Seq[WorklogMatch]()
    var i = 0;
    var j = 0;
    while (i < jiraEntries.length && j < filteredCatsEntries.length) {
      val je = jiraEntries(i)
      val ce = filteredCatsEntries(j)
      val comparison = worklogComparator.compare(je, ce)
      if (comparison == 0) {
        matches = matches.:+(WorklogMatch(je.date, je.description, Option(je.duration), Option(ce.duration)))
        i += 1
        j += 1
      } else if (comparison < 0) {
        matches = matches :+ WorklogMatch(je.date, je.description, Option(je.duration), None)
        i += 1
      } else {
        matches = matches :+ WorklogMatch(ce.date, ce.description, None, Option(ce.duration))
        j += 1
      }
    }
    if (i < jiraEntries.length)
      for (n <- i until jiraEntries.length) {
        val je = jiraEntries(n)
        matches = matches :+ WorklogMatch(je.date, je.description, Option(je.duration), None)
      }
    else if (j < filteredCatsEntries.length)
      for (n <- j until filteredCatsEntries.length) {
        val ce = filteredCatsEntries(n)
        matches = matches :+ WorklogMatch(ce.date, ce.description, None, Option(ce.duration))
      }
    return matches
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

  implicit val worklogWrites = Json.writes[WorklogEntry]

  implicit val worklogMatchWrites = Json.writes[WorklogMatch]

  implicit val jiraParamWrites = Json.writes[JiraReportParams]

  implicit val jiraParamsReads = Json.reads[JiraReportParams]

  implicit val catsParamWrites = Json.writes[CatsReportParams]

  implicit val catsParamsReads = Json.reads[CatsReportParams]

  //  implicit val paramWrites: Writes[ReportParams] = (
  //    (JsPath \ "fromDate").write[LocalDate] and
  //    (JsPath \ "toDate").write[LocalDate] and
  //    (JsPath \ "tzOffsetMinutes").write[Int] and
  //    (JsPath \ "jiraParams").write[JiraReportParams] and
  //    (JsPath \ "catsParams").write[CatsReportParams]
  //  ) (unlift(ReportParams.unapply))
  implicit val paramWrites = Json.writes[ReportParams]

  //  implicit val paramsReads: Reads[ReportParams] = (
  //    (JsPath \ "fromDate").read[LocalDate] and
  //    (JsPath \ "toDate").read[LocalDate] and
  //    (JsPath \ "tzOffsetMinutes").read[Int] and
  //    (JsPath \ "jiraParams").read[JiraReportParams] and
  //    (JsPath \ "catsParams").read[CatsReportParams]
  //  ) (ReportParams.apply _)
  implicit val paramsReads = Json.reads[ReportParams]
}
