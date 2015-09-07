package controllers

import com.github.macpersia.planty_jira_view.{Config, ReportGenerator, WorklogEntry}
import org.joda.time.LocalDate
import play.api.Play.current
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

case class ConfigParams(baseUrl: String, username: String, password: String, jiraQuery: String,
                         fromDate: LocalDate, toDate: LocalDate)

class Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def worklogs = Action {
    Ok(views.html.worklogs())
  }

/*
  val configForm = Form(
    mapping(
      "baseUrl" -> nonEmptyText,
      "username" -> nonEmptyText,
      "password" -> nonEmptyText,
      "jiraQuery" -> text,
      "fromDate" -> jodaLocalDate,
      "toDate" -> jodaLocalDate
    )(ConfigParams.apply)(ConfigParams.unapply)
  )

  def worklogs = Action {
    Ok(views.html.worklogs(configForm))
  }
*/

  def initialConfig = Action {
    Ok(Json.toJson(Config()))
  }

  def retrieveWorklogs = Action(BodyParsers.parse.json) { request =>
    val configResult = request.body.validate[Config]
    configResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> JsString("KO"), "message" -> JsError.toJson(errors)))
      },
      config => {
        val entries = new ReportGenerator(config).generateEntries()
        // Ok(Json.toJson(entries)
        Ok(Json.obj("status" -> JsString("OK"), "entries" -> entries))
      })
  }

/*
  def retrieveWorklogs = Action { implicit request =>
    configForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.worklogs(formWithErrors))
      },
      params => {
        /* binding success, you get the actual value. */
        val config = Config(params.baseUrl, params.username, params.password, params.jiraQuery, params.fromDate, params.toDate)
        val entries = new ReportGenerator(config).generateEntries()
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

  implicit val configWrites: Writes[Config] = (
    (JsPath \ "baseUrl").write[String] and
    (JsPath \ "username").write[String] and
    (JsPath \ "password").write[String] and
    (JsPath \ "jiraQuery").write[String] and
    (JsPath \ "fromDate").write[LocalDate] and
    (JsPath \ "toDate").write[LocalDate]
  )(unlift({ (config: Config) => Some(config.baseUrl, config.username, config.password, config.jiraQuery,
                                      config.fromDate, config.toDate) }))

  implicit val configReads: Reads[Config] = (
    (JsPath \ "baseUrl").read[String] and
    (JsPath \ "username").read[String] and
    (JsPath \ "password").read[String] and
    (JsPath \ "jiraQuery").read[String] and
    (JsPath \ "fromDate").read[LocalDate] and
    (JsPath \ "toDate").read[LocalDate]
  )({ (baseUrl, username, password, jiraQuery, fromDate, toDate) =>
          Config(baseUrl = baseUrl, username = username, password = password, jiraQuery = jiraQuery,
                 fromDate = fromDate, toDate = toDate) })
}
