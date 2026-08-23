package com.wraithhawit.rsmc.test;

import com.wraithhawit.rsmc.content.BlockNames;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Proves every block is craftable and that every ingredient actually exists.
 *
 * <h2>Why an ingredient typo needs a test</h2>
 *
 * <p>A recipe naming an item that does not exist does not crash and is not a compile error.
 * Minecraft logs one line during datapack load, drops that recipe, and carries on. The only
 * symptom is a block nobody can make, discovered by a player, in a world, after the jar shipped.
 *
 * <p>That is the same class of failure {@link HeadlessAssetCheck} exists for: silent, invisible
 * to the compiler, and cheap to catch here.
 *
 * <h2>What is checked, and what is skipped</h2>
 *
 * <p>Ingredient existence is checked against the real Refined Storage jar in {@code libs/}. That
 * directory is gitignored, so a fresh clone has none of it -- the Refined Storage half is
 * therefore SKIPPED rather than failed when the jar is absent, and the run says so. Everything
 * checkable from the repository alone is always checked.
 *
 * <p>Vanilla ingredients are taken on trust: there is no vanilla jar in {@code libs/} to check
 * against, and a wrong vanilla id is far likelier to be noticed by eye than a wrong RS one.
 */
public final class HeadlessRecipeCheck {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path RECIPES = RESOURCES.resolve("data/rsmc/recipe");
    private static final Path LIBS = Path.of("libs");

    /** Matches {@code "item": "namespace:path"} and the recipe result's {@code "id"}. */
    private static final Pattern ITEM_REF =
        Pattern.compile("\"(?:item|id)\"\\s*:\\s*\"([a-z0-9_.-]+):([a-z0-9_./-]+)\"");

    private static final Set<String> TRUSTED_NAMESPACES = Set.of("minecraft");

    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;
    private static int skipped;

    private HeadlessRecipeCheck() {
    }

    public static void main(final String[] args) throws IOException {
        final Set<String> refinedStorageItems = readRefinedStorageItems();
        for (final String block : BlockNames.all()) {
            final Path recipe = RECIPES.resolve(block + ".json");
            if (!expect("recipe exists for " + block, Files.exists(recipe))) {
                continue;
            }
            checkIngredients(block, Files.readString(recipe, StandardCharsets.UTF_8),
                refinedStorageItems);
        }
        checkNoOrphanRecipes();

        System.out.printf("scenarios: %d%s%n", checks,
            skipped == 0 ? "" : "  (" + skipped + " skipped: no Refined Storage jar in libs/)");
        if (FAILURES.isEmpty()) {
            System.out.println("PASS");
            return;
        }
        FAILURES.forEach(failure -> System.out.println("FAIL  " + failure));
        System.out.printf("%d of %d scenarios failed%n", FAILURES.size(), checks);
        System.exit(1);
    }

    private static void checkIngredients(final String block, final String json,
                                         final Set<String> refinedStorageItems) {
        final Matcher matcher = ITEM_REF.matcher(json);
        while (matcher.find()) {
            final String namespace = matcher.group(1);
            final String path = matcher.group(2);
            final String id = namespace + ":" + path;
            switch (namespace) {
                case "rsmc" -> expect(block + " references " + id,
                    BlockNames.all().contains(path));
                case "refinedstorage" -> {
                    if (refinedStorageItems.isEmpty()) {
                        ++skipped;
                    } else {
                        expect(block + " references " + id, refinedStorageItems.contains(path));
                    }
                }
                default -> {
                    if (!TRUSTED_NAMESPACES.contains(namespace)) {
                        // rsmc depends on Refined Storage and nothing else. An ingredient from a
                        // third mod would make the block uncraftable for anyone without that mod,
                        // which is a dependency decision rather than a recipe detail.
                        expect(block + " uses an unexpected mod: " + id, false);
                    }
                }
            }
        }
    }

    /** A recipe for a block that no longer exists is dead weight that still loads. */
    private static void checkNoOrphanRecipes() throws IOException {
        if (!Files.isDirectory(RECIPES)) {
            expect("the recipe directory exists", false);
            return;
        }
        final List<Path> files;
        try (var stream = Files.list(RECIPES)) {
            files = stream.toList();
        }
        for (final Path recipe : files) {
            final String name = recipe.getFileName().toString().replace(".json", "");
            expect("recipe " + name + " belongs to a real block", BlockNames.all().contains(name));
        }
    }

    /**
     * Every item and block id Refined Storage registers, read from its lang file.
     *
     * <p>The lang file rather than the registry, because this runs in a plain JVM with no
     * Minecraft loaded -- and everything RS ships has a translation key. Returns empty when there
     * is no jar, which the caller treats as "skip" rather than "fail".
     */
    private static Set<String> readRefinedStorageItems() throws IOException {
        if (!Files.isDirectory(LIBS)) {
            return Set.of();
        }
        final Path jar;
        try (var stream = Files.list(LIBS)) {
            jar = stream
                .filter(path -> path.getFileName().toString().startsWith("refinedstorage-neoforge-"))
                .findFirst()
                .orElse(null);
        }
        if (jar == null) {
            return Set.of();
        }
        final Set<String> items = new HashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            final ZipEntry lang = zip.getEntry("assets/refinedstorage/lang/en_us.json");
            if (lang == null) {
                return Set.of();
            }
            final String json = new String(zip.getInputStream(lang).readAllBytes(),
                StandardCharsets.UTF_8);
            final Matcher matcher = Pattern
                .compile("\"(?:item|block)\\.refinedstorage\\.([a-z0-9_./]+)\"")
                .matcher(json);
            while (matcher.find()) {
                items.add(matcher.group(1));
            }
        }
        return items;
    }

    private static boolean expect(final String what, final boolean condition) {
        ++checks;
        if (!condition) {
            FAILURES.add(what);
        }
        return condition;
    }
}
