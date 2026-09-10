package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.input.HotbarOrder;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** One compact preference with a bounded, scrollable explanation. */
public final class HerziumConfigScreen extends Screen {
    private final Screen parent;
    private int panelX, panelY, panelWidth, panelHeight, textTop, textBottom;
    private int scroll, maxScroll;
    private List<FormattedCharSequence> lines = List.of();
    private boolean titleVisible;

    public HerziumConfigScreen(Screen parent) {
        super(Component.translatable("herzium.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HerziumConfigLayout layout = HerziumConfigLayout.fit(width, height);
        panelWidth = layout.width(); panelHeight = layout.height();
        panelX = layout.x(); panelY = layout.y();
        titleVisible = layout.titleVisible();
        int innerWidth = Math.max(1, panelWidth - layout.padding() * 2);
        int buttonHeight = layout.buttonHeight();
        int doneY = layout.doneY();
        textTop = layout.textTop(); textBottom = layout.textBottom();
        addRenderableWidget(new AnimatedPurpleButton(panelX + layout.padding(), layout.modeY(), innerWidth, buttonHeight,
                orderLabel(), () -> true, button -> {
                    HerziumConfig.get().cycleHotbarOrder();
                    button.setMessage(orderLabel());
                    refreshExplanation();
                }));
        int doneWidth = Math.min(150, innerWidth);
        addRenderableWidget(new AnimatedPurpleButton((width - doneWidth) / 2, doneY, doneWidth,
                buttonHeight, CommonComponents.GUI_DONE, () -> false, button -> onClose()));
        refreshExplanation();
    }

    private Component orderLabel() {
        return Component.translatable("herzium.config.order.label",
                Component.translatable(HerziumConfig.get().hotbarOrder().translationKey()));
    }

    private void refreshExplanation() {
        HotbarOrder order = HerziumConfig.get().hotbarOrder();
        Component text = Component.translatable(order.translationKey() + ".description").append("\n\n")
                .append(Component.translatable(order == HotbarOrder.VANILLA
                        ? "herzium.config.vanilla_note" : "herzium.config.alternate_note"));
        lines = font.split(text, Math.max(1, panelWidth - 28));
        scroll = 0;
        maxScroll = Math.max(0, lines.size() * 11 - (textBottom - textTop));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88200E32);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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
