package me.cloud.driver.ex

import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import me.cloud.driver.vo.ResultBean

//vertx
fun <T> Vertx.safeLaunch(fn: suspend () -> T): Job {
    return launch(this.dispatcher()) {
        fn.invoke()
    }
}

private val logger = LoggerFactory.getLogger("CoroutineHandler")
//Route
fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) {
    handler { ctx ->
        launch(ctx.vertx().dispatcher()) {
            try {
                fn(ctx)
            } catch (e: Exception) {
                logger.warn("error : ${e.message}")
                ctx.render(ResultBean.Error(e.message ?: e.stackTrace.joinToString()))
            }
        }
    }
}

//RoutingContext
fun <T> RoutingContext.safeLaunch(fn: suspend () -> T): Job {
    return launch(this.vertx().dispatcher()) {
        fn.invoke()
    }
}

fun <T> RoutingContext.safeAsync(start: CoroutineStart = CoroutineStart.DEFAULT, fn: suspend () -> T) = async(this.vertx().dispatcher()) { fn.invoke() }
fun <T> Vertx.safeAsync(start: CoroutineStart = CoroutineStart.DEFAULT, fn: suspend () -> T) = async(this.dispatcher()) { fn.invoke() }

fun RoutingContext.render(obj: Any) {
    this.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8")
    this.response().end(Json.encode(obj))
}