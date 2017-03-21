import sbt._
import sbt.Keys._

object BuildBuild {
  val settings = sbtPlugins ++ Seq(
    scalaVersion := "2.10.6",
    resolvers ++= Seq(
      Resolver.url("malliina bintray sbt", url("https://dl.bintray.com/malliina/sbt-plugins/"))(Resolver.ivyStylePatterns)
    ),
    scalacOptions ++= Seq("-unchecked", "-deprecation")
  )

  def sbtPlugins = Seq(
    "com.malliina" %% "sbt-play" % "0.9.3",
    "com.malliina" % "sbt-aws-docker" % "0.0.1"
//    "se.marcuslonnberg" % "sbt-docker" % "1.4.0",
  ) map addSbtPlugin
}
