package com.wraithhawit.rsmbac.block;

import net.minecraft.util.StringRepresentable;

/**
 * What the Controller's screen is showing, and therefore what the player can tell at a glance.
 *
 * <p>Three states because there are exactly three things worth distinguishing, and until now they
 * all looked identical: a box that is not a valid structure, a valid structure that is not plugged
 * into anything, and one that is live. "I built it and nothing happened" was indistinguishable from
 * "I built it wrong".
 */
public enum ControllerState implements StringRepresentable {
    /** Not a valid structure. Bare grey panel -- the screen is not even on. */
    UNFORMED("unformed"),
    /** A valid structure with no network. Dark screen: it is a machine, and it is switched off. */
    INACTIVE("inactive"),
    /** Valid and attached to a network. Blue screen. */
    ACTIVE("active");

    private final String name;

    ControllerState(final String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
