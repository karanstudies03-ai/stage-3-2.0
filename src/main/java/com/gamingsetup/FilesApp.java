package com.gamingsetup;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Files app. Left: folder list (This PC, Windows (C:) with its folders, External device with your cameras).
 * Right: what is inside as big tiles - photos show a small preview. Click a photo to see it big.
 */
final class FilesApp {
    private enum Loc { ROOT, C, EXT, CAM }
    private enum Kind { FOLDER, PHOTO, DRIVE, CAMERA }
    private record Item(Kind kind, String label, Path path, Runnable open) {}
    private record Tile(Item item, int x, int y, ButtonWidget btn) {}
    private record Side(String label, int depth, int icon, int y, boolean active) {}
    private record Geo(int wx, int wy, int ww, int wh, int navY, int toolY, int bodyTop, int bodyBottom,
                       int sideX, int sideW, int contentX, int contentR) {}

    private static final int RH = 14, CELL_W = 76, CELL_H = 66;

    private Loc loc = Loc.ROOT;
    private Path dir;
    private String camName = "";
    private int scrollRow = 0, sideScroll = 0, totalItems = 0;
    private String status = "";
    private boolean naming = false, viewing = false, menuOpen = false;
    private TextFieldWidget nameField;
    private int viewIndex = 0;
    private final List<Path> photos = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final List<Side> sides = new ArrayList<>();
    private static Path clipboard;   // copied photo, survives closing the monitor

    void reset() {
        loc = Loc.ROOT; dir = null; camName = ""; scrollRow = 0; sideScroll = 0; status = "";
        naming = false; viewing = false; menuOpen = false;
    }

    private Geo geo(MonitorScreen s) {
        int wx = 4, wy = 4, ww = s.width - 8, wh = s.height - 24 - 8;
        return new Geo(wx, wy, ww, wh, wy + 17, wy + 37, wy + 58, wy + wh - 14, wx + 4, 112, wx + 4 + 112 + 4, wx + ww - 4);
    }

    private ButtonWidget ghost(MonitorScreen s, int x, int y, int w, int h, Runnable action) {
        ButtonWidget b = s.btn("", x, y, w, h, action);
        b.setAlpha(0.0f);                // invisible click area; the app draws the picture itself
        return b;
    }

    // ================================================================== widgets
    void build(MonitorScreen s) {
        Geo g = geo(s);
        tiles.clear();
        sides.clear();
        s.btn("x", g.wx() + g.ww() - 18, g.wy() + 1, 16, 12, s::closeFiles);   // close window -> desktop

        if (viewing) { buildViewer(s, g); return; }

        s.btn("<", g.wx() + 4, g.navY(), 18, 16, () -> up(s));
        s.btn("^", g.wx() + 24, g.navY(), 18, 16, () -> up(s));

        // ---- toolbar (only inside Windows (C:))
        int tx = g.wx() + 6, ty = g.toolY();
        if (naming) {
            nameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, tx, ty, 130, 16, Text.literal("Folder name"));
            nameField.setMaxLength(24);
            s.addField(nameField);
            nameField.setFocused(true);
            s.btn("OK", tx + 134, ty, 30, 16, () -> makeFolder(s));
            s.btn("Cancel", tx + 168, ty, 46, 16, () -> { naming = false; s.rebuild(); });
        } else if (loc == Loc.C) {
            s.btn("New folder", tx, ty, 62, 16, () -> { naming = true; s.rebuild(); });
            if (clipboard != null) s.btn("Paste", tx + 66, ty, 40, 16, () -> paste(s));
        }

        // ---- what is inside
        List<Item> items = items(s);
        totalItems = items.size();
        buildSide(s, g);

        int areaX = g.contentX() + 6, areaY = g.bodyTop() + 6;
        int areaW = g.contentR() - areaX - 20, areaH = g.bodyBottom() - areaY - 2;
        int cols = Math.max(1, areaW / CELL_W), rowsVis = Math.max(1, areaH / CELL_H);
        int totalRows = (items.size() + cols - 1) / cols;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - rowsVis)));
        for (int r = 0; r < rowsVis; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (scrollRow + r) * cols + c;
                if (idx >= items.size()) break;
                Item it = items.get(idx);
                int x = areaX + c * CELL_W, y = areaY + r * CELL_H;
                tiles.add(new Tile(it, x, y, ghost(s, x, y, CELL_W - 4, CELL_H - 4, it.open())));
            }
        }
        if (totalRows > rowsVis) {
            s.btn("^", g.contentR() - 18, areaY, 16, 16, () -> { scrollRow = Math.max(0, scrollRow - 1); s.rebuild(); });
            s.btn("v", g.contentR() - 18, areaY + 18, 16, 16, () -> { scrollRow++; s.rebuild(); });
        }
    }

    private void buildSide(MonitorScreen s, Geo g) {
        List<Side> all = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        all.add(new Side("This PC", 0, 4, 0, loc == Loc.ROOT));
        acts.add(() -> goRoot(s));
        all.add(new Side("Windows (C:)", 1, 2, 0, loc == Loc.C && dir != null && dir.equals(CameraStorage.driveC())));
        acts.add(() -> goC(s));
        addFolders(all, acts, CameraStorage.driveC(), 2, s);
        List<String> cams = cameraNames();
        if (!cams.isEmpty()) {
            all.add(new Side("External device", 1, 2, 0, loc == Loc.EXT));
            acts.add(() -> goExt(s));
            for (String cam : cams) {
                all.add(new Side(cam, 2, 3, 0, loc == Loc.CAM && cam.equals(camName)));
                acts.add(() -> goCam(s, cam));
            }
        }
        int visible = Math.max(1, (g.bodyBottom() - g.bodyTop() - 6) / RH);
        sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, all.size() - visible)));
        for (int i = 0; i < visible && sideScroll + i < all.size(); i++) {
            Side si = all.get(sideScroll + i);
            int y = g.bodyTop() + 4 + i * RH;
            sides.add(new Side(si.label(), si.depth(), si.icon(), y, si.active()));
            ghost(s, g.sideX(), y, g.sideW(), RH, acts.get(sideScroll + i));
        }
        if (all.size() > visible) {
            s.btn("^", g.sideX() + g.sideW() - 30, g.bodyBottom() - 14, 14, 12, () -> { sideScroll = Math.max(0, sideScroll - 1); s.rebuild(); });
            s.btn("v", g.sideX() + g.sideW() - 14, g.bodyBottom() - 14, 14, 12, () -> { sideScroll++; s.rebuild(); });
        }
    }

    /** Folders of drive C, up to 3 levels deep, as an indented list. */
    private void addFolders(List<Side> all, List<Runnable> acts, Path base, int depth, MonitorScreen s) {
        if (depth > 4 || all.size() > 150 || !Files.isDirectory(base)) return;
        try (Stream<Path> st = Files.list(base)) {
            List<Path> dirs = new ArrayList<>(st.filter(Files::isDirectory).toList());
            Collections.sort(dirs);
            for (Path d : dirs) {
                all.add(new Side(d.getFileName().toString(), depth, 0, 0, loc == Loc.C && d.equals(dir)));
                acts.add(() -> goFolder(s, d));
                addFolders(all, acts, d, depth + 1, s);
            }
        } catch (IOException ignored) { }
    }

    private void buildViewer(MonitorScreen s, Geo g) {
        s.btn("Back", g.wx() + 4, g.navY(), 50, 16, () -> {
            if (!photos.isEmpty()) ImageCache.release(photos.get(viewIndex));
            viewing = false; menuOpen = false; s.rebuild();
        });
        s.btn("...", g.wx() + g.ww() - 34, g.navY(), 30, 16, () -> { menuOpen = !menuOpen; s.rebuild(); });
        if (menuOpen) {
            s.btn("Set wallpaper", g.wx() + g.ww() - 138, g.navY() + 20, 134, 16, () -> setWallpaper(s));
            s.btn("Copy", g.wx() + g.ww() - 138, g.navY() + 38, 134, 16, () -> {
                clipboard = photos.get(viewIndex); status = "Copied. Open Windows (C:) and press Paste.";
                menuOpen = false; s.rebuild();
            });
        }
        if (photos.size() > 1) {
            int my = g.navY() + (g.bodyBottom() - g.navY()) / 2 - 12;
            s.btn("<", g.wx() + 6, my, 18, 26, () -> step(s, -1));
            s.btn(">", g.wx() + g.ww() - 24, my, 18, 26, () -> step(s, 1));
        }
    }

    private void step(MonitorScreen s, int d) {
        ImageCache.release(photos.get(viewIndex));
        viewIndex = (viewIndex + d + photos.size()) % photos.size();
        s.rebuild();
    }

    // ================================================================== what is shown
    private List<Item> items(MonitorScreen s) {
        List<Item> out = new ArrayList<>();
        photos.clear();
        switch (loc) {
            case ROOT -> {
                out.add(new Item(Kind.DRIVE, "Windows (C:)", null, () -> goC(s)));
                if (!cameraNames().isEmpty()) out.add(new Item(Kind.DRIVE, "External device", null, () -> goExt(s)));
            }
            case EXT -> {
                for (String n : cameraNames()) out.add(new Item(Kind.CAMERA, n, null, () -> goCam(s, n)));
            }
            case C, CAM -> {
                List<Item> folders = new ArrayList<>(), pics = new ArrayList<>();
                try {
                    Files.createDirectories(dir);
                    try (Stream<Path> st = Files.list(dir)) {
                        List<Path> all = new ArrayList<>(st.toList());
                        Collections.sort(all);
                        for (Path p : all) {
                            String name = p.getFileName().toString();
                            if (Files.isDirectory(p)) {
                                if (loc == Loc.C) folders.add(new Item(Kind.FOLDER, name, p, () -> goFolder(s, p)));
                            } else if (name.toLowerCase().endsWith(".png")) {
                                final int pi = photos.size();
                                photos.add(p);
                                pics.add(new Item(Kind.PHOTO, name, p, () -> {
                                    viewing = true; viewIndex = pi; menuOpen = false; s.rebuild();
                                }));
                            }
                        }
                    }
                } catch (IOException ignored) { }
                out.addAll(folders);
                out.addAll(pics);
            }
        }
        return out;
    }

    /** Only cameras that are in the player's inventory right now. */
    private List<String> cameraNames() {
        List<String> out = new ArrayList<>();
        var p = MinecraftClient.getInstance().player;
        if (p == null) return out;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(ModItems.CAMERA)) {
                String n = CameraManager.nameOf(st);
                if (n != null && !out.contains(n)) out.add(n);
            }
        }
        Collections.sort(out);
        return out;
    }

    // ================================================================== navigation / actions
    private void goRoot(MonitorScreen s) { loc = Loc.ROOT; dir = null; resetView(); s.rebuild(); }
    private void goC(MonitorScreen s) { loc = Loc.C; dir = CameraStorage.driveC(); resetView(); s.rebuild(); }
    private void goExt(MonitorScreen s) { loc = Loc.EXT; dir = null; resetView(); s.rebuild(); }
    private void goCam(MonitorScreen s, String n) { loc = Loc.CAM; camName = n; dir = CameraStorage.cameraDir(n); resetView(); s.rebuild(); }
    private void goFolder(MonitorScreen s, Path d) { loc = Loc.C; dir = d; resetView(); s.rebuild(); }
    private void resetView() { scrollRow = 0; status = ""; naming = false; }

    private void up(MonitorScreen s) {
        resetView();
        switch (loc) {
            case ROOT -> { s.closeFiles(); return; }
            case C -> { if (dir.equals(CameraStorage.driveC())) loc = Loc.ROOT; else dir = dir.getParent(); }
            case CAM -> loc = Loc.EXT;
            case EXT -> loc = Loc.ROOT;
        }
        s.rebuild();
    }

    private void makeFolder(MonitorScreen s) {
        String n = CameraStorage.sanitize(nameField.getText());
        if (n.isEmpty()) status = "Invalid folder name.";
        else {
            try { Files.createDirectories(dir.resolve(n)); status = "Folder created."; }
            catch (IOException e) { status = "Could not create the folder."; }
        }
        naming = false;
        s.rebuild();
    }

    private void paste(MonitorScreen s) {
        try {
            String file = clipboard.getFileName().toString();
            String base = file.toLowerCase().endsWith(".png") ? file.substring(0, file.length() - 4) : file;
            Path target = dir.resolve(file);
            int n = 1;
            while (Files.exists(target)) target = dir.resolve(base + " (" + (n++) + ").png");
            Files.copy(clipboard, target);
            status = "Pasted.";
        } catch (Exception e) {
            status = "Could not paste (the file may be gone).";
        }
        s.rebuild();
    }

    private void setWallpaper(MonitorScreen s) {
        try {
            Files.createDirectories(CameraStorage.root());
            Files.copy(photos.get(viewIndex), CameraStorage.wallpaper(), StandardCopyOption.REPLACE_EXISTING);
            ImageCache.release(CameraStorage.wallpaper());
            s.refreshWallpaper();
            status = "Wallpaper set!";
        } catch (Exception e) {
            status = "Could not set the wallpaper.";
        }
        menuOpen = false;
        s.rebuild();
    }

    // ================================================================== drawing
    void render(MonitorScreen s, DrawContext ctx) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Geo g = geo(s);
        int right = g.wx() + g.ww();

        ctx.fill(g.wx() - 1, g.wy() - 1, right + 1, g.wy() + g.wh() + 1, 0xFF3A3A3A);
        ctx.fill(g.wx(), g.wy(), right, g.wy() + g.wh(), 0xFF1E1E1E);
        ctx.fill(g.wx(), g.wy(), right, g.wy() + 14, 0xFF2A2A2A);
        ctx.drawText(tr, "Files", g.wx() + 6, g.wy() + 3, 0xFFFFFFFF, false);

        if (viewing) { renderViewer(s, ctx, tr, g, right); return; }

        // address bar + toolbar
        ctx.fill(g.wx() + 46, g.navY(), right - 4, g.navY() + 16, 0xFF2D2D2D);
        ctx.drawText(tr, tr.trimToWidth(pathText(), right - g.wx() - 62), g.wx() + 52, g.navY() + 4, 0xFFFFFFFF, false);
        ctx.fill(g.wx(), g.toolY() - 2, right, g.toolY() + 18, 0xFF232323);
        if (!naming && loc != Loc.C) {
            String hint = loc == Loc.ROOT ? "Drives and devices" : loc == Loc.EXT ? "Cameras in your inventory" : "Photos taken with " + camName;
            ctx.drawText(tr, hint, g.wx() + 8, g.toolY() + 4, 0xFF8A8A8A, false);
        }
        if (clipboard != null && !naming) {
            String c = "1 photo copied";
            ctx.drawText(tr, c, right - 8 - tr.getWidth(c), g.toolY() + 4, 0xFF8A8A8A, false);
        }

        // left: folder list
        ctx.fill(g.sideX() - 4, g.bodyTop(), g.sideX() + g.sideW() + 2, g.bodyBottom(), 0xFF191919);
        for (Side si : sides) {
            if (si.active()) {
                ctx.fill(g.sideX(), si.y(), g.sideX() + g.sideW(), si.y() + RH, 0xFF2F3447);
                ctx.fill(g.sideX(), si.y() + 2, g.sideX() + 2, si.y() + RH - 2, 0xFF4DA3FF);
            }
            int ix = g.sideX() + 6 + si.depth() * 7;
            drawSmallIcon(ctx, ix, si.y() + 2, si.icon());
            ctx.drawText(tr, tr.trimToWidth(si.label(), g.sideW() - (ix - g.sideX()) - 16), ix + 13, si.y() + 3, 0xFFEDEDED, false);
        }

        // right: tiles
        ctx.fill(g.contentX(), g.bodyTop(), right - 2, g.bodyBottom(), 0xFF202020);
        for (Tile t : tiles) {
            Item it = t.item();
            int x = t.x(), y = t.y();
            if (t.btn().isHovered()) ctx.fill(x - 1, y - 1, x + CELL_W - 3, y + CELL_H - 3, 0xFF2F2F36);
            int bx = x + (CELL_W - 4 - 68) / 2, by = y + 2;
            if (it.kind() == Kind.PHOTO) {
                ThumbCache.Thumb th = ThumbCache.get(it.path());
                ctx.fill(bx, by, bx + 68, by + 42, 0xFF141414);
                if (th != null) {
                    float sc = Math.min(68f / th.w(), 42f / th.h());
                    int dw = Math.max(1, (int) (th.w() * sc)), dh = Math.max(1, (int) (th.h() * sc));
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED, th.id(), bx + (68 - dw) / 2, by + (42 - dh) / 2,
                            0f, 0f, dw, dh, th.w(), th.h(), th.w(), th.h());
                }
                String[] lines = photoLabel(it.label());
                ctx.drawText(tr, tr.trimToWidth(lines[0], CELL_W - 6), x + 2, y + 46, 0xFFEDEDED, false);
                if (!lines[1].isEmpty()) ctx.drawText(tr, tr.trimToWidth(lines[1], CELL_W - 6), x + 2, y + 55, 0xFF9A9A9A, false);
            } else {
                drawBigIcon(ctx, it.kind(), bx, by);
                String lab = tr.trimToWidth(it.label(), CELL_W - 6);
                ctx.drawText(tr, lab, x + (CELL_W - 4 - tr.getWidth(lab)) / 2, y + 48, 0xFFEDEDED, false);
            }
        }
        if (totalItems == 0) {
            String empty = loc == Loc.EXT ? "No cameras in your inventory."
                    : loc == Loc.CAM ? "No photos yet. Right-click with the camera to take one." : "This folder is empty.";
            ctx.drawText(tr, empty, g.contentX() + 10, g.bodyTop() + 12, 0xFF8A8A8A, false);
        }

        // status bar
        ctx.fill(g.wx(), g.bodyBottom(), right, g.wy() + g.wh(), 0xFF1B1B1B);
        ctx.drawText(tr, totalItems + (totalItems == 1 ? " item" : " items"), g.wx() + 6, g.bodyBottom() + 3, 0xFFB0B0B0, false);
        if (!status.isEmpty()) {
            String st = tr.trimToWidth(status, g.ww() / 2);
            ctx.drawText(tr, st, right - 6 - tr.getWidth(st), g.bodyBottom() + 3, 0xFFFFFF55, false);
        }
    }

    private void renderViewer(MonitorScreen s, DrawContext ctx, TextRenderer tr, Geo g, int right) {
        Path p = photos.isEmpty() ? null : photos.get(viewIndex);
        ctx.fill(g.wx() + 58, g.navY(), right - 38, g.navY() + 16, 0xFF2D2D2D);
        String name = p == null ? "" : p.getFileName().toString();
        ctx.drawText(tr, tr.trimToWidth(name, right - 38 - (g.wx() + 58) - 8), g.wx() + 62, g.navY() + 4, 0xFFFFFFFF, false);
        if (menuOpen) ctx.fill(g.wx() + g.ww() - 140, g.navY() + 18, g.wx() + g.ww() - 2, g.navY() + 56, 0xF0232730);

        ImageCache.Entry e = p == null ? null : ImageCache.get(p);
        int top = g.navY() + 22, bottom = g.bodyBottom() - 2;
        if (e == null) {
            ctx.drawText(tr, "Could not open this image.", g.wx() + 8, top + 4, 0xFFFF7777, false);
        } else {
            int aw = g.ww() - 60, ah = bottom - top;
            float sc = Math.min((float) aw / e.w(), (float) ah / e.h());
            int dw = Math.max(1, (int) (e.w() * sc)), dh = Math.max(1, (int) (e.h() * sc));
            int x = g.wx() + (g.ww() - dw) / 2, y = top + (ah - dh) / 2;
            ctx.fill(x - 1, y - 1, x + dw + 1, y + dh + 1, 0xFF000000);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, e.id(), x, y, 0f, 0f, dw, dh, e.w(), e.h(), e.w(), e.h());
        }
        ctx.fill(g.wx(), g.bodyBottom(), right, g.wy() + g.wh(), 0xFF1B1B1B);
        ctx.drawText(tr, (viewIndex + 1) + " of " + photos.size(), g.wx() + 6, g.bodyBottom() + 3, 0xFFB0B0B0, false);
        if (!status.isEmpty()) {
            String st = tr.trimToWidth(status, g.ww() / 2);
            ctx.drawText(tr, st, right - 6 - tr.getWidth(st), g.bodyBottom() + 3, 0xFFFFFF55, false);
        }
    }

    /** "IMG_2026-10-11_16.16.19.png" -> {"2026-10-11", "16.16.19"} */
    private static String[] photoLabel(String file) {
        String n = file.toLowerCase().endsWith(".png") ? file.substring(0, file.length() - 4) : file;
        if (n.startsWith("IMG_")) n = n.substring(4);
        int i = n.indexOf('_');
        return i > 0 ? new String[]{n.substring(0, i), n.substring(i + 1)} : new String[]{n, ""};
    }

    private static void drawBigIcon(DrawContext ctx, Kind kind, int x, int y) {
        int cx = x + 34, cy = y + 21;
        switch (kind) {
            case FOLDER -> { ctx.fill(cx - 20, cy - 14, cx - 4, cy - 10, 0xFFE0A800); ctx.fill(cx - 20, cy - 10, cx + 20, cy + 14, 0xFFF5C542); }
            case DRIVE -> { ctx.fill(cx - 22, cy - 8, cx + 22, cy + 10, 0xFFB0B7C3); ctx.fill(cx + 12, cy + 3, cx + 18, cy + 6, 0xFF2E7D32); }
            default -> { ctx.fill(cx - 20, cy - 10, cx + 20, cy + 14, 0xFF55555E); ctx.fill(cx - 6, cy - 14, cx + 6, cy - 10, 0xFF55555E); ctx.fill(cx - 8, cy - 6, cx + 8, cy + 10, 0xFF8FB8F0); }
        }
    }

    /** 0 folder, 1 photo, 2 drive, 3 camera, 4 this PC */
    private static void drawSmallIcon(DrawContext ctx, int x, int y, int kind) {
        switch (kind) {
            case 0 -> { ctx.fill(x, y, x + 5, y + 2, 0xFFE0A800); ctx.fill(x, y + 2, x + 10, y + 9, 0xFFF5C542); }
            case 1 -> { ctx.fill(x, y, x + 10, y + 9, 0xFF3B8BEB); ctx.fill(x + 6, y + 1, x + 8, y + 3, 0xFFFFFFFF); }
            case 2 -> { ctx.fill(x, y + 2, x + 10, y + 8, 0xFFB0B7C3); ctx.fill(x + 7, y + 6, x + 9, y + 7, 0xFF2E7D32); }
            case 3 -> { ctx.fill(x, y + 2, x + 10, y + 9, 0xFF55555E); ctx.fill(x + 3, y, x + 6, y + 2, 0xFF55555E); ctx.fill(x + 3, y + 3, x + 7, y + 7, 0xFF8FB8F0); }
            default -> { ctx.fill(x, y, x + 10, y + 7, 0xFF4DA3FF); ctx.fill(x + 3, y + 7, x + 7, y + 9, 0xFF8A8A8A); }
        }
    }

    private String pathText() {
        StringBuilder sb = new StringBuilder("This PC");
        switch (loc) {
            case C -> {
                sb.append("  >  Windows (C:)");
                if (dir != null && !dir.equals(CameraStorage.driveC())) {
                    for (Path part : CameraStorage.driveC().relativize(dir)) sb.append("  >  ").append(part);
                }
            }
            case EXT -> sb.append("  >  External device");
            case CAM -> sb.append("  >  External device  >  ").append(camName);
            default -> { }
        }
        return sb.toString();
    }
}
