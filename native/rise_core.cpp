#include "rise_core.h"

#include <cmath>

namespace rise {

namespace {

constexpr int RAYS = 16;
constexpr double STEP_LEN = 0.3;

struct SplitMix64 {
    uint64_t s;
    explicit SplitMix64(uint64_t seed) : s(seed) {}
    uint64_t next() {
        uint64_t z = (s += 0x9E3779B97F4A7C15ULL);
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
        return z ^ (z >> 31);
    }
    // [0,1)
    double next_double() { return (next() >> 11) * (1.0 / 9007199254740992.0); }
};

inline long long jround(double v) { return static_cast<long long>(std::floor(v + 0.5)); }

}  // namespace

void explosion_rays(const float* resist,
                    int ox, int oy, int oz, int dx, int dy, int dz,
                    double cx, double cy, double cz,
                    double size, double fire_chance,
                    int min_y, int max_y, uint64_t seed,
                    std::vector<int32_t>& out) {
    const bool deterministic = (seed == 0);
    SplitMix64 rng(seed);
    const size_t cells = static_cast<size_t>(dx) * dy * dz;
    std::vector<uint8_t> seen(cells, 0);
    const bool incendiary = fire_chance > 0 && !deterministic;
    const int m_rays = RAYS - 1;

    for (int i = 0; i < RAYS; ++i) {
        for (int j = 0; j < RAYS; ++j) {
            for (int k = 0; k < RAYS; ++k) {
                if (!(i == 0 || i == m_rays || j == 0 || j == m_rays || k == 0 || k == m_rays)) continue;

                double vx = static_cast<double>(i) / m_rays * 2.0 - 1.0;
                double vy = static_cast<double>(j) / m_rays * 2.0 - 1.0;
                double vz = static_cast<double>(k) / m_rays * 2.0 - 1.0;
                double len = std::sqrt(vx * vx + vy * vy + vz * vz);
                vx = (vx / len) * STEP_LEN;
                vy = (vy / len) * STEP_LEN;
                vz = (vz / len) * STEP_LEN;

                double px = cx, py = cy, pz = cz;
                double factor = deterministic ? 1.0 : (700 + static_cast<int>(rng.next() % 601)) / 1000.0;

                for (double blast = size * factor; blast > 0; blast -= STEP_LEN * 0.75) {
                    int bx = static_cast<int>(std::floor(px));
                    int by = static_cast<int>(std::floor(py));
                    int bz = static_cast<int>(std::floor(pz));
                    if (by < min_y || by > max_y) break;

                    int gx = bx - ox, gy = by - oy, gz = bz - oz;
                    if (gx < 0 || gy < 0 || gz < 0 || gx >= dx || gy >= dy || gz >= dz) break;

                    size_t idx = (static_cast<size_t>(gy) * dz + gz) * dx + gx;
                    float res = resist[idx];
                    if (res >= 0.0f) {
                        blast -= (static_cast<double>(res) / 5 + 0.3) * STEP_LEN;
                        if (blast > 0 && !seen[idx]) {
                            seen[idx] = 1;
                            int32_t e = static_cast<int32_t>(idx);
                            if (incendiary && rng.next_double() <= fire_chance) {
                                e = static_cast<int32_t>(static_cast<uint32_t>(e) | 0x80000000u);
                            }
                            out.push_back(e);
                        }
                    }
                    px += vx;
                    py += vy;
                    pz += vz;
                }
            }
        }
    }
}

float block_density(const uint8_t* solid,
                    int ox, int oy, int oz, int dx, int dy, int dz,
                    double sx, double sy, double sz,
                    double min_x, double min_y, double min_z,
                    double max_x, double max_y, double max_z) {
    const double x_interval = 1 / ((max_x - min_x) * 2 + 1);
    const double y_interval = 1 / ((max_y - min_y) * 2 + 1);
    const double z_interval = 1 / ((max_z - min_z) * 2 + 1);
    const double x_offset = (1 - std::floor(1 / x_interval) * x_interval) / 2;
    const double z_offset = (1 - std::floor(1 / z_interval) * z_interval) / 2;

    if (!(x_interval >= 0 && y_interval >= 0 && z_interval >= 0)) return 0.0f;

    int visible = 0, total = 0;
    for (float x = 0; x <= 1; x = static_cast<float>(static_cast<double>(x) + x_interval)) {
        for (float y = 0; y <= 1; y = static_cast<float>(static_cast<double>(y) + y_interval)) {
            for (float z = 0; z <= 1; z = static_cast<float>(static_cast<double>(z) + z_interval)) {
                double tx = min_x + (max_x - min_x) * static_cast<double>(x) + x_offset;
                double ty = min_y + (max_y - min_y) * static_cast<double>(y);
                double tz = min_z + (max_z - min_z) * static_cast<double>(z) + z_offset;

                double dirx = tx - sx, diry = ty - sy, dirz = tz - sz;
                double length = std::sqrt(dirx * dirx + diry * diry + dirz * dirz);
                double nx = dirx / length, ny = diry / length, nz = dirz / length;

                bool hit = false;
                for (double t = 0.0; t < length; t += 0.3) {
                    int bx = static_cast<int>(jround(sx + nx * t));
                    int by = static_cast<int>(jround(sy + ny * t));
                    int bz = static_cast<int>(jround(sz + nz * t));
                    int gx = bx - ox, gy = by - oy, gz = bz - oz;
                    if (gx < 0 || gy < 0 || gz < 0 || gx >= dx || gy >= dy || gz >= dz) return -1.0f;
                    if (solid[(static_cast<size_t>(gy) * dz + gz) * dx + gx]) { hit = true; break; }
                }
                if (hit) visible++;
                total++;
            }
        }
    }
    return static_cast<float>(visible) / static_cast<float>(total);
}

}  // namespace rise
