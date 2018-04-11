package me.cloud.driver.route

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders
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
import me.cloud.driver.c.RedisKey
import me.cloud.driver.ex.safeAsync
import me.cloud.driver.vo.ResultBean

private val logger = LoggerFactory.getLogger(Router::class.java.name)
private const val DaySeconds = 60 * 60 * 24L
private const val PageCount = 15

class Router(vertx: Vertx) {

    private val redisClient = RedisClient.create(vertx)
    fun RoutingContext.render(obj: Any) {
        this.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8")
        this.response().end(Json.encode(obj))
    }

    suspend fun putRecommend(ctx: RoutingContext) {
        logger.info("putRecommend ${ctx.bodyAsString}")
        val uid = ctx.request().getParam("uid")
        val key = ctx.request().getParam("key")?.trim()
        val reason = ctx.request().getParam("reason")?.trim()
        var isAdd = false
        val score = ctx.safeAsync { awaitResult<String?> { redisClient.zscore(RedisKey.Recommend_Key, key, it) } }
        try {
            if (!uid.isNullOrBlank() && !key.isNullOrBlank()) {
                val setKey = RedisKey.Recommend_Count + ":$uid"
                val c = awaitResult<String?> { redisClient.get(setKey, it) }
                if (c == null) {
                    redisClient.set(setKey, "1", null)
                    redisClient.expire(setKey, DaySeconds, null)
                    isAdd = true
                    ctx.render(ResultBean.MSG("点赞成功！"))
                } else {
                    val count = awaitResult<Long> { redisClient.incr(setKey, it) }
                    if (count <= 3) {
                        isAdd = true
                        ctx.render(ResultBean.MSG("点赞成功！"))
                    } else {
                        ctx.render(ResultBean.Error("一天点赞最多3次"))
                    }
                }
            } else {
                ctx.render(ResultBean.Error("缺少参数"))
            }
        } finally {
            if (isAdd) {
                redisClient.zadd(RedisKey.Recommend_Key, score.await()?.toDouble()?.inc() ?: 1.0, key, null)
                if (!reason.isNullOrBlank()) {
                    val setkey = RedisKey.Recommend_Reason + ":$key"
                    redisClient.sadd(setkey, reason, null)
                    //save week
                    redisClient.expire(setkey, DaySeconds * 7, null)
                }

            }
        }

    }


    suspend fun recommends(ctx: RoutingContext) {
        var tryCount = ctx.request().getParam("try")?.toIntOrNull() ?: 1

        logger.info("try: $tryCount")
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

        logger.info(array)
        val ff = array.map { obj ->
            val f = Future.future<JsonObject>()
            redisClient.srandmember("${RedisKey.Recommend_Reason}:$obj") {
                if (it.succeeded()) {
                    f.complete(json {
                        obj(
                                "key" to obj,
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
    }
}