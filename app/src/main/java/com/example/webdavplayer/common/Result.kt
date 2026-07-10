package com.example.webdavplayer.common

/**
 * 统一结果封装（§8 共享约定）。
 * 领域/数据层统一用 [Result] 表达成功/失败，UI 只消费中文文案。
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val throwable: Throwable) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success<*>
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = if (this is Success<T>) data else null

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw throwable
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(throwable: Throwable): Result<Nothing> = Error(throwable)

        inline fun <T> runCatching(block: () -> T): Result<T> = try {
            Success(block())
        } catch (e: Throwable) {
            Error(e)
        }
    }
}
