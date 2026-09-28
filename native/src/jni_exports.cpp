// JNI export layer for chuying native engines.
// Bound to Java class com.chuying.engine.NativeEngineBridge.
// GPL-3.0-only.

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

extern "C" JNIEXPORT jint JNICALL
Java_com_chuying_engine_NativeEngineBridge_start(JNIEnv* env, jobject, jobjectArray jargs) {
    return EngineBridge::instance().start(toArgs(env, jargs)) ? 0 : 1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_chuying_engine_NativeEngineBridge_send(JNIEnv* env, jobject, jstring cmd) {
    if (!cmd) return 1;
    const char* utf = env->GetStringUTFChars(cmd, nullptr);
    if (!utf) return 1;
    const bool ok = EngineBridge::instance().send(utf);
    env->ReleaseStringUTFChars(cmd, utf);
    return ok ? 0 : 1;
}

// Returns one output line, or NULL on timeout / engine exit.
extern "C" JNIEXPORT jstring JNICALL
Java_com_chuying_engine_NativeEngineBridge_read(JNIEnv* env, jobject, jint timeoutMs) {
    std::string line;
    if (!EngineBridge::instance().readLine(timeoutMs, line)) return nullptr;
    return env->NewStringUTF(line.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_chuying_engine_NativeEngineBridge_stop(JNIEnv*, jobject) {
    EngineBridge::instance().stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_chuying_engine_NativeEngineBridge_isAlive(JNIEnv*, jobject) {
    return EngineBridge::instance().alive() ? JNI_TRUE : JNI_FALSE;
}
