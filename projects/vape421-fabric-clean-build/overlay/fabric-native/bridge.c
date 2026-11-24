/* In-process class bytecode bridge. Loaded by the mod through System.load;
 * no attach API, process access, or external injector. */
#include <jni.h>
#include <jvmti.h>

static jvmtiEnv *ti;
static jclass captured_class;
static unsigned char *captured_bytes;
static jint captured_length;
static jvmtiError init_error;

static void JNICALL class_hook(jvmtiEnv *env, JNIEnv *jni, jclass target,
        jobject loader, const char *name, jobject domain, jint length,
        const unsigned char *bytes, jint *new_length, unsigned char **new_bytes) {
    (void)loader; (void)name; (void)domain; (void)new_length; (void)new_bytes;
    if (!target || !captured_class || !bytes || length <= 0 ||
            !(*jni)->IsSameObject(jni, target, captured_class)) return;
    if (captured_bytes) (*env)->Deallocate(env, captured_bytes);
    captured_bytes = 0;
    captured_length = 0;
    if ((*env)->Allocate(env, length, &captured_bytes) != JVMTI_ERROR_NONE) return;
    for (jint i = 0; i < length; ++i) captured_bytes[i] = bytes[i];
    captured_length = length;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    if ((*vm)->GetEnv(vm, (void **)&ti, JVMTI_VERSION_1_2) != JNI_OK || !ti)
        return JNI_ERR;
    jvmtiCapabilities potential = {0}, requested = {0};
    jvmtiEventCallbacks callbacks = {0};
    init_error = (*ti)->GetPotentialCapabilities(ti, &potential);
    if (init_error != JVMTI_ERROR_NONE) return JNI_ERR;
    if (!potential.can_redefine_classes || !potential.can_retransform_classes)
        return JNI_ERR;
    requested.can_redefine_classes = 1;
    requested.can_retransform_classes = 1;
    init_error = (*ti)->AddCapabilities(ti, &requested);
    if (init_error != JVMTI_ERROR_NONE) return JNI_ERR;
    callbacks.ClassFileLoadHook = class_hook;
    init_error = (*ti)->SetEventCallbacks(ti, &callbacks, sizeof(callbacks));
    if (init_error != JVMTI_ERROR_NONE) return JNI_ERR;
    init_error = (*ti)->SetEventNotificationMode(ti, JVMTI_ENABLE,
        JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, 0);
    if (init_error != JVMTI_ERROR_NONE) return JNI_ERR;
    return JNI_VERSION_1_8;
}

JNIEXPORT jbyteArray JNICALL Java_gg_vape_fabric_InProcessBytecode_capture0(
        JNIEnv *jni, jclass bridge, jclass target) {
    (void)bridge;
    if (!ti || !target) return 0;
    captured_class = (*jni)->NewGlobalRef(jni, target);
    if (!captured_class) return 0;
    captured_bytes = 0;
    captured_length = 0;
    jvmtiError error = (*ti)->RetransformClasses(ti, 1, &target);
    jbyteArray result = 0;
    if (error == JVMTI_ERROR_NONE && captured_bytes && captured_length > 0) {
        result = (*jni)->NewByteArray(jni, captured_length);
        if (result) (*jni)->SetByteArrayRegion(jni, result, 0, captured_length,
            (const jbyte *)captured_bytes);
    }
    if (captured_bytes) (*ti)->Deallocate(ti, captured_bytes);
    captured_bytes = 0;
    captured_length = 0;
    (*jni)->DeleteGlobalRef(jni, captured_class);
    captured_class = 0;
    return result;
}

JNIEXPORT jint JNICALL Java_gg_vape_fabric_InProcessBytecode_redefine0(
        JNIEnv *jni, jclass bridge, jclass target, jbyteArray data) {
    (void)bridge;
    if (!ti || !target || !data) return JVMTI_ERROR_INVALID_ENVIRONMENT;
    jbyte *bytes = (*jni)->GetByteArrayElements(jni, data, 0);
    if (!bytes) return JVMTI_ERROR_OUT_OF_MEMORY;
    jvmtiClassDefinition def = {target, (*jni)->GetArrayLength(jni, data),
        (const unsigned char *)bytes};
    jint result = (*ti)->RedefineClasses(ti, 1, &def);
    (*jni)->ReleaseByteArrayElements(jni, data, bytes, JNI_ABORT);
    return result;
}
