package controllers

import com.malliina.app.build.BuildInfo.{gitHash, name, version}
import play.api.mvc.Call
import scalatags.Text.GenericAttr
import scalatags.Text.all._

object AppTags {
  implicit val callAttr = new GenericAttr[Call]
  val reverse = routes.Home
  val empty = modifier()

  def index(msgs: Seq[String]) = TagPage(
    html(
      head(
        link(rel := "stylesheet", href := reverse.versioned("css/main.css")),
        link(rel := "shortcut icon", href := reverse.versioned("img/favicon-256.png"))
      ),
      body(
        h2("Welcome"),
        p(s"$name $version $gitHash"),
        msgs.map { msg => p(msg) }
      )
    )
  )
}
