package com.gamingsetup;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Random;

/** Fullscreen monitor. Esc closes it (default Screen behaviour). */
public class MonitorScreen extends Screen {
    private enum Page { DESKTOP, FOOD, TTT, CLICKER }

    private Page page = Page.DESKTOP;

    // food app
    private final int[] qty = new int[FoodMenu.ENTRIES.size()];
    private String foodStatus = "";

    // tic-tac-toe
    private final char[] board = new char[9];
    private boolean tttOver = false;
    private String tttStatus = "Your turn (X)";

    // click game
    private int score = 0, best = 0, cx = -1, cy = -1;
    private final Random rng = new Random();

    public MonitorScreen() { super(Text.literal("Monitor")); }

    @Override
    public boolean shouldPause() { return false; }   // world keeps running behind the screen

    @Override
    protected void init() { build(); }

    private void setPage(Page p) { page = p; rebuild(); }

    private void rebuild() { clearChildren(); build(); }

    private ButtonWidget btn(String label, int x, int y, int w, int h, Runnable action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), x0 -> action.run()).dimensions(x, y, w, h).build();
        addDrawableChild(b);
        return b;
    }

    // ------------------------------------------------------------------ pages
    private void build() {
        switch (page) {
            case DESKTOP -> {
                btn("Food Order", 30, 50, 110, 20, () -> setPage(Page.FOOD));
                btn("Tic-Tac-Toe", 30, 80, 110, 20, () -> { resetTtt(); setPage(Page.TTT); });
                btn("Click Game", 30, 110, 110, 20, () -> { score = 0; cx = -1; setPage(Page.CLICKER); });
                btn("Shut down", width - 90, height - 22, 86, 20, this::close);
            }
            case FOOD -> buildFood();
            case TTT -> buildTtt();
            case CLICKER -> buildClicker();
        }
    }

    // ------------------------------------------------------------------ food order
    private void buildFood() {
        int rowsPerCol = (FoodMenu.ENTRIES.size() + 1) / 2;
        int colW = (width - 30) / 2;
        for (int i = 0; i < FoodMenu.ENTRIES.size(); i++) {
            final int idx = i;
            int col = i / rowsPerCol, row = i % rowsPerCol;
            int x0 = 10 + col * (colW + 10);
            int y = 36 + row * 26;
            int bx = x0 + colW - 112;
            btn("-", bx, y, 18, 18, () -> qty[idx] = Math.max(0, qty[idx] - 1));
            btn("+", bx + 50, y, 18, 18, () -> qty[idx] = Math.min(64, qty[idx] + 1));
            btn("64", bx + 70, y, 24, 18, () -> qty[idx] = 64);
            btn("0", bx + 96, y, 16, 18, () -> qty[idx] = 0);
        }
        int by = height - 46;
        btn("Back", 10, by, 60, 20, () -> setPage(Page.DESKTOP));
        btn("Place Order", width - 130, by, 120, 20, this::placeOrder);
    }

    private void placeOrder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < qty.length; i++) {
            if (qty[i] > 0) {
                if (sb.length() > 0) sb.append(',');
                sb.append(FoodMenu.ENTRIES.get(i).id()).append(':').append(qty[i]);
            }
        }
        if (sb.length() == 0) { foodStatus = "Your cart is empty."; return; }
        ClientPlayNetworking.send(new OrderPayload(sb.toString()));
        java.util.Arrays.fill(qty, 0);
        foodStatus = "Order placed! Wait for the delivery villager.";
    }

    // ------------------------------------------------------------------ tic-tac-toe
    private void resetTtt() {
        java.util.Arrays.fill(board, ' ');
        tttOver = false;
        tttStatus = "Your turn (X)";
    }

    private void buildTtt() {
        int size = 40, gx = width / 2 - 60, gy = 60;
        for (int i = 0; i < 9; i++) {
            final int idx = i;
            String label = board[i] == ' ' || board[i] == 0 ? "" : String.valueOf(board[i]);
            btn(label, gx + (i % 3) * (size + 2), gy + (i / 3) * (size + 2), size, size, () -> tttClick(idx));
        }
        btn("New game", width / 2 - 100, height - 46, 90, 20, () -> { resetTtt(); rebuild(); });
        btn("Back", width / 2 + 10, height - 46, 90, 20, () -> setPage(Page.DESKTOP));
    }

    private void tttClick(int i) {
        if (tttOver || board[i] != ' ') return;
        board[i] = 'X';
        if (wins('X')) { tttStatus = "You win!"; tttOver = true; }
        else if (full()) { tttStatus = "Draw!"; tttOver = true; }
        else {
            board[cpuMove()] = 'O';
            if (wins('O')) { tttStatus = "Computer wins!"; tttOver = true; }
            else if (full()) { tttStatus = "Draw!"; tttOver = true; }
        }
        rebuild();
    }

    private static final int[][] LINES = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};

    private boolean wins(char p) {
        for (int[] l : LINES) if (board[l[0]] == p && board[l[1]] == p && board[l[2]] == p) return true;
        return false;
    }

    private boolean full() { for (char c : board) if (c == ' ') return false; return true; }

    private int cpuMove() {
        for (char who : new char[]{'O', 'X'}) {           // win first, then block
            for (int i = 0; i < 9; i++) {
                if (board[i] != ' ') continue;
                board[i] = who;
                boolean w = wins(who);
                board[i] = ' ';
                if (w) return i;
            }
        }
        if (board[4] == ' ') return 4;
        int i;
        do { i = rng.nextInt(9); } while (board[i] != ' ');
        return i;
    }

    // ------------------------------------------------------------------ click game
    private void buildClicker() {
        if (cx < 0) moveTarget();
        btn("Click!", cx, cy, 60, 20, () -> {
            score++;
            best = Math.max(best, score);
            moveTarget();
            rebuild();
        });
        btn("Back", 10, height - 46, 60, 20, () -> setPage(Page.DESKTOP));
    }

    private void moveTarget() {
        cx = 20 + rng.nextInt(Math.max(1, width - 100));
        cy = 50 + rng.nextInt(Math.max(1, height - 120));
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // no default blur/dim: we paint our own desktop in render()
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xFF0B3D91, 0xFF1E90FF);
        ctx.fill(0, height - 24, width, height, 0xFF202028);
        ctx.drawText(textRenderer, "Minecraft OS", 8, height - 16, 0xFFFFFFFF, false);

        switch (page) {
            case DESKTOP -> {
                ctx.drawText(textRenderer, "My Desktop", 30, 30, 0xFFFFFFFF, true);
                ctx.drawText(textRenderer, "Press Esc to leave the monitor", 30, height - 50, 0xFFCCDDFF, false);
            }
            case FOOD -> renderFood(ctx);
            case TTT -> {
                ctx.drawText(textRenderer, "Tic-Tac-Toe", 10, 10, 0xFFFFFFFF, true);
                ctx.drawText(textRenderer, tttStatus, width / 2 - textRenderer.getWidth(tttStatus) / 2, 40, 0xFFFFFF55, false);
            }
            case CLICKER -> {
                ctx.drawText(textRenderer, "Click Game", 10, 10, 0xFFFFFFFF, true);
                ctx.drawText(textRenderer, "Score: " + score + "   Best: " + best, 10, 28, 0xFFFFFF55, false);
            }
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderFood(DrawContext ctx) {
        ctx.drawText(textRenderer, "Food Order  (price in emeralds)", 10, 10, 0xFFFFFFFF, true);
        int rowsPerCol = (FoodMenu.ENTRIES.size() + 1) / 2;
        int colW = (width - 30) / 2;
        int total = 0;
        for (int i = 0; i < FoodMenu.ENTRIES.size(); i++) {
            FoodMenu.Entry e = FoodMenu.ENTRIES.get(i);
            int col = i / rowsPerCol, row = i % rowsPerCol;
            int x0 = 10 + col * (colW + 10);
            int y = 36 + row * 26;
            ctx.fill(x0 - 2, y - 3, x0 + colW, y + 21, 0x66000000);
            ctx.drawItem(new ItemStack(e.item()), x0, y);
            String name = new ItemStack(e.item()).getName().getString();
            ctx.drawText(textRenderer, name, x0 + 20, y, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, e.price() + " emerald" + (e.price() == 1 ? "" : "s"), x0 + 20, y + 10, 0xFF55FF55, false);
            int bx = x0 + colW - 112;
            String q = String.valueOf(qty[i]);
            ctx.drawText(textRenderer, q, bx + 18 + (32 - textRenderer.getWidth(q)) / 2, y + 5, 0xFFFFFF55, false);
            total += e.price() * qty[i];
        }
        ctx.drawText(textRenderer, "Total: " + total + " emeralds", width / 2 - 40, height - 40, 0xFF55FF55, true);
        if (!foodStatus.isEmpty()) ctx.drawText(textRenderer, foodStatus, width / 2 - textRenderer.getWidth(foodStatus) / 2, height - 54, 0xFFFFFF55, false);
    }
}
