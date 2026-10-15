package dev.lumen.teleportlab.forge.mixin;

import dev.lumen.teleportlab.core.Position;
import dev.lumen.teleportlab.forge.LabRuntime;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Method;

/** Optional private-record boundary: no Lumen compile dependency, no changes to return values. */
@Pseudo
@Mixin(targets = "com.example.examplemod.waypoint.TeleportService", remap = false)
public abstract class LumenTeleportMixin {
    @Unique private static Position teleportlab$before;
    @Unique private static Position teleportlab$target;
    @Unique private static long teleportlab$utc;
    @Unique private static long teleportlab$mono;

    @Inject(method = "teleportTo", at = @At("HEAD"), require = 0, remap = false)
    private static void teleportlab$capture(@Coerce Object target, String ignoredLabel, CallbackInfoReturnable<Boolean> ci) {
        teleportlab$before = null; teleportlab$target = null;
        LabRuntime lab = LabRuntime.get();
        lab.hookSeen = true;
        if (!lab.analyzer.recording()) return;
        try {
            teleportlab$utc = System.currentTimeMillis();
            teleportlab$mono = System.nanoTime() / 1_000_000;
            teleportlab$before = LabRuntime.position();
            // Location is a private Lumen record. Read its accessors, never write its fields.
            teleportlab$target = new Position((String) teleportlab$read(target, "dimension"),
                    (double) teleportlab$read(target, "x"), (double) teleportlab$read(target, "y"),
                    (double) teleportlab$read(target, "z"));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            lab.message = "Lumen-Schnittstelle inkompatibel: keine Messung gestartet";
        }
    }
    @Inject(method = "teleportTo", at = @At("RETURN"), require = 0, remap = false)
    private static void teleportlab$accepted(@Coerce Object target, String ignoredLabel, CallbackInfoReturnable<Boolean> ci) {
        try {
            if (Boolean.TRUE.equals(ci.getReturnValue()) && teleportlab$before != null && teleportlab$target != null)
                LabRuntime.get().begin(teleportlab$before, teleportlab$target, teleportlab$utc, teleportlab$mono);
        } catch (RuntimeException failure) {
            LabRuntime.get().message = "Diagnose konnte den Versuch nicht erfassen";
        } finally { teleportlab$before = null; teleportlab$target = null; }
    }
    @Unique private static Object teleportlab$read(Object record, String name) throws ReflectiveOperationException {
        Method method = record.getClass().getDeclaredMethod(name);
        if (!method.trySetAccessible()) throw new IllegalAccessException(name);
        return method.invoke(record);
    }
}
