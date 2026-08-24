package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.compat.ExternalInputCompatibility;
import dev.zymekoh.herzium.compat.KoHsiumIntegration;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Mod Menu screen: Herzium's visual feature toggles and the diagnostics panel
 * that lets the mod be checked without reading a log.
 *
 * <p>The left pane is deliberately not scrollable. Minecraft widgets are not
 * clipped by a scissor, so scrolling a pane that contains buttons would draw
 * them outside the panel. The rows are laid out to fit the space instead,
 * shedding description lines before they shed the controls themselves; only the
 * diagnostics pane, which is pure text, scrolls.</p>
 */
public final class HerziumConfigScreen extends Screen {
    private static final Component TITLE = Component.translatable("herzium.config.title");
    private static final Component OPTIONS_HEADING =
            Component.translatable("herzium.config.section.options");
    private static final Component STATUS_HEADING =
            Component.translatable("herzium.config.section.overview");
    private static final Component SUBTITLE =
            Component.translatable("herzium.config.subtitle");
    private static final long DIAGNOSTIC_REFRESH_MS = 250L;
    private static final int OPTION_COUNT = 2;
    private static final int MAX_ROW_HEIGHT = 62;
    private static final int MAX_DESCRIPTION_LINES = 3;

    private final Screen parent;
    private final HerziumTheme.ParticleField particles = new HerziumTheme.ParticleField();
    private final OptionRow[] optionRows = new OptionRow[OPTION_COUNT];
    private List<InfoLine> infoLines = List.of();
    private CoreDiagnostics.Snapshot diagnostics;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int footerHeight;
    private int optionsX;
    private int optionsY;
    private int optionsWidth;
    private int optionsHeight;
    private int optionsInset;
    private int optionsHeadingHeight;
    private int rowHeight;
    private int rowGap;
    private int buttonWidth;
    private int buttonHeight;
    private int infoX;
    private int infoY;
    private int infoWidth;
    private int infoHeight;
    private int infoHeaderHeight;
    private int logicalInfoHeight;
    private int maxScroll;
    private int scrollOffset;
    private int detectedIssueCount;
    private boolean compactLayout;
    private long lastDiagnosticRefresh;

    public HerziumConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.calculateLayout();
        this.particles.resize(this.width, this.height);
        this.refreshDiagnostics(true);
        this.addOptionToggles();
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
        this.headerHeight = this.panelHeight < 230 ? 34 : 48;
        this.footerHeight = this.panelHeight < 230 ? 30 : 42;

        int contentPadding = this.panelWidth < 420 ? 7 : this.panelWidth < 620 ? 10 : 14;
        int bodyX = this.panelX + contentPadding;
        int bodyY = this.panelY + this.headerHeight;
        int bodyWidth = Math.max(1, this.panelWidth - contentPadding * 2);
        int bodyBottom = this.panelY + this.panelHeight - this.footerHeight;
        int bodyHeight = Math.max(1, bodyBottom - bodyY);
        int paneGap = this.panelWidth < 420 ? 6 : 12;

        // Two columns wherever they fit. The stacked fallback puts the status
        // list under the options, which pushes it into a short strip that has
        // to be scrolled a line at a time; as a side column it gets the panel's
        // full height instead. Only a genuinely tiny window falls back.
        this.compactLayout = bodyWidth < 250 || bodyHeight < 130;
        if (this.compactLayout) {
            // Stacked. The options pane takes what it needs and no more, and
            // never so much that the diagnostics pane disappears entirely.
            this.optionsX = bodyX;
            this.optionsY = bodyY;
            this.optionsWidth = bodyWidth;
            int minimumInfoHeight = Math.max(1, Math.min(44, bodyHeight / 3));
            // Never taller than the rows actually need. Rows stop growing
            // at MAX_ROW_HEIGHT, so asking for a fixed fraction of the body left
            // dead space under the last row on tall windows; that space is worth
            // more to the diagnostics pane, which always has more to show.
            int neededHeight = 8 * 2 + 24 + MAX_ROW_HEIGHT * OPTION_COUNT + 8 * (OPTION_COUNT - 1);
            int preferred = Math.min(neededHeight, Math.max(1, Math.round(bodyHeight * 0.62F)));
            this.optionsHeight = Mth.clamp(
                    preferred,
                    1,
                    Math.max(1, bodyHeight - paneGap - minimumInfoHeight));
            int actualGap = Math.min(paneGap, Math.max(0, bodyBottom - this.optionsY - this.optionsHeight));
            this.infoX = bodyX;
            this.infoY = this.optionsY + this.optionsHeight + actualGap;
            this.infoWidth = bodyWidth;
            this.infoHeight = Math.max(1, bodyBottom - this.infoY);
        } else {
            this.optionsX = bodyX;
            this.optionsY = bodyY;
            // The status column is the one that has to hold aligned rows of
            // label plus value, so it gets a guaranteed share: options never
            // grow past what would leave it less than 40% of the body.
            int optionsUpperBound = Math.max(
                    1,
                    bodyWidth - paneGap - Math.max(1, Math.round(bodyWidth * 0.40F)));
            this.optionsWidth = Mth.clamp(
                    Math.round(bodyWidth * 0.46F),
                    1,
                    Math.max(1, Math.min(330, optionsUpperBound)));
            this.optionsHeight = bodyHeight;
            this.infoX = this.optionsX + this.optionsWidth + paneGap;
            this.infoY = bodyY;
            this.infoWidth = Math.max(1, bodyX + bodyWidth - this.infoX);
            this.infoHeight = bodyHeight;
        }

        this.optionsInset = this.compactLayout ? 6 : this.optionsWidth < 210 ? 7 : 10;
        this.optionsHeadingHeight = this.optionsHeight >= 74 ? 24 : 0;
        this.infoHeaderHeight = this.infoHeight >= 54 ? 28 : 0;
        this.rowGap = this.compactLayout ? 5 : 8;

        int rowsArea = Math.max(
                3,
                this.optionsHeight - this.optionsInset * 2 - this.optionsHeadingHeight
                        - this.rowGap * (OPTION_COUNT - 1));
        this.rowHeight = Mth.clamp(rowsArea / OPTION_COUNT, 1, MAX_ROW_HEIGHT);

        int rowWidth = Math.max(1, this.optionsWidth - this.optionsInset * 2);
        // A shorter toggle in the stacked fallback buys back the nine pixels a
        // description line needs.
        this.buttonHeight = Mth.clamp(this.rowHeight - 4, 8, this.compactLayout ? 16 : 18);
        this.buttonWidth = Mth.clamp(rowWidth / 4, 32, 50);
        this.buildOptionRows(rowWidth);
    }

    private void buildOptionRows(int rowWidth) {
        int rowX = this.optionsX + this.optionsInset;
        int firstRowY = this.optionsY + this.optionsInset + this.optionsHeadingHeight;
        // The padding inside a row scales with the row, so a narrow side column
        // spends its pixels on the label instead of on empty margins.
        int rowInset = Mth.clamp(rowWidth / 16, 4, 10);
        int labelWidth = Math.max(1, rowWidth - rowInset * 2 - this.buttonWidth - 5);
        int descriptionWidth = Math.max(24, rowWidth - rowInset * 2);

        this.optionRows[0] = new OptionRow(
                "herzium.option.instant_equip",
                () -> HerziumConfig.get().instantEquip(),
                enabled -> HerziumConfig.get().setInstantEquip(enabled));
        this.optionRows[1] = new OptionRow(
                "herzium.option.hotbar_preview",
                () -> HerziumConfig.get().hotbarPreview(),
                enabled -> HerziumConfig.get().setHotbarPreview(enabled));

        for (int index = 0; index < OPTION_COUNT; index++) {
            OptionRow row = this.optionRows[index];
            row.x = rowX;
            row.y = firstRowY + index * (this.rowHeight + this.rowGap);
            row.width = rowWidth;
            row.height = this.rowHeight;
            row.textX = rowX + rowInset;
            row.textWidth = labelWidth;
            row.buttonX = rowX + rowWidth - rowInset - this.buttonWidth;

            // A name that does not fit beside the toggle wraps to a second line
            // rather than being cut. The description then starts below whichever
            // of the two -- label block or toggle -- is taller.
            row.labelLines = this.font.split(
                    Component.translatable(row.translationKey),
                    Math.max(24, labelWidth));
            int labelLineCount = Mth.clamp(row.labelLines.size(), 1, 2);
            row.labelBlockHeight = Math.max(this.buttonHeight + 4, labelLineCount * 10 + 2);
            row.buttonY = row.y + Math.max(1, (row.labelBlockHeight - this.buttonHeight) / 2);
            row.descriptionLines = this.font.split(
                    Component.translatable(row.translationKey + ".description"),
                    descriptionWidth);
            // Whatever is left under the label block becomes description; when
            // the row is too short for even one line, the control still stands.
            row.descriptionLimit = Mth.clamp(
                    (this.rowHeight - row.labelBlockHeight - 3) / 9,
                    0,
                    MAX_DESCRIPTION_LINES);
        }
    }

    private void addOptionToggles() {
        for (OptionRow row : this.optionRows) {
            AnimatedPurpleButton toggle = new AnimatedPurpleButton(
                    row.buttonX,
                    row.buttonY,
                    this.buttonWidth,
                    this.buttonHeight,
                    stateLabel(row.getter.getAsBoolean()),
                    row.getter,
                    button -> {
                        boolean next = !row.getter.getAsBoolean();
                        row.setter.accept(next);
                        button.setMessage(stateLabel(next));
                        // The diagnostics pane reports these options, so it has
                        // to be rebuilt now rather than up to 250 ms later.
                        this.refreshDiagnostics(true);
                    },
                    true);
            // The stacked layout on a small window can end up with no room for
            // the description under the label; the tooltip keeps it reachable.
            toggle.setTooltip(Tooltip.create(
                    Component.translatable(row.translationKey + ".description")));
            this.addRenderableWidget(toggle);
        }
    }

    private static Component stateLabel(boolean enabled) {
        return Component.translatable(enabled ? "herzium.state.on" : "herzium.state.off");
    }

    private void addDoneButton() {
        int doneWidth = Math.min(142, Math.max(1, this.panelWidth - 12));
        int doneHeight = Math.min(this.panelHeight < 205 ? 20 : 24, Math.max(1, this.footerHeight - 4));
        int doneY = this.panelY + this.panelHeight - this.footerHeight
                + Math.max(2, (this.footerHeight - doneHeight) / 2);
        this.addRenderableWidget(new AnimatedPurpleButton(
                this.panelX + this.panelWidth - doneWidth - Math.min(14, Math.max(6, this.panelWidth / 30)),
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
        HerziumConfig config = HerziumConfig.get();
        List<InfoLine> lines = new ArrayList<>();
        int textWidth = Math.max(24, this.infoWidth - 26);
        this.detectedIssueCount = 0;

        addHeading(lines, "herzium.config.section.active", textWidth, false);
        addStatus(
                lines,
                "herzium.config.status.hotbar_option",
                stateLabel(config.hotbarPreview()),
                config.hotbarPreview() ? HerziumTheme.TEXT_GOOD : HerziumTheme.TEXT_MUTED,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.minecraft_core",
                "MinecraftMixin",
                snapshot.coreFrameHookObserved(),
                true,
                snapshot,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.hotbar_visual",
                "HotbarVisualMixin",
                snapshot.hotbarVisualHookObserved(),
                config.hotbarPreview(),
                snapshot,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.keyboard_hook",
                "KeyMappingMixin",
                snapshot.keyboardHookObserved() || snapshot.mouseHookObserved(),
                config.hotbarPreview(),
                snapshot,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.mouse_hook",
                "MouseHandlerMixin",
                snapshot.wheelHookObserved(),
                true,
                snapshot,
                textWidth);
        addMixinStatus(
                lines,
                "herzium.config.status.hand_equip",
                "ItemInHandRendererMixin",
                snapshot.handRenderHookObserved(),
                config.instantEquip(),
                snapshot,
                textWidth);

        Component configState = switch (snapshot.configState()) {
            case LOADING -> Component.translatable("herzium.config.state.loading");
            case HEALTHY -> Component.translatable("herzium.config.state.healthy");
            case READ_FAILED -> Component.translatable("herzium.config.state.read_failed");
            case WRITE_FAILED -> Component.translatable("herzium.config.state.write_failed");
        };
        int configColor = snapshot.configState() == CoreDiagnostics.ConfigState.HEALTHY
                ? HerziumTheme.TEXT_GOOD
                : snapshot.configState() == CoreDiagnostics.ConfigState.LOADING
                        ? HerziumTheme.TEXT_WARN
                        : HerziumTheme.TEXT_BAD;
        addStatus(lines, "herzium.config.status.config", configState, configColor, textWidth);

        Component inputOwner = KoHsiumIntegration.present()
                ? Component.translatable("herzium.config.state.managed_by_kohsium")
                : Component.translatable("herzium.config.state.managed_by_herzium");
        addStatus(lines, "herzium.config.status.input_owner", inputOwner, HerziumTheme.TEXT_ACCENT, textWidth);

        Component inputPipeline;
        int inputPipelineColor;
        if (ExternalInputCompatibility.competingExternalPipelinesPresent()) {
            inputPipeline = Component.translatable("herzium.config.state.external_input_multiple");
            inputPipelineColor = HerziumTheme.TEXT_BAD;
        } else if (ExternalInputCompatibility.rawInputBufferPresent()) {
            inputPipeline = Component.translatable("herzium.config.state.external_input_raw_buffer");
            inputPipelineColor = HerziumTheme.TEXT_WARN;
        } else if (ExternalInputCompatibility.ixerisPresent()) {
            inputPipeline = Component.translatable("herzium.config.state.external_input_ixeris");
            inputPipelineColor = HerziumTheme.TEXT_WARN;
        } else {
            inputPipeline = Component.translatable("herzium.config.state.external_input_none");
            inputPipelineColor = HerziumTheme.TEXT_GOOD;
        }
        addStatus(lines, "herzium.config.status.input_pipeline", inputPipeline, inputPipelineColor, textWidth);
        addStatus(
                lines,
                "herzium.config.status.inventory_render",
                Component.translatable("herzium.config.state.vanilla_backdrop"),
                HerziumTheme.TEXT_GOOD,
                textWidth);

        boolean exordiumPresent = FabricLoader.getInstance().isModLoaded("exordium");
        Component exordiumState;
        int exordiumColor;
        if (!exordiumPresent) {
            exordiumState = Component.translatable("herzium.config.state.not_installed");
            exordiumColor = HerziumTheme.TEXT_MUTED;
        } else if (snapshot.mixinApplied("ExordiumBufferInstanceMixin")) {
            exordiumState = Component.translatable("herzium.config.state.applied");
            exordiumColor = HerziumTheme.TEXT_GOOD;
        } else {
            exordiumState = Component.translatable("herzium.config.state.not_applied");
            exordiumColor = HerziumTheme.TEXT_BAD;
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
                HerziumTheme.TEXT_BODY,
                textWidth);
        addWrapped(
                lines,
                Component.translatable(
                        "herzium.config.status.render_counters",
                        snapshot.instantEquipFrames(),
                        snapshot.combatEquipFramesPreserved()),
                HerziumTheme.TEXT_BODY,
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
                    HerziumTheme.TEXT_BODY,
                    textWidth);
        } else {
            addWrapped(
                    lines,
                    Component.translatable("herzium.config.status.no_preview"),
                    HerziumTheme.TEXT_MUTED,
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
                    HerziumTheme.TEXT_GOOD,
                    textWidth);
        }
        addWrapped(
                lines,
                Component.translatable("herzium.config.issue.pending_note"),
                HerziumTheme.TEXT_MUTED,
                textWidth);

        this.infoLines = List.copyOf(lines);
        this.logicalInfoHeight = 12;
        for (InfoLine line : this.infoLines) {
            this.logicalInfoHeight += line.height();
        }
        this.maxScroll = Math.max(
                0,
                this.logicalInfoHeight - Math.max(1, this.infoHeight - this.infoHeaderHeight - 12));
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
    }

    private void addMixinStatus(
            List<InfoLine> lines,
            String labelKey,
            String mixinName,
            boolean observed,
            boolean optionEnabled,
            CoreDiagnostics.Snapshot snapshot,
            int textWidth) {
        boolean applied = snapshot.mixinApplied(mixinName);
        Component state;
        int color;
        if (!applied) {
            state = Component.translatable("herzium.config.state.not_applied");
            color = HerziumTheme.TEXT_BAD;
        } else if (!optionEnabled) {
            // The hook is in place and simply told not to act. Reporting that as
            // "waiting" would look like a fault the user cannot fix.
            state = Component.translatable("herzium.config.state.option_off");
            color = HerziumTheme.TEXT_MUTED;
        } else if (observed) {
            state = Component.translatable("herzium.config.state.observed");
            color = HerziumTheme.TEXT_GOOD;
        } else {
            state = Component.translatable("herzium.config.state.applied_waiting");
            color = HerziumTheme.TEXT_WARN;
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
                HerziumTheme.TEXT_BAD,
                textWidth);
    }

    private void addIssue(List<InfoLine> lines, String translationKey, int textWidth) {
        this.detectedIssueCount++;
        addWrapped(lines, Component.translatable(translationKey), HerziumTheme.TEXT_BAD, textWidth);
    }

    private void addWarning(List<InfoLine> lines, String translationKey, int textWidth) {
        this.detectedIssueCount++;
        addWrapped(lines, Component.translatable(translationKey), HerziumTheme.TEXT_WARN, textWidth);
    }

    private void addHeading(List<InfoLine> lines, String translationKey, int textWidth, boolean spaceBefore) {
        if (spaceBefore) {
            lines.add(new InfoLine(null, 0, 7));
        }
        addWrapped(lines, Component.translatable(translationKey), HerziumTheme.TEXT_HEADING, textWidth);
        lines.add(new InfoLine(null, 0, 2));
    }

    private void addStatus(
            List<InfoLine> lines,
            String labelKey,
            Component state,
            int color,
            int textWidth) {
        List<FormattedCharSequence> wrapped = this.font.split(
                Component.translatable(
                        "herzium.config.status.entry",
                        Component.translatable(labelKey),
                        state),
                Math.max(24, textWidth - 9));
        for (int index = 0; index < wrapped.size(); index++) {
            lines.add(new InfoLine(
                    wrapped.get(index),
                    HerziumTheme.TEXT_BODY,
                    10,
                    index == 0 ? color : 0));
        }
    }

    private void addParagraph(List<InfoLine> lines, String translationKey, int textWidth) {
        addWrapped(lines, Component.translatable(translationKey), HerziumTheme.TEXT_BODY, textWidth);
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
        HerziumTheme.backdrop(graphics, this.width, this.height);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        long now = System.nanoTime() / 1_000_000L;
        this.refreshDiagnostics(false);
        // Order matters and is load bearing: the field goes down first, then
        // every opaque-enough surface, then the widgets and text on top. No
        // label ever ends up sitting directly on a particle.
        this.particles.render(graphics, this.width, this.height, mouseX, mouseY, now);
        this.drawPanel(graphics, now);
        this.drawOptionsPane(graphics);
        this.drawInfoPanel(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, long now) {
        HerziumTheme.fillRounded(
                graphics,
                this.panelX,
                this.panelY,
                this.panelWidth,
                this.panelHeight,
                HerziumTheme.PANEL_TOP,
                HerziumTheme.PANEL_BOTTOM);
        HerziumTheme.drawOutline(
                graphics,
                this.panelX + 1,
                this.panelY + 1,
                this.panelWidth - 2,
                this.panelHeight - 2,
                HerziumTheme.PANE_OUTLINE);

        int titleX = this.panelX + Math.min(18, Math.max(8, this.panelWidth / 26));
        int titleY = this.panelY + (this.headerHeight >= 42 ? 10 : Math.max(6, (this.headerHeight - 9) / 2));
        graphics.text(
                this.font,
                TITLE,
                titleX,
                titleY,
                HerziumTheme.TEXT_TITLE);
        if (this.headerHeight >= 42 && this.panelWidth >= 250) {
            graphics.text(
                    this.font,
                    SUBTITLE,
                    titleX,
                    titleY + 12,
                    HerziumTheme.TEXT_MUTED);
        }
        int dividerY = this.panelY + this.headerHeight - 1;
        graphics.fill(
                this.panelX + 8,
                dividerY,
                this.panelX + this.panelWidth - 8,
                dividerY + 1,
                HerziumTheme.DIVIDER);
    }

    private void drawOptionsPane(GuiGraphicsExtractor graphics) {
        HerziumTheme.fillRounded(
                graphics,
                this.optionsX,
                this.optionsY,
                this.optionsWidth,
                this.optionsHeight,
                HerziumTheme.PANE_TOP,
                HerziumTheme.PANE_BOTTOM);
        HerziumTheme.drawOutline(
                graphics,
                this.optionsX,
                this.optionsY,
                this.optionsWidth,
                this.optionsHeight,
                HerziumTheme.PANE_OUTLINE);

        if (this.optionsHeadingHeight > 0) {
            graphics.text(
                    this.font,
                    OPTIONS_HEADING,
                    this.optionsX + this.optionsInset + 1,
                    this.optionsY + this.optionsInset + 1,
                    HerziumTheme.TEXT_HEADING);
            HerziumTheme.hairline(
                    graphics,
                    this.optionsX + this.optionsInset,
                    this.optionsY + this.optionsHeadingHeight - 2,
                    this.optionsWidth - this.optionsInset * 2);
        }

        int paneBottom = this.optionsY + this.optionsHeight;
        for (OptionRow row : this.optionRows) {
            if (row.y >= paneBottom) {
                continue;
            }

            boolean enabled = row.getter.getAsBoolean();
            HerziumTheme.fillRounded(
                    graphics,
                    row.x,
                    row.y,
                    row.width,
                    row.height,
                    HerziumTheme.CARD_TOP,
                    HerziumTheme.CARD_BOTTOM);
            graphics.fill(
                    row.x,
                    row.y + 5,
                    row.x + 2,
                    row.y + Math.max(6, row.height - 5),
                    enabled ? HerziumTheme.ACCENT : HerziumTheme.TEXT_OFF);

            int labelCount = Math.min(2, row.labelLines.size());
            int labelTop = row.y + Math.max(2, (row.labelBlockHeight - labelCount * 10) / 2);
            for (int line = 0; line < labelCount; line++) {
                graphics.text(
                        this.font,
                        row.labelLines.get(line),
                        row.textX,
                        labelTop + line * 10,
                        enabled ? HerziumTheme.TEXT_PRIMARY : HerziumTheme.TEXT_MUTED);
            }

            int descriptionY = row.y + row.labelBlockHeight;
            int rowBottom = row.y + row.height;
            int drawn = Math.min(row.descriptionLimit, row.descriptionLines.size());
            for (int line = 0; line < drawn; line++) {
                int lineY = descriptionY + line * 9;
                if (lineY + 9 > rowBottom || lineY + 9 > paneBottom) {
                    break;
                }
                graphics.text(
                        this.font,
                        row.descriptionLines.get(line),
                        row.textX,
                        lineY,
                        HerziumTheme.TEXT_MUTED);
            }
        }
    }

    private void drawInfoPanel(GuiGraphicsExtractor graphics) {
        HerziumTheme.fillRounded(
                graphics,
                this.infoX,
                this.infoY,
                this.infoWidth,
                this.infoHeight,
                HerziumTheme.PANE_TOP,
                HerziumTheme.PANE_BOTTOM);
        HerziumTheme.drawOutline(
                graphics,
                this.infoX,
                this.infoY,
                this.infoWidth,
                this.infoHeight,
                HerziumTheme.PANE_OUTLINE);

        if (this.infoHeaderHeight > 0) {
            graphics.text(
                    this.font,
                    STATUS_HEADING,
                    this.infoX + 10,
                    this.infoY + 8,
                    HerziumTheme.TEXT_HEADING);
            HerziumTheme.hairline(
                    graphics,
                    this.infoX + 9,
                    this.infoY + this.infoHeaderHeight - 3,
                    this.infoWidth - 18);
        }

        int clipLeft = this.infoX + 1;
        int clipTop = this.infoY + this.infoHeaderHeight;
        int clipRight = this.infoX + this.infoWidth - 1;
        int clipBottom = this.infoY + this.infoHeight - 1;
        if (clipRight <= clipLeft || clipBottom <= clipTop) {
            return;
        }

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int textX = this.infoX + 10;
        int textY = this.infoY + this.infoHeaderHeight + 6 - this.scrollOffset;
        for (InfoLine line : this.infoLines) {
            if (line.text() != null && textY > clipTop - 10 && textY < this.infoY + this.infoHeight) {
                int lineX = textX;
                if (line.dotColor() != 0) {
                    HerziumTheme.statusDot(graphics, textX, textY + 3, line.dotColor());
                    lineX += 9;
                }
                graphics.text(this.font, line.text(), lineX, textY, line.color());
            }
            textY += line.height();
        }
        graphics.disableScissor();

        HerziumTheme.drawScrollbar(
                graphics,
                this.infoX,
                this.infoY,
                this.infoWidth,
                this.infoHeight,
                this.scrollOffset,
                this.maxScroll);
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

    private record InfoLine(FormattedCharSequence text, int color, int height, int dotColor) {
        private InfoLine(FormattedCharSequence text, int color, int height) {
            this(text, color, height, 0);
        }
    }

    private static final class OptionRow {
        private final String translationKey;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        private List<FormattedCharSequence> labelLines = List.of();
        private List<FormattedCharSequence> descriptionLines = List.of();
        private int labelBlockHeight;
        private int descriptionLimit;
        private int x;
        private int y;
        private int width;
        private int height;
        private int textX;
        private int textWidth;
        private int buttonX;
        private int buttonY;

        private OptionRow(String translationKey, BooleanSupplier getter, Consumer<Boolean> setter) {
            this.translationKey = translationKey;
            this.getter = getter;
            this.setter = setter;
        }
    }
}
