package me.cloud.driver

import com.fasterxml.jackson.annotation.JsonInclude
import io.vertx.core.Launcher
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.experimental.runBlocking


private val logger by lazy { LoggerFactory.getLogger(Launcher::class.java) }
val VERTX: Vertx by lazy { Vertx.vertx() }
val DEPLOYS: MutableList<String> = arrayListOf()

fun main(args: Array<String>) {
    //Force to use slf4j
    DEPLOYS.clear()
    System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.SLF4JLogDelegateFactory")
    runBlocking {
        Json.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        val res = awaitResult<String> { VERTX.deployVerticle(MainVertical(), it) }
        logger.info("deploy result :$res")
        DEPLOYS.add(res)
    }
}


