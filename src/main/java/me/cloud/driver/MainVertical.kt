package me.cloud.driver

import io.vertx.core.http.HttpServer
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.TimeoutHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.awaitResult
import me.cloud.driver.c.API
import me.cloud.driver.ex.coroutineHandler
import java.time.LocalDateTime

private val logger = LoggerFactory.getLogger(MainVertical::class.java.name)

class MainVertical : CoroutineVerticle() {

    private val router by lazy { Router.router(vertx) }

    override suspend fun start() {
        super.start()
        router.route().handler(BodyHandler.create())
        router.route("/*").handler(TimeoutHandler.create())
        router.route("/*").handler {
            logger.info("Match route ----------------- " + LocalDateTime.now() + " ------------------------------")
            logger.info("Method       : " + it.request().method() + "  Path         : ${it.request().uri()}")
            logger.info("User-Agent   : " + it.request().getHeader("User-Agent"))
            val params = it.request().params()
            logger.info("Params       : " + Json.encode(params?.entries() ?: "[]"))
            logger.info("Cookie       : " + Json.encode(it.request().getHeader("Cookie") ?: ""))
            logger.info("--------------------------------------------------------------------------------")
            it.next()
        }
        val r = me.cloud.driver.route.Router(vertx)
        router.put(API.Put_Recommend).coroutineHandler { r.putRecommend(it) }
        router.get(API.Get_Recommends).coroutineHandler { r.recommends(it) }

        val server = awaitResult<HttpServer> { vertx.createHttpServer().requestHandler({ router.accept(it) }).listen(8001, it) }

        logger.info("http server start ok : $server")

    }

    override suspend fun stop() {
        super.stop()
        logger.warn("MainVertical stop")
    }


}