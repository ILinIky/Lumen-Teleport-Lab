package dev.lumen.teleportlab.forge;

import dev.lumen.teleportlab.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Independent Minecraft screen using Lumen's dark violet / cyan visual palette. */
public final class LabScreen extends Screen {
    private static final int TEXT = 0xFFF1F1F6, MUTED = 0xFFA3A6B8, CARD = 0xFF1B1C25;
    private static final int ACCENT = 0xFF8B5CF6, CYAN = 0xFF22D3EE;
    private final Screen parent;
    private int page;
    private int pageSize;
    private List<UUID> shown = List.of();
    private Button record, export;

    public LabScreen(Screen parent) { super(Component.literal("Lumen Teleport Lab")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    private int left() { return Math.max(8, (width - 730) / 2); }
    private int right() { return width - left(); }
    private List<AttemptSnapshot> recent() {
        var list = new ArrayList<>(LabRuntime.get().analyzer.snapshots()); Collections.reverse(list); return list;
    }
    @Override protected void init() {
        clearWidgets();
        int x = left(), w = right() - x, gap = 5, bw = (w - gap * 3) / 4;
        LabRuntime lab = LabRuntime.get();
        record = button(x, height - 47, bw, lab.analyzer.recording() ? "Stoppen" : "Aufzeichnen", () -> {
            lab.analyzer.setRecording(!lab.analyzer.recording());
            lab.message = lab.analyzer.recording() ? "Aufzeichnung aktiv · Teleport in Lumen auslösen" : "Aufzeichnung gestoppt";
            init();
        });
        button(x + bw + gap, height - 47, bw, "Zurücksetzen", () -> minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                accepted -> { if (accepted) lab.analyzer.reset(); minecraft.setScreen(this); },
                Component.literal("Messdaten zurücksetzen?"), Component.literal("Nicht exportierte Versuche werden verworfen."))));
        export = button(x + (bw + gap) * 2, height - 47, bw, "CSV exportieren", lab::export);
        button(x + (bw + gap) * 3, height - 47, bw, "Schließen", this::onClose);
        pageSize = Math.max(1, (height - 207) / 25);
        var attempts = recent();
        int pages = Math.max(1, (attempts.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages - 1);
        button(right() - 105, 99, 28, "<", () -> { page = Math.max(0, page - 1); init(); });
        button(right() - 28, 99, 28, ">", () -> { page = Math.min(pages - 1, page + 1); init(); });
        shown = attempts.stream().map(AttemptSnapshot::id).toList();
        for (int i = 0; i < pageSize && page * pageSize + i < attempts.size(); i++) {
            UUID id = attempts.get(page * pageSize + i).id();
            button(right() - 53, 133 + i * 25, 49, "Details", () -> minecraft.setScreen(new Detail(this, id)));
        }
    }
    private Button button(int x, int y, int w, String text, Runnable action) {
        return addRenderableWidget(new LabButton(x, y, w, text, action,
                text.equals("Aufzeichnen") || text.equals("Stoppen")));
    }
    @Override public void tick() {
        if (!recent().stream().map(AttemptSnapshot::id).toList().equals(shown)) init();
        export.active = !LabRuntime.get().exporting;
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        int x = left(), r = right(), w = r - x;
        g.fill(0, 0, width, height, 0xF20E0F14);
        g.fill(x, 16, x + 3, 39, ACCENT);
        text(g, "LUMEN", x + 12, 16, TEXT);
        text(g, "TELEPORT LAB / DIAGNOSE", x + 12, 29, CYAN);
        LabRuntime lab = LabRuntime.get();
        String state = lab.analyzer.recording() ? "● AUFZEICHNUNG" : "○ DEAKTIVIERT";
        text(g, state, r - font.width(state), 20, lab.analyzer.recording() ? 0xFF34D399 : MUTED);
        var all = lab.analyzer.snapshots();
        var stats = DashboardStats.of(all);
        int cw = (w - 15) / 4;
        metric(g, x, 49, cw, "VERSUCHE", "" + stats.attempts(), TEXT);
        metric(g, x + cw + 5, 49, cw, "STABIL / KORR.", stats.stable() + " / " + stats.corrected(), 0xFF34D399);
        metric(g, x + (cw + 5) * 2, 49, cw, "ERFOLGSQUOTE", String.format(Locale.ROOT, "%.1f %%", stats.successRate()), CYAN);
        metric(g, x + (cw + 5) * 3, 49, cw, "PING Ø", stats.averagePingMs() == null ? "—" : String.format(Locale.ROOT, "%.0f ms", stats.averagePingMs()), TEXT);
        text(g, "Letzte Versuche · " + stats.unclear() + " unklar · " + (page + 1) + "/" + Math.max(1, (all.size() + pageSize - 1) / pageSize), x, 105, MUTED);
        int idX = x + 7, distanceX = x + w * 28 / 100, statusX = x + w * 53 / 100;
        text(g, w < 550 ? "VERSUCH" : "VERSUCH / UTC", idX, 123, MUTED); text(g, "DISTANZ", distanceX, 123, MUTED); text(g, "ERGEBNIS", statusX, 123, MUTED);
        var attempts = recent();
        for (int i = 0; i < pageSize && page * pageSize + i < attempts.size(); i++) {
            var a = attempts.get(page * pageSize + i); int y = 133 + i * 25;
            g.fill(x, y, r, y + 23, CARD);
            String time = java.time.Instant.ofEpochMilli(a.startedUtcMs()).toString();
            String identifier = a.id().toString().substring(0, 8) + (w < 550 ? "" : " · " + time.substring(11, 19));
            text(g, font.plainSubstrByWidth(identifier, distanceX - idX - 5), idX, y + 7, TEXT);
            String distance = a.comparableDimensions() ? String.format(Locale.ROOT, "%.1f Bl.", a.origin().distanceTo(a.target())) : "DIM-Wechsel";
            text(g, font.plainSubstrByWidth(distance, statusX - distanceX - 5), distanceX, y + 7, MUTED);
            String status = a.status() == null ? "BEOBACHTUNG" : a.status().toString();
            text(g, font.plainSubstrByWidth(status, Math.max(15, r - 60 - statusX)), statusX, y + 7, color(a.status()));
        }
        if (all.isEmpty()) {
            text(g, font.plainSubstrByWidth("Aufzeichnung starten · Lumen-Teleport auslösen.", w - 14), x + 7, 148, MUTED);
            text(g, font.plainSubstrByWidth("Lokale Ankunft allein ist kein Serverbeleg.", w - 14), x + 7, 164, CYAN);
        }
        String hint = "Fenster " + lab.config.observationMs() / 1000 + " s · F8 Dashboard · " + lab.analyzer.evictedAttempts() + " ältere Versuche verworfen";
        text(g, font.plainSubstrByWidth(hint, w), x, height - 66, MUTED);
        text(g, font.plainSubstrByWidth(lab.message, w), x, height - 20, MUTED);
        super.render(g, mx, my, delta);
    }
    private void metric(GuiGraphics g, int x, int y, int w, String label, String value, int color) {
        g.fill(x, y, x + w, y + 44, CARD); g.fill(x, y, x + w, y + 1, 0xFF2A2C3A);
        text(g, font.plainSubstrByWidth(label, w - 12), x + 6, y + 7, MUTED);
        text(g, value, x + 6, y + 25, color);
    }
    private void text(GuiGraphics g, String s, int x, int y, int color) { g.drawString(font, s, x, y, color, false); }
    private static int color(Status status) {
        if (status == null) return CYAN;
        return switch (status) { case SUCCESS_STABLE -> 0xFF34D399; case CORRECTED -> 0xFFF87171;
            case PARTIAL -> 0xFFFBBF24; case TIMEOUT, UNKNOWN -> MUTED; };
    }
    private static final class LabButton extends Button {
        private final boolean primary;
        LabButton(int x, int y, int w, String label, Runnable action, boolean primary) {
            super(x, y, w, 20, Component.literal(label), b -> action.run(), DEFAULT_NARRATION);
            this.primary = primary;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            int border = isFocused() ? CYAN : primary ? ACCENT : 0xFF353848;
            int bg = !active ? 0xFF16171D : primary ? (isHoveredOrFocused() ? 0xFF9B6CFF : ACCENT)
                    : (isHoveredOrFocused() ? 0xFF292B39 : CARD);
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
            g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, bg);
            renderScrollingString(g, net.minecraft.client.Minecraft.getInstance().font, 4, active ? TEXT : MUTED);
        }
    }
    private static final class Detail extends Screen {
        private final Screen parent;
        private final UUID id;
        private int offset;
        Detail(Screen parent, UUID id) { super(Component.literal("Versuchsdetails")); this.parent = parent; this.id = id; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.literal("Zurück"), b -> onClose()).bounds(14, height - 30, 90, 20).build());
        }
        @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
            offset = Math.max(0, Math.min(10000, offset - (int) (dy * 24))); return true;
        }
        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            g.fill(0, 0, width, height, 0xFF121319);
            var found = LabRuntime.get().analyzer.snapshots().stream().filter(a -> a.id().equals(id)).findFirst();
            if (found.isPresent()) {
                var a = found.get();
                g.drawString(font, "LUMEN / VERSUCH " + id.toString().substring(0, 8), 14, 14, CYAN, false);
                var lines = new ArrayList<String>();
                lines.add("Status: " + (a.status() == null ? "BEOBACHTUNG" : a.status()));
                lines.add("Grund: " + (a.reason() == null ? "Beobachtungsfenster läuft" : a.reason()));
                lines.add("Start UTC: " + java.time.Instant.ofEpochMilli(a.startedUtcMs()));
                lines.add("Methode: " + a.method() + " · Serverkennung: " + a.serverId());
                lines.add("Ausgang: " + pos(a.origin())); lines.add("Ziel: " + pos(a.target())); lines.add("Ende: " + pos(a.end()));
                lines.add("Abweichung: " + value(a.finalDeviation(), " Blöcke") + " · Korrekturen: " + a.corrections());
                lines.add("Lokale Ankunft: " + value(a.firstLocalArrivalMs(), " ms") + " · Erste Serverposition: " + value(a.firstServerResponseMs(), " ms"));
                lines.add("Stabile Endposition ab: " + value(a.stableEndMs(), " ms") + " · Datenlücke: " + a.sampleGap());
                lines.add("Beobachtet: " + a.observedMs() + " ms / " + a.config().observationMs() + " ms");
                lines.add("Serverposition am Ziel beobachtet: " + a.serverTargetObserved());
                lines.add("EREIGNISSE (Mausrad scrollen)");
                for (var e : a.events()) if (e.type() != LabEvent.Type.SAMPLE && e.type() != LabEvent.Type.LOCAL_POSITION)
                    lines.add(e.elapsedMs() + " ms · " + e.type() + (e.checkpointMs() == null ? "" : " · geplant " + e.checkpointMs() + " ms") + " · " + pos(e.position()));
                int lineWidth = Math.max(80, width - 30);
                var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
                for (String line : lines) wrapped.addAll(font.split(Component.literal(line), lineWidth));
                offset = Math.min(offset, Math.max(0, wrapped.size() * 14 - (height - 83)));
                g.enableScissor(12, 36, width - 12, height - 40);
                for (int i = 0; i < wrapped.size(); i++) g.drawString(font, wrapped.get(i), 14, 39 + i * 14 - offset, TEXT, false);
                g.disableScissor();
            }
            super.render(g, mx, my, delta);
        }
        private static String pos(Position p) { return p == null ? "—" : String.format(Locale.ROOT, "%s [%.2f, %.2f, %.2f]", p.dimension(), p.x(), p.y(), p.z()); }
        private static String value(Object n, String unit) { return n == null ? "nicht beobachtet" : n + unit; }
    }
}
