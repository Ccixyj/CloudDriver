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
import io.vertx.redis.op.SetOptions
import kotlinx.coroutines.experimental.CoroutineStart
import me.cloud.driver.DEPLOYS_RESULTS
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
                DEPLOYS_RESULTS.forEach { vertx.undeploy(it) }
            }
        }
    }


    suspend fun putRecommend(ctx: RoutingContext) {
        val uid = ctx.request().getParam("uid")
        val key = ctx.request().getParam("key")?.trim()
        val reason = ctx.request().getParam("reason")?.trim()
        var score = 0 //需要增减的分数
        var isAdd = false //是否添加
        logger.debug("putRecommend")
        logger.debug("uid :$uid")
        logger.debug("key : $key")
        logger.debug("reason : $reason")

        try {
            if (!uid.isNullOrBlank() && !key.isNullOrBlank()) {
                val countSetKey = RedisKey.Recommend_Count + ":$uid"
                val now = LocalDateTime.now()
                val gap = fun(): Long {
                    val nexMidNight = LocalDateTime.of(now.plusDays(1).toLocalDate(), LocalTime.MIDNIGHT)
                    return Duration.between(now, nexMidNight).toMillis()
                }() //与下一天零点时间差（毫秒）
                logger.debug("gap : $gap")
                val hjson = ctx.safeAsync(CoroutineStart.LAZY) { awaitResult<JsonObject> { redisClient.hgetall(countSetKey, it) } }
                val hp = JsonObject().put("key", key).put("count", "1").put("reason", reason)
                //add ，成功+1分
                fun setNewIn(key: String, jsonObject: JsonObject): Int {
                    redisClient.setWithOptions(RedisKey.ShadowKey + key, "", SetOptions().setEX(DaySeconds - 1), null)
                    redisClient.hmset(countSetKey, jsonObject, null)
                    isAdd = true
                    return 1
                }
                //没有，先设置
                if (hjson.await().isEmpty) {
                    score += setNewIn(countSetKey, hp)
                } else {
                    val count = awaitResult<Long> { redisClient.hincrby(countSetKey, "count", 1, it) }
                    if (count <= 3) {
                        isAdd = true
                        score += 1
                    } else {
                        // 减去插值
                        score = 3 - count.toInt()
                        error("一天点赞最多3次")
                    }
                }

                if (gap <= 1000) { //允许1s误差,防止订阅延迟 1s内马上下一天
                    val count = awaitResult<String?> { redisClient.hget(countSetKey, "count", it) }?.toIntOrNull() ?: -1
                    logger.debug("gap < 1000 :$count")
                    if (count > 0) {
                        //预先删除
                        redisClient.del(RedisKey.ShadowKey + countSetKey, null)
                        redisClient.del(countSetKey, null)
                        score -= count
                    }
                    score += setNewIn(countSetKey, hp)
                }
            } else {
                error("缺少参数")
            }
        } catch (e: Exception) {
            logger.error(e)
            throw  e
        } finally {
            logger.debug("inc score :$score")
            if (score != 0) {
                //改变分数
                redisClient.zincrby(RedisKey.Recommend_Key, score.toDouble(), key, null)
            }
            if (isAdd) {
                ctx.render(ResultBean.MSG("点赞成功！"))
                if (!reason.isNullOrBlank()) {
                    logger.debug("add reason :$reason")
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
                redisClient.zrevrangebyscore(RedisKey.Recommend_Key, "+inf", "1",
                        RangeLimitOptions().apply {
                            setLimit(offset.toLong(), PageCount.toLong())
                            useWithScores()
                        }, it)
            }.chunked(2) { it.component1().toString() to it.component2().toString().toIntOrNull() }


            val ff = array.map { obj ->
                val f = Future.future<JsonObject>()
                redisClient.srandmember("${RedisKey.Recommend_Reason}:${obj.first}") {
                    if (it.succeeded()) {
                        val kvs = Json.decodeValue(obj.first, Map::class.java)
                        f.complete(json {
                            obj(
                                    "key" to kvs,
                                    "score" to obj.second,
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
            ctx.render(ResultBean.OK(mapOf("data" to ff.map { java.util.Base64.getUrlEncoder().encodeToString(it.result().toBuffer().bytes) }, "pages" to pages.await())))
        } catch (e: Exception) {
            logger.error(e)
            ctx.fail(e)
        }
    }
}