package cn.nukkit.level;

import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.Task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Slowly generates a square of chunks around the world spawn. One job at a time. */
public final class ChunkPregenerator extends Task {
    private static final int IN_FLIGHT = 4;
    private static final int TIMEOUT_TICKS = 2400;
    private static ChunkPregenerator active;

    private final Level level;
    private final CommandSender sender;
    private final int minX, minZ, side;
    private final long total;
    private long next, done, failed;
    private int ticks;
    private final List<int[]> pending = new ArrayList<>(); // x, z, start tick

    private ChunkPregenerator(Level level, CommandSender sender, int radiusBlocks) {
        this.level = level;
        this.sender = sender;
        int radius = (radiusBlocks + 15) >> 4;
        Position spawn = level.getSpawnLocation();
        this.minX = (spawn.getFloorX() >> 4) - radius;
        this.minZ = (spawn.getFloorZ() >> 4) - radius;
        this.side = radius * 2 + 1;
        this.total = (long) side * side;
    }

    public static boolean start(Level level, CommandSender sender, int radiusBlocks) {
        if (active != null) return false;
        active = new ChunkPregenerator(level, sender, radiusBlocks);
        sender.getServer().getScheduler().scheduleDelayedRepeatingTask(InternalPlugin.INSTANCE, active, 1, 1);
        sender.sendMessage("Прогрузка " + level.getName() + ": чанков " + active.total);
        return true;
    }

    public static boolean stop() {
        if (active == null) return false;
        active.cancel();
        active = null;
        return true;
    }

    @Override
    public void onRun(int currentTick) {
        if (sender.getServer().getLevelByName(level.getName()) != level) {
            sender.sendMessage("Прогрузка остановлена: мир выгружен");
            stop();
            return;
        }
        ticks++;
        // Back off while the server is struggling.
        boolean healthy = sender.getServer().getTicksPerSecond() >= 17;
        while (healthy && pending.size() < IN_FLIGHT && next < total) {
            pending.add(new int[]{minX + (int) (next % side), minZ + (int) (next / side), ticks});
            next++;
        }
        Iterator<int[]> iterator = pending.iterator();
        int finished = 0;
        while (iterator.hasNext() && finished < 2) {
            int[] chunk = iterator.next();
            boolean ready;
            try {
                ready = level.populateChunk(chunk[0], chunk[1]);
            } catch (Exception e) {
                sender.getServer().getLogger().error("Pregeneration failed at " + chunk[0] + ", " + chunk[1], e);
                failed++;
                iterator.remove();
                continue;
            }
            if (ready) {
                done++;
                finished++;
                iterator.remove();
                if (level.getChunkLoaders(chunk[0], chunk[1]).length == 0) {
                    level.unloadChunkRequest(chunk[0], chunk[1]);
                }
            } else if (ticks - chunk[2] > TIMEOUT_TICKS) {
                failed++;
                iterator.remove();
            }
        }
        if (ticks % 200 == 0) {
            sender.sendMessage("Прогрузка: " + done + "/" + total + ", ошибок " + failed);
        }
        if (next >= total && pending.isEmpty()) {
            sender.sendMessage("Прогрузка завершена: " + done + "/" + total + ", ошибок " + failed);
            stop();
        }
    }
}
