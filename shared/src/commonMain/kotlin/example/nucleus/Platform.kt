package example.nucleus

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform