package cn.nukkit.entity.ai;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockLava;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.EntityEventPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lightweight mob AI: wandering, fleeing, chasing, melee and ranged attacks.
 * There is no pathfinding. Disable with -Druppim.mobai=false
 */
public class SimpleMobAI {

    private enum Move { GROUND, HOP, FLY, SWIM, STILL }

    private enum Temper { PASSIVE, NEUTRAL, HOSTILE, GUARD }

    private enum Attack { NONE, MELEE, SHOOT, FANGS, BEAM, EXPLODE }

    private record Profile(Move move, Temper temper, Attack attack, String projectile,
                           float damage, double speed, double range, int cooldown) {
    }

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("ruppim.mobai", "true"));
    private static final double ACTIVE_RANGE_SQ = 48 * 48;

    // Exact classes only, so plugin subclasses (NPCs, bosses) keep their own behaviour
    private static final Map<Class<?>, Profile> PROFILES = new HashMap<>();

    private static void add(Move move, Temper temper, Attack attack, String projectile,
                            float damage, double speed, double range, int cooldown, Class<?>... types) {
        Profile profile = new Profile(move, temper, attack, projectile, damage, speed, range, cooldown);
        for (Class<?> type : types) {
            PROFILES.put(type, profile);
        }
    }

    private static void melee(Temper temper, float damage, double speed, Class<?>... types) {
        add(Move.GROUND, temper, Attack.MELEE, null, damage, speed, 16, 20, types);
    }

    private static void peaceful(Move move, double speed, Class<?>... types) {
        add(move, Temper.PASSIVE, Attack.NONE, null, 0, speed, 0, 20, types);
    }

    static {
        melee(Temper.HOSTILE, 3, 0.17, EntityZombie.class, EntityHusk.class, EntityZombieVillager.class,
                EntityZombieVillagerV2.class, EntityDrowned.class);
        melee(Temper.HOSTILE, 3, 0.22, EntitySpider.class, EntityCaveSpider.class);
        melee(Temper.HOSTILE, 5, 0.2, EntityWitherSkeleton.class, EntityPiglin.class, EntityVindicator.class);
        melee(Temper.HOSTILE, 7, 0.2, EntityPiglinBrute.class, EntityHoglin.class, EntityZoglin.class);
        melee(Temper.HOSTILE, 9, 0.19, EntityRavager.class);
        melee(Temper.HOSTILE, 1, 0.2, EntitySilverfish.class, EntityEndermite.class);
        melee(Temper.HOSTILE, 4, 0.2, EntityCreaking.class);
        add(Move.GROUND, Temper.HOSTILE, Attack.MELEE, null, 16, 0.21, 24, 22, EntityWarden.class);
        melee(Temper.NEUTRAL, 5, 0.22, EntityZombiePigman.class, EntityEnderman.class);
        melee(Temper.NEUTRAL, 3, 0.22, EntityWolf.class, EntityBee.class, EntityPolarBear.class,
                EntityPanda.class, EntityGoat.class);
        add(Move.GROUND, Temper.GUARD, Attack.MELEE, null, 10, 0.15, 16, 30, EntityIronGolem.class);
        add(Move.HOP, Temper.HOSTILE, Attack.MELEE, null, 2, 0.2, 16, 20, EntitySlime.class);
        add(Move.HOP, Temper.HOSTILE, Attack.MELEE, null, 4, 0.2, 16, 20, EntityMagmaCube.class);

        add(Move.GROUND, Temper.HOSTILE, Attack.SHOOT, "Arrow", 3, 0.16, 16, 40,
                EntitySkeleton.class, EntityStray.class, EntityPillager.class);
        add(Move.GROUND, Temper.HOSTILE, Attack.SHOOT, "ThrownPotion", 0, 0.16, 12, 60, EntityWitch.class);
        add(Move.GROUND, Temper.GUARD, Attack.SHOOT, "Snowball", 0, 0.14, 12, 25, EntitySnowGolem.class);
        add(Move.GROUND, Temper.HOSTILE, Attack.FANGS, null, 6, 0.15, 14, 80, EntityEvoker.class);
        add(Move.GROUND, Temper.HOSTILE, Attack.EXPLODE, null, 3, 0.17, 16, 30, EntityCreeper.class);
        add(Move.STILL, Temper.HOSTILE, Attack.SHOOT, "ShulkerBullet", 0, 0, 16, 60, EntityShulker.class);

        add(Move.FLY, Temper.HOSTILE, Attack.SHOOT, "SmallFireBall", 0, 0.14, 20, 50, EntityBlaze.class);
        add(Move.FLY, Temper.HOSTILE, Attack.SHOOT, "GhastFireBall", 0, 0.1, 40, 70, EntityGhast.class);
        add(Move.FLY, Temper.HOSTILE, Attack.SHOOT, "WitherSkull", 0, 0.18, 32, 40, EntityWither.class);
        add(Move.FLY, Temper.HOSTILE, Attack.MELEE, null, 5, 0.28, 32, 30, EntityPhantom.class);
        add(Move.FLY, Temper.HOSTILE, Attack.MELEE, null, 5, 0.22, 20, 20, EntityVex.class);
        peaceful(Move.FLY, 0.1, EntityBat.class, EntityParrot.class, EntityAllay.class);

        add(Move.SWIM, Temper.HOSTILE, Attack.BEAM, null, 5, 0.14, 15, 60, EntityGuardian.class);
        add(Move.SWIM, Temper.HOSTILE, Attack.BEAM, null, 8, 0.1, 15, 60, EntityElderGuardian.class);
        peaceful(Move.SWIM, 0.12, EntityCod.class, EntitySalmon.class, EntitySquid.class, EntityGlowSquid.class,
                EntityTropicalFish.class, EntityPufferfish.class, EntityDolphin.class, EntityTadpole.class);

        peaceful(Move.GROUND, 0.1, EntityCow.class, EntityPig.class, EntityChicken.class, EntitySheep.class,
                EntityMooshroom.class, EntityCamel.class, EntityCat.class, EntityDonkey.class, EntityFox.class,
                EntityHorse.class, EntityLlama.class, EntityMule.class, EntityOcelot.class, EntitySkeletonHorse.class,
                EntityStrider.class, EntityTurtle.class, EntityVillager.class, EntityVillagerV2.class,
                EntityWanderingTrader.class, EntityZombieHorse.class, EntityAxolotl.class);
        peaceful(Move.HOP, 0.14, EntityRabbit.class, EntityFrog.class);
    }

    private final EntityCreature mob;
    private final Profile profile;

    private Entity target;
    private Entity aggressor;
    private int aggressorUntil;
    private boolean active;
    private int nextSearch;
    private int nextWander;
    private int fleeUntil;
    private int knockedUntil;
    private int nextAttack;
    private int fuse;
    private float lastHealth;
    private double dirX;
    private double dirY;
    private double dirZ;
    private boolean jump;

    public SimpleMobAI(EntityCreature mob) {
        this.mob = mob;
        this.profile = PROFILES.get(mob.getClass());
        this.lastHealth = mob.getHealth();
    }

    public static boolean supports(EntityCreature mob) {
        return ENABLED && PROFILES.containsKey(mob.getClass()) && !mob.isImmobile() && mob.riding == null
                && mob.getPassengers().isEmpty() && !mob.namedTag.getBoolean("NoAI");
    }

    public void tick(int now) {
        checkHurt(now);
        if (now >= nextSearch) {
            nextSearch = now + 10;
            search(now);
        }
        if (!active) {
            return;
        }
        if (target != null && (!isValidTarget(target) || mob.distanceSquared(target) > square(profile.range() + 4))) {
            target = null;
        }

        boolean swimming = profile.move() == Move.SWIM;
        boolean flying = profile.move() == Move.FLY;
        double speed = profile.speed();

        if (target != null) {
            speed = chase(now);
        } else {
            fuse = 0;
            wander(now);
            if (now < fleeUntil) {
                speed *= 2;
            }
        }
        if (mob.closed) {
            return; // creeper exploded
        }

        if (profile.move() == Move.GROUND && mob.onGround && (dirX != 0 || dirZ != 0) && !isSafeAhead(target != null)) {
            dirX = dirZ = 0;
            nextWander = 0;
        }

        boolean knocked = now < knockedUntil;
        boolean inWater = mob.isInsideOfWater();

        if (profile.move() == Move.STILL) {
            mob.updateMovement();
            return;
        }

        if (!knocked) {
            mob.motionX = dirX * speed;
            mob.motionZ = dirZ * speed;
        }

        if (flying) {
            if (!knocked) {
                mob.motionY = dirY * speed;
            }
            mob.resetFallDistance();
        } else if (swimming && inWater) {
            if (!knocked) {
                mob.motionY = dirY * speed;
            }
            mob.resetFallDistance();
        } else {
            if (swimming) {
                // Out of water: only fall
                mob.motionX = 0;
                mob.motionZ = 0;
            }
            if (inWater) {
                mob.motionY = 0.08;
            } else if (mob.onGround && (jump || profile.move() == Move.HOP && now % 20 == 0 && (dirX != 0 || dirZ != 0))) {
                mob.motionY = 0.42;
            } else {
                mob.motionY -= 0.08;
            }
            if (profile.move() == Move.HOP && mob.onGround && mob.motionY <= 0 && !knocked) {
                mob.motionX = 0;
                mob.motionZ = 0;
            }
        }
        jump = false;

        double wantedX = mob.motionX;
        double wantedZ = mob.motionZ;
        double oldX = mob.x;
        double oldY = mob.y;
        double oldZ = mob.z;
        mob.move(mob.motionX, mob.motionY, mob.motionZ);

        // Fish must not leave the water by themselves
        if (swimming && inWater && !mob.isInsideOfWater() && !knocked) {
            mob.move(oldX - mob.x, oldY - mob.y, oldZ - mob.z);
            dirY = -Math.abs(dirY);
            nextWander = 0;
        }

        if (Math.abs(mob.x - oldX) + 0.001 < Math.abs(wantedX) || Math.abs(mob.z - oldZ) + 0.001 < Math.abs(wantedZ)) {
            jump = true;
            if (flying) {
                dirY = 0.8;
            }
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

    private void checkHurt(int now) {
        if (mob.getHealth() < lastHealth) {
            knockedUntil = now + 10;
            nextWander = 0;
            if (mob.getLastDamageCause() instanceof EntityDamageByEntityEvent event) {
                if (profile.temper() == Temper.PASSIVE) {
                    fleeUntil = now + 100;
                } else if (isValidTarget(event.getDamager())) {
                    aggressor = event.getDamager();
                    aggressorUntil = now + 400;
                    target = aggressor;
                    nextSearch = now + 10;
                }
            }
        }
        lastHealth = mob.getHealth();
    }

    private void search(int now) {
        active = false;
        Entity closest = null;
        double best = square(profile.range());
        for (Player player : mob.level.getPlayers().values()) {
            double distance = mob.distanceSquared(player);
            if (distance <= ACTIVE_RANGE_SQ) {
                active = true;
            }
            if (profile.temper() == Temper.HOSTILE && distance <= best && isValidTarget(player) && canSee(player)) {
                best = distance;
                closest = player;
            }
        }
        if (active && profile.temper() == Temper.GUARD) {
            for (Entity entity : mob.level.getNearbyEntities(mob.getBoundingBox().grow(profile.range(), 4, profile.range()), mob)) {
                Profile other = PROFILES.get(entity.getClass());
                if (other != null && other.temper() == Temper.HOSTILE && other.attack() != Attack.EXPLODE
                        && mob.distanceSquared(entity) <= best && isValidTarget(entity) && canSee(entity)) {
                    best = mob.distanceSquared(entity);
                    closest = entity;
                }
            }
        }
        if (aggressor != null && now < aggressorUntil && isValidTarget(aggressor)) {
            closest = aggressor;
        } else {
            aggressor = null;
        }
        target = closest;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mob || entity.closed || !entity.isAlive() || entity.level != mob.level) {
            return false;
        }
        if (entity instanceof Player player) {
            return player.spawned && player.isOnline() && (player.isSurvival() || player.isAdventure())
                    && Server.getInstance().getDifficulty().getId() > 0;
        }
        return true;
    }

    private double chase(int now) {
        double dx = target.x - mob.x;
        double dz = target.z - mob.z;
        double dy = target.y + target.getHeight() / 2 - (mob.y + mob.getHeight() / 2);
        double flat = Math.max(Math.sqrt(dx * dx + dz * dz), 0.001);
        face(dx, dz);

        boolean ranged = profile.attack() == Attack.SHOOT || profile.attack() == Attack.FANGS
                || profile.attack() == Attack.BEAM;
        double reach = 1.1 + (mob.getWidth() + target.getWidth()) / 2;
        double stop = ranged ? profile.range() * 0.6 : reach * 0.8;

        if (flat > stop) {
            dirX = dx / flat;
            dirZ = dz / flat;
        } else {
            dirX = dirZ = 0;
        }
        // Ranged fliers keep some height over the target
        double heightGoal = ranged && profile.move() == Move.FLY ? dy + 4 : dy;
        dirY = Math.max(-1, Math.min(1, heightGoal / 3));

        if (profile.attack() == Attack.EXPLODE) {
            if (flat <= 3 && Math.abs(dy) < 3 && canSee(target)) {
                dirX = dirZ = 0;
                if (fuse == 0) {
                    mob.setDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_IGNITED, true);
                }
                if (++fuse >= profile.cooldown()) {
                    explode();
                }
            } else if (fuse > 0) {
                fuse = 0;
                mob.setDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_IGNITED, false);
            }
            return profile.speed();
        }

        if (now >= nextAttack) {
            if (!ranged && flat <= reach && Math.abs(dy) < 2) {
                nextAttack = now + profile.cooldown();
                swing();
                hurt(target, profile.damage());
            } else if (ranged && mob.distanceSquared(target) <= square(profile.range()) && canSee(target)) {
                nextAttack = now + profile.cooldown();
                swing();
                if (profile.attack() == Attack.SHOOT) {
                    shoot(dx, dy, dz);
                } else {
                    if (profile.attack() == Attack.FANGS) {
                        Entity fangs = Entity.createEntity("EvocationFangs", target.getChunk(),
                                Entity.getDefaultNBT(target.getPosition()));
                        if (fangs != null) {
                            fangs.spawnToAll();
                        }
                    }
                    hurt(target, profile.damage());
                }
            }
        }
        return profile.speed();
    }

    private void wander(int now) {
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
            dirY = random.nextDouble(-0.4, 0.4);
        }
        // Fliers stay near the ground while idle
        if (profile.move() == Move.FLY && now % 20 == 0) {
            int x = mob.getFloorX();
            int y = mob.getFloorY();
            int z = mob.getFloorZ();
            if (mob.level.getBlock(x, y - 6, z).canPassThrough()) {
                dirY = -0.4;
            }
        }
        if (dirX != 0 || dirZ != 0) {
            face(dirX, dirZ);
        }
    }

    private void shoot(double dx, double dy, double dz) {
        Vector3 aim = new Vector3(dx, dy, dz);
        double distance = aim.length();
        if (distance < 0.001) {
            return;
        }
        double power = "Arrow".equals(profile.projectile()) ? 1.6 : 1.0;
        // Small lift to compensate for gravity
        double lift = "Arrow".equals(profile.projectile()) || "Snowball".equals(profile.projectile())
                || "ThrownPotion".equals(profile.projectile()) ? distance * 0.015 : 0;
        Vector3 motion = aim.normalize().multiply(power).add(0, lift, 0);

        Vector3 start = new Vector3(mob.x + dx / distance * (mob.getWidth() / 2 + 0.4),
                mob.y + mob.getEyeHeight(), mob.z + dz / distance * (mob.getWidth() / 2 + 0.4));
        CompoundTag nbt = Entity.getDefaultNBT(start, motion, (float) motion.yRotFromDirection(), (float) motion.xRotFromDirection());
        if ("ThrownPotion".equals(profile.projectile())) {
            nbt.putShort("PotionId", 23); // harming
        }

        Entity projectile = Entity.createEntity(profile.projectile(), mob.chunk, nbt, mob);
        if (projectile instanceof cn.nukkit.entity.projectile.EntityArrow arrow) {
            // No free arrow farm
            arrow.setPickupMode(cn.nukkit.entity.projectile.EntityArrow.PICKUP_NONE);
        }
        if (projectile instanceof EntityProjectile entityProjectile) {
            if (!new ProjectileLaunchEvent(entityProjectile).call()) {
                projectile.close();
                return;
            }
        }
        if (projectile != null) {
            projectile.spawnToAll();
        }
    }

    private void explode() {
        EntityExplosionPrimeEvent event = new EntityExplosionPrimeEvent(mob, profile.damage());
        if (!event.call()) {
            fuse = 0;
            mob.setDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_IGNITED, false);
            return;
        }
        Explosion explosion = new Explosion(mob, event.getForce(), mob);
        if (event.isBlockBreaking() && mob.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
            explosion.explodeA();
        }
        explosion.explodeB();
        mob.close();
    }

    private void swing() {
        EntityEventPacket packet = new EntityEventPacket();
        packet.eid = mob.getId();
        packet.event = EntityEventPacket.ARM_SWING;
        Server.broadcastPacket(mob.getViewers().values(), packet);
    }

    private void hurt(Entity victim, float damage) {
        int difficulty = Server.getInstance().getDifficulty().getId();
        if (victim instanceof Player) {
            damage *= difficulty == 1 ? 0.7f : difficulty == 3 ? 1.5f : 1f;
        }
        victim.attack(new EntityDamageByEntityEvent(mob, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
    }

    private boolean canSee(Entity entity) {
        double fromY = mob.y + mob.getEyeHeight();
        double dx = entity.x - mob.x;
        double dy = entity.y + entity.getEyeHeight() - fromY;
        double dz = entity.z - mob.z;
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = NukkitMath.floorDouble(mob.x + dx * t);
            int y = NukkitMath.floorDouble(fromY + dy * t);
            int z = NukkitMath.floorDouble(mob.z + dz * t);
            if (!mob.level.isChunkLoaded(x >> 4, z >> 4) || mob.level.getBlock(x, y, z).isSolid()) {
                return false;
            }
        }
        return true;
    }

    private void face(double dx, double dz) {
        mob.yaw = Math.toDegrees(Math.atan2(-dx, dz));
        mob.headYaw = mob.yaw;
    }

    private static double square(double value) {
        return value * value;
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
            if (block instanceof BlockLava || block.getId() == Block.FIRE || block.getId() == Block.CACTUS) {
                return false;
            }
            if (block.isWater() || !block.canPassThrough()) {
                return true;
            }
        }
        return false;
    }
}
