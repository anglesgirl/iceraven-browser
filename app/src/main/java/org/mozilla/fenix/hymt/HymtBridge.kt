package org.mozilla.fenix.hymt

import android.util.Log

/**
 * JNI 声明：libhymt（llama.cpp + 混元 Hy-MT GGUF）。
 * loadLibrary 失败不抛异常，由 isLoaded 标记降级（扩展走在线引擎）。
 */
internal object HymtBridge {
    private const val TAG = "HyMT"

    var isLoaded = false
        private set

    init {
        try {
            System.loadLibrary("hymt")
            isLoaded = true
            Log.i(TAG, "libhymt loaded")
        } catch (e: UnsatisfiedLinkError) {
            isLoaded = false
            Log.w(TAG, "libhymt missing (32-bit or stub), falling back to online engines")
        }
    }

    external fun nativeInit(modelPath: String, nThreads: Int): Boolean
    external fun nativeTranslate(text: String, maxTokens: Int): String?
    external fun nativeFree()
    external fun nativeIsReady(): Boolean
}
