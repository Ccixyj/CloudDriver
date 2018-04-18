package me.cloud.driver

import com.fasterxml.jackson.annotation.JsonInclude
import io.vertx.core.Launcher
import io.vertx.core.Verticle
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.experimental.runBlocking
import me.cloud.driver.redis.RedisVertical


private val logger by lazy { LoggerFactory.getLogger(Launcher::class.java) }
val VERTX: Vertx by lazy { Vertx.vertx() }
val DEPLOYS_RESULTS: MutableList<String> = arrayListOf()
val DEPLOY_VERTICALS: MutableList<Verticle> = arrayListOf(MainVertical(), RedisVertical())


fun main(args: Array<String>) {
    //Force to use slf4j
    DEPLOYS_RESULTS.clear()
    System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.SLF4JLogDelegateFactory")
    runBlocking {
        Json.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        DEPLOY_VERTICALS.mapTo(DEPLOYS_RESULTS) { v ->
            awaitResult { VERTX.deployVerticle(v, it) }
        }

        logger.info("deploy result :$DEPLOYS_RESULTS")
    }
}


