package com.wraithhawit.rsmbac.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.wraithhawit.rsmbac.content.BlockNames;

/**
 * Proves every registered block has the five files it needs: {@code ./gradlew assetCheck}.
 *
 * <p>A block missing its loot table looks perfectly fine until someone breaks it and it drops
 * nothing. A block missing its model is a purple cube nobody sees until they open the creative
 * tab. Neither is a compile error, and neither shows up in any test that does not launch the game
 * and look at the block -- which is exactly the kind of bug that ships.
 *
 * <p>So this reads the resources directory directly. Not a substitute for launching the game, but
 * it turns "did I remember all five files for all seven blocks" from something a person checks
 * into something the build checks.
 *
 * <p>Both this and the block registry read {@link BlockNames}, so the check cannot be checking a
 * different set of blocks than the one being registered.
 */
public final class HeadlessAssetCheck {
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private static int checks;
    private static int failures;

    private HeadlessAssetCheck() {
    }

    public static void main(final String[] args) throws IOException {
        final String lang = readLang();
        final Path pickaxe = RESOURCES.resolve("data/minecraft/tags/block/mineable/pickaxe.json");
        for (final String block : BlockNames.all()) {
            // Every rsmbac block is requiresCorrectToolForDrops. That is a promise the block makes
            // and this tag is the only thing that keeps it: with the block absent from every
            // mineable tag, NO tool is the correct one, so the block drops nothing when mined --
            // with any tool, forever. It is silent, it is not a compile error, and the loot table
            // is perfectly valid the whole time. It was written exactly this way and nearly shipped.
            contains("in the pickaxe mineable tag", pickaxe, "\"rsmbac:" + block + "\"");
            exists("blockstate", RESOURCES.resolve("assets/rsmbac/blockstates/" + block + ".json"));
            exists("block model", RESOURCES.resolve("assets/rsmbac/models/block/" + block + ".json"));
            exists("item model", RESOURCES.resolve("assets/rsmbac/models/item/" + block + ".json"));
            exists("loot table",
                RESOURCES.resolve("data/rsmbac/loot_table/blocks/" + block + ".json"));
            exists("texture",
                RESOURCES.resolve("assets/rsmbac/textures/block/"
                    + BlockNames.textureOf(block) + ".png"));
            translated(block, lang);
            // A loot table naming the wrong item is the one failure that survives every "the file
            // is there" check and still drops the wrong block.
            contains("loot table names its own block",
                RESOURCES.resolve("data/rsmbac/loot_table/blocks/" + block + ".json"),
                "\"rsmbac:" + block + "\"");
        }
        translated("itemGroup", lang, "itemGroup.rsmbac");

        // The Controller has one face per screen state and a model for each. textureOf() only
        // names the unformed one, so without this the other two could be deleted or renamed and
        // nothing would notice until a structure formed in game and the face went missing.
        for (final String state : new String[] {"unformed", "inactive", "active"}) {
            exists("controller " + state + " face",
                RESOURCES.resolve("assets/rsmbac/textures/block/controller_front_" + state + ".png"));
        }
        controllerModels();
        texturesResolve("assets/rsmbac/models/block");
        texturesResolve("assets/rsmbac/athena");
        specularMaps();
        framedInTheWall();

        System.out.println("asset checks: " + checks + " (" + BlockNames.all().size() + " blocks)");
        if (failures > 0) {
            System.out.println("FAIL (" + failures + ")");
            System.exit(1);
        }
        System.out.println("PASS");
    }

    /**
     * One orientable model per screen state, each showing its own face, rotated by the blockstate.
     * Each facing's rotation is pinned too: a variant that lost its {@code "y"} would face north
     * and still render perfectly, so nothing short of placing one facing east would notice.
     */
    private static void controllerModels() {
        final Path blockstate = RESOURCES.resolve("assets/rsmbac/blockstates/controller.json");
        final String[] models = {"controller", "controller_inactive", "controller_active"};
        final String[] states = {"unformed", "inactive", "active"};
        final String[] rotations = {"", ", \"y\": 90", ", \"y\": 180", ", \"y\": 270"};
        final String[] facings = {"north", "east", "south", "west"};
        for (int s = 0; s < states.length; s++) {
            contains(models[s] + " shows the " + states[s] + " screen",
                RESOURCES.resolve("assets/rsmbac/models/block/" + models[s] + ".json"),
                "\"front\": \"rsmbac:block/controller_front_" + states[s] + "\"");
            for (int f = 0; f < facings.length; f++) {
                contains("controller " + facings[f] + "/" + states[s] + " variant", blockstate,
                    "\"facing=" + facings[f] + ",state=" + states[s] + "\": { \"model\": \"rsmbac:block/"
                        + models[s] + "\"" + rotations[f] + " }");
            }
        }
    }

    /**
     * The artist's call in 0.11.1: the Controller and the Port keep their own border, and only the
     * Casing drops its border against them. The Casing half is the {@code rsmbac:shell} tag, which
     * both stay in. The other half is that neither draws connected tiles -- no Athena definition,
     * and no model reaching Athena's loader. 0.10.0-0.11.0 did the opposite; this pins the reversal
     * so a well-meant "make them join" does not silently undo a design decision.
     */
    private static void framedInTheWall() throws IOException {
        contains("controller is in rsmbac:shell, so the casing joins it",
            RESOURCES.resolve("data/rsmbac/tags/block/shell.json"), "\"rsmbac:controller\"");
        contains("port is in rsmbac:shell, so the casing joins it",
            RESOURCES.resolve("data/rsmbac/tags/block/shell.json"), "\"rsmbac:port\"");
        for (final String block : new String[] {"controller", "port"}) {
            checks++;
            if (Files.exists(RESOURCES.resolve("assets/rsmbac/athena/" + block + ".json"))) {
                failures++;
                System.out.println("FAILED athena/" + block + ".json exists: the " + block
                    + " keeps its own border" + (block.equals("controller")
                        ? ", and the definition would also replace all twelve variants with one cube" : ""));
            }
        }
        try (var files = Files.list(RESOURCES.resolve("assets/rsmbac/models/block"))) {
            for (final Path model : files.toList()) {
                absent(model.getFileName() + " does not use Athena's loader", model, "athena:athena");
            }
        }
    }

    private static final java.util.regex.Pattern TEXTURE_REF =
        java.util.regex.Pattern.compile("\"rsmbac:(block/[a-z0-9_/]+)\"");

    /**
     * Every {@code rsmbac:block/...} a model or an Athena definition names must be a real texture.
     *
     * <p>This is the check that makes it safe to ship connected textures at all. Athena renders
     * whatever its JSON points at, so a definition that lands before its tiles is a wall of
     * missing-texture blocks for every player with Athena -- on ATM10, all of them -- and nothing
     * short of looking at one in game notices.
     */
    private static void texturesResolve(final String folder) throws IOException {
        final Path dir = RESOURCES.resolve(folder);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (final Path json : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                final var matcher = TEXTURE_REF.matcher(Files.readString(json, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    exists(json.getFileName() + " -> " + matcher.group(1),
                        RESOURCES.resolve("assets/rsmbac/textures/" + matcher.group(1) + ".png"));
                }
            }
        }
    }

    /**
     * labPBR glow maps. Iris pairs {@code foo_s.png} with {@code foo.png} by name and nothing else,
     * so a map whose texture was renamed is not an error anywhere -- the screen just stops glowing
     * under shaders, which nobody without shaders will ever see. Hence both directions: every map
     * has its texture, and the running screen has its map.
     */
    private static void specularMaps() throws IOException {
        final Path textures = RESOURCES.resolve("assets/rsmbac/textures/block");
        try (var files = Files.walk(textures)) {
            for (final Path map : files.filter(p -> p.getFileName().toString().endsWith("_s.png")).toList()) {
                final String name = map.getFileName().toString();
                exists("texture for glow map " + textures.relativize(map),
                    map.resolveSibling(name.substring(0, name.length() - "_s.png".length()) + ".png"));
            }
        }
        exists("active screen glow map", textures.resolve("controller_front_active_s.png"));
    }

    private static void absent(final String what, final Path path, final String needle) {
        checks++;
        try {
            if (!Files.exists(path) || Files.readString(path, StandardCharsets.UTF_8).contains(needle)) {
                failures++;
                System.out.println("FAILED " + what + ": " + path + " contains " + needle);
            }
        } catch (final IOException e) {
            failures++;
            System.out.println("FAILED " + what + ": could not read " + path + " -- " + e);
        }
    }

    private static String readLang() throws IOException {
        final Path path = RESOURCES.resolve("assets/rsmbac/lang/en_us.json");
        if (!Files.exists(path)) {
            System.out.println("FAILED lang: no en_us.json at " + path.toAbsolutePath());
            failures++;
            return "";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void exists(final String what, final Path path) {
        checks++;
        if (!Files.exists(path)) {
            failures++;
            System.out.println("FAILED missing " + what + ": " + path);
        }
    }

    private static void contains(final String what, final Path path, final String needle) {
        checks++;
        try {
            // LF-normalised: git on Windows checks these out CRLF, and a needle spanning a line
            // would then fail on a clean clone while passing here.
            if (!Files.exists(path)
                || !Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n").contains(needle)) {
                failures++;
                System.out.println("FAILED " + what + ": " + path + " does not contain " + needle);
            }
        } catch (final IOException e) {
            failures++;
            System.out.println("FAILED " + what + ": could not read " + path + " -- " + e);
        }
    }

    private static void translated(final String block, final String lang) {
        translated(block, lang, "block.rsmbac." + block);
    }

    private static void translated(final String what, final String lang, final String key) {
        checks++;
        if (!lang.contains("\"" + key + "\"")) {
            failures++;
            System.out.println("FAILED untranslated " + what + ": no key " + key + " in en_us.json");
        }
    }

    /** Exposed so the shape and asset suites can share a summary line if they are ever merged. */
    public static List<String> blocks() {
        return BlockNames.all();
    }
}
