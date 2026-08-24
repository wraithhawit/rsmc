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
        for (final String model : new String[] {"controller", "controller_inactive", "controller_active"}) {
            exists("controller model " + model,
                RESOURCES.resolve("assets/rsmbac/models/block/" + model + ".json"));
        }

        System.out.println("asset checks: " + checks + " (" + BlockNames.all().size() + " blocks)");
        if (failures > 0) {
            System.out.println("FAIL (" + failures + ")");
            System.exit(1);
        }
        System.out.println("PASS");
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
            if (!Files.exists(path) || !Files.readString(path, StandardCharsets.UTF_8).contains(needle)) {
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
