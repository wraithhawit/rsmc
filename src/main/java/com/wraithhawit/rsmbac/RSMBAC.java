package com.wraithhawit.rsmbac;

import com.mojang.logging.LogUtils;

import com.wraithhawit.rsmbac.content.RsmcBlockEntities;
import com.wraithhawit.rsmbac.content.RsmcBlocks;
import com.wraithhawit.rsmbac.content.RsmcCapabilities;
import com.wraithhawit.rsmbac.client.RsmcClient;
import com.wraithhawit.rsmbac.content.RsmcCreativeTab;
import com.wraithhawit.rsmbac.content.RsmcMenus;
import com.wraithhawit.rsmbac.content.RsmcItems;
import com.wraithhawit.rsmbac.test.StructureGameTests;

import com.wraithhawit.rsmbac.network.RsmcPayloads;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

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
 * <p>Sibling mod to rstweaks, but independent of it: rsmbac requires Refined Storage and nothing
 * else.
 */
@Mod(RSMBAC.MODID)
public class RSMBAC {
    public static final String MODID = "rsmbac";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Build version, read from the jar's own metadata rather than duplicated as a constant. The
     * same rule rstweaks arrived at the hard way: a hardcoded copy drifts from
     * {@code gradle.properties} exactly when it matters, which is while working out whether the
     * running jar is the one just built.
     */
    public static String version = "unknown";

    public RSMBAC(final IEventBus modEventBus, final ModContainer modContainer) {
        version = modContainer.getModInfo().getVersion().toString();
        // Order matters here, and only in one place: RsmcItems and RsmcBlockEntities both read
        // RsmcBlocks' fields while their own static initialisers run, so blocks must be the first
        // of the three touched. Registering them in this order is what guarantees that -- the
        // class loads when it is first referenced.
        RsmcBlocks.BLOCKS.register(modEventBus);
        RsmcItems.ITEMS.register(modEventBus);
        RsmcBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        RsmcMenus.MENUS.register(modEventBus);
        RsmcCreativeTab.TABS.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(RegisterMenuScreensEvent.class, RsmcClient::registerScreens);
            modEventBus.addListener(FMLClientSetupEvent.class, RsmcClient::setup);
        }
        NeoForge.EVENT_BUS.register(StructureInfoCommand.class);
        // Only fires when -Dneoforge.enabledGameTestNamespaces includes this mod, which the
        // runGameTestServer run configuration sets.
        modEventBus.addListener(RegisterCapabilitiesEvent.class, RsmcCapabilities::register);
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, RsmcPayloads::register);
        modEventBus.addListener(RegisterGameTestsEvent.class,
            event -> event.register(StructureGameTests.class));
        LOGGER.info("[rsmbac] v{} loaded", version);
    }
}
