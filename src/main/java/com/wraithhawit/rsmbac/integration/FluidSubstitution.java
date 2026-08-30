package com.wraithhawit.rsmbac.integration;

import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;

import com.wraithhawit.rsmbac.menu.StructurePatterns;

import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * The gate in front of {@link FluidSubstitutionPatterns}, and the only class the rest of the mod
 * touches.
 *
 * <p><strong>Nothing here names a Refined Fluid Substitution type.</strong> That is the whole point
 * of splitting it: the JVM resolves a class when a method that references it is first executed, so
 * as long as every RFS reference lives in {@link FluidSubstitutionPatterns} and every call to it is
 * behind {@link #available()}, a pack without RFS never loads a class that is not there. A single
 * combined class would fail on its own {@code available()} check.
 *
 * <p>Not a split package either — see the module rules; a class of ours in someone else's package
 * fails at module resolution on NeoForge, and it compiles perfectly first.
 */
public final class FluidSubstitution {
    private static final String MOD_ID = "refinedfluidsubstitution";

    private static Boolean present;

    private FluidSubstitution() {
    }

    /** Whether Refined Fluid Substitution is installed. Answered once. */
    public static boolean available() {
        Boolean cached = present;
        if (cached == null) {
            cached = ModList.get().isLoaded(MOD_ID);
            present = cached;
        }
        return cached;
    }

    /**
     * Resolves every fluid-substitution pattern in the structure and pushes it, with its helper
     * patterns, into the node.
     *
     * <p>Does nothing at all when RFS is absent.
     */
    public static void refresh(final Level level,
                               final PatternProviderNetworkNode node,
                               final StructurePatterns patterns) {
        if (!available()) {
            return;
        }
        FluidSubstitutionPatterns.refresh(level, node, patterns);
    }
}
