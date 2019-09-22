package com.malliina.app.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import play.api.Logger

case class DatabaseConf(user: String, pass: String, url: String)

object DatabaseConf {
  def orFail() = apply().fold(err => throw new Exception(err), identity)

  def apply(): Either[String, DatabaseConf] = for {
    user <- read("DB_USER")
    pass <- read("DB_PASSWORD")
    url <- read("DB_URL")
  } yield DatabaseConf(user, pass, url)

  def read(key: String) = sys.env.get(key).filter(_.nonEmpty).toRight(s"Key not found: '$key'.")
}

object HikariConnection {
  private val log = Logger(getClass)

  def apply(conf: DatabaseConf): HikariDataSource = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName("com.mysql.jdbc.Driver")
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    log info s"Connecting to '${conf.url}'..."
    new HikariDataSource(hikari)
  }
}
