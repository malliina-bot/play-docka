package com.malliina.app.redis

import org.scalatest.FunSuite

class JedisRedisTests extends FunSuite {
  test("get from nonexistent Redis fails") {
    val client = JedisRedis("nonexistent-host")
    assert(client.get("key").isFailure)
  }

  ignore("set redis") {
    val client = JedisRedis("localhost")
    val outcome = client.set("a", "b")
    assert(outcome.get === "OK")
    assert(client.get("a").get.contains("b"))
  }
}
