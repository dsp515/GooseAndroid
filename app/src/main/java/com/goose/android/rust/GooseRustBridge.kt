package com.goose.android.rust

import android.util.Log

/**
 * JNI bridge to the Goose Rust core (libgoose_core.so).
 * Mirrors GooseRustBridge.swift exactly — JSON over C FFI.
 *
 * Bridge request schema:
 *   { "schema": "goose.bridge.request.v1", "request_id": String, "method": String, "args": Object }
 *
 * Bridge response schema:
 *   { "ok": Boolean, "result": Any, "error": { "message": String }?, "timing": { "method": String, "method_elapsed_us": Number }? }
 */
object GooseRustBridge {

    private var nativeAvailable = false
    private var counter = 0L

    init {
        nativeAvailable = try {
            System.loadLibrary("goose_core")
            Log.i(TAG, "goose_core native library loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "goose_core native library not available — using stub mode: ${e.message}")
            false
        }
    }

    /**
     * Calls goose_bridge_handle_json() via JNI and returns the parsed result map.
     * Throws GooseRustBridgeException on failure.
     */
    @Throws(GooseRustBridgeException::class)
    fun request(method: String, args: Map<String, Any> = emptyMap()): Map<String, Any> {
        return requestValue(method, args) as? Map<String, Any> ?: emptyMap()
    }

    @Throws(GooseRustBridgeException::class)
    fun requestValue(method: String, args: Map<String, Any> = emptyMap()): Any {
        val id = "goose-android-${System.currentTimeMillis()}-${++counter}"
        val payload = mapOf(
            "schema" to "goose.bridge.request.v1",
            "request_id" to id,
            "method" to method,
            "args" to args
        )
        val requestJson = toJson(payload)

        if (!nativeAvailable) {
            Log.d(TAG, "Stub bridge call: $method")
            return stubResponse(method)
        }

        val responseJson = nativeBridgeHandleJson(requestJson)
            ?: throw GooseRustBridgeException.NullResponse(method)

        val response = fromJson(responseJson)
            ?: throw GooseRustBridgeException.MalformedResponse(method)

        val ok = response["ok"] as? Boolean
            ?: throw GooseRustBridgeException.MalformedResponse(method)

        if (!ok) {
            val error = response["error"] as? Map<*, *>
            val message = error?.get("message") as? String ?: "Rust bridge method failed"
            throw GooseRustBridgeException.MethodFailed(method, message)
        }

        return response["result"] ?: emptyMap<String, Any>()
    }

    fun isNativeAvailable(): Boolean = nativeAvailable

    // Native JNI declarations — implemented in libgoose_core.so
    private external fun nativeBridgeHandleJson(requestJson: String): String?

    // Minimal JSON serialization helpers (avoids full Gson dependency in bridge layer)
    private fun toJson(map: Map<String, Any>): String = buildString {
        append("{")
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(",")
            append("\"$k\":${valueToJson(v)}")
        }
        append("}")
    }

    private fun valueToJson(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"${v.replace("\"", "\\\"")}\"" 
        is Boolean -> v.toString()
        is Number -> v.toString()
        is Map<*, *> -> toJson(v as Map<String, Any>)
        is List<*> -> "[${v.joinToString(",") { valueToJson(it) }}]"
        else -> "\"${v.toString().replace("\"", "\\\"")}\""
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromJson(json: String): Map<String, Any>? {
        return try {
            // Use Android's built-in org.json for parsing
            val obj = org.json.JSONObject(json)
            jsonObjectToMap(obj)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse bridge response: $e")
            null
        }
    }

    private fun jsonObjectToMap(obj: org.json.JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.get(key).let { value ->
                when (value) {
                    is org.json.JSONObject -> jsonObjectToMap(value)
                    is org.json.JSONArray -> jsonArrayToList(value)
                    else -> value
                }
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: org.json.JSONArray): List<Any> {
        return (0 until arr.length()).map { i ->
            when (val v = arr.get(i)) {
                is org.json.JSONObject -> jsonObjectToMap(v)
                is org.json.JSONArray -> jsonArrayToList(v)
                else -> v
            }
        }
    }

    /**
     * Returns stub data when native library isn't available (development/simulator mode).
     * Mirrors the shape the Rust core would return for each method.
     */
    private fun stubResponse(method: String): Map<String, Any> {
        return when {
            method.startsWith("version") -> mapOf("version" to "0.1.0-stub")
            method.startsWith("health") -> mapOf("status" to "stub", "available" to false)
            method.startsWith("recovery") -> mapOf(
                "score" to 0,
                "hrv_rmssd" to 0.0,
                "resting_hr" to 0,
                "status" to "unavailable"
            )
            method.startsWith("sleep") -> mapOf(
                "score" to 0,
                "total_minutes" to 0,
                "rem_minutes" to 0,
                "deep_minutes" to 0,
                "status" to "unavailable"
            )
            else -> mapOf("status" to "stub")
        }
    }

    private const val TAG = "GooseRustBridge"
}

sealed class GooseRustBridgeException(message: String) : Exception(message) {
    class EncodingFailed(method: String) : GooseRustBridgeException("Encoding failed for method: $method")
    class NullResponse(method: String) : GooseRustBridgeException("Null response for method: $method")
    class MalformedResponse(method: String) : GooseRustBridgeException("Malformed response for method: $method")
    class MethodFailed(method: String, detail: String) : GooseRustBridgeException("Method $method failed: $detail")
}
