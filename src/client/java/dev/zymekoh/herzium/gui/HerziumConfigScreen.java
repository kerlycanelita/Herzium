package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.input.HotbarOrder;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The order, the three burst options and a bounded, scrollable explanation of whichever control
 * the pointer or keyboard focus is on (the order when none is).
 */
public final class HerziumConfigScreen extends Screen {
    private enum Topic { ORDER, SPLIT, STRICT, SYNC }

    private final Screen parent;
    private int panelX, panelY, panelWidth, panelHeight, textTop, textBottom;
    private int scroll, maxScroll;
    private List<FormattedCharSequence> lines = List.of();
    private boolean titleVisible;
    private Topic topic = Topic.ORDER;
    private AnimatedPurpleButton orderButton;
    private final AnimatedPurpleButton[] options = new AnimatedPurpleButton[HerziumConfigLayout.OPTIONS];

    public HerziumConfigScreen(Screen parent) {
        super(Component.translatable("herzium.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int labelWidth = 0;
        for (Topic option : new Topic[] {Topic.SPLIT, Topic.STRICT, Topic.SYNC}) {
            labelWidth = Math.max(labelWidth, font.width(Component.translatable(optionKey(option))) + 26);
        }
        HerziumConfigLayout layout = HerziumConfigLayout.fit(width, height, labelWidth);
        panelWidth = layout.width(); panelHeight = layout.height();
        panelX = layout.x(); panelY = layout.y();
        titleVisible = layout.titleVisible();
        int innerWidth = Math.max(1, panelWidth - layout.padding() * 2);
        int buttonHeight = layout.buttonHeight();
        textTop = layout.textTop(); textBottom = layout.textBottom();
        orderButton = addRenderableWidget(new AnimatedPurpleButton(panelX + layout.padding(), layout.modeY(), innerWidth,
                buttonHeight, orderLabel(), () -> true, button -> {
                    HerziumConfig.get().cycleHotbarOrder();
                    button.setMessage(orderLabel());
                    refreshOptions();
                    explain(Topic.ORDER);
                }));
        Topic[] topics = {Topic.SPLIT, Topic.STRICT, Topic.SYNC};
        for (int index = 0; index < topics.length; index++) {
            Topic option = topics[index];
            int[] bounds = layout.option(index);
            options[index] = addRenderableWidget(new AnimatedPurpleButton(bounds[0], bounds[1], bounds[2], bounds[3],
                    Component.translatable(optionKey(option)), selected(option), button -> {
                        toggle(option);
                        explain(option);
                    }, true));
        }
        int doneWidth = Math.min(150, innerWidth);
        addRenderableWidget(new AnimatedPurpleButton((width - doneWidth) / 2, layout.doneY(), doneWidth,
                buttonHeight, CommonComponents.GUI_DONE, () -> false, button -> onClose()));
        refreshOptions();
        explain(topic);
    }

    private static String optionKey(Topic option) {
        return switch (option) {
            case SPLIT -> "herzium.config.option.split";
            case STRICT -> "herzium.config.option.strict";
            case SYNC -> "herzium.config.option.sync";
            case ORDER -> "herzium.config.order.label";
        };
    }

    private static BooleanSupplier selected(Topic option) {
        return switch (option) {
            case SPLIT -> () -> HerziumConfig.get().splitBursts();
            case STRICT -> () -> HerziumConfig.get().strictActionOrder();
            case SYNC -> () -> HerziumConfig.get().offhandSync();
            case ORDER -> () -> true;
        };
    }

    private static void toggle(Topic option) {
        HerziumConfig config = HerziumConfig.get();
        switch (option) {
            case SPLIT -> config.toggleSplitBursts();
            case STRICT -> config.toggleStrictActionOrder();
            case SYNC -> config.toggleOffhandSync();
            case ORDER -> { }
        }
    }

    /** Split bursts and strict attacks only mean something in the Herzium order; sync in any but Vanilla. */
    private static boolean applies(Topic option, HotbarOrder order) {
        return switch (option) {
            case SPLIT, STRICT -> order == HotbarOrder.HERZIUM;
            case SYNC -> order != HotbarOrder.VANILLA;
            case ORDER -> true;
        };
    }

    private void refreshOptions() {
        HotbarOrder order = HerziumConfig.get().hotbarOrder();
        Topic[] topics = {Topic.SPLIT, Topic.STRICT, Topic.SYNC};
        for (int index = 0; index < topics.length; index++) {
            if (options[index] != null) options[index].active = applies(topics[index], order);
        }
    }

    private Component orderLabel() {
        return Component.translatable("herzium.config.order.label",
                Component.translatable(HerziumConfig.get().hotbarOrder().translationKey()));
    }

    private void explain(Topic next) {
        topic = next;
        HotbarOrder order = HerziumConfig.get().hotbarOrder();
        Component text;
        if (next == Topic.ORDER) {
            text = Component.translatable(order.translationKey() + ".description").append("\n\n")
                    .append(Component.translatable(order == HotbarOrder.VANILLA
                            ? "herzium.config.vanilla_note" : "herzium.config.alternate_note"));
        } else {
            text = Component.translatable(optionKey(next) + ".description");
            if (!applies(next, order)) {
                text = Component.translatable(next == Topic.SYNC
                        ? "herzium.config.option.not_vanilla" : "herzium.config.option.only_herzium")
                        .append("\n\n").append(text);
            }
        }
        lines = font.split(text, Math.max(1, panelWidth - 28));
        scroll = 0;
        maxScroll = Math.max(0, lines.size() * 11 - (textBottom - textTop));
    }

    /** The explanation follows the control under the pointer, or the focused one. */
    private void followPointer() {
        Topic hovered = null;
        if (orderButton != null && orderButton.isHoveredOrFocused()) hovered = Topic.ORDER;
        Topic[] topics = {Topic.SPLIT, Topic.STRICT, Topic.SYNC};
        for (int index = 0; index < topics.length; index++) {
            if (options[index] != null && options[index].isHoveredOrFocused()) hovered = topics[index];
        }
        if (hovered != null && hovered != topic) explain(hovered);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88200E32);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        followPointer();
        HerziumTheme.fillRounded(graphics, panelX, panelY, panelWidth, panelHeight, 0xDD251331, 0xDD120A1F);
        if (titleVisible) graphics.centeredText(font, title, width / 2, panelY + 9, 0xFFF1E7FF);
        if (textBottom > textTop) {
            graphics.enableScissor(panelX + 10, textTop, panelX + panelWidth - 10, textBottom);
            for (int i = 0; i < lines.size(); i++) {
                graphics.text(font, lines.get(i), panelX + 12, textTop + i * 11 - scroll, 0xFFD7C8E4);
            }
            graphics.disableScissor();
            if (maxScroll > 0) {
                int trackHeight = textBottom - textTop;
                int thumbHeight = Math.min(trackHeight, Math.max(6, trackHeight * trackHeight / (lines.size() * 11)));
                int y = textTop + scroll * (trackHeight - thumbHeight) / maxScroll;
                graphics.fill(panelX + panelWidth - 7, y, panelX + panelWidth - 5, y + thumbHeight, 0xFFB975ED);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= textTop && mouseY < textBottom) {
            scroll = Mth.clamp(scroll - (int) Math.round(vertical * 22), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
