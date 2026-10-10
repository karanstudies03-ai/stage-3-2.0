package com.gamingsetup;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Files app. This PC > Windows (C:) (folders + photos you copied) and, only when you carry cameras,
 * External device > Camera N (that camera's photos). Photos open in a viewer with a "..." menu.
 */
final class FilesApp {
    private enum Loc { ROOT, C, EXT, CAM }
    private record Row(String label, Path path, boolean photo, Runnable action) {}

    private Loc loc = Loc.ROOT;
    private Path dir;
    private String camName = "";
    private int scroll = 0, lastRows = 0;
    private String status = "";
    private boolean naming = false, viewing = false, menuOpen = false;
    private TextFieldWidget nameField;
    private Path viewPath;
    private static Path clipboard;   // copied photo, survives closing the monitor

    void reset() {
        loc = Loc.ROOT; dir = null; camName = ""; scroll = 0; status = "";
        naming = false; viewing = false; menuOpen = false;
    }

    // ------------------------------------------------------------------ widgets
    void build(MonitorScreen s) {
        if (viewing) { buildViewer(s); return; }

        s.btn("Back", 10, 22, 50, 18, () -> up(s));
        if (loc == Loc.C && !naming) s.btn("New folder", 66, 22, 76, 18, () -> { naming = true; s.rebuild(); });
        if (loc == Loc.C && !naming && clipboard != null) s.btn("Paste", 148, 22, 50, 18, () -> paste(s));
        if (naming) {
            nameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 66, 22, 130, 18, Text.literal("Folder name"));
            nameField.setMaxLength(24);
            s.addField(nameField);
            nameField.setFocused(true);
            s.btn("OK", 202, 22, 34, 18, () -> makeFolder(s));
            s.btn("Cancel", 240, 22, 50, 18, () -> { naming = false; s.rebuild(); });
        }

        List<Row> rows = rows(s);
        lastRows = rows.size();
        int visible = Math.max(1, (s.height - 24 - 54 - 6) / 22);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        int w = Math.min(300, s.width - 140);
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int y = 48 + i * 22;
            s.btn(r.label(), 10, y, w, 20, r.action());
            if (r.photo()) {
                s.btn("Copy", 14 + w, y, 44, 20, () -> {
                    clipboard = r.path();
                    status = "Copied. Open Windows (C:) and press Paste.";
                    s.rebuild();
                });
            }
        }
        if (rows.size() > visible) {
            s.btn("^", s.width - 34, 48, 24, 20, () -> { scroll = Math.max(0, scroll - 1); s.rebuild(); });
            s.btn("v", s.width - 34, 72, 24, 20, () -> { scroll++; s.rebuild(); });
        }
    }

    private void buildViewer(MonitorScreen s) {
        s.btn("Back", 10, 22, 50, 18, () -> {
            if (viewPath != null) ImageCache.release(viewPath);
            viewing = false; menuOpen = false; s.rebuild();
        });
        s.btn("...", s.width - 40, 22, 30, 18, () -> { menuOpen = !menuOpen; s.rebuild(); });
        if (menuOpen) {
            s.btn("Set wallpaper", s.width - 140, 44, 130, 18, () -> setWallpaper(s));
            s.btn("Copy", s.width - 140, 64, 130, 18, () -> {
                clipboard = viewPath; status = "Copied."; menuOpen = false; s.rebuild();
            });
        }
    }

    // ------------------------------------------------------------------ what is listed
    private List<Row> rows(MonitorScreen s) {
        List<Row> rows = new ArrayList<>();
        switch (loc) {
            case ROOT -> {
                rows.add(new Row("Windows (C:)", null, false, () -> {
                    loc = Loc.C; dir = CameraStorage.driveC(); scroll = 0; status = ""; s.rebuild();
                }));
                if (!cameraNames().isEmpty()) {
                    rows.add(new Row("External device", null, false, () -> {
                        loc = Loc.EXT; scroll = 0; status = ""; s.rebuild();
                    }));
                }
            }
            case EXT -> {
                for (String n : cameraNames()) {
                    rows.add(new Row(n, null, false, () -> {
                        loc = Loc.CAM; camName = n; dir = CameraStorage.cameraDir(n); scroll = 0; status = ""; s.rebuild();
                    }));
                }
            }
            case C, CAM -> {
                List<Row> folders = new ArrayList<>(), photos = new ArrayList<>();
                try {
                    Files.createDirectories(dir);
                    try (Stream<Path> st = Files.list(dir)) {
                        List<Path> all = new ArrayList<>(st.toList());
                        Collections.sort(all);
                        for (Path p : all) {
                            String name = p.getFileName().toString();
                            if (Files.isDirectory(p)) {
                                if (loc == Loc.C) folders.add(new Row("[Folder]  " + name, p, false, () -> {
                                    dir = p; scroll = 0; status = ""; s.rebuild();
                                }));
                            } else if (name.toLowerCase().endsWith(".png")) {
                                photos.add(new Row(name, p, true, () -> {
                                    viewing = true; viewPath = p; menuOpen = false; s.rebuild();
                                }));
                            }
                        }
                    }
                } catch (IOException ignored) { }
                rows.addAll(folders);
                rows.addAll(photos);
            }
        }
        return rows;
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

    // ------------------------------------------------------------------ actions
    private void up(MonitorScreen s) {
        scroll = 0; status = "";
        switch (loc) {
            case ROOT -> { s.closeFiles(); return; }
            case C -> {
                if (dir.equals(CameraStorage.driveC())) loc = Loc.ROOT; else dir = dir.getParent();
            }
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
            Files.copy(viewPath, CameraStorage.wallpaper(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ImageCache.release(CameraStorage.wallpaper());
            s.refreshWallpaper();
            status = "Wallpaper set!";
        } catch (Exception e) {
            status = "Could not set the wallpaper.";
        }
        menuOpen = false;
        s.rebuild();
    }

    // ------------------------------------------------------------------ drawing
    void render(MonitorScreen s, DrawContext ctx) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        ctx.drawText(tr, "Files", 10, 8, 0xFFFFFFFF, true);

        if (viewing) {
            if (menuOpen) {
                ctx.fill(s.width - 144, 42, s.width - 6, 86, 0xF0232730);
            }
            ImageCache.Entry e = viewPath == null ? null : ImageCache.get(viewPath);
            if (e == null) {
                ctx.drawText(tr, "Could not open this image.", 10, 52, 0xFFFF7777, false);
            } else {
                int aw = s.width - 20, ah = s.height - 24 - 52 - 6;
                float sc = Math.min((float) aw / e.w(), (float) ah / e.h());
                int dw = Math.max(1, (int) (e.w() * sc)), dh = Math.max(1, (int) (e.h() * sc));
                int x = (s.width - dw) / 2, y = 46;
                ctx.fill(x - 2, y - 2, x + dw + 2, y + dh + 2, 0xFF000000);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, e.id(), x, y, 0f, 0f, dw, dh, e.w(), e.h(), e.w(), e.h());
            }
            ctx.drawText(tr, viewPath == null ? "" : viewPath.getFileName().toString(), 66, 26, 0xFFCCDDFF, false);
        } else {
            ctx.drawText(tr, pathText(), 54, 8, 0xFFCCDDFF, false);
            if (lastRows == 0) {
                String empty = switch (loc) {
                    case EXT -> "No cameras in your inventory.";
                    case CAM -> "No photos yet. Right-click with the camera to take one.";
                    default -> "This folder is empty.";
                };
                ctx.drawText(tr, empty, 10, 54, 0xFFCCCCCC, false);
            }
        }
        if (!status.isEmpty()) ctx.drawText(tr, status, 10, s.height - 36, 0xFFFFFF55, false);
    }

    private String pathText() {
        StringBuilder sb = new StringBuilder("This PC");
        switch (loc) {
            case C -> {
                sb.append(" > Windows (C:)");
                if (dir != null && !dir.equals(CameraStorage.driveC())) {
                    for (Path part : CameraStorage.driveC().relativize(dir)) sb.append(" > ").append(part);
                }
            }
            case EXT -> sb.append(" > External device");
            case CAM -> sb.append(" > External device > ").append(camName);
            default -> { }
        }
        return sb.toString();
    }
}
