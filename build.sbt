import com.github.play2war.plugin._

name := """planty-worklogs-web"""

organization := "planty.be"

version := "1.0-SNAPSHOT"

Play2WarPlugin.play2WarSettings

Play2WarKeys.servletVersion := "3.1"

lazy val worklogsWeb = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2 % Test,
  "planty.be" %% "planty-worklogs-adapter-jira" % "1.1-SNAPSHOT",
  "planty.be" %% "planty-worklogs-adapter-cats" % "1.0-SNAPSHOT",
  "mysql" % "mysql-connector-java" % "5.1.18",
  "org.webjars" %% "webjars-play" % "2.4.0",
  "org.webjars" % "bootstrap" % "3.3.5",
  "org.webjars" % "bootswatch-united" % "3.3.4+1",
  "org.webjars" % "html5shiv" % "3.7.0",
  "org.webjars" % "respond" % "1.4.2",
  "com.kenshoo" %% "metrics-play" % "2.4.0_0.4.1",
  //"com.mohiva" %% "play-silhouette" % "3.0.0",
  "com.mohiva" %% "play-silhouette" % "3.0.5",
  filters
)

resolvers ++= Seq(
  "Atlassian Releases" at "https://maven.atlassian.com/public/",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  Resolver.jcenterRepo
)

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

fork in run := true
