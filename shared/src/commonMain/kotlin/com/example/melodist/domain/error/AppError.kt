package com.example.melodist.domain.error

sealed class AppError : Throwable() {
    data class Network(val code: Int? = null, override val message: String? = null) : AppError()
    data class Database(override val message: String? = null) : AppError()
    data class Auth(override val message: String? = null) : AppError()
    data class Parsing(override val message: String? = null) : AppError()
    data class Unknown(override val message: String? = null, val throwable: Throwable? = null) : AppError()

    override fun toString(): String = when (this) {
        is Network -> "Network Error${code?.let { " ($it)" } ?: ""}: ${message ?: "Unknown"}"
        is Database -> "Database Error: ${message ?: "Unknown"}"
        is Auth -> "Authentication Error: ${message ?: "Unknown"}"
        is Parsing -> "Parsing Error: ${message ?: "Unknown"}"
        is Unknown -> "Unknown Error: ${message ?: throwable?.message ?: "Unknown"}"
    }
}

fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    // Here we could add more specific mappings if we had more libraries (like Ktor, etc.)
    else -> AppError.Unknown(throwable = this)
}
