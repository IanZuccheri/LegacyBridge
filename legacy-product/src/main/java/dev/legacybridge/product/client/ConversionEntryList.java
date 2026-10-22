package dev.legacybridge.product.client;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Native, narrated, keyboard-focusable rows. Models are bounded inert UI summaries. */
@OnlyIn(Dist.CLIENT)
public final class ConversionEntryList extends ObjectSelectionList<ConversionEntryList.Row> {
    public record RowModel(String key, String title, String subtitle, String detail, String fingerprint, boolean checkable, boolean checked,
                           boolean enabled, int color, Runnable select, Runnable activate) { }

    private boolean rebuilding;

    public ConversionEntryList(Minecraft minecraft, int width, int height, int top, int bottom) {
        super(minecraft, width, height, top, bottom, 50);
        setRenderBackground(false);
        setRenderTopAndBottom(false);
    }

    @Override public int getRowWidth() { return width - 14; }
    @Override protected int getScrollbarPosition() { return getRight() - 6; }

    public Optional<Component> hoveredDescription() { return Optional.ofNullable(getHovered()).map(Row::getNarration); }

    public void rows(List<RowModel> models, String selectedKey) {
        double scroll = getScrollAmount();
        boolean hadFocus = getFocused() != null;
        rebuilding = true;
        setFocused(null);
        clearEntries();
        Row selection = null;
        for (RowModel model : models) {
            Row row = new Row(model);
            addEntry(row);
            if (model.key().equals(selectedKey)) selection = row;
        }
        super.setSelected(selection);
        if (hadFocus && selection != null) setFocused(selection);
        rebuilding = false;
        setScrollAmount(scroll);
    }

    @Override public void setSelected(Row row) {
        Row previous = getSelected();
        super.setSelected(row);
        if (!rebuilding && row != null && row != previous) row.model.select().run();
    }

    @OnlyIn(Dist.CLIENT)
    public final class Row extends ObjectSelectionList.Entry<Row> {
        private final RowModel model;
        private final Checkbox checkbox;

        private Row(RowModel model) {
            this.model = model;
            checkbox = new Checkbox(0, 0, 20, 20, Component.literal(model.title()), model.checked(), false);
            checkbox.active = model.enabled();
        }

        @Override public Component getNarration() {
            return Component.literal(model.title() + ". " + model.subtitle() + ". " + model.detail() + ". " + model.fingerprint() + ". ")
                    .append(Component.translatable(model.checkable()
                            ? !model.enabled() ? "legacybridge.screen.ineligible"
                            : model.checked() ? "legacybridge.screen.checked" : "legacybridge.screen.unchecked"
                            : "legacybridge.screen.row_details"));
        }

        @Override public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            int textX = x + (model.checkable() ? 25 : 4);
            if (model.checkable()) {
                checkbox.setX(x);
                checkbox.setY(y + 5);
                checkbox.setFocused(isFocused());
                checkbox.render(graphics, mouseX, mouseY, partialTick);
            }
            int available = Math.max(20, rowWidth - (textX - x) - 7);
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(model.title(), available), textX, y + 3, model.color());
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(model.subtitle(), available), textX, y + 15, 0xBBBBBB);
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(model.detail(), available), textX, y + 27, 0xAAAAAA);
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(model.fingerprint(), available), textX, y + 39, 0xAAAAAA);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            ConversionEntryList.this.setFocused(this);
            if (model.checkable() && model.enabled() && mouseX < checkbox.getX() + 21) model.activate().run();
            return true;
        }

        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (!model.checkable() || model.enabled()) model.activate().run();
                return true;
            }
            return false;
        }
    }
}
