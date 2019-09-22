package com.malliina.app.redis

import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig, Transaction}

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.duration.Duration
import scala.util.Try

object JedisRedis {
  val HostKey = "REDIS_HOST"
  val PortKey = "REDIS_PORT"

  val DefaultPort = 6379

  def apply(host: String, port: Int = DefaultPort): JedisRedis = new JedisRedis(host, port)

  def apply(): Either[String, JedisRedis] = {
    sys.env.get(HostKey).filter(_.nonEmpty).toRight(s"Key not found: '$HostKey'.").map { host =>
      val idx = host.lastIndexOf(":")
      val port = sys.env.get(PortKey).map(_.toInt).getOrElse(DefaultPort)
      if (idx == -1) {
        apply(host, port)
      } else {
        Try(host.drop(idx + 1).toInt).toOption
          .map(p => apply(host.take(idx), p))
          .getOrElse(apply(host, port))
      }
    }
  }
}

class JedisRedis(val host: String, val port: Int) {
  private val pool = new JedisPool(new JedisPoolConfig, host, port)

  def get(key: String): Try[Option[String]] = withRedis { c => Option(c.get(key)) }

  def set(key: String, value: String): Try[String] = withRedis { c => c.set(key, value) }

  def setWithTtl(key: String, value: String, ttl: Duration): Unit = withTransaction { t =>
    t.set(key, value)
    t.expire(key, ttl.toSeconds.toInt)
  }

  def withTransaction(code: Transaction => Unit): Try[List[AnyRef]] = withRedis { c =>
    val t = c.multi()
    code(t)
    t.exec().asScala.toList
  }

  def withRedis[T](code: Jedis => T): Try[T] = Try {
    val jedis = pool.getResource
    try {
      code(jedis)
    } finally {
      jedis.close()
    }
  }

  def close(): Unit = pool.close()
}
