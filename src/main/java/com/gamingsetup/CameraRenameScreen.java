package com.gamingsetup;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Shift + right-click with a camera: change its name. */
public class CameraRenameScreen extends Screen {
    private final String oldName;
    private TextFieldWidget field;

    public CameraRenameScreen(String oldName) {
        super(Text.literal("Rename camera"));
        this.oldName = oldName;
    }

    @Override
    protected void init() {
        field = new TextFieldWidget(textRenderer, width / 2 - 80, height / 2 - 10, 160, 20, Text.literal("Camera name"));
        field.setMaxLength(24);
        field.setText(oldName);
        addDrawableChild(field);
        setInitialFocus(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> {
            ClientPlayNetworking.send(new CameraRenamePayload(oldName, field.getText()));
            close();
        }).dimensions(width / 2 - 80, height / 2 + 16, 78, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(width / 2 + 2, height / 2 + 16, 78, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(textRenderer, "Rename camera", width / 2 - textRenderer.getWidth("Rename camera") / 2, height / 2 - 30, 0xFFFFFFFF, true);
    }
}
