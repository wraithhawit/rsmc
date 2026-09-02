import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Draws the block faces that are the Casing texture with something inset into it.
 *
 * <p>Run by hand when the art changes:
 *
 * <pre>
 *   java tools/GenerateTextures.java
 * </pre>
 *
 * <p>Two of them: the three Controller screen states, and the Pattern Port's opening. Generated
 * rather than drawn because every one is the Casing texture with one inset panel on top, and the
 * only difference between them is what the panel is. Hand-editing four near-identical 16x16 images
 * is how they drift apart -- one gets a border tweak the others do not, and nobody notices for a
 * month. It also means that when the real Casing art lands, all four follow it for free.
 *
 * <p>Building on the Casing texture is the point: the earlier placeholder used RS's actual
 * {@code grid/front.png}, which made an rsmbac Controller look like a Grid closely enough to be
 * mistaken for one in game. These read as our own block with something on it.
 *
 * <p>(Was {@code GenerateControllerTextures.java} until 0.5.0, when the Port gave it a second
 * customer.)
 */
public final class GenerateTextures {
    private static final int SIZE = 16;

    /** The inset panel, leaving a 3px bezel of casing around it. Shared by every face here. */
    private static final int PANEL_MIN = 3;
    private static final int PANEL_MAX = 12;

    private static final int BEZEL_SHADOW = 0xFF23262B;
    private static final int BEZEL_HIGHLIGHT = 0x40FFFFFF;

    private GenerateTextures() {
    }

    public static void main(final String[] args) throws IOException {
        final File dir = new File("src/main/resources/assets/rsmbac/textures/block");
        final BufferedImage casing = ImageIO.read(new File(dir, "casing.png"));

        write(dir, "controller_front_unformed.png", screen(casing, State.UNFORMED));
        write(dir, "controller_front_inactive.png", screen(casing, State.INACTIVE));
        write(dir, "controller_front_active.png", screen(casing, State.ACTIVE));
        write(dir, "port.png", port(casing));
        System.out.println("wrote 3 controller faces and the port to " + dir);
    }

    private enum State {
        /** Not a structure: a dead panel, closer to the casing than to a screen. */
        UNFORMED(0xFF56595E, 0xFF62666B, false),
        /** A structure with no network: a real screen, switched off. */
        INACTIVE(0xFF1B1E22, 0xFF24282D, true),
        /** Live. Light blue, matching what a Refined Storage machine looks like when running. */
        ACTIVE(0xFF3A7FC4, 0xFF5FA8E8, true);

        private final int dark;
        private final int light;
        private final boolean lit;

        State(final int dark, final int light, final boolean lit) {
            this.dark = dark;
            this.light = light;
            this.lit = lit;
        }
    }

    private static BufferedImage screen(final BufferedImage casing, final State state) {
        final BufferedImage out = base(casing);
        for (int x = PANEL_MIN; x <= PANEL_MAX; x++) {
            for (int y = PANEL_MIN; y <= PANEL_MAX; y++) {
                if (onPanelEdge(x, y)) {
                    out.setRGB(x, y, BEZEL_SHADOW);
                    continue;
                }
                // A dot matrix, so a lit screen reads as a display rather than a flat colour --
                // and so the unlit states still show the same texture, just dark.
                final boolean dot = ((x + y) & 1) == 0;
                out.setRGB(x, y, dot ? state.light : state.dark);
            }
        }
        if (state.lit) {
            // One highlight pixel row along the top inside edge: enough to suggest glass.
            for (int x = PANEL_MIN + 1; x < PANEL_MAX; x++) {
                out.setRGB(x, PANEL_MIN + 1, blend(out.getRGB(x, PANEL_MIN + 1), BEZEL_HIGHLIGHT));
            }
        }
        return out;
    }

    /**
     * The Pattern Port: the same inset panel, but a hole rather than a screen.
     *
     * <p>Deliberately the same geometry as the Controller face. The two blocks sit in the same wall
     * and the player reads them as a pair -- one with a display in the opening and one with nothing
     * in it -- which is also the honest description of what they do.
     */
    private static BufferedImage port(final BufferedImage casing) {
        final BufferedImage out = base(casing);
        for (int x = PANEL_MIN; x <= PANEL_MAX; x++) {
            for (int y = PANEL_MIN; y <= PANEL_MAX; y++) {
                if (onPanelEdge(x, y)) {
                    out.setRGB(x, y, BEZEL_SHADOW);
                    continue;
                }
                out.setRGB(x, y, VOID_COLOUR);
            }
        }
        // Light from above, so the inside of the top lip catches it and the bottom does not. Two
        // rows of shading is all it takes for the square to read as a hole rather than a black
        // sticker.
        for (int x = PANEL_MIN + 1; x < PANEL_MAX; x++) {
            out.setRGB(x, PANEL_MIN + 1, VOID_LIP);
        }
        for (int y = PANEL_MIN + 1; y < PANEL_MAX; y++) {
            out.setRGB(PANEL_MIN + 1, y, VOID_LIP);
        }
        return out;
    }

    /** Inside the opening. Not pure black: pure black reads as a missing texture. */
    private static final int VOID_COLOUR = 0xFF101216;

    /** The lit inside edge of the opening, top and left. */
    private static final int VOID_LIP = 0xFF2A2E34;

    /** A full tile of Casing to inset into. */
    private static BufferedImage base(final BufferedImage casing) {
        final BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                out.setRGB(x, y, casing.getRGB(x % casing.getWidth(), y % casing.getHeight()));
            }
        }
        return out;
    }

    private static boolean onPanelEdge(final int x, final int y) {
        return x == PANEL_MIN || x == PANEL_MAX || y == PANEL_MIN || y == PANEL_MAX;
    }

    private static int blend(final int base, final int overlay) {
        final int alpha = (overlay >>> 24) & 0xFF;
        final int inverse = 255 - alpha;
        final int r = (((overlay >> 16) & 0xFF) * alpha + ((base >> 16) & 0xFF) * inverse) / 255;
        final int g = (((overlay >> 8) & 0xFF) * alpha + ((base >> 8) & 0xFF) * inverse) / 255;
        final int b = ((overlay & 0xFF) * alpha + (base & 0xFF) * inverse) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void write(final File dir, final String name, final BufferedImage image)
        throws IOException {
        ImageIO.write(image, "PNG", new File(dir, name));
    }
}
