// JNI bridge for cn.nukkit.rise.NativeCore
#include <jni.h>

#include <vector>

#include "rise_core.h"

extern "C" {

JNIEXPORT jint JNICALL Java_cn_nukkit_rise_NativeCore_abiVersion(JNIEnv*, jclass) {
    return RISE_ABI_VERSION;
}

JNIEXPORT jintArray JNICALL Java_cn_nukkit_rise_NativeCore_explosionRays(
        JNIEnv* env, jclass, jfloatArray resist,
        jint ox, jint oy, jint oz, jint dx, jint dy, jint dz,
        jdouble cx, jdouble cy, jdouble cz,
        jdouble size, jdouble fireChance, jint minY, jint maxY, jlong seed) {
    std::vector<int32_t> out;
    out.reserve(512);

    jsize len = env->GetArrayLength(resist);
    if (static_cast<jlong>(len) < static_cast<jlong>(dx) * dy * dz) return nullptr;

    jfloat* data = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(resist, nullptr));
    if (data == nullptr) return nullptr;
    rise::explosion_rays(data, ox, oy, oz, dx, dy, dz, cx, cy, cz, size, fireChance,
                         minY, maxY, static_cast<uint64_t>(seed), out);
    env->ReleasePrimitiveArrayCritical(resist, data, JNI_ABORT);

    jintArray result = env->NewIntArray(static_cast<jsize>(out.size()));
    if (result == nullptr) return nullptr;
    if (!out.empty()) env->SetIntArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}

// bbs: 6 doubles per entity (minX,minY,minZ,maxX,maxY,maxZ). Result: one float per entity,
// -1 where the Java fallback must be used.
JNIEXPORT jfloatArray JNICALL Java_cn_nukkit_rise_NativeCore_blockDensityBatch(
        JNIEnv* env, jclass, jbyteArray solid,
        jint ox, jint oy, jint oz, jint dx, jint dy, jint dz,
        jdouble sx, jdouble sy, jdouble sz, jdoubleArray bbs, jint count) {
    if (env->GetArrayLength(solid) < static_cast<jlong>(dx) * dy * dz) return nullptr;
    if (env->GetArrayLength(bbs) < count * 6) return nullptr;

    std::vector<jfloat> res(static_cast<size_t>(count), -1.0f);

    jbyte* sd = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(solid, nullptr));
    if (sd == nullptr) return nullptr;
    jdouble* bb = static_cast<jdouble*>(env->GetPrimitiveArrayCritical(bbs, nullptr));
    if (bb == nullptr) {
        env->ReleasePrimitiveArrayCritical(solid, sd, JNI_ABORT);
        return nullptr;
    }
    for (jint i = 0; i < count; ++i) {
        const jdouble* b = bb + static_cast<size_t>(i) * 6;
        res[i] = rise::block_density(reinterpret_cast<const uint8_t*>(sd), ox, oy, oz, dx, dy, dz,
                                     sx, sy, sz, b[0], b[1], b[2], b[3], b[4], b[5]);
    }
    env->ReleasePrimitiveArrayCritical(bbs, bb, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(solid, sd, JNI_ABORT);

    jfloatArray result = env->NewFloatArray(count);
    if (result == nullptr) return nullptr;
    env->SetFloatArrayRegion(result, 0, count, res.data());
    return result;
}

}  // extern "C"
