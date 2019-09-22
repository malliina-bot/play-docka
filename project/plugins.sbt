scalaVersion := "2.12.8"

resolvers ++= Seq(
  Resolver.bintrayRepo("malliina", "maven"),
  Resolver.url("malliina bintray sbt", url("https://dl.bintray.com/malliina/sbt-plugins/"))(Resolver.ivyStylePatterns)
)
classpathTypes += "maven-plugin"
scalacOptions ++= Seq("-unchecked", "-deprecation")

Seq(
  "com.malliina" %% "sbt-play" % "1.4.1",
//  "com.malliina" % "sbt-aws-docker" % "0.2.2",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "com.typesafe.sbt" % "sbt-less" % "1.1.2"
) map addSbtPlugin
