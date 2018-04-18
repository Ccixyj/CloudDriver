package me.cloud.driver.c

object API {

    const val API_VERSION = "/v1"
    const val API_ENDPOINT = "$API_VERSION/api"
    const val Put_Recommend = "$API_ENDPOINT/recommend"
    const val Get_Recommends = "$API_ENDPOINT/recommends/:try"
}

object RedisKey {
    const val Recommend = "Recommend"
    const val Recommend_Count = "$Recommend:count"
    const val Recommend_Key = "$Recommend:key"
    const val Recommend_Reason = "$Recommend:reason"

    //pub - sub can not get value
    const val ShadowKey = "shadowkey:"
}