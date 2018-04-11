package me.cloud.driver

import io.vertx.core.http.HttpServer
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.TimeoutHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.experimental.launch
import me.cloud.driver.C.API
import me.cloud.driver.ex.coroutineHandler

private val logger = LoggerFactory.getLogger(MainVertical::class.java.name)
fun RoutingContext.suspendHandler(block: suspend RoutingContext.() -> Unit) = launch(this.vertx().dispatcher()) {
    block.invoke(this@suspendHandler)
}

class MainVertical : CoroutineVerticle() {

    val router by lazy { Router.router(vertx) }

    override suspend fun start() {
        super.start()
        router.route().handler(BodyHandler.create())
        router.route("/*").handler(TimeoutHandler.create())
//        router.route("/*").handler {
//            it.response().putHeader("Access-Control-Allow-Origin", "*")
//            it.response().putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
//            it.response().putHeader("Access-Control-Allow-Headers", "Content-Type")
//            it.response().putHeader("Access-Control-Max-Age", "1800")
//        }
        val r = me.cloud.driver.route.Router()
        router.put(API.Put_Recent).coroutineHandler { r.putRecants(it) }

        val server = awaitResult<HttpServer> { vertx.createHttpServer().requestHandler({ router.accept(it) }).listen(8001, it) }

        logger.info("http server start ok : $server")

    }

    override suspend fun stop() {
        super.stop()
    }


}