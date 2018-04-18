package me.cloud.driver.redis

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.redis.RedisClient
import me.cloud.driver.c.RedisKey
import me.cloud.driver.ex.safeAsync
import me.cloud.driver.ex.safeLaunch

private val logger = LoggerFactory.getLogger(RedisVertical::class.java.simpleName)
const val NOTIFY_EVENT_EVENT = "notify-keyspace-events"
const val NOTIFY_EVENT_CHANNEL = "__keyevent@0__:expired"

class RedisVertical : CoroutineVerticle() {
    private val redisClient by lazy {
        RedisClient.create(vertx)
    }

    override suspend fun start() {
        super.start()
        logger.info("start $redisClient")
        val set = awaitResult<String> { redisClient.configSet(NOTIFY_EVENT_EVENT, "Ex", it) }

        if (set == "OK") {
            logger.info("set $NOTIFY_EVENT_EVENT success")
        }
        var batchCount = 0
        // register a handler for the incoming message the naming the Redis module will use is base address + '.' + redis channel
        vertx.eventBus().consumer<JsonObject>("io.vertx.redis.$NOTIFY_EVENT_CHANNEL") { received ->
            vertx.safeLaunch {
                if (batchCount >= 100) {
                    batchCount = 0
                    logger.debug("exec batch remove")
                    vertx.safeAsync {
                        val rems = awaitResult<JsonArray> {
                            redisClient.zrevrangebyscore(RedisKey.Recommend_Key, "0", "-inf", null, it)
                        }.mapNotNull { it?.toString() }
                        redisClient.zremMany(RedisKey.Recommend_Key, rems, null)
                    }
                }
                logger.debug("headers" + received.headers())
                logger.debug("body" + received.body())
                val body = received.body().getJsonObject("value")
                if (received.body().getString("status") == "ok" && body.getString("channel") == NOTIFY_EVENT_CHANNEL) {
                    val shadowUid = body.getString("message")
                    val realKey = shadowUid.removePrefix(RedisKey.ShadowKey)
                    val json = awaitResult<JsonObject> { redisClient.hgetall(realKey, it) }
                    val score = json.getString("count", "0")?.toIntOrNull() ?: 0
                    val recommemdKey = json.getString("key", "")
                    logger.debug("desc recommend score $score ")
                    redisClient.zincrby(RedisKey.Recommend_Key, (-score).toDouble(), recommemdKey, null)
                    redisClient.del(realKey, null)
                    batchCount++
                }
            }
        }

        val channels = awaitResult<JsonArray> { redisClient.subscribe(NOTIFY_EVENT_CHANNEL, it) }
        logger.debug(channels)
    }

    override suspend fun stop() {
        super.stop()
    }
}