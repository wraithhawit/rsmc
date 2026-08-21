import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Draws the three Controller faces, so the screen states are ours rather than Refined Storage's.
 *
 * <p>Run by hand when the art changes:
 *
 * <pre>
 *   java tools/GenerateControllerTextures.java
 * </pre>
 *
 * <p>Generated rather than drawn because all three faces are the Casing texture with an inset
 * screen on top, and the only difference between them is what the screen is doing. Hand-editing
 * three near-identical 16x16 images is how they drift apart -- one gets a border tweak the others
 * do not, and nobody notices for a month.
 *
 * <p>Building on the Casing texture is the point: the earlier placeholder used RS's actual
 * {@code grid/front.png}, which made an rsmc Controller look like a Grid closely enough to be
 * mistaken for one in game. This reads as our own block with a display on it.
 */
public final class GenerateControllerTextures {
    private static final int SIZE = 16;

    /** The inset screen, leaving a 3px bezel of casing around it. */
    private static final int SCREEN_MIN = 3;
    private static final int SCREEN_MAX = 12;

    private static final int BEZEL_SHADOW = 0xFF23262B;
    private static final int BEZEL_HIGHLIGHT = 0x40FFFFFF;

    private GenerateControllerTextures() {
    }

    public static void main(final String[] args) throws IOException {
        final File dir = new File("src/main/resources/assets/rsmc/textures/block");
        final BufferedImage casing = ImageIO.read(new File(dir, "casing.png"));

        write(dir, "controller_front_unformed.png", face(casing, State.UNFORMED));
        write(dir, "controller_front_inactive.png", face(casing, State.INACTIVE));
        write(dir, "controller_front_active.png", face(casing, State.ACTIVE));
        System.out.println("wrote 3 controller faces to " + dir);
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

    private static BufferedImage face(final BufferedImage casing, final State state) {
        final BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        // Start from the casing so the bezel is literally the same metal as the rest of the block.
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                out.setRGB(x, y, casing.getRGB(x % casing.getWidth(), y % casing.getHeight()));
            }
        }
        for (int x = SCREEN_MIN; x <= SCREEN_MAX; x++) {
            for (int y = SCREEN_MIN; y <= SCREEN_MAX; y++) {
                final boolean edge = x == SCREEN_MIN || x == SCREEN_MAX
                    || y == SCREEN_MIN || y == SCREEN_MAX;
                if (edge) {
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
            for (int x = SCREEN_MIN + 1; x < SCREEN_MAX; x++) {
                out.setRGB(x, SCREEN_MIN + 1, blend(out.getRGB(x, SCREEN_MIN + 1), BEZEL_HIGHLIGHT));
            }
        }
        return out;
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
