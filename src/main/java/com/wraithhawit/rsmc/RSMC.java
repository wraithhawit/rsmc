package com.wraithhawit.rsmc;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;

/**
 * Refined Storage Multiblock Crafter.
 *
 * <p>A crafting structure for Refined Storage: a solid box of CPU and pattern storage blocks,
 * anywhere from 1x1x1 to 16x16x16, that crafts far faster than a wall of autocrafters and takes up
 * one shape instead of a hundred blocks.
 *
 * <h2>Why this is an addon and not a mixin mod</h2>
 *
 * <p>Refined Storage already models crafting throughput as an interface. {@code StepBehavior} --
 * {@code canStep(pattern)} and {@code getSteps(pattern)}, both {@code @API STABLE} -- is what a
 * pattern provider answers to say how fast it works, and it is the whole of RS's speed-upgrade
 * ladder: a stock autocrafter reports 1 step every 10 ticks bare and 5 steps every 2 ticks with
 * four upgrades, which is the entire 0.1 to 2.5 steps/tick range the mod offers.
 *
 * <p>So a multiblock crafter does not need its own task engine, its own scheduler, or a single
 * mixin. It needs to be a network node that answers those two methods with numbers derived from
 * how big the structure is. Everything else -- planning, task execution, the autocrafting monitor
 * -- is RS's, unchanged.
 *
 * <p><strong>Scope, stated once:</strong> this accelerates <em>crafting</em> patterns, the ones RS
 * runs internally. Processing patterns push ingredients into a machine and wait for it, so their
 * speed is the machine's, not the crafter's; those still want an ordinary pattern provider next to
 * the machine. You cannot parallelise a furnace by building a bigger cube.
 *
 * <p>Sibling mod to rstweaks, but independent of it: rsmc requires Refined Storage and nothing
 * else.
 */
@Mod(RSMC.MODID)
public class RSMC {
    public static final String MODID = "rsmc";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Build version, read from the jar's own metadata rather than duplicated as a constant. The
     * same rule rstweaks arrived at the hard way: a hardcoded copy drifts from
     * {@code gradle.properties} exactly when it matters, which is while working out whether the
     * running jar is the one just built.
     */
    public static String version = "unknown";

    public RSMC(final IEventBus modEventBus, final ModContainer modContainer) {
        version = modContainer.getModInfo().getVersion().toString();
        LOGGER.info("[rsmc] v{} loaded", version);
    }
}
