package cn.nukkit.entity.ai;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.Server;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Basic ground AI: wandering, fleeing, chasing and melee. No pathfinding.
 * Disable with -Dlumi.mobai=false
 */
public class SimpleMobAI {

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("lumi.mobai", "true"));

    // Exact classes only, so plugin subclasses (NPCs, bosses) keep their own behaviour
    private static final Set<Class<?>> HOSTILE = Set.of(
            EntityZombie.class, EntityHusk.class, EntityZombieVillager.class, EntityZombieVillagerV2.class,
            EntitySpider.class, EntityCaveSpider.class, EntityWitherSkeleton.class);
    private static final Set<Class<?>> PASSIVE = Set.of(
            EntityCow.class, EntityPig.class, EntityChicken.class, EntitySheep.class,
            EntityRabbit.class, EntityMooshroom.class);

    private static final double FOLLOW_RANGE_SQ = 16 * 16;
    private static final double ACTIVE_RANGE_SQ = 48 * 48;

    private final EntityCreature mob;
    private final boolean hostile;

    private Player target;
    private boolean active;
    private int nextSearch;
    private int nextWander;
    private int fleeUntil;
    private int knockedUntil;
    private int nextAttack;
    private float lastHealth;
    private double dirX;
    private double dirZ;
    private boolean jump;

    public SimpleMobAI(EntityCreature mob) {
        this.mob = mob;
        this.hostile = HOSTILE.contains(mob.getClass());
        this.lastHealth = mob.getHealth();
    }

    public static boolean supports(EntityCreature mob) {
        return ENABLED && (HOSTILE.contains(mob.getClass()) || PASSIVE.contains(mob.getClass()))
                && !mob.isImmobile() && mob.riding == null && mob.passengers.isEmpty()
                && !mob.namedTag.getBoolean("NoAI");
    }

    public void tick(int now) {
        if (now >= nextSearch) {
            nextSearch = now + 20;
            search();
        }
        if (!active) {
            return;
        }

        boolean hurt = mob.getHealth() < lastHealth;
        lastHealth = mob.getHealth();
        if (hurt) {
            knockedUntil = now + 10;
        }
        if (hurt && !hostile) {
            fleeUntil = now + 100;
            nextWander = 0;
        }

        double speed = 0.1;
        boolean chasing = false;
        if (target != null && (!canTarget(target) || mob.distanceSquared(target) > FOLLOW_RANGE_SQ)) {
            target = null;
        }

        if (hostile && target != null) {
            chasing = true;
            double dx = target.x - mob.x;
            double dz = target.z - mob.z;
            double flat = Math.sqrt(dx * dx + dz * dz);
            face(dx, dz);
            if (flat > 1.2) {
                dirX = dx / flat;
                dirZ = dz / flat;
                speed = 0.18;
            } else {
                dirX = dirZ = 0;
            }
            if (flat <= 1.6 && Math.abs(target.y - mob.y) < 1.5 && now >= nextAttack) {
                nextAttack = now + 20;
                attack(target);
            }
        } else {
            if (now >= nextWander) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                boolean fleeing = now < fleeUntil;
                nextWander = now + (fleeing ? 20 : random.nextInt(40, 120));
                if (fleeing || random.nextInt(3) == 0) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    dirX = Math.cos(angle);
                    dirZ = Math.sin(angle);
                } else {
                    dirX = dirZ = 0;
                }
            }
            if (now < fleeUntil) {
                speed = 0.22;
            }
            if (dirX != 0 || dirZ != 0) {
                face(dirX, dirZ);
            }
        }

        if ((dirX != 0 || dirZ != 0) && mob.onGround && !isSafeAhead(chasing)) {
            dirX = dirZ = 0;
            nextWander = 0;
        }

        // Do not override knockback
        boolean knocked = now < knockedUntil;
        if (!knocked) {
            mob.motionX = dirX * speed;
            mob.motionZ = dirZ * speed;
        }

        if (mob.isInsideOfWater()) {
            mob.motionY = 0.08;
        } else if (jump && mob.onGround) {
            mob.motionY = 0.42;
        } else {
            mob.motionY -= mob.getGravity();
        }
        jump = false;

        double wantedX = mob.motionX;
        double wantedZ = mob.motionZ;
        double oldX = mob.x;
        double oldZ = mob.z;
        mob.move(mob.motionX, mob.motionY, mob.motionZ);

        // Blocked horizontally: try to jump next tick
        if (Math.abs(mob.x - oldX) + 0.001 < Math.abs(wantedX) || Math.abs(mob.z - oldZ) + 0.001 < Math.abs(wantedZ)) {
            jump = true;
        }
        if (mob.onGround && mob.motionY < 0) {
            mob.motionY = 0;
        }
        if (knocked) {
            mob.motionX *= 0.6;
            mob.motionZ *= 0.6;
        }
        mob.updateMovement();
    }

    private void search() {
        active = false;
        Player closest = null;
        double best = FOLLOW_RANGE_SQ;
        for (Player player : mob.level.getPlayers().values()) {
            double distance = mob.distanceSquared(player);
            if (distance <= ACTIVE_RANGE_SQ) {
                active = true;
            }
            if (hostile && distance <= best && canTarget(player)) {
                best = distance;
                closest = player;
            }
        }
        target = closest;
    }

    private boolean canTarget(Player player) {
        return player.spawned && player.isOnline() && player.isAlive() && player.level == mob.level
                && (player.isSurvival() || player.isAdventure())
                && Server.getInstance().getDifficulty().getId() > 0;
    }

    private void face(double dx, double dz) {
        mob.yaw = Math.toDegrees(Math.atan2(-dx, dz));
        mob.headYaw = mob.yaw;
    }

    private boolean isSafeAhead(boolean chasing) {
        int x = NukkitMath.floorDouble(mob.x + dirX * 0.8);
        int y = NukkitMath.floorDouble(mob.y);
        int z = NukkitMath.floorDouble(mob.z + dirZ * 0.8);
        if (!mob.level.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        for (int down = 0; down <= (chasing ? 4 : 3); down++) {
            Block block = mob.level.getBlock(x, y - down, z);
            if (block instanceof cn.nukkit.block.BlockLava || block.getId() == Block.FIRE || block.getId() == Block.CACTUS) {
                return false;
            }
            if (block.isWater() || !block.canPassThrough()) {
                return true;
            }
        }
        return false;
    }

    private void attack(Player player) {
        int difficulty = Server.getInstance().getDifficulty().getId();
        float damage = difficulty == 1 ? 2f : difficulty == 2 ? 3f : 4.5f;

        EntityEventPacket packet = new EntityEventPacket();
        packet.eid = mob.getId();
        packet.event = EntityEventPacket.ARM_SWING;
        Server.broadcastPacket(mob.getViewers().values(), packet);

        player.attack(new EntityDamageByEntityEvent(mob, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
    }
}
