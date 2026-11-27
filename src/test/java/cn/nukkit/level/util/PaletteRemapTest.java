package cn.nukkit.level.util;

import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PaletteRemapTest {

    private static byte[] oldWay(int[] blocks) {
        PalettedBlockStorage storage = PalettedBlockStorage.createWithDefaultState(0);
        for (int i = 0; i < 4096; i++) storage.setBlock(i, blocks[i] * 31 + 7);
        BinaryStream stream = new BinaryStream();
        storage.writeTo(stream);
        return stream.getBuffer();
    }

    private static byte[] newWay(int[] blocks, int kinds) {
        PalettedBlockStorage storage = PalettedBlockStorage.createWithDefaultState(0);
        int[] remap = new int[kinds];
        for (int i = 0; i < 4096; i++) {
            int target = remap[blocks[i]] - 1;
            if (target < 0) {
                target = storage.paletteIndex(blocks[i] * 31 + 7);
                remap[blocks[i]] = target + 1;
            }
            storage.setPaletteIndex(i, target);
        }
        BinaryStream stream = new BinaryStream();
        storage.writeTo(stream);
        return stream.getBuffer();
    }

    @Test
    void sameBytesAndTiming() {
        Random random = new Random(1);
        for (int kinds : new int[]{1, 2, 4, 5, 16, 17, 64, 200, 700}) {
            int[] blocks = new int[4096];
            for (int i = 0; i < blocks.length; i++) blocks[i] = random.nextInt(kinds);
            assertArrayEquals(oldWay(blocks), newWay(blocks, kinds), "kinds=" + kinds);
            long oldTime = 0, newTime = 0;
            for (int round = 0; round < 600; round++) {
                long start = System.nanoTime();
                oldWay(blocks);
                long middle = System.nanoTime();
                newWay(blocks, kinds);
                if (round >= 300) { // first half is JIT warm-up
                    oldTime += middle - start;
                    newTime += System.nanoTime() - middle;
                }
            }
            System.out.println("BENCH kinds=" + kinds + " old_us=" + oldTime / 300_000 + " new_us=" + newTime / 300_000);
        }
    }
}
