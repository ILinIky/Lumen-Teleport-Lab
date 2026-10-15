package dev.lumen.teleportlab.forge.mixin;

import dev.lumen.teleportlab.forge.LabRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ServerPositionMixin {
    @Inject(method = "handleMovePlayer", at = @At("RETURN"), remap = false)
    private void teleportlab$resolvedServerPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        // RETURN runs after ensureRunningOnSameThread and vanilla's relative-coordinate resolution / acknowledgement.
        if (Minecraft.getInstance().isSameThread()) LabRuntime.get().serverPosition((ClientPacketListener) (Object) this);
    }
}
