package me.cloud.driver.route

import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext

 private val logger = LoggerFactory.getLogger(Router::class.java.name)

class Router {

    fun putRecants(ctx: RoutingContext) {
        logger.info("put putRecants ${ctx.bodyAsJson}")
        ctx.response().end("ok")
    }
}