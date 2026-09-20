package org.mozilla.fenix.hymt

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本机翻译桥：OpenAI 兼容协议，供沉浸式翻译等扩展调用。
 * - GET  /v1/models                -> {data:[{id:"hymt"}]}
 * - POST /v1/chat/completions      -> OpenAI 格式请求，返回 choices[0].message.content
 * - GET  /status                   -> {ready, modelExists}
 *
 * 沉浸式翻译配置：
 *   自定义 API 端点: http://127.0.0.1:18911/v1/chat/completions
 *   API Key: 任意（本地不校验）
 *   模型: hymt
 */
object HymtLocalServer {
    private const val TAG = "HyMT"
    const val PORT = 18911

    private var server: Server? = null

    fun start(context: Context) {
        if (server != null) return
        try {
            val s = Server(context)
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = s
            Log.i(TAG, "OpenAI-compatible server on http://127.0.0.1:$PORT/v1/chat/completions")
        } catch (e: Exception) {
            Log.e(TAG, "failed to start local server", e)
        }
    }

    fun stop() {
        server?.stop()
        server = null
    }

    private class Server(val ctx: Context) : NanoHTTPD("127.0.0.1", PORT) {
        override fun serve(session: IHTTPSession): Response {
            if (session.method == Method.OPTIONS) {
                return cors(Response.Status.OK, "")
            }
            return try {
                when (session.uri) {
                    "/status" -> status()
                    "/v1/models" -> models()
                    "/v1/chat/completions" -> chatCompletions(session)
                    else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", JSONObject().put("message", "not found")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "serve error", e)
                json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", JSONObject().put("message", e.message ?: "internal error")))
            }
        }

        private fun status(): Response {
            val obj = JSONObject()
                .put("ready", HymtManager.isReady(ctx))
                .put("modelExists", HymtManager.modelExists(ctx))
            return json(Response.Status.OK, obj)
        }

        private fun models(): Response {
            val data = JSONArray()
                .put(JSONObject().put("id", "hymt").put("object", "model").put("owned_by", "hunyuan"))
            return json(Response.Status.OK, JSONObject().put("object", "list").put("data", data))
        }

        private fun chatCompletions(session: IHTTPSession): Response {
            if (session.method != Method.POST) {
                return json(Response.Status.METHOD_NOT_ALLOWED,
                    JSONObject().put("error", JSONObject().put("message", "POST only")))
            }
            if (!HymtManager.isReady(ctx)) {
                return json(Response.Status.SERVICE_UNAVAILABLE,
                    JSONObject().put("error", JSONObject().put("message", "model not loaded; download model first")))
            }
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"] ?: ""
            val req = JSONObject(body)
            val messages = req.optJSONArray("messages") ?: JSONArray()
            // 拼接 user 消息作为待翻译文本（沉浸式翻译会把 system/user 拼好）
            val sb = StringBuilder()
            for (i in 0 until messages.length()) {
                val m = messages.optJSONObject(i) ?: continue
                val role = m.optString("role", "user")
                val content = m.optString("content", "")
                if (role == "user") sb.append(content).append("\n")
            }
            val text = sb.toString().trim()
            if (text.isBlank()) {
                return json(Response.Status.BAD_REQUEST,
                    JSONObject().put("error", JSONObject().put("message", "no user content")))
            }
            val out = HymtManager.translate(text, 512)
            if (out.isNullOrEmpty()) {
                return json(Response.Status.INTERNAL_ERROR,
                    JSONObject().put("error", JSONObject().put("message", "empty translation")))
            }
            val choice = JSONObject()
                .put("index", 0)
                .put("message", JSONObject().put("role", "assistant").put("content", out))
                .put("finish_reason", "stop")
            return json(Response.Status.OK, JSONObject()
                .put("id", "chatcmpl-local")
                .put("object", "chat.completion")
                .put("model", "hymt")
                .put("choices", JSONArray().put(choice)))
        }

        private fun json(status: Response.Status, obj: JSONObject): Response {
            return cors(status, obj.toString())
        }

        private fun cors(status: Response.Status, body: String): Response {
            val r = newFixedLengthResponse(status, "application/json; charset=utf-8", body)
            r.addHeader("Access-Control-Allow-Origin", "*")
            r.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
            r.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
            return r
        }
    }
}
