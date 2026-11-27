#include <jni.h>
#include <cstdint>
#include <vector>

// index = (y << 8) | (z << 4) | x. Bucket flood fill from light level 15 down to 2.
static void flood(uint8_t* light, const uint8_t* filter, int size) {
    std::vector<int> buckets[16];
    for (int i = 0; i < size; ++i)
        if (light[i] > 1 && light[i] < 16) buckets[light[i]].push_back(i);
    const int height = size >> 8;
    for (int level = 15; level > 1; --level) {
        for (int i : buckets[level]) {
            if (light[i] != level) continue;
            const int x = i & 15, z = (i >> 4) & 15, y = i >> 8;
            auto spread = [&](int n) {
                const int cost = filter[n] > 1 ? filter[n] : 1;
                const int next = level - cost;
                if (next > light[n]) {
                    light[n] = static_cast<uint8_t>(next);
                    if (next > 1) buckets[next].push_back(n);
                }
            };
            if (x > 0) spread(i - 1);
            if (x < 15) spread(i + 1);
            if (z > 0) spread(i - 16);
            if (z < 15) spread(i + 16);
            if (y > 0) spread(i - 256);
            if (y < height - 1) spread(i + 256);
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_cn_nukkit_level_light_NativeLight_flood0(JNIEnv* env, jclass, jbyteArray light, jbyteArray filter) {
    const jsize size = env->GetArrayLength(light);
    if (size % 256 != 0 || env->GetArrayLength(filter) != size) return;
    std::vector<jbyte> l(size), f(size);
    env->GetByteArrayRegion(light, 0, size, l.data());
    env->GetByteArrayRegion(filter, 0, size, f.data());
    flood(reinterpret_cast<uint8_t*>(l.data()), reinterpret_cast<const uint8_t*>(f.data()), size);
    env->SetByteArrayRegion(light, 0, size, l.data());
}
