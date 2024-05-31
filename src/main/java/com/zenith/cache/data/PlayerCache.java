package com.zenith.cache.data;

import com.zenith.Proxy;
import com.zenith.cache.CachedData;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityCache;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.cache.data.inventory.InventoryCache;
import com.zenith.util.math.MutableVec3i;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.CreativeGrabAction;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.data.game.setting.Difficulty;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundChangeDifficultyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.zenith.Shared.CLIENT_LOG;
import static com.zenith.Shared.CONFIG;
import static java.util.Objects.nonNull;


@Getter
@Setter
@Accessors(chain = true)
public class PlayerCache implements CachedData {
    public boolean hardcore;
    public boolean reducedDebugInfo;
    public int maxPlayers;
    public boolean enableRespawnScreen;
    public boolean doLimitedCrafting;
    public GlobalPos lastDeathPos;
    public int portalCooldown;
    public GameMode gameMode;
    public int heldItemSlot = 0;

    public EntityPlayer thePlayer = (EntityPlayer) new EntityPlayer(true).setEntityId(-1);

    public final InventoryCache inventoryCache = new InventoryCache();

    public final EntityCache entityCache;
    public Difficulty difficulty = Difficulty.NORMAL;
    public boolean isDifficultyLocked;
    public boolean invincible;
    public boolean canFly;
    public boolean flying;
    public boolean creative;
    public float flySpeed;
    public float walkSpeed;
    public boolean isSneaking = false;
    public boolean isSprinting = false;
    public EntityEvent opLevel = EntityEvent.PLAYER_OP_PERMISSION_LEVEL_0;
    public AtomicInteger actionId = new AtomicInteger(0);
    private static final MutableVec3i DEFAULT_SPAWN_POSITION = new MutableVec3i(0, 0, 0);
    public MutableVec3i spawnPosition = DEFAULT_SPAWN_POSITION;
    public int lastTeleportReceived = 0;
    public int lastTeleportAccepted = 0;

    public PlayerCache(final EntityCache entityCache) {
        this.entityCache = entityCache;
    }

    @Override
    public void getPackets(@NonNull Consumer<Packet> consumer) {
        // todo: may need to move this out so spectators don't get sent wrong abilities
        consumer.accept(new ClientboundPlayerAbilitiesPacket(this.invincible, this.canFly, this.flying, this.creative, this.flySpeed, this.walkSpeed));
        consumer.accept(new ClientboundChangeDifficultyPacket(this.difficulty, this.isDifficultyLocked));
        consumer.accept(new ClientboundGameEventPacket(GameEvent.CHANGE_GAMEMODE, this.gameMode));
        consumer.accept(new ClientboundEntityEventPacket(this.thePlayer.getEntityId(), this.opLevel));
        var container = this.inventoryCache.getContainers().get(this.inventoryCache.getOpenContainerId());
        if (container == this.inventoryCache.getContainers().defaultReturnValue()) {
            container = this.inventoryCache.getPlayerInventory();
        }
        if (container.getContainerId() != 0) {
            consumer.accept(new ClientboundOpenScreenPacket(container.getContainerId(), container.getType(), container.getTitle()));
        }
        consumer.accept(new ClientboundContainerSetContentPacket(
            container.getContainerId(),
            actionId.get(),
            container.getContents().toArray(new ItemStack[0]),
            null));
        if (!CONFIG.debug.sendChunksBeforePlayerSpawn)
            consumer.accept(new ClientboundPlayerPositionPacket(this.getX(), this.getY(), this.getZ(), this.getYaw(), this.getPitch(), ThreadLocalRandom.current().nextInt(16, 1024)));
        consumer.accept(new ClientboundSetDefaultSpawnPositionPacket(spawnPosition.getX(), spawnPosition.getY(), spawnPosition.getZ(), 0.0f));
        consumer.accept(new ClientboundSetCarriedItemPacket(heldItemSlot));
    }

    @Override
    public void reset(boolean full) {
        if (full) {
            this.thePlayer = (EntityPlayer) new EntityPlayer(true).setEntityId(-1);
            this.hardcore = this.reducedDebugInfo = false;
            this.maxPlayers = -1;
            this.inventoryCache.reset();
            this.heldItemSlot = 0;
            this.doLimitedCrafting = false;
            this.lastTeleportReceived = 0;
            this.lastTeleportAccepted = 0;
        }
        this.spawnPosition = DEFAULT_SPAWN_POSITION;
        this.gameMode = null;
        this.thePlayer.setHealth(20.0f);
        this.thePlayer.setFood(20);
        this.thePlayer.setSaturation(5);
        this.thePlayer.getPotionEffectMap().clear();
        this.isSneaking = this.isSprinting = false;
    }

    @Override
    public String getSendingMessage() {
        return String.format(
                "Sending player position: (x=%.2f, y=%.2f, z=%.2f, yaw=%.2f, pitch=%.2f)",
                this.getX(),
                this.getY(),
                this.getZ(),
                this.getYaw(),
                this.getPitch()
        );
    }

    public static void sync() {
        if (nonNull(Proxy.getInstance().getClient())) {
            try {
                // intentionally sends an invalid inventory packet to make the server send us our full inventory
                Proxy.getInstance().getClient().sendAsync(new ServerboundContainerClickPacket(
                    0,
                    -1337,
                    0,
                    ContainerActionType.CREATIVE_GRAB_MAX_STACK,
                    CreativeGrabAction.GRAB,
                    new ItemStack(1, 1),
                    Int2ObjectMaps.emptyMap()));
            } catch (final Exception e) {
                CLIENT_LOG.warn("Failed Player Sync", e);
            }
        }
    }

    public void setInventory(final int containerId, final ItemStack[] inventory) {
        this.inventoryCache.setInventory(containerId, inventory);
    }

    public ItemStack getEquipment(final EquipmentSlot slot) {
        var inventory = this.inventoryCache.getPlayerInventory();
        if (inventory == null) return null;
        return switch (slot) {
            case EquipmentSlot.HELMET -> inventory.getItemStack(5);
            case EquipmentSlot.CHESTPLATE -> inventory.getItemStack(6);
            case EquipmentSlot.LEGGINGS -> inventory.getItemStack(7);
            case EquipmentSlot.BOOTS -> inventory.getItemStack(8);
            case EquipmentSlot.OFF_HAND -> inventory.getItemStack(45);
            case EquipmentSlot.MAIN_HAND -> inventory.getItemStack(heldItemSlot + 36);
        };
    }

    // prefer calling getEquipment with a slot type instead of this, creates gc spam
    public Map<EquipmentSlot, ItemStack> getEquipment() {
        final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        equipment.put(EquipmentSlot.HELMET, getEquipment(EquipmentSlot.HELMET));
        equipment.put(EquipmentSlot.CHESTPLATE, getEquipment(EquipmentSlot.CHESTPLATE));
        equipment.put(EquipmentSlot.LEGGINGS, getEquipment(EquipmentSlot.LEGGINGS));
        equipment.put(EquipmentSlot.BOOTS, getEquipment(EquipmentSlot.BOOTS));
        equipment.put(EquipmentSlot.OFF_HAND, getEquipment(EquipmentSlot.OFF_HAND));
        equipment.put(EquipmentSlot.MAIN_HAND, getEquipment(EquipmentSlot.MAIN_HAND));
        return equipment;
    }

    public void setInventorySlot(final int containerId, ItemStack newItemStack, int slot) {
        this.inventoryCache.setItemStack(containerId, slot, newItemStack);
    }

    public double getX() {
        return this.thePlayer.getX();
    }

    public PlayerCache setX(double x) {
        this.thePlayer.setX(x);
        return this;
    }

    public double getY()    {
        return this.thePlayer.getY();
    }

    public double getEyeY() {
        return getY() + (isSneaking ? 1.27 : 1.62);
    }

    public PlayerCache setY(double y)    {
        this.thePlayer.setY(y);
        return this;
    }

    public double getZ()    {
        return this.thePlayer.getZ();
    }

    public PlayerCache setZ(double z)    {
        this.thePlayer.setZ(z);
        return this;
    }

    public float getYaw()    {
        return this.thePlayer.getYaw();
    }

    public PlayerCache setYaw(float yaw)    {
        this.thePlayer.setYaw(yaw);
        return this;
    }

    public float getPitch()    {
        return this.thePlayer.getPitch();
    }

    public PlayerCache setPitch(float pitch)    {
        this.thePlayer.setPitch(pitch);
        return this;
    }

    public int getEntityId()    {
        return this.thePlayer.getEntityId();
    }

    public PlayerCache setEntityId(int id)  {
        if (this.thePlayer.getEntityId() != -1) {
            this.entityCache.remove(this.thePlayer.getEntityId());
        }
        this.thePlayer.setEntityId(id);
        this.entityCache.add(this.thePlayer);
        return this;
    }

    public PlayerCache setUuid(UUID uuid) {
        this.thePlayer.setUuid(uuid);
        return this;
    }

    public double distanceToSelf(final Entity entity) {
        return Math.sqrt(
            Math.pow(getX() - entity.getX(), 2)
                + Math.pow(getY() - entity.getY(), 2)
                + Math.pow(getZ() - entity.getZ(), 2));
    }

    public void closeContainer(final int containerId) {
        this.inventoryCache.closeContainer(containerId);
    }

    public void openContainer(final int containerId, final ContainerType type, final Component title) {
        this.inventoryCache.openContainer(containerId, type, title);
    }

    public List<ItemStack> getPlayerInventory() {
        return this.inventoryCache.getPlayerInventory().getContents();
    }
}
