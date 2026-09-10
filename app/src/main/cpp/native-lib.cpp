#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_mithril_ui_activities_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Mithril Firmware Scanner - Native";
    return env->NewStringUTF(hello.c_str());
}
