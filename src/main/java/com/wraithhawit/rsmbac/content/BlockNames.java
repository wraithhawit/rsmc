package com.wraithhawit.rsmbac.content;

import java.util.ArrayList;
import java.util.List;

import com.wraithhawit.rsmbac.structure.CpuTier;

/**
 * The registry name of every block rsmbac adds, and the only list of them.
 *
 * <p>Free of Minecraft types on purpose, so that two things can both read it: {@link RsmcBlocks},
 * which registers them, and {@code HeadlessAssetCheck}, which proves each one has a blockstate, a
 * model, an item model, a loot table and a translation.
 *
 * <p>That is the entire reason this class exists. A block whose loot table is missing looks fine
 * until someone breaks it and it drops nothing; a block whose model is missing is a purple cube
 * nobody notices until they open the creative tab. Both are invisible to the compiler and to every
 * test that does not launch the game. With one list, the check cannot be checking a different set
 * of blocks than the one being registered.
 */
public final class BlockNames {
    public static final String FRAME = "frame";
    public static final String CASING = "casing";
    public static final String CONTROLLER = "controller";
    public static final String PORT = "port";
    public static final String PATTERN_STORAGE = "pattern_storage";

    private BlockNames() {
    }

    /** Every block, in the order they appear in the creative tab: shell, then what goes inside. */
    public static List<String> all() {
        final List<String> names = new ArrayList<>();
        names.add(FRAME);
        names.add(CASING);
        names.add(CONTROLLER);
        names.add(PORT);
        for (final CpuTier tier : CpuTier.values()) {
            names.add(tier.blockName());
        }
        names.add(PATTERN_STORAGE);
        return names;
    }

    /**
     * The block texture a given block draws with: its own name, except the Controller.
     *
     * <p>Until 0.11.0 every CPU tier mapped to one shared {@code cpu.png}. That mapping outlived
     * 0.9.1, when each tier got its own file, and only surfaced when the placeholder was deleted.
     */
    public static String textureOf(final String blockName) {
        // The Controller has three faces, one per screen state. The asset check tracks the unformed
        // one here; the other two are checked by name.
        return CONTROLLER.equals(blockName) ? "controller_front_unformed" : blockName;
    }
}
