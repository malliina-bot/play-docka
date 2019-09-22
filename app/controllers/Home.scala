package controllers

import com.malliina.app.build.BuildInfo
import com.malliina.app.db.{DatabaseConf, HikariConnection}
import com.malliina.app.redis.JedisRedis
import controllers.Assets.Asset
import controllers.Home._
import play.api.libs.json.Json
import play.api.mvc._

object Home {
  val Welcome = "Welcome"
  val App = "app"
  val Version = "version"
  val GitHash = "gitHash"
  val NoCache = "no-cache"
}

class Home(assets: AssetsBuilder, comps: ControllerComponents) extends AbstractController(comps) {
  val db = DatabaseConf().map { conf => HikariConnection(conf) }
  val redis = JedisRedis().toOption

  def index = Action {
    val dbMessage = db.map(data => s"Connected to ${data.getJdbcUrl}.").toOption
    val redisMessage = redis.flatMap { c =>
      c.get("test").map(_ => s"Connected to Redis at '${c.host}'.").toOption
    }
    Ok(AppTags.index(dbMessage.toSeq ++ redisMessage.toSeq))
      .withHeaders(CACHE_CONTROL -> s"max-age=10")
  }

  def health = Action {
    Ok(Json.obj(App -> BuildInfo.name, Version -> BuildInfo.version, GitHash -> BuildInfo.gitHash))
      .withHeaders(CACHE_CONTROL -> NoCache)
  }

  def versioned(path: String, file: Asset) = assets.versioned(path, file)
}
