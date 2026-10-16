#include <jni.h>
#include <string>
#include <algorithm>
#include "whisper.h"

// JNI for com.simmer.app.listen.WhisperLib. One context per transcription; Kotlin frees it right after.

extern "C" JNIEXPORT jlong JNICALL
Java_com_simmer_app_listen_WhisperLib_init(JNIEnv *env, jobject, jstring path) {
    const char *model = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(model, params);
    env->ReleaseStringUTFChars(path, model);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_simmer_app_listen_WhisperLib_free(JNIEnv *, jobject, jlong handle) {
    whisper_free(reinterpret_cast<whisper_context *>(handle));
}

// Returns the transcript as UTF-8 bytes (decoded leniently in Kotlin: a segment may cut a multi-byte character),
// or null when whisper failed. The language is detected automatically (German or English in practice).
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_simmer_app_listen_WhisperLib_transcribe(JNIEnv *env, jobject, jlong handle, jfloatArray pcm, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    jsize n = env->GetArrayLength(pcm);
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "auto";
    params.detect_language = false;
    params.no_timestamps = true;
    params.n_threads = std::max(1, (int) threads);

    int rc = whisper_full(ctx, params, samples, n);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (rc != 0) return nullptr;

    std::string text;
    int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        text += whisper_full_get_segment_text(ctx, i);
    }
    jbyteArray out = env->NewByteArray((jsize) text.size());
    env->SetByteArrayRegion(out, 0, (jsize) text.size(), reinterpret_cast<const jbyte *>(text.data()));
    return out;
}
