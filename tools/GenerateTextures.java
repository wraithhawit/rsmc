import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Draws the block faces that are the Casing texture with something inset into it.
 *
 * <p>Run by hand when the art changes:
 *
 * <pre>
 *   java tools/GenerateTextures.java
 *   java tools/GenerateTextures.java --export-overlays some/dir   (writes the panels alone)
 * </pre>
 *
 * <p>Reads {@code casing.png} and the five tiles in {@code ctm/casing/}; those are drawn art. Each
 * panel is an <b>overlay</b>: a 16x16 image, transparent wherever the casing should show. If
 * {@code tools/overlays/<name>.png} exists it is used as drawn; otherwise the panel below is drawn
 * procedurally. So an artist can take over any one of the four panels by dropping a single file in,
 * and still never draw the fifteen connected tiles it gets inset into. Everything this writes under
 * {@code src/} is output and will be overwritten on the next run.
 *
 * <p>Four panels: the three Controller screen states, and the Pattern Port's opening. Generated
 * rather than drawn because every face is the Casing texture with one inset panel on top, and the
 * only difference between them is what the panel is. Hand-editing near-identical 16x16 images is
 * how they drift apart -- one gets a border tweak the others do not, and nobody notices for a
 * month. It also means that when the real Casing art lands, every face follows it for free.
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

    /** Athena's five connected-texture tiles. Every one is a full 16x16; see PLACEHOLDERS.md. */
    private static final String[] CTM_TILES = {"particle", "empty", "center", "vertical", "horizontal"};

    /** Drawn panels that replace the procedural ones, one file per panel, by face name. */
    private static final File OVERLAYS = new File("tools/overlays");

    private GenerateTextures() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length == 2 && args[0].equals("--export-overlays")) {
            final File out = new File(args[1]);
            for (final State state : State.values()) {
                write(out, state.face() + ".png", screen(state));
            }
            write(out, "port.png", port());
            System.out.println("wrote the 4 procedural overlays to " + out);
            return;
        }

        final File dir = new File("src/main/resources/assets/rsmbac/textures/block");
        final BufferedImage casing = ImageIO.read(new File(dir, "casing.png"));
        final File ctm = new File(dir, "ctm");

        int drawn = 0;
        for (final String face : faces()) {
            final BufferedImage overlay = overlay(face);
            if (new File(OVERLAYS, face + ".png").exists()) {
                drawn++;
            }
            write(dir, face + ".png", inset(casing, overlay));

            // Since 0.10.0 the same panels are inset into each of the Casing's five connected tiles
            // as well, so a Port or a Controller screen sits in a seamless wall instead of carrying
            // a border of its own. A panel must stay off the outer edge pixels -- the only pixels
            // the five tiles differ in -- or the identical-interiors rule breaks and seams appear.
            for (final String tile : CTM_TILES) {
                final BufferedImage source = ImageIO.read(new File(ctm, "casing/" + tile + ".png"));
                write(new File(ctm, face), tile + ".png", inset(source, overlay));
            }
        }
        System.out.println("wrote 3 controller faces and the port, flat and as connected tiles, to "
            + dir + " (" + drawn + " from drawn overlays)");
    }

    private static String[] faces() {
        final String[] faces = new String[State.values().length + 1];
        for (final State state : State.values()) {
            faces[state.ordinal()] = state.face();
        }
        faces[faces.length - 1] = "port";
        return faces;
    }

    /** The drawn overlay for a face if there is one, otherwise the procedural panel. */
    private static BufferedImage overlay(final String face) throws IOException {
        final File drawn = new File(OVERLAYS, face + ".png");
        if (drawn.exists()) {
            return ImageIO.read(drawn);
        }
        for (final State state : State.values()) {
            if (state.face().equals(face)) {
                return screen(state);
            }
        }
        return port();
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

        String face() {
            return "controller_front_" + name().toLowerCase(Locale.ROOT);
        }
    }

    private static BufferedImage screen(final State state) {
        final BufferedImage out = transparent();
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
    private static BufferedImage port() {
        final BufferedImage out = transparent();
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

    private static BufferedImage transparent() {
        return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * A full tile of {@code base} with {@code overlay} laid over it. The result is always opaque:
     * the blocks render solid, so a drawn overlay's partial alpha is flattened here, not in game.
     */
    private static BufferedImage inset(final BufferedImage base, final BufferedImage overlay) {
        final BufferedImage out = transparent();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                final int under = base.getRGB(x % base.getWidth(), y % base.getHeight());
                out.setRGB(x, y, blend(under, overlay.getRGB(x % overlay.getWidth(), y % overlay.getHeight())));
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
        dir.mkdirs();
        ImageIO.write(image, "PNG", new File(dir, name));
    }
}
