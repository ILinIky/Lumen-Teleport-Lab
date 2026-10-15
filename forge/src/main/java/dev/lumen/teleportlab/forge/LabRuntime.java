package dev.lumen.teleportlab.forge;

import dev.lumen.teleportlab.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

/** Client-thread adapter. No movement APIs, packet sending, or packet cancellation. */
public final class LabRuntime {
    private static LabRuntime instance;
    public final TeleportAnalyzer analyzer;
    public final LabConfig config;
    private final ServerPseudonym pseudonym;
    private final Path directory;
    private ClientPacketListener session;
    private String originDimension, targetDimension;
    public String message = "Aufzeichnung ist ausgeschaltet";
    public boolean exporting;
    public boolean hookSeen;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Teleport-Lab-CSV"); t.setDaemon(true); return t;
    });

    private LabRuntime() {
        directory = Minecraft.getInstance().gameDirectory.toPath().resolve("config/teleportlab");
        LabConfig loaded;
        byte[] key;
        try {
            loaded = LabSettings.load(directory);
            Path secret = directory.resolve("server-hmac.key");
            if (Files.notExists(secret)) Files.write(secret, ServerPseudonym.newKey(), StandardOpenOption.CREATE_NEW);
            key = Files.readAllBytes(secret);
            if (key.length != 32) throw new IllegalArgumentException("Invalid key length");
        } catch (Exception failure) {
            // An ephemeral key preserves privacy if settings storage is unavailable.
            loaded = LabConfig.defaults(); key = ServerPseudonym.newKey();
            message = "Konfiguration nicht lesbar: Standardwerte, temporäre Serverkennung";
        }
        config = loaded;
        pseudonym = new ServerPseudonym(key);
        analyzer = new TeleportAnalyzer(config, LabClock.system());
    }
    public static LabRuntime get() {
        if (instance == null) instance = new LabRuntime();
        return instance;
    }
    public static Position position() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || mc.level == null ? null : new Position(
                mc.level.dimension().location().toString(), mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }
    private static Integer ping() {
        Minecraft mc = Minecraft.getInstance();
        var info = mc.player == null || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? null : info.getLatency();
    }
    /** Called only for a teleport accepted by the unchanged Lumen service. */
    public void begin(Position before, Position target, long startedUtcMs, long startedMonotonicMs) {
        hookSeen = true;
        if (!analyzer.recording() || before == null || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        String address = mc.getCurrentServer() == null ? "local-integrated-world" : mc.getCurrentServer().ip;
        String method = mc.getSingleplayerServer() == null ? "CLIENT_ONLY" : "SINGLEPLAYER";
        analyzer.begin(before, target, pseudonym.identify(address), method, ping(), startedUtcMs, startedMonotonicMs);
        session = mc.getConnection(); originDimension = before.dimension(); targetDimension = target.dimension();
        Position current = position();
        if (current != null) analyzer.sample(current, ping());
    }
    public void tick() {
        if (!analyzer.observing()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) { analyzer.interrupt(Reason.DISCONNECTED); return; }
        if (mc.getConnection() != session) { analyzer.interrupt(Reason.SESSION_CHANGED); return; }
        Position p = position();
        if (p == null || mc.player.isDeadOrDying()) { analyzer.interrupt(Reason.PLAYER_UNAVAILABLE); return; }
        if (!p.dimension().equals(originDimension) && !p.dimension().equals(targetDimension)) {
            analyzer.interrupt(Reason.DIMENSION_CHANGED); return;
        }
        // Pauses are observation gaps. They cannot quietly turn into successes.
        if (!mc.isPaused()) analyzer.sample(p, ping());
    }
    public void serverPosition(ClientPacketListener connection) {
        if (connection != session || connection != Minecraft.getInstance().getConnection()) return;
        Position p = position();
        if (p != null) analyzer.serverPosition(p);
    }
    public void export() {
        if (exporting) return;
        List<AttemptSnapshot> snapshot = analyzer.snapshots();
        exporting = true; message = "CSV wird geschrieben …";
        writer.submit(() -> {
            String result;
            try {
                Path path = new CsvExporter().export(directory.resolve("exports"), snapshot);
                result = "Export: " + path.getFileName() + " (abgeschlossene Versuche)";
            } catch (Exception failure) { result = "Export fehlgeschlagen: Schreibrechte / Speicherplatz prüfen"; }
            String finalResult = result;
            Minecraft.getInstance().execute(() -> { exporting = false; message = finalResult; });
        });
    }
}
