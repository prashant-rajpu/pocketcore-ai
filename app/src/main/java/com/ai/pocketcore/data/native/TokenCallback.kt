package com.ai.pocketcore.data.native

/**
 * Functional interface invoked by the native C++ inference engine
 * whenever a new token is generated.
 */
fun interface TokenCallback {
    fun onToken(token: String)
}
