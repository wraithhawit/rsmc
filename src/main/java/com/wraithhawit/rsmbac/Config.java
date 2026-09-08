package com.wraithhawit.rsmbac;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's tunables, in COMMON config because crafting happens on the logical server.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_CRAFTING_MILLIS_PER_TICK = BUILDER
        .comment(
            "How many milliseconds of a tick the structure may spend crafting. 0 disables the",
            "budget entirely and restores the original behaviour.",
            "",
            "WHAT THIS IS FOR. A maxed structure asks Refined Storage for 175,552 steps per tick and",
            "RS performs them synchronously before the tick can end. If that takes 150ms then the",
            "tick takes 150ms, the server runs at about 6 TPS, and every mob, machine and chunk in",
            "the world gets those same 6 ticks. The crafting is real work you asked for -- it is",
            "just all taken at once.",
            "",
            "WHAT IT COSTS. Almost nothing. Ticks currently run back to back and the crafter fills",
            "them, so it already gets ~100% of wall-clock time. Under a 45ms budget it gets",
            "45/50ths of it instead: roughly a tenth less crafting per second, for four times the",
            "tick rate. A 100k craft goes from about 34 seconds to about 37, and the world runs",
            "normally while it happens.",
            "",
            "NOTHING HAPPENS WHEN NOTHING IS WRONG. While ticks fit inside the budget the allowance",
            "climbs back to the structure's full rate and stays there, so a small structure, an idle",
            "one, or a big one on a server with headroom is completely unaffected. Only a structure",
            "that is currently overrunning gets throttled, and only while it does.",
            "",
            "Raise it to favour crafting speed, lower it to favour TPS. 50 is a whole tick and",
            "therefore no real limit; below about 10 the structure will crawl."
        )
        .defineInRange("maxCraftingMillisPerTick", 45, 0, 50);

    public static final ModConfigSpec.IntValue MAX_PATTERN_PUSH_MICROS_PER_TICK = BUILDER
        .comment(
            "How many MICROseconds of a tick may be spent handing patterns to the network. 0",
            "disables the budget and pushes the whole backlog in one tick.",
            "",
            "WHAT THIS IS FOR. When a structure loads, or gains or loses Pattern Storage, every",
            "pattern it holds has to be handed to Refined Storage again. Until 0.7.0 that was a",
            "fixed eight per refresh and a refresh happened once a second -- eight patterns a",
            "second, so a few thousand patterns took hours to become craftable. It was reported as",
            "exactly that: about seven hours.",
            "",
            "WHY MICROSECONDS. A push was measured at about 0.2us against the real Refined Storage",
            "classes (./gradlew pushCheck), so this default is roughly nine thousand patterns a",
            "tick -- far more than any structure holds. The unit is small because the work is: the",
            "whole point is that this bound is never the thing you are waiting on.",
            "",
            "A TIME BUDGET RATHER THAN A COUNT, because the cost of a push is not constant. It grows",
            "mildly with the number of patterns already registered, and it grows with how many",
            "screens are watching the network. A count tuned for one of those is wrong for the",
            "other; a time budget is right for both without knowing either.",
            "",
            "Lower it if handing patterns over ever costs you a visible tick. It should not."
        )
        .defineInRange("maxPatternPushMicrosPerTick", 2000, 0, 50_000);

    /** Cached: read every tick, on the only hot path in the mod. */
    public static volatile int maxCraftingMillisPerTick = 45;

    /** Cached for the same reason: the pattern drain is also attempted every tick. */
    public static volatile int maxPatternPushMicrosPerTick = 2000;

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    /** The budget in nanoseconds, or 0 when disabled. */
    public static long budgetNanos() {
        return (long) maxCraftingMillisPerTick * 1_000_000L;
    }

    /** The pattern-push budget in nanoseconds, or 0 when disabled. */
    public static long patternPushNanos() {
        return (long) maxPatternPushMicrosPerTick * 1_000L;
    }

    public static void refresh() {
        maxCraftingMillisPerTick = MAX_CRAFTING_MILLIS_PER_TICK.get();
        maxPatternPushMicrosPerTick = MAX_PATTERN_PUSH_MICROS_PER_TICK.get();
    }
}
