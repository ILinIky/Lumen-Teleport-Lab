package dev.lumen.teleportlab.smoke;

import dev.lumen.teleportlab.forge.LabRuntime;
import dev.lumen.teleportlab.forge.LabScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Development-only visual smoke; never packaged in the distributable mod JAR. */
@Mod.EventBusSubscriber(modid = "teleportlab", value = Dist.CLIENT)
public final class DashboardSmoke {
    private static int frames;
    private static boolean opened, requested;
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        if (!opened) {
            try {
                Class.forName("net.minecraft.client.multiplayer.ClientPacketListener");
                Class<?> service = Class.forName("com.example.examplemod.waypoint.TeleportService");
                Class<?> location = Class.forName("com.example.examplemod.waypoint.TeleportService$Location");
                var constructor = location.getDeclaredConstructor(String.class, double.class, double.class, double.class, float.class, float.class);
                constructor.setAccessible(true);
                var target = constructor.newInstance("minecraft:overworld", 100.0, 70.0, 40.0, 0f, 0f);
                var teleport = service.getDeclaredMethod("teleportTo", location, String.class);
                teleport.setAccessible(true);
                var lab = LabRuntime.get();
                lab.analyzer.setRecording(true);
                boolean accepted = (boolean) teleport.invoke(null, target, "ignored test label");
                lab.analyzer.setRecording(false);
                if (accepted || !lab.hookSeen || !lab.analyzer.snapshots().isEmpty() || lab.message.contains("inkompatibel"))
                    throw new IllegalStateException("Rejected-call hook / record accessor test failed");
                System.out.println("TELEPORTLAB_REJECTED_CALL_AND_RECORD_ACCESS_PASS");
                System.out.println("TELEPORTLAB_LUMEN_TARGET_LOADED");
            } catch (ClassNotFoundException absent) {
                System.out.println("TELEPORTLAB_SMOKE_NO_LUMEN");
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Lumen integration smoke failed", failure);
            }
            LabRuntime.get().message = "UI-SMOKETEST · keine realen Messdaten · Aufzeichnung AUS";
            mc.setScreen(new LabScreen(null)); opened = true; frames = 0; return;
        }
        if (++frames > 60 && !requested) {
            requested = true;
            Screenshot.grab(mc.gameDirectory, "teleportlab-dashboard-smoke.png", mc.getMainRenderTarget(), 1,
                    result -> mc.execute(() -> {
                        System.out.println("TELEPORTLAB_UI_SMOKE_SCREENSHOT_COMPLETE");
                        mc.stop();
                    }));
        }
    }
}
