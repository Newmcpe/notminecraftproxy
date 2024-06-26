package com.zenith.feature.spectator.entity.mob;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.zenith.cache.data.PlayerCache;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Arrays.asList;

public class SpectatorEntityEndCrystal extends SpectatorMob {

    @Override
    public List<EntityMetadata<?, ?>> getSelfEntityMetadata(final GameProfile spectatorRealProfile, final GameProfile spectatorFakeProfile, final int spectatorEntityId) {
        return getEntityMetadata(spectatorRealProfile, spectatorEntityId, true);
    }

    @Override
    public List<EntityMetadata<?, ?>> getEntityMetadata(final GameProfile spectatorRealProfile, final GameProfile spectatorFakeProfile, final int spectatorEntityId) {
        return getEntityMetadata(spectatorRealProfile, spectatorEntityId, false);
    }

    private List<EntityMetadata<?, ?>> getEntityMetadata(final GameProfile spectatorProfile, final int spectatorEntityId, final boolean self) {
        return asList(
            new ObjectEntityMetadata<>(2, MetadataType.OPTIONAL_CHAT, Optional.of(Component.text(spectatorProfile.getName()))),
            new BooleanEntityMetadata(3, MetadataType.BOOLEAN, !self), // hide nametag on self
            new BooleanEntityMetadata(9, MetadataType.BOOLEAN, false)
        );
    }

    @Override
    public double getEyeHeight() {
        return 1.5;
    }

    @Override
    public double getHeight() {
        return 2;
    }

    @Override
    public double getWidth() {
        return 2;
    }

    @Override
    EntityType getType() {
        return EntityType.END_CRYSTAL;
    }

    @Override
    public Optional<Packet> getSoundPacket(final PlayerCache playerCache) {
        var f1 = ThreadLocalRandom.current().nextFloat();
        var f2 = ThreadLocalRandom.current().nextFloat();
        return Optional.of(new ClientboundSoundPacket(
            BuiltinSound.ENTITY_GENERIC_EXPLODE,
            SoundCategory.BLOCK,
            playerCache.getX(),
            playerCache.getY(),
            playerCache.getZ(),
            4.0f,
            (1.0F + (f1 - f2) * 0.2F) * 0.7F,
            0L
        ));
    }
}
