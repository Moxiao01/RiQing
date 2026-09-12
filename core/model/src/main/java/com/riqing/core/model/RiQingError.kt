package com.riqing.core.model

/**
 * 工具/仓库层错误，与 D4 错误码对齐。
 */
sealed class RiQingError(
    open val code: String,
    override val message: String,
    open val field: String? = null,
) : Exception(message) {
    data class Validation(
        override val message: String,
        override val field: String? = null,
    ) : RiQingError("VALIDATION_ERROR", message, field)

    data class NotFound(
        override val message: String = "找不到该条目",
    ) : RiQingError("NOT_FOUND", message)

    data class Conflict(
        override val message: String,
    ) : RiQingError("CONFLICT", message)

    data class Unauthorized(
        override val message: String = "未配置 API",
    ) : RiQingError("UNAUTHORIZED", message)

    data class Timeout(
        override val message: String = "请求超时",
    ) : RiQingError("TIMEOUT", message)

    data class Cancelled(
        override val message: String = "已取消",
    ) : RiQingError("CANCELLED", message)

    data class Internal(
        override val message: String = "内部错误",
    ) : RiQingError("INTERNAL", message)
}
