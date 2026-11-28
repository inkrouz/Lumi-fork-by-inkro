package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class EntityCreature extends EntityLiving {

    public EntityCreature(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    private cn.nukkit.entity.ai.SimpleMobAI simpleAI;

    @Override
    public boolean onUpdate(int currentTick) {
        int tickDiff = currentTick - this.lastUpdate;
        boolean hasUpdate = super.onUpdate(currentTick);
        if (tickDiff <= 0 || this.closed || !this.isAlive() || !cn.nukkit.entity.ai.SimpleMobAI.supports(this)) {
            return hasUpdate;
        }
        if (this.simpleAI == null) {
            this.simpleAI = new cn.nukkit.entity.ai.SimpleMobAI(this);
        }
        this.simpleAI.tick(currentTick);
        return true;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.NAME_TAG && !player.isAdventure()) {
            return this.applyNameTag(player, item);
        }
        return false;
    }

    /**
     * Called when player interacts the entity with a name tag item
     * @param player player
     * @param nameTag name tag item
     * @return true to reduce name tag item count
     */
    protected boolean applyNameTag(Player player, Item item) {
        return false; // Override in BaseEntity
    }
}
