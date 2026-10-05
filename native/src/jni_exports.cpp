// JNI export layer for chuying native engines.
// Bound to Java classes com.chuying.engine.{CChessNativeBridge,
// WChessNativeBridge, GomokuNativeBridge} — one class per engine.
// GPL-3.0-only.
//
// Why one class per engine: the three shared libraries used to export the same
// JNI symbols (Java_com_chuying_engine_NativeEngineBridge_*), and the JVM binds
// a native method name to the FIRST loaded library that exports it. With all
// three engines resident in one game JVM, the 2nd/3rd engine's start() landed on
// the 1st engine's EngineBridge singleton (already running) and failed at once.
// Distinct class names -> distinct symbols -> distinct singletons, no crosstalk.
//
// Each library compiles this file with exactly one CHUYING_ENGINE_* definition
// (see native/CMakeLists.txt) and therefore exports only its own five symbols.

#include <jni.h>

#include <string>
#include <vector>

#include "uci_bridge.h"

using chuying::EngineBridge;

static std::vector<std::string> toArgs(JNIEnv* env, jobjectArray jargs) {
    std::vector<std::string> args;
    if (jargs) {
        const jsize n = env->GetArrayLength(jargs);
        args.reserve(static_cast<size_t>(n));
        for (jsize i = 0; i < n; ++i) {
            auto* s = static_cast<jstring>(env->GetObjectArrayElement(jargs, i));
            if (!s) continue;
            const char* utf = env->GetStringUTFChars(s, nullptr);
            if (utf) {
                args.emplace_back(utf);
                env->ReleaseStringUTFChars(s, utf);
            }
            env->DeleteLocalRef(s);
        }
    }
    return args;
}

#if defined(CHUYING_ENGINE_CCHESS)

extern "C" JNIEXPORT jint JNICALL
Java_com_chuying_engine_CChessNativeBridge_start(JNIEnv* env, jobject, jobjectArray jargs) {
    return EngineBridge::instance().start(toArgs(env, jargs)) ? 0 : 1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_chuying_engine_CChessNativeBridge_send(JNIEnv* env, jobject, jstring cmd) {
    if (!cmd) return 1;
    const char* utf = env->GetStringUTFChars(cmd, nullptr);
    if (!utf) return 1;
    const bool ok = EngineBridge::instance().send(utf);
    env->ReleaseStringUTFChars(cmd, utf);
    return ok ? 0 : 1;
}

// Returns one output line, or NULL on timeout / engine exit.
extern "C" JNIEXPORT jstring JNICALL
Java_com_chuying_engine_CChessNativeBridge_read(JNIEnv* env, jobject, jint timeoutMs) {
    std::string line;
    if (!EngineBridge::instance().readLine(timeoutMs, line)) return nullptr;
    return env->NewStringUTF(line.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_chuying_engine_CChessNativeBridge_stop(JNIEnv*, jobject) {
    EngineBridge::instance().stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_chuying_engine_CChessNativeBridge_isAlive(JNIEnv*, jobject) {
    return EngineBridge::instance().alive() ? JNI_TRUE : JNI_FALSE;
}

#elif defined(CHUYING_ENGINE_WCHESS)

extern "C" JNIEXPORT jint JNICALL
Java_com_chuying_engine_WChessNativeBridge_start(JNIEnv* env, jobject, jobjectArray jargs) {
    return EngineBridge::instance().start(toArgs(env, jargs)) ? 0 : 1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_chuying_engine_WChessNativeBridge_send(JNIEnv* env, jobject, jstring cmd) {
    if (!cmd) return 1;
    const char* utf = env->GetStringUTFChars(cmd, nullptr);
    if (!utf) return 1;
    const bool ok = EngineBridge::instance().send(utf);
    env->ReleaseStringUTFChars(cmd, utf);
    return ok ? 0 : 1;
}

// Returns one output line, or NULL on timeout / engine exit.
extern "C" JNIEXPORT jstring JNICALL
Java_com_chuying_engine_WChessNativeBridge_read(JNIEnv* env, jobject, jint timeoutMs) {
    std::string line;
    if (!EngineBridge::instance().readLine(timeoutMs, line)) return nullptr;
    return env->NewStringUTF(line.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_chuying_engine_WChessNativeBridge_stop(JNIEnv*, jobject) {
    EngineBridge::instance().stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_chuying_engine_WChessNativeBridge_isAlive(JNIEnv*, jobject) {
    return EngineBridge::instance().alive() ? JNI_TRUE : JNI_FALSE;
}

#elif defined(CHUYING_ENGINE_GOMOKU)

extern "C" JNIEXPORT jint JNICALL
Java_com_chuying_engine_GomokuNativeBridge_start(JNIEnv* env, jobject, jobjectArray jargs) {
    return EngineBridge::instance().start(toArgs(env, jargs)) ? 0 : 1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_chuying_engine_GomokuNativeBridge_send(JNIEnv* env, jobject, jstring cmd) {
    if (!cmd) return 1;
    const char* utf = env->GetStringUTFChars(cmd, nullptr);
    if (!utf) return 1;
    const bool ok = EngineBridge::instance().send(utf);
    env->ReleaseStringUTFChars(cmd, utf);
    return ok ? 0 : 1;
}

// Returns one output line, or NULL on timeout / engine exit.
extern "C" JNIEXPORT jstring JNICALL
Java_com_chuying_engine_GomokuNativeBridge_read(JNIEnv* env, jobject, jint timeoutMs) {
    std::string line;
    if (!EngineBridge::instance().readLine(timeoutMs, line)) return nullptr;
    return env->NewStringUTF(line.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_chuying_engine_GomokuNativeBridge_stop(JNIEnv*, jobject) {
    EngineBridge::instance().stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_chuying_engine_GomokuNativeBridge_isAlive(JNIEnv*, jobject) {
    return EngineBridge::instance().alive() ? JNI_TRUE : JNI_FALSE;
}

#else
#error "CHUYING_ENGINE_CCHESS / CHUYING_ENGINE_WCHESS / CHUYING_ENGINE_GOMOKU must be defined (see native/CMakeLists.txt)"
#endif
