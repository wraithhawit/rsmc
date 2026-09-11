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
        controllerFacings();
        texturesResolve("assets/rsmbac/models/block");
        texturesResolve("assets/rsmbac/athena");
        specularMaps();

        // Athena's per-block definition replaces EVERY variant of the block with one connected
        // cube: on the Controller that is the screen, the facing and the state, all gone. The
        // Controller connects through its models' optional loader instead (see controllerFacings).
        checks++;
        if (Files.exists(RESOURCES.resolve("assets/rsmbac/athena/controller.json"))) {
            failures++;
            System.out.println("FAILED athena/controller.json exists: it would replace all twelve"
                + " Controller variants with a plain cube");
        }

        System.out.println("asset checks: " + checks + " (" + BlockNames.all().size() + " blocks)");
        if (failures > 0) {
            System.out.println("FAIL (" + failures + ")");
            System.exit(1);
        }
        System.out.println("PASS");
    }

    /**
     * One Controller model per facing and state, each with its screen on its own face.
     *
     * <p>Athena's baked model never reads the {@code ModelState}, so a blockstate {@code "y": 90}
     * is silently dropped whenever Athena is installed and every Controller would face north. The
     * rotation therefore lives in the models, and the blockstate must carry none -- a {@code "y"}
     * there would double-rotate the vanilla fallback without anyone on ATM10 ever seeing it.
     *
     * <p>The loader must be the {@code optional} object form. A bare {@code "loader": "athena:athena"}
     * is a hard model-load failure for anyone without Athena, which is the whole reason tterrag's CTM
     * was ruled out for a standalone addon.
     */
    private static void controllerFacings() {
        final Path blockstate = RESOURCES.resolve("assets/rsmbac/blockstates/controller.json");
        absent("controller blockstate carries no rotation", blockstate, "\"y\"");
        for (final String state : new String[] {"unformed", "inactive", "active"}) {
            for (final String facing : new String[] {"north", "east", "south", "west"}) {
                final String name = "controller_" + state + "_" + facing;
                final Path model = RESOURCES.resolve("assets/rsmbac/models/block/" + name + ".json");
                contains(name + " is the blockstate's model for its variant", blockstate,
                    "\"facing=" + facing + ",state=" + state + "\": { \"model\": \"rsmbac:block/" + name + "\" }");
                contains(name + " puts the flat screen on its facing", model,
                    "\"" + facing + "\":" + " ".repeat(9 - facing.length())
                        + "\"rsmbac:block/controller_front_" + state + "\"");
                contains(name + " puts the connected screen on its facing", model,
                    "\"" + facing + "\": {\n      \"particle\":   \"rsmbac:block/ctm/controller_front_" + state + "/");
                contains(name + " loads Athena optionally", model,
                    "\"loader\": { \"id\": \"athena:athena\", \"optional\": true }");
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
     * has its texture, and the running screen has a map on every face Athena might draw.
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
        for (final String tile : new String[] {"particle", "empty", "center", "vertical", "horizontal"}) {
            exists("active screen glow map on connected tile " + tile,
                textures.resolve("ctm/controller_front_active/" + tile + "_s.png"));
        }
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
