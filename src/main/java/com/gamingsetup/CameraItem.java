package com.gamingsetup;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Right-click: take a photo. Shift + right-click: rename the camera. */
public class CameraItem extends Item {
    public CameraItem(Settings settings) { super(settings); }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) {
            ItemStack stack = user.getStackInHand(hand);
            Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
            if (name == null) {
                user.sendMessage(Text.literal("The camera is starting up, try again in a second."), true);
            } else if (user.isSneaking()) {
                ClientHooks.openRename(name.getString());
            } else {
                ClientHooks.takePhoto(name.getString());
            }
        }
        return ActionResult.SUCCESS;
    }
}
