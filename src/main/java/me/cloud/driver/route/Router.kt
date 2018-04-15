package me.cloud.driver.route

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.redis.RedisClient
import io.vertx.redis.op.RangeLimitOptions
import kotlinx.coroutines.experimental.CoroutineStart
import me.cloud.driver.DEPLOYS
import me.cloud.driver.c.RedisKey
import me.cloud.driver.ex.render
import me.cloud.driver.ex.safeAsync
import me.cloud.driver.vo.ResultBean
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

private val logger = LoggerFactory.getLogger(Router::class.java.name)
private const val DaySeconds = 60 * 60 * 24L
private const val PageCount = 15

class Router(vertx: Vertx) {

    private val redisClient = RedisClient.create(vertx).apply {
        this.configGet("*") {
            if (it.succeeded()) {
                logger.info("redis start success ${it.result().take(3)}")
            } else {
                logger.error("redis start error ${it.cause()}")
                DEPLOYS.forEach { vertx.undeploy(it) }
            }
        }
    }


    suspend fun putRecommend(ctx: RoutingContext) {
        val uid = ctx.request().getParam("uid")
        val key = ctx.request().getParam("key")?.trim()
        val reason = ctx.request().getParam("reason")?.trim()
        var isAdd = false
        val score = ctx.safeAsync { awaitResult<String?> { redisClient.zscore(RedisKey.Recommend_Key, key, it) } }
        logger.debug("putRecommend")
        logger.debug("uid :$uid")
        logger.debug("key : $key")
        logger.debug("reason : $reason")
        try {
            if (!uid.isNullOrBlank() && !key.isNullOrBlank()) {
                val setKey = RedisKey.Recommend_Count + ":$uid"
                val now = LocalDateTime.now()
                val gap = fun(): Long {

                    val nexMidNight = LocalDateTime.of(now.plusDays(1).toLocalDate(), LocalTime.MIDNIGHT)
                    return Duration.between(now, nexMidNight).toMillis()
                }() //与下一天零点时间差（毫秒）
                logger.debug("gap : $gap")
                val c = ctx.safeAsync(CoroutineStart.LAZY) { awaitResult<String?> { redisClient.get(setKey, it) } }
                if (gap <= 1000 || c.await() == null) { //允许1s误差
                    redisClient.set(setKey, "1", null)

                    redisClient.expire(setKey, DaySeconds - 1, null)
                    isAdd = true
                    ctx.render(ResultBean.MSG("点赞成功！"))
                } else {
                    val count = awaitResult<Long> { redisClient.incr(setKey, it) }
                    if (count <= 3) {
                        isAdd = true
                        ctx.render(ResultBean.MSG("点赞成功！"))
                    } else {
                        error("一天点赞最多3次")
                    }
                }
            } else {
                error("缺少参数")
            }
        } catch (e: Exception) {
            logger.error(e)
            throw  e
        } finally {
            if (isAdd) {
                redisClient.zadd(RedisKey.Recommend_Key, score.await()?.toDouble()?.inc() ?: 1.0, key, null)
                if (!reason.isNullOrBlank()) {
                    val setKey = RedisKey.Recommend_Reason + ":$key"
                    redisClient.sadd(setKey, reason, null)
                    //save week
                    redisClient.expire(setKey, DaySeconds * 7, null)
                }

            }
        }

    }


    suspend fun recommends(ctx: RoutingContext) {

        try {
            var tryCount = ctx.request().getParam("try")?.toIntOrNull() ?: 1
            logger.info("get recommends : $tryCount")
            if (tryCount <= 0) tryCount = 1
            val offset = (tryCount - 1) * PageCount

            val pages = ctx.safeAsync {
                val total = awaitResult<Long> { redisClient.zcard(RedisKey.Recommend_Key, it) }
                (total - 1).div(PageCount) + 1
            }
            val array = awaitResult<JsonArray> {
                redisClient.zrevrangebyscore(RedisKey.Recommend_Key, "+inf", "-inf",
                        RangeLimitOptions().apply {
                            setLimit(offset.toLong(), PageCount.toLong())
                        }, it)
            }

            val ff = array.map { obj ->
                val f = Future.future<JsonObject>()
                redisClient.srandmember("${RedisKey.Recommend_Reason}:$obj") {
                    if (it.succeeded()) {
                        val kvs = Json.decodeValue(obj.toString(), Map::class.java)
                        f.complete(json {
                            obj(
                                    "key" to kvs,
                                    "reason" to it.result()
                            )
                        })
                    } else {
                        f.fail(it.cause())
                    }
                }
                f
            }

            CompositeFuture.join(ff).await()
            ctx.render(ResultBean.OK(mapOf("data" to ff.map { it.result() }, "pages" to pages.await())))
        } catch (e: Exception) {
            logger.error(e)
            ctx.fail(e)
        }
    }
}