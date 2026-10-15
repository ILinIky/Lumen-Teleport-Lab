package dev.lumen.teleportlab.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import dev.lumen.teleportlab.core.Reason;

@Mod.EventBusSubscriber(modid = TeleportLabMod.ID, value = Dist.CLIENT)
public final class ClientHooks {
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
        LabRuntime.get().tick();
        while (Keys.dashboard != null && Keys.dashboard.consumeClick())
            Minecraft.getInstance().setScreen(new LabScreen(Minecraft.getInstance().screen));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        LabRuntime.get().analyzer.interrupt(Reason.DISCONNECTED);
    }
    @Mod.EventBusSubscriber(modid = TeleportLabMod.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        static KeyMapping dashboard;
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            dashboard = new KeyMapping("key.teleportlab.dashboard", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.teleportlab");
            event.register(dashboard);
        }
    }
}
