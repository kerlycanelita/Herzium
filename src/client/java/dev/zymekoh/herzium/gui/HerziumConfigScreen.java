package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.compat.ExternalInputCompatibility;
import dev.zymekoh.herzium.compat.KoHsiumIntegration;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Responsive Mod Menu screen for Herzium's core controls and diagnostics. */
public final class HerziumConfigScreen extends Screen {
    private static final Component TITLE = Component.translatable("herzium.config.title");
    private static final Component COMING_SOON = Component.translatable("herzium.config.subtitle");
    private static final Component IMMEDIATE_HOTBAR_OPTION =
            Component.translatable("herzium.option.immediate_hotbar_selection");
    private static final Component CORE_ACTIVE = Component.translatable("herzium.state.core_active");
    private static final long DIAGNOSTIC_REFRESH_MS = 250L;

    private final Screen parent;
    private Particle[] particles = new Particle[0];
    private List<InfoLine> infoLines = List.of();
    private CoreDiagnostics.Snapshot diagnostics;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int footerHeight;
    private int navX;
    private int navY;
    private int navWidth;
    private int navHeight;
    private int optionCardX;
    private int optionCardY;
    private int optionCardWidth;
    private int optionCardHeight;
    private int optionTextX;
    private int optionTextY;
    private int optionTextWidth;
    private int optionTitleLineLimit;
    private int infoX;
    private int infoY;
    private int infoWidth;
    private int infoHeight;
    private int logicalInfoHeight;
    private int maxScroll;
    private int scrollOffset;
    private int detectedIssueCount;
    private boolean compactLayout;
    private long lastParticleFrame = System.nanoTime() / 1_000_000L;
    private long lastDiagnosticRefresh;

    public HerziumConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.calculateLayout();
        this.initializeParticles();
        this.refreshDiagnostics(true);
        this.addDoneButton();
    }

    private void calculateLayout() {
        int horizontalMargin = this.width < 360 ? 6 : this.width < 560 ? 12 : this.width < 780 ? 28 : 54;
        int verticalMargin = this.height < 240 ? 6 : this.height < 360 ? 12 : this.height < 520 ? 26 : 42;
        int maxPanelWidth = Math.max(1, this.width - horizontalMargin * 2);
        int maxPanelHeight = Math.max(1, this.height - verticalMargin * 2);
        int minPanelWidth = Math.min(280, maxPanelWidth);
        int minPanelHeight = Math.min(170, maxPanelHeight);
        int preferredWidth = this.width < 780 ? maxPanelWidth : 760;
        int preferredHeight = this.height < 460 ? maxPanelHeight : 430;

        this.panelWidth = Mth.clamp(preferredWidth, minPanelWidth, maxPanelWidth);
        this.panelHeight = Mth.clamp(preferredHeight, minPanelHeight, maxPanelHeight);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.headerHeight = this.panelHeight < 230 ? 30 : 38;
        this.footerHeight = this.panelHeight < 230 ? 28 : 38;

        int contentPadding = this.panelWidth < 420 ? 7 : this.panelWidth < 620 ? 10 : 14;
        int bodyX = this.panelX + contentPadding;
        int bodyY = this.panelY + this.headerHeight;
        int bodyWidth = Math.max(1, this.panelWidth - contentPadding * 2);
        int bodyBottom = this.panelY + this.panelHeight - this.footerHeight;
        int bodyHeight = Math.max(1, bodyBottom - bodyY);
        int paneGap = this.panelWidth < 420 ? 5 : 9;

        this.compactLayout = bodyWidth < 420 || bodyHeight < 115;
        if (this.compactLayout) {
            this.navX = bodyX;
            this.navY = bodyY;
            this.navWidth = bodyWidth;
            int minimumInfoHeight = Math.max(1, Math.min(64, bodyHeight / 2));
            this.navHeight = Math.min(92, Math.max(44, bodyHeight - paneGap - minimumInfoHeight));
            int actualGap = Math.min(paneGap, Math.max(0, bodyBottom - this.navY - this.navHeight));
            this.infoX = bodyX;
            this.infoY = this.navY + this.navHeight + actualGap;
            this.infoWidth = bodyWidth;
            this.infoHeight = Math.max(1, bodyBottom - this.infoY);
        } else {
            this.navX = bodyX;
            this.navY = bodyY;
            this.navWidth = Mth.clamp(Math.round(bodyWidth * 0.34F), 154, 270);
            this.navHeight = bodyHeight;
            this.infoX = this.navX + this.navWidth + paneGap;
            this.infoY = bodyY;
            this.infoWidth = Math.max(1, bodyX + bodyWidth - this.infoX);
            this.infoHeight = bodyHeight;
        }

        int sidebarInset = this.compactLayout ? 4 : 7;
        int comingSoonReserve = this.compactLayout ? 18 : 28;
        int preferredCardHeight = this.compactLayout ? 38 : 42;
        this.optionCardX = this.navX + sidebarInset;
        this.optionCardY = this.navY + sidebarInset;
        this.optionCardWidth = Math.max(1, this.navWidth - sidebarInset * 2);
        this.optionCardHeight = Math.min(
                preferredCardHeight,
                Math.max(1, this.navHeight - sidebarInset * 2 - comingSoonReserve));
        int textInset = Math.min(12, Math.max(4, this.optionCardWidth / 28));
        this.optionTextX = this.optionCardX + textInset;
        this.optionTextY = this.optionCardY + 5;
        this.optionTextWidth = Math.max(1, this.optionCardWidth - textInset * 2);
        int availableLines = Math.max(1, (this.optionCardHeight - 16) / 10);
        this.optionTitleLineLimit = Math.min(
                this.font.split(IMMEDIATE_HOTBAR_OPTION, Math.max(24, this.optionTextWidth)).size(),
                Math.min(2, availableLines));
    }

    private void addDoneButton() {
        int doneWidth = Math.min(180, Math.max(1, this.panelWidth - 12));
        int doneHeight = Math.min(this.panelHeight < 205 ? 20 : 24, Math.max(1, this.footerHeight - 4));
        int doneY = this.panelY + this.panelHeight - this.footerHeight
                + Math.max(2, (this.footerHeight - doneHeight) / 2);
        this.addRenderableWidget(new AnimatedPurpleButton(
                this.panelX + (this.panelWidth - doneWidth) / 2,
                doneY,
                doneWidth,
                doneHeight,
                Component.translatable("gui.done"),
                () -> false,
                button -> this.onClose()));
    }

    private void refreshDiagnostics(boolean force) {
        long now = System.nanoTime() / 1_000_000L;
        if (!force && now - this.lastDiagnosticRefresh < DIAGNOSTIC_REFRESH_MS) {
            return;
        }
        this.lastDiagnosticRefresh = now;
        this.diagnostics = CoreDiagnostics.snapshot();
        this.rebuildInfoLines();
    }

    private void rebuildInfoLines() {
        CoreDiagnostics.Snapshot snapshot = this.diagnostics;
        List<InfoLine> lines = new ArrayList<>();
        int textWidth = Math.max(24, this.infoWidth - 16);
        this.detectedIssueCount = 0;

        addHeading(lines, "herzium.config.section.active", textWidth, false);
        addStatus(
                lines,
                "herzium.config.status.hotbar_option",
                CORE_ACTIVE,
                0xFF9BE8B1,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.minecraft_core",
                "MinecraftMixin",
                snapshot.coreFrameHookObserved(),
                snapshot,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.hotbar_visual",
                "HotbarVisualMixin",
                snapshot.hotbarVisualHookObserved(),
                snapshot,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.keyboard_hook",
                "KeyMappingMixin",
                snapshot.keyboardHookObserved() || snapshot.mouseHookObserved(),
                snapshot,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.mouse_hook",
                "MouseHandlerMixin",
                snapshot.wheelHookObserved(),
                snapshot,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.hand_equip",
                "ItemInHandRendererMixin",
                snapshot.handRenderHookObserved(),
                snapshot,
                textWidth);

        Component configState = switch (snapshot.configState()) {
            case LOADING -> Component.translatable("herzium.config.state.loading");
            case HEALTHY -> Component.translatable("herzium.config.state.healthy");
            case READ_FAILED -> Component.translatable("herzium.config.state.read_failed");
            case WRITE_FAILED -> Component.translatable("herzium.config.state.write_failed");
        };
        int configColor = snapshot.configState() == CoreDiagnostics.ConfigState.HEALTHY
                ? 0xFF9BE8B1
                : snapshot.configState() == CoreDiagnostics.ConfigState.LOADING
                        ? 0xFFFFD18A
                        : 0xFFFF9D9D;
        addStatus(lines, "herzium.config.status.config", configState, configColor, textWidth);

        Component inputOwner = KoHsiumIntegration.present()
                ? Component.translatable("herzium.config.state.managed_by_kohsium")
                : Component.translatable("herzium.config.state.managed_by_herzium");
        addStatus(lines, "herzium.config.status.input_owner", inputOwner, 0xFFCBA8ED, textWidth);

        Component inputPipeline;
        int inputPipelineColor;
        if (ExternalInputCompatibility.competingExternalPipelinesPresent()) {
            inputPipeline = Component.translatable("herzium.config.state.external_input_multiple");
            inputPipelineColor = 0xFFFF9D9D;
        } else if (ExternalInputCompatibility.rawInputBufferPresent()) {
            inputPipeline = Component.translatable("herzium.config.state.external_input_raw_buffer");
            inputPipelineColor = 0xFFFFD18A;
        } else if (ExternalInputCompatibility.ixerisPresent()) {
            inputPipeline = Component.translatable("herzium.config.state.external_input_ixeris");
            inputPipelineColor = 0xFFFFD18A;
        } else {
            inputPipeline = Component.translatable("herzium.config.state.external_input_none");
            inputPipelineColor = 0xFF9BE8B1;
        }
        addStatus(lines, "herzium.config.status.input_pipeline", inputPipeline, inputPipelineColor, textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.inventory_render",
                "ContainerFocusRendererMixin",
                snapshot.containerOptimizationHookObserved(),
                snapshot,
                textWidth);

        boolean exordiumPresent = FabricLoader.getInstance().isModLoaded("exordium");
        Component exordiumState;
        int exordiumColor;
        if (!exordiumPresent) {
            exordiumState = Component.translatable("herzium.config.state.not_installed");
            exordiumColor = 0xFFAFA4B7;
        } else if (snapshot.mixinApplied("ExordiumBufferInstanceMixin")) {
            exordiumState = Component.translatable("herzium.config.state.applied");
            exordiumColor = 0xFF9BE8B1;
        } else {
            exordiumState = Component.translatable("herzium.config.state.not_applied");
            exordiumColor = 0xFFFF9D9D;
        }
        addStatus(lines, "herzium.config.status.exordium", exordiumState, exordiumColor, textWidth);

        addWrapped(
                lines,
                Component.translatable(
                        "herzium.config.status.counters",
                        snapshot.previewRequests(),
                        snapshot.vanillaConfirmations(),
                        snapshot.previewMismatches(),
                        snapshot.ambiguousPreviewsResolved()),
                0xFFBFAFCC,
                textWidth);
        addWrapped(
                lines,
                Component.translatable(
                        "herzium.config.status.render_counters",
                        snapshot.containerFramesOptimized(),
                        snapshot.instantEquipFrames(),
                        snapshot.combatEquipFramesPreserved()),
                0xFFBFAFCC,
                textWidth);
        if (snapshot.lastPreviewSlot() >= 0 && snapshot.lastInputSource() != null) {
            Component source = Component.translatable(snapshot.lastInputSource() == CoreDiagnostics.InputSource.KEYBOARD
                    ? "herzium.config.input.keyboard"
                    : "herzium.config.input.mouse");
            addWrapped(
                    lines,
                    Component.translatable(
                            "herzium.config.status.last_preview",
                            snapshot.lastPreviewSlot() + 1,
                            source),
                    0xFFBFAFCC,
                    textWidth);
        } else {
            addWrapped(
                    lines,
                    Component.translatable("herzium.config.status.no_preview"),
                    0xFF9C91A5,
                    textWidth);
        }

        addHeading(lines, "herzium.config.section.how", textWidth, true);
        addParagraph(lines, "herzium.config.how.preview", textWidth);
        addParagraph(lines, "herzium.config.how.vanilla", textWidth);
        addParagraph(lines, "herzium.config.how.hands", textWidth);
        addParagraph(lines, "herzium.config.how.inventory", textWidth);

        addHeading(lines, "herzium.config.section.risks", textWidth, true);
        addParagraph(lines, "herzium.config.risk.performance", textWidth);
        addParagraph(lines, "herzium.config.risk.mixins", textWidth);
        addParagraph(lines, "herzium.config.risk.attestation", textWidth);
        addParagraph(lines, "herzium.config.risk.server", textWidth);

        addHeading(lines, "herzium.config.section.errors", textWidth, true);
        if (snapshot.configState() == CoreDiagnostics.ConfigState.READ_FAILED) {
            addIssue(lines, "herzium.config.issue.config_read", textWidth);
        } else if (snapshot.configState() == CoreDiagnostics.ConfigState.WRITE_FAILED) {
            addIssue(lines, "herzium.config.issue.config_write", textWidth);
        }
        addMissingMixinIssue(lines, snapshot, "HotbarVisualMixin", textWidth);
        addMissingMixinIssue(lines, snapshot, "KeyMappingAccessor", textWidth);
        addMissingMixinIssue(lines, snapshot, "KeyMappingMixin", textWidth);
        addMissingMixinIssue(lines, snapshot, "MouseHandlerMixin", textWidth);
        addMissingMixinIssue(lines, snapshot, "ItemInHandRendererMixin", textWidth);
        addMissingMixinIssue(lines, snapshot, "ContainerFocusRendererMixin", textWidth);
        addMissingMixinIssue(lines, snapshot, "MinecraftMixin", textWidth);
        if (exordiumPresent && !snapshot.mixinApplied("ExordiumBufferInstanceMixin")) {
            addIssue(lines, "herzium.config.issue.exordium", textWidth);
        }
        if (ExternalInputCompatibility.competingExternalPipelinesPresent()) {
            addIssue(lines, "herzium.config.issue.external_input_collision", textWidth);
        } else if (ExternalInputCompatibility.rawInputBufferPresent()
                && !ExternalInputCompatibility.inventoryTweaksPresent()) {
            addWarning(lines, "herzium.config.issue.raw_input_cursor_adapter_missing", textWidth);
        } else if (ExternalInputCompatibility.rawInputBufferPresent()) {
            addWarning(lines, "herzium.config.issue.raw_input_buffer", textWidth);
        } else if (ExternalInputCompatibility.ixerisPresent()) {
            addWarning(lines, "herzium.config.issue.ixeris", textWidth);
        }
        if (this.detectedIssueCount == 0) {
            addWrapped(
                    lines,
                    Component.translatable("herzium.config.issue.none"),
                    0xFF9BE8B1,
                    textWidth);
        }
        addWrapped(
                lines,
                Component.translatable("herzium.config.issue.pending_note"),
                0xFF9C91A5,
                textWidth);

        this.infoLines = List.copyOf(lines);
        this.logicalInfoHeight = 12;
        for (InfoLine line : this.infoLines) {
            this.logicalInfoHeight += line.height();
        }
        this.maxScroll = Math.max(0, this.logicalInfoHeight - Math.max(1, this.infoHeight - 12));
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
    }

    private void addMixinStatus(
            List<InfoLine> lines,
            String labelKey,
            String mixinName,
            boolean observed,
            CoreDiagnostics.Snapshot snapshot,
            int textWidth) {
        boolean applied = snapshot.mixinApplied(mixinName);
        Component state;
        int color;
        if (observed) {
            state = Component.translatable("herzium.config.state.observed");
            color = 0xFF9BE8B1;
        } else if (applied) {
            state = Component.translatable("herzium.config.state.applied_waiting");
            color = 0xFFFFD18A;
        } else {
            state = Component.translatable("herzium.config.state.not_applied");
            color = 0xFFFF9D9D;
        }
        addStatus(lines, labelKey, state, color, textWidth);
    }

    private void addMissingMixinIssue(
            List<InfoLine> lines,
            CoreDiagnostics.Snapshot snapshot,
            String mixinName,
            int textWidth) {
        if (snapshot.mixinApplied(mixinName)) {
            return;
        }
        this.detectedIssueCount++;
        addWrapped(
                lines,
                Component.translatable("herzium.config.issue.mixin_missing", mixinName),
                0xFFFF9D9D,
                textWidth);
    }

    private void addIssue(List<InfoLine> lines, String translationKey, int textWidth) {
        this.detectedIssueCount++;
        addWrapped(lines, Component.translatable(translationKey), 0xFFFF9D9D, textWidth);
    }

    private void addWarning(List<InfoLine> lines, String translationKey, int textWidth) {
        this.detectedIssueCount++;
        addWrapped(lines, Component.translatable(translationKey), 0xFFFFD18A, textWidth);
    }

    private void addHeading(List<InfoLine> lines, String translationKey, int textWidth, boolean spaceBefore) {
        if (spaceBefore) {
            lines.add(new InfoLine(null, 0, 7));
        }
        addWrapped(lines, Component.translatable(translationKey), 0xFFE9C7FF, textWidth);
        lines.add(new InfoLine(null, 0, 2));
    }

    private void addStatus(
            List<InfoLine> lines,
            String labelKey,
            Component state,
            int color,
            int textWidth) {
        addWrapped(
                lines,
                Component.translatable(
                        "herzium.config.status.entry",
                        Component.translatable(labelKey),
                        state),
                color,
                textWidth);
    }

    private void addParagraph(List<InfoLine> lines, String translationKey, int textWidth) {
        addWrapped(lines, Component.translatable(translationKey), 0xFFD6C7DF, textWidth);
        lines.add(new InfoLine(null, 0, 4));
    }

    private void addWrapped(List<InfoLine> lines, Component text, int color, int textWidth) {
        for (FormattedCharSequence line : this.font.split(text, Math.max(24, textWidth))) {
            lines.add(new InfoLine(line, color, 10));
        }
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (this.minecraft.level == null) {
            this.extractPanorama(graphics, partialTick);
        }
        graphics.fillGradient(0, 0, this.width, this.height, 0x68070311, 0x7D12051F);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        long now = System.nanoTime() / 1_000_000L;
        this.refreshDiagnostics(false);
        this.drawParticles(graphics, mouseX, mouseY, now);
        this.drawPanel(graphics, now);
        this.drawSidebar(graphics);
        this.drawOptionCard(graphics);
        this.drawInfoPanel(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, long now) {
        fillRounded(
                graphics,
                this.panelX,
                this.panelY,
                this.panelWidth,
                this.panelHeight,
                0xA4140922,
                0xB20A0412);
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 520.0F);
        drawOutline(
                graphics,
                this.panelX + 1,
                this.panelY + 1,
                this.panelWidth - 2,
                this.panelHeight - 2,
                argb(145 + Math.round(pulse * 45.0F), 155, 62, 222));

        int titleY = this.panelY + Math.max(6, (this.headerHeight - 9) / 2);
        graphics.centeredText(this.font, TITLE, this.panelX + this.panelWidth / 2, titleY, 0xFFF3E6FF);
        int dividerY = this.panelY + this.headerHeight - 1;
        graphics.fill(this.panelX + 8, dividerY, this.panelX + this.panelWidth - 8, dividerY + 1, 0x5D8E43B8);
    }

    private void drawSidebar(GuiGraphicsExtractor graphics) {
        fillRounded(graphics, this.navX, this.navY, this.navWidth, this.navHeight, 0x681C0D2D, 0x7810061C);
        drawOutline(graphics, this.navX, this.navY, this.navWidth, this.navHeight, 0x70733A94);
        int textX = this.navX + 8;
        int textY = this.optionCardY + this.optionCardHeight + (this.compactLayout ? 4 : 10);
        int textWidth = Math.max(24, this.navWidth - 16);
        int lineLimit = Math.max(0, (this.navY + this.navHeight - textY - 4) / 10);
        drawWrapped(
                graphics,
                COMING_SOON,
                textX,
                textY,
                textWidth,
                0xFFCDAFE0,
                Math.min(3, lineLimit));
    }

    private void drawOptionCard(GuiGraphicsExtractor graphics) {
        fillRounded(
                graphics,
                this.optionCardX,
                this.optionCardY,
                this.optionCardWidth,
                this.optionCardHeight,
                0x741F1031,
                0x8611081D);
        drawOutline(
                graphics,
                this.optionCardX,
                this.optionCardY,
                this.optionCardWidth,
                this.optionCardHeight,
                0x8A7135A3);
        int titleLines = drawWrapped(
                graphics,
                IMMEDIATE_HOTBAR_OPTION,
                this.optionTextX,
                this.optionTextY,
                this.optionTextWidth,
                0xFFF1E6FF,
                this.optionTitleLineLimit);
        drawWrapped(
                graphics,
                CORE_ACTIVE,
                this.optionTextX,
                this.optionTextY + titleLines * 10 + 2,
                this.optionTextWidth,
                0xFF9BE8B1,
                1);
    }

    private void drawInfoPanel(GuiGraphicsExtractor graphics) {
        fillRounded(graphics, this.infoX, this.infoY, this.infoWidth, this.infoHeight, 0x68150822, 0x78100619);
        drawOutline(graphics, this.infoX, this.infoY, this.infoWidth, this.infoHeight, 0x68713A91);
        int clipLeft = this.infoX + 1;
        int clipTop = this.infoY + 1;
        int clipRight = this.infoX + this.infoWidth - 1;
        int clipBottom = this.infoY + this.infoHeight - 1;
        if (clipRight <= clipLeft || clipBottom <= clipTop) {
            return;
        }

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int textX = this.infoX + 8;
        int textY = this.infoY + 6 - this.scrollOffset;
        for (InfoLine line : this.infoLines) {
            if (line.text() != null && textY > this.infoY - 10 && textY < this.infoY + this.infoHeight) {
                graphics.text(this.font, line.text(), textX, textY, line.color());
            }
            textY += line.height();
        }
        graphics.disableScissor();

        if (this.maxScroll > 0) {
            int trackX = this.infoX + this.infoWidth - 5;
            int trackTop = this.infoY + 4;
            int trackHeight = Math.max(1, this.infoHeight - 8);
            int thumbHeight = Math.max(8, trackHeight * trackHeight / Math.max(1, trackHeight + this.maxScroll));
            thumbHeight = Math.min(trackHeight, thumbHeight);
            int thumbTravel = Math.max(0, trackHeight - thumbHeight);
            int thumbY = trackTop + thumbTravel * this.scrollOffset / this.maxScroll;
            graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0x5A3C1C4E);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xD6CE75F4);
        }
    }

    private void initializeParticles() {
        long area = Math.max(1L, (long) this.width * this.height);
        int count = Mth.clamp(72 + (int) (area / 15_000L), 72, 110);
        this.particles = new Particle[count];
        Random random = new Random(0x4845525A49554DL ^ (long) this.width << 32 ^ this.height);
        for (int index = 0; index < this.particles.length; index++) {
            this.particles[index] = new Particle(
                    random.nextFloat() * Math.max(1, this.width),
                    random.nextFloat() * Math.max(1, this.height),
                    22.0F + random.nextFloat() * 54.0F,
                    -12.0F + random.nextFloat() * 24.0F,
                    1 + random.nextInt(3),
                    24 + random.nextInt(58));
        }
        this.lastParticleFrame = System.nanoTime() / 1_000_000L;
    }

    private void drawParticles(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            long now) {
        float elapsedSeconds = Mth.clamp((now - this.lastParticleFrame) / 1000.0F, 0.0F, 0.05F);
        this.lastParticleFrame = now;
        float reactionRadius = Mth.clamp(Math.min(this.width, this.height) / 4.5F, 30.0F, 78.0F);
        float radiusSquared = reactionRadius * reactionRadius;

        for (Particle particle : this.particles) {
            float dx = particle.x - mouseX;
            float dy = particle.y - mouseY;
            float distanceSquared = dx * dx + dy * dy;
            float influence = distanceSquared >= radiusSquared
                    ? 0.0F
                    : 1.0F - (float) Math.sqrt(distanceSquared) / reactionRadius;
            if (influence > 0.0F) {
                float inverseDistance = 1.0F / Math.max(1.0F, (float) Math.sqrt(distanceSquared));
                particle.x += dx * inverseDistance * influence * 18.0F * elapsedSeconds;
                particle.y += dy * inverseDistance * influence * 18.0F * elapsedSeconds;
            }
            particle.x += particle.speedX * elapsedSeconds;
            particle.y += particle.speedY * elapsedSeconds;
            if (particle.x > this.width + 10.0F) {
                particle.x = -10.0F;
            }
            if (particle.y < -8.0F) {
                particle.y = this.height + 8.0F;
            } else if (particle.y > this.height + 8.0F) {
                particle.y = -8.0F;
            }

            int alpha = Mth.clamp(particle.baseAlpha + Math.round(influence * 74.0F), 8, 150);
            int x = Math.round(particle.x);
            int y = Math.round(particle.y);
            int trail = Math.max(2, particle.size * 2);
            graphics.fill(
                    x - trail,
                    y,
                    x,
                    y + particle.size,
                    argb(Math.max(6, alpha / 3), 136, 55, 214));
            graphics.fill(
                    x,
                    y,
                    x + particle.size,
                    y + particle.size,
                    argb(alpha, 213, 132, 250));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.infoX
                && mouseX <= this.infoX + this.infoWidth
                && mouseY >= this.infoY
                && mouseY <= this.infoY + this.infoHeight
                && this.maxScroll > 0) {
            this.scrollOffset = Mth.clamp(
                    this.scrollOffset - (int) Math.round(scrollY * 22.0),
                    0,
                    this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return this.parent != null && this.parent.isPauseScreen();
    }

    private int drawWrapped(
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int width,
            int color,
            int maxLines) {
        List<FormattedCharSequence> lines = this.font.split(text, Math.max(24, width));
        int count = Math.min(lines.size(), Math.max(0, maxLines));
        for (int index = 0; index < count; index++) {
            graphics.text(this.font, lines.get(index), x, y + index * 10, color);
        }
        return count;
    }

    private static void fillRounded(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int topColor,
            int bottomColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int radius = Math.min(3, Math.min(width / 2, height / 2));
        graphics.fillGradient(x + radius, y, x + width - radius, y + height, topColor, bottomColor);
        graphics.fillGradient(x, y + radius, x + width, y + height - radius, topColor, bottomColor);
    }

    private static void drawOutline(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color) {
        if (width <= 1 || height <= 1) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private record InfoLine(FormattedCharSequence text, int color, int height) {
    }

    private static final class Particle {
        private float x;
        private float y;
        private final float speedX;
        private final float speedY;
        private final int size;
        private final int baseAlpha;

        private Particle(
                float x,
                float y,
                float speedX,
                float speedY,
                int size,
                int baseAlpha) {
            this.x = x;
            this.y = y;
            this.speedX = speedX;
            this.speedY = speedY;
            this.size = size;
            this.baseAlpha = baseAlpha;
        }
    }
}
