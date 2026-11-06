/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 *
 * Bridge from com.qualcomm.qti.Performance to Qualcomm's perf client
 * (libqti-perfd-client_system.so, loaded at run time so the build does not depend on it).
 */

#include <dlfcn.h>
#include <jni.h>
#include <string.h>

namespace {

typedef int (*lock_acq_t)(int, int, int*, int);
typedef int (*lock_rel_t)(int);
typedef int (*hint_t)(int, const char*, int, int);
typedef int (*feedback_t)(int, const char*);
typedef void (*event_t)(int, const char*, int, int*);
typedef int (*hint_acq_rel_t)(int, int, const char*, int, int, int, int*);

lock_acq_t g_lock_acq;
lock_rel_t g_lock_rel;
hint_t g_hint;
feedback_t g_feedback;
event_t g_event;
hint_acq_rel_t g_hint_acq_rel;

struct Utf {
    JNIEnv* env;
    jstring s;
    const char* c;
    Utf(JNIEnv* e, jstring str) : env(e), s(str), c(str ? e->GetStringUTFChars(str, nullptr) : nullptr) {}
    ~Utf() { if (c) env->ReleaseStringUTFChars(s, c); }
};

jboolean init(JNIEnv*, jclass) {
    void* h = dlopen("libqti-perfd-client_system.so", RTLD_NOW | RTLD_LOCAL);
    if (!h) return JNI_FALSE;
    g_lock_acq = reinterpret_cast<lock_acq_t>(dlsym(h, "perf_lock_acq"));
    g_lock_rel = reinterpret_cast<lock_rel_t>(dlsym(h, "perf_lock_rel"));
    g_hint = reinterpret_cast<hint_t>(dlsym(h, "perf_hint"));
    g_feedback = reinterpret_cast<feedback_t>(dlsym(h, "perf_get_feedback"));
    g_event = reinterpret_cast<event_t>(dlsym(h, "perf_event"));
    g_hint_acq_rel = reinterpret_cast<hint_acq_rel_t>(dlsym(h, "perf_hint_acq_rel"));
    return (g_lock_acq && g_lock_rel && g_hint) ? JNI_TRUE : JNI_FALSE;
}

jint lockAcq(JNIEnv* env, jclass, jint handle, jint duration, jintArray list) {
    if (!g_lock_acq || !list) return -1;
    jsize n = env->GetArrayLength(list);
    jint* p = env->GetIntArrayElements(list, nullptr);
    int r = g_lock_acq(handle, duration, reinterpret_cast<int*>(p), n);
    env->ReleaseIntArrayElements(list, p, JNI_ABORT);
    return r;
}

jint lockRel(JNIEnv*, jclass, jint handle) {
    return g_lock_rel ? g_lock_rel(handle) : -1;
}

jint hint(JNIEnv* env, jclass, jint hint, jstring pkg, jint duration, jint type) {
    if (!g_hint) return -1;
    Utf p(env, pkg);
    return g_hint(hint, p.c, duration, type);
}

jint feedback(JNIEnv* env, jclass, jint req, jstring pkg) {
    if (!g_feedback) return -1;
    Utf p(env, pkg);
    return g_feedback(req, p.c);
}

void event(JNIEnv* env, jclass, jint id, jstring pkg, jintArray list) {
    if (!g_event) return;
    Utf p(env, pkg);
    jsize n = list ? env->GetArrayLength(list) : 0;
    jint* a = list ? env->GetIntArrayElements(list, nullptr) : nullptr;
    g_event(id, p.c, n, reinterpret_cast<int*>(a));
    if (a) env->ReleaseIntArrayElements(list, a, JNI_ABORT);
}

jint hintAcqRel(JNIEnv* env, jclass, jint handle, jint hint, jstring pkg, jint duration, jint type,
                jintArray list) {
    if (!g_hint_acq_rel) return -1;
    Utf p(env, pkg);
    jsize n = list ? env->GetArrayLength(list) : 0;
    jint* a = list ? env->GetIntArrayElements(list, nullptr) : nullptr;
    int r = g_hint_acq_rel(handle, hint, p.c, duration, type, n, reinterpret_cast<int*>(a));
    if (a) env->ReleaseIntArrayElements(list, a, JNI_ABORT);
    return r;
}

#define M(n, sig, fn) {const_cast<char*>(n), const_cast<char*>(sig), reinterpret_cast<void*>(fn)}

JNINativeMethod kMethods[] = {
    M("native_init", "()Z", init),
    M("native_lock_acq", "(II[I)I", lockAcq),
    M("native_lock_rel", "(I)I", lockRel),
    M("native_hint", "(ILjava/lang/String;II)I", hint),
    M("native_get_feedback", "(ILjava/lang/String;)I", feedback),
    M("native_event", "(ILjava/lang/String;[I)V", event),
    M("native_hint_acq_rel", "(IILjava/lang/String;II[I)I", hintAcqRel),
};

}  // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass c = env->FindClass("com/qualcomm/qti/Performance");
    if (!c) return JNI_ERR;
    if (env->RegisterNatives(c, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) != 0) return JNI_ERR;
    return JNI_VERSION_1_6;
}
