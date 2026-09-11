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
 * <p>Since 0.11.0 a face can also be drawn whole, as {@code tools/faces/<face>.png} -- which is how
 * the artist actually worked. It is used exactly as drawn for the flat texture and the lone tile,
 * and its shared interior goes onto the four joining tiles; see {@link #main}.
 *
 * <p>Since 0.10.3 it also writes labPBR {@code _s.png} maps, which is how shader packs are told a
 * pixel glows: the active screen by default, or any face with a drawn {@code tools/overlays/<face>_s.png}.
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

    /** Whole drawn faces. Take priority over an overlay for the same face. */
    private static final File FACES = new File("tools/faces");

    private GenerateTextures() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length == 2 && args[0].equals("--export-overlays")) {
            final File out = new File(args[1]);
            for (final State state : State.values()) {
                write(out, state.face() + ".png", screen(state));
                if (state.glows) {
                    write(out, state.face() + "_s.png", glow());
                }
            }
            write(out, "port.png", port());
            System.out.println("wrote the procedural overlays and glow maps to " + out);
            return;
        }

        final File dir = new File("src/main/resources/assets/rsmbac/textures/block");
        final BufferedImage casing = ImageIO.read(new File(dir, "casing.png"));
        final File ctm = new File(dir, "ctm");
        final BufferedImage[] casingTiles = new BufferedImage[CTM_TILES.length];
        for (int i = 0; i < CTM_TILES.length; i++) {
            casingTiles[i] = ImageIO.read(new File(ctm, "casing/" + CTM_TILES[i] + ".png"));
        }
        final boolean[][] shared = sharedInterior(casingTiles);

        int drawnOverlays = 0;
        int drawnFaces = 0;
        for (final String face : faces()) {
            final File drawnFace = new File(FACES, face + ".png");
            if (drawnFace.exists()) {
                // A whole face, drawn. It is the flat texture and the lone (particle) tile exactly as
                // drawn, detail in the border included. The four joining tiles take the Casing's
                // tile and only the face's SHARED INTERIOR -- the pixels all five casing tiles agree
                // on. Border detail (corner screws, a cracked frame) cannot go on those: the border
                // band is what varies between tiles, so it would land where the wall joins.
                drawnFaces++;
                final BufferedImage art = ImageIO.read(drawnFace);
                copy(drawnFace, dir, face + ".png");
                for (int i = 0; i < CTM_TILES.length; i++) {
                    if (CTM_TILES[i].equals("particle")) {
                        copy(drawnFace, new File(ctm, face), "particle.png");
                    } else {
                        write(new File(ctm, face), CTM_TILES[i] + ".png", interiorOnto(casingTiles[i], art, shared));
                    }
                }
            } else {
                final BufferedImage overlay = overlay(face);
                if (new File(OVERLAYS, face + ".png").exists()) {
                    drawnOverlays++;
                }
                write(dir, face + ".png", inset(casing, overlay));

                // Since 0.10.0 the same panels are inset into each of the Casing's five connected
                // tiles as well, so a Port or a Controller screen sits in a seamless wall instead of
                // carrying a border of its own. A panel must stay off the outer edge pixels -- the
                // only pixels the five tiles differ in -- or the identical-interiors rule breaks.
                for (int i = 0; i < CTM_TILES.length; i++) {
                    write(new File(ctm, face), CTM_TILES[i] + ".png", inset(casingTiles[i], overlay));
                }
            }

            // labPBR specular map, read by Iris and used by shader packs for glow. Iris pairs a
            // map with its texture by name, so the Athena tiles each need their own copy. One
            // image serves all six: a specular map has no casing edge to vary.
            final File drawnSpecular = new File(OVERLAYS, face + "_s.png");
            final BufferedImage specular = specular(face);
            final File[] targets = new File[CTM_TILES.length + 1];
            targets[0] = new File(dir, face + "_s.png");
            for (int i = 0; i < CTM_TILES.length; i++) {
                targets[i + 1] = new File(new File(ctm, face), CTM_TILES[i] + "_s.png");
            }
            for (final File target : targets) {
                if (drawnSpecular.exists()) {
                    copy(drawnSpecular, target.getParentFile(), target.getName());
                } else if (specular != null) {
                    write(target.getParentFile(), target.getName(), specular);
                } else {
                    // A face that stopped glowing must not keep last run's map.
                    target.delete();
                }
            }
        }
        System.out.println("wrote 3 controller faces and the port, flat and as connected tiles, to "
            + dir + " (" + drawnFaces + " drawn faces, " + drawnOverlays + " drawn overlays)");
    }

    /**
     * Pixels identical in all five casing tiles: the part of a face that is safe to repeat on every
     * tile. Derived from the art rather than declared, so a casing with a wider or narrower border
     * moves it automatically. If the casing set breaks the identical-interiors rule, this shrinks
     * and says so, rather than silently cutting a panel in half.
     */
    private static boolean[][] sharedInterior(final BufferedImage[] tiles) {
        final boolean[][] shared = new boolean[SIZE][SIZE];
        int count = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                boolean same = true;
                for (int i = 1; i < tiles.length; i++) {
                    same &= tiles[i].getRGB(x, y) == tiles[0].getRGB(x, y);
                }
                shared[x][y] = same;
                if (same) {
                    count++;
                }
            }
        }
        System.out.println("casing tiles share " + count + " interior pixels");
        return shared;
    }

    private static BufferedImage interiorOnto(final BufferedImage tile, final BufferedImage art,
        final boolean[][] shared) {
        final BufferedImage out = transparent();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                out.setRGB(x, y, shared[x][y] ? art.getRGB(x, y) : tile.getRGB(x, y));
            }
        }
        return out;
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

    /**
     * The labPBR map for a face, or null if it does not glow: a drawn
     * {@code tools/overlays/<face>_s.png} used as-is, otherwise the procedural one for a lit screen.
     *
     * <p>Unlike a colour overlay a drawn map is not composited over anything -- in labPBR the alpha
     * channel IS the emission, so it cannot also mean "let the casing show". It is a whole 16x16 map.
     */
    private static BufferedImage specular(final String face) throws IOException {
        final File drawn = new File(OVERLAYS, face + "_s.png");
        if (drawn.exists()) {
            return ImageIO.read(drawn);
        }
        for (final State state : State.values()) {
            if (state.face().equals(face) && state.glows) {
                return glow();
            }
        }
        return null;
    }

    /**
     * labPBR 1.3: red is smoothness, green is reflectance (F0, 0-229), blue is porosity, and alpha is
     * emission -- 0-254 is how bright, and 255 means none. Verified against Complementary r5.8.1,
     * whose {@code GetCustomEmission} reads {@code a < 1.0 ? a : 0.0}.
     *
     * <p>255 rather than 0 for "none" matters: it is what an ordinary opaque PNG already has, which
     * is the whole reason the standard reserves it.
     */
    private static final int SPECULAR_NONE = 0xFF000000;

    /** The screen: full emission, fairly glossy, and glass's reflectance (F0 0.04 is 10/255). */
    private static final int SPECULAR_SCREEN = 0xFEA00A00;

    /**
     * Emission over the whole inside of the panel, uniformly -- not per lit dot. Complementary
     * takes the smaller of the sampled and the full-resolution emission to avoid mipmap bleed, so
     * a checkerboard of glowing pixels would average down and fade with distance.
     */
    private static BufferedImage glow() {
        final BufferedImage out = transparent();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                final boolean screen = x > PANEL_MIN && x < PANEL_MAX && y > PANEL_MIN && y < PANEL_MAX;
                out.setRGB(x, y, screen ? SPECULAR_SCREEN : SPECULAR_NONE);
            }
        }
        return out;
    }

    private enum State {
        /** Not a structure: a dead panel, closer to the casing than to a screen. */
        UNFORMED(0xFF56595E, 0xFF62666B, false, false),
        /** A structure with no network: a real screen, switched off. */
        INACTIVE(0xFF1B1E22, 0xFF24282D, true, false),
        /** Live. Light blue, matching what a Refined Storage machine looks like when running. */
        ACTIVE(0xFF3A7FC4, 0xFF5FA8E8, true, true);

        private final int dark;
        private final int light;
        private final boolean lit;
        /** Whether shaders make it glow. Only a running machine's screen is on. */
        private final boolean glows;

        State(final int dark, final int light, final boolean lit, final boolean glows) {
            this.dark = dark;
            this.light = light;
            this.lit = lit;
            this.glows = glows;
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

    /** Drawn art goes in byte for byte: re-encoding would change the file, if not the pixels. */
    private static void copy(final File source, final File dir, final String name) throws IOException {
        dir.mkdirs();
        java.nio.file.Files.copy(source.toPath(), new File(dir, name).toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void write(final File dir, final String name, final BufferedImage image)
        throws IOException {
        dir.mkdirs();
        ImageIO.write(image, "PNG", new File(dir, name));
    }
}
