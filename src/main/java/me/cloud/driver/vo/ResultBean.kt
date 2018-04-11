package me.cloud.driver.vo

data class ResultBean<out T>(val code: Int = 200, val message: String = "", val result: T? = null) {

    companion object {

        fun <T> OK(data: T,msg: String? = null) = ResultBean(message = msg ?: "处理成功", result = data)
        fun MSG(msg: String) = ResultBean<Unit>(message = msg)

        fun Error(msg: String) = ResultBean<Unit>(code = 500, message = msg)
        fun Error(e: Throwable) = ResultBean<Unit>(code = 500, message = e.message ?: e.localizedMessage)
    }
}

