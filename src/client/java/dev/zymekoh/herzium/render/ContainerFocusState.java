package dev.zymekoh.herzium.render;

/**
 * Single-frame handshake between the two container-focus mixins.
 *
 * <p>{@code ContainerFocusRendererMixin} omits the live 3D world while a
 * container screen is open, and {@code ContainerFocusBackgroundMixin} paints an
 * opaque gradient to fill the gap it leaves. Neither used to check the other.
 * If the renderer failed to apply -- a signature change in
 * {@code GameRenderer.extract}/{@code render}, another mod winning on priority,
 * or a multiversion variant that ships a different renderer overlay -- the
 * background still applied: an opaque purple sheet drawn on top of a world that
 * was being rendered at full cost. The worst of both options, silently.</p>
 *
 * <p>The flag is therefore written only by the renderer, only for the frame it
 * actually acted on, and read by the background as a precondition. It is reset
 * at the start of every frame rather than left standing, so a renderer that
 * stops applying mid-session degrades to plain vanilla instead of to a stale
 * {@code true}. If the renderer never applies at all, the flag simply never
 * leaves its initial {@code false}.</p>
 *
 * <p>Both hooks run on the render thread inside one frame, so no
 * synchronisation is needed or wanted on this path.</p>
 */
public final class ContainerFocusState {
    private static boolean levelOmittedThisFrame;

    private ContainerFocusState() {
    }

    /**
     * Records the renderer's decision for the frame that is starting.
     *
     * <p>Must be called unconditionally at the frame boundary, with
     * {@code false} when the world was left alone, so that the previous frame's
     * decision never survives into this one.</p>
     */
    public static void beginFrame(boolean levelOmitted) {
        levelOmittedThisFrame = levelOmitted;
    }

    /** True only if the renderer confirmed omitting the level in this frame. */
    public static boolean levelOmittedThisFrame() {
        return levelOmittedThisFrame;
    }
}
