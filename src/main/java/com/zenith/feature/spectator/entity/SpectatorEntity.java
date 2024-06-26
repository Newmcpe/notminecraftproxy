package com.zenith.feature.spectator.entity;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.zenith.cache.data.PlayerCache;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class SpectatorEntity {
    public abstract List<EntityMetadata<?, ?>> getSelfEntityMetadata(final GameProfile spectatorRealProfile, final GameProfile spectatorFakeProfile, final int spectatorEntityId);

    public abstract List<EntityMetadata<?, ?>> getEntityMetadata(final GameProfile spectatorRealProfile, final GameProfile spectatorFakeProfile, final int spectatorEntityId);

    public abstract Packet getSpawnPacket(final int entityId, final UUID uuid, final PlayerCache playerCache, final GameProfile gameProfile);
    public Optional<Packet> getSoundPacket(final PlayerCache playerCache) {
        return Optional.empty();
    }
    // A list of all minecraft mobs with eye height, total height, and total width (on 1.20)
    // https://gist.github.com/bradcarnage/c894976345a0e57280c8619fe3ac0282
    public abstract double getEyeHeight();
    public abstract double getHeight();
    public abstract double getWidth();
}
