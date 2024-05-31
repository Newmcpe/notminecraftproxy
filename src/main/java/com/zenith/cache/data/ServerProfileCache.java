package com.zenith.cache.data;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.zenith.cache.CachedData;
import lombok.NonNull;
import lombok.Setter;
import org.geysermc.mcprotocollib.network.packet.Packet;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class ServerProfileCache implements CachedData {

    protected GameProfile profile;
    public float yaw;
    public float pitch;

    @Override
    public void getPackets(@NonNull Consumer<Packet> consumer) {}

    public @Nullable GameProfile getProfile() {
        return profile;
    }

    @Override
    public void reset(boolean full) {
        if (full)   {
            this.profile = null;
        }
    }

    //set profile
    public ServerProfileCache setProfile(GameProfile profile) {
        this.profile = profile;
        return this;
    }

    public ServerProfileCache setYaw(float yaw) {
        this.yaw = yaw;
        return this;
    }

    //set pitch
    public ServerProfileCache setPitch(float pitch) {
        this.pitch = pitch;
        return this;
    }
}
