package me.cloud.driver.ex

import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import me.cloud.driver.vo.ResultBean


fun <T> RoutingContext.safeLaunch(fn: suspend () -> T): Job {
    return launch(this.vertx().dispatcher()) {
        fn.invoke()
    }
}

fun <T> Vertx.safeLaunch(fn: suspend () -> T): Job {
    return launch(this.dispatcher()) {
        fn.invoke()
    }
}

fun <T> RoutingContext.safeAsync(fn: suspend () -> T) = async(this.vertx().dispatcher()) { fn.invoke() }

fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) {
    handler { ctx ->
        launch(ctx.vertx().dispatcher()) {
            try {
                fn(ctx)
            } catch (e: Exception) {
                ctx.response().end(Json.encode(ResultBean.Error(e.message ?: e.stackTrace.joinToString())))
            }
        }
    }
}