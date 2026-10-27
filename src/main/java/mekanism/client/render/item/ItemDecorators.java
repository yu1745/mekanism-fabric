package mekanism.client.render.item;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric port of NeoForge's item decoration surface ({@code IItemDecorator} +
 * {@code RegisterItemDecorationsEvent}): the decorators register per item here and the GuiGraphics mixin invokes
 * the registry after vanilla's count/durability decorations.
 */
public final class ItemDecorators {

    /** The port stand-in for {@code IItemDecorator}: return true when something was drawn. */
    public interface ItemDecorator {

        boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset);
    }

    private static final Map<Item, ItemDecorator> DECORATORS = new HashMap<>();

    private ItemDecorators() {
    }

    public static void register(Item item, ItemDecorator decorator) {
        DECORATORS.put(item, decorator);
    }

    public static void render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        if (!stack.isEmpty()) {
            ItemDecorator decorator = DECORATORS.get(stack.getItem());
            if (decorator != null) {
                decorator.render(guiGraphics, font, stack, xOffset, yOffset);
            }
        }
    }
}
