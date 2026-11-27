// Rise native core: compute-heavy kernels moved out of the JVM.
// Pure C++ (no JNI, no Nukkit types) so it can be unit-tested standalone.
//
// Grid layout everywhere: index = (y * dz + z) * dx + x   (cell = world - origin)
#pragma once
#include <cstdint>
#include <vector>

#define RISE_ABI_VERSION 1

namespace rise {

// Explosion ray casting (port of cn.nukkit.level.Explosion#explodeA).
// resist[i] < 0  -> air (layer 0), otherwise max(resistance layer0, layer1).
// Output entries: cell index, with bit 31 set if the block is ignited.
// seed == 0 -> deterministic mode (factor 1.0, no fire) used by tests.
void explosion_rays(const float* resist,
                    int ox, int oy, int oz, int dx, int dy, int dz,
                    double cx, double cy, double cz,
                    double size, double fire_chance,
                    int min_y, int max_y, uint64_t seed,
                    std::vector<int32_t>& out);

// Entity exposure (port of Level#getBlockDensity + isRayCollidingWithBlocks).
// solid[i] != 0 -> block collision box contains the integer point.
// Returns visibleRays / totalRays, or -1.0f if a ray left the grid
// (caller must fall back to the Java implementation).
float block_density(const uint8_t* solid,
                    int ox, int oy, int oz, int dx, int dy, int dz,
                    double sx, double sy, double sz,
                    double min_x, double min_y, double min_z,
                    double max_x, double max_y, double max_z);

}  // namespace rise
