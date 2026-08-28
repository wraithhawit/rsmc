# Refined Storage - Multiblock Autocrafter

A crafting structure for [Refined Storage](https://github.com/refinedmods/refinedstorage) 2.
Build a hollow box of frame, casing, CPU and pattern storage blocks — up to 16×16×16 — and it
crafts as one machine. Faster than a wall of autocrafters, and it is one structure instead of a
hundred blocks.

## Download

**[Latest release →](https://github.com/wraithhawit/rsmc/releases/latest)**

Drop the `.jar` into your `mods` folder. **Delete any older `rsmbac-*.jar` first** — do not rely
on load order to pick the newest. Install it on the server; in singleplayer that means installing
it normally, since the structure and everything it crafts run on the integrated server.

Confirm it actually loaded before judging any result — the log line is `[rsmbac] vX.Y.Z loaded`,
and `/rsmbac info` prints the version on every path.

| | |
|---|---|
| Requires | Minecraft 1.21.1, NeoForge 21.1.234+, **Refined Storage 2.0.9+** |
| In game | Look at any block of the structure and run `/rsmbac info` — it describes the structure, or names the failure, its position and what that position wanted |
| Config | None. Nothing here is tunable yet |
| Worlds | A world built with 0.1.x loses these blocks: the mod id changed `rsmc` → `rsmbac` in 0.2.0 and no registry knows the old namespace |

> **Status: it crafts, and it is young.** The structure, the network node, the pattern screen
> and the recipes all work in a real world. The textures are still Reborn Storage's
> placeholders. See [What works today](#what-works-today).

Bug reports and questions go in [Issues](https://github.com/wraithhawit/rsmc/issues) — the repo
is still named `rsmc`, from before the rename.

## Why this can exist without patching Refined Storage

RS already models crafting throughput as a public interface. `StepBehavior` — `canStep(pattern)`
and `getSteps(pattern)`, both `@API STABLE` — is what a pattern provider answers to say how fast
it works, and it is the *entirety* of RS's speed ladder:

| autocrafter | steps per action | tick gate | steps/tick |
|---|---|---|---|
| no upgrades | 1 | every 10 | 0.10 |
| 4 speed upgrades | 5 | every 2 | **2.50** |

2.5 steps/tick is the ceiling no amount of stock RS gear passes. So a multiblock crafter needs no
task engine of its own, no scheduler, and **no mixins** — it needs to be a network node that
answers those two methods with numbers derived from how big the structure is. Planning, task
execution and the autocrafting monitor stay RS's, unchanged.

That is also why this is a separate mod rather than a feature of
[rstweaks](https://github.com/wraithhawit/rstweaks): rstweaks registers no content at all and is a
pure mixin mod. This one is blocks.

## The structure

A **hollow rectangular box** with a working core, up to 16 blocks on each axis, built from four
block types. Where a block goes is decided entirely by where it sits in the box:

| position | how many coordinates at an extreme | block |
|---|---|---|
| edges and corners | 2 or 3 | **Frame** |
| flat wall panels | 1 | **Casing** |
| interior | 0 | **CPU** or **Pattern Storage** |

The interior needs at least one CPU and at least one Pattern Storage.

### The 3×3×4 minimum is derived, not chosen

Nothing in the code states a minimum size. A box needs 3 on every axis before it has any interior
at all, and the core needs a CPU *and* a pattern storage — so an interior of one block is not
enough, and the smallest legal structure is the smallest box with an interior of two. That is
3×3×4, and it falls out of the interior requirement on its own.

(Reborn Storage's own floor is 27 blocks — 3×3×3 — and its effective minimum is 3×3×4 for exactly
this reason.)

### Speed

A structure's rate is the plain sum of its CPU blocks' tier weights, in steps per tick:

| CPU tier | steps/tick each |
|---|---|
| 1x | 1 |
| 4x | 4 |
| 16x | 16 |
| 64x | 64 |

A plain sum, deliberately: you can count blocks and know the answer, and adding a CPU is never a
downgrade. For scale — one 1x CPU is 1 step/tick, ten times a bare autocrafter and still under a
fully upgraded one; three of them pass it. A 5×5×5 with a 3×3×3 interior of 1x CPUs is 27
steps/tick, about eleven maxed autocrafters.

### One rule that surprises people

Two separate structures placed **flush against each other are one connected region**, that region
is not a box, and so *both* stop working. Leave a gap.

This falls out of the shape rule rather than being a special case, and it is kept on purpose. An
"any connected blob counts" rule cannot answer the two questions a player actually asks — did my
structure form, and which block is wrong — because there is no such thing as a missing block in a
shape with no expected form. A box gives every failure a coordinate *and* the role that position
needed to fill.

## Scope

rsmbac accelerates **crafting patterns** — the ones RS runs internally.

**Processing patterns** push ingredients into a machine and wait for it, so their speed is the
machine's, not the crafter's. Those still want an ordinary pattern provider next to the machine.
You cannot parallelise a furnace by building a bigger cube.

## What works today

- [x] Structure detection and validation — flood fill, bounding box, per-position roles, size limits
- [x] Throughput model — CPU tiers, weight summing
- [x] **The eight blocks** — Frame, Casing, Controller, four CPU tiers, Pattern Storage, with
      items, creative tab, loot tables and block entities where they are needed
- [x] The network node — `PatternProvider` answering with the structure's `StepBehavior` (#2)
- [x] Structure lifecycle: form, break, tell the player why (#3) — the offending block is
      outlined through the walls, not just named as a coordinate
- [x] Pattern GUI (#4), searchable by what a pattern makes or uses
- [x] Recipes for every block (#5), every ingredient id verified against the real RS jar
- [x] Headless suites, run on every `build` — 30 shape cases, 71 asset checks, 49 recipe
      scenarios, 15 refresh scenarios
- [x] Gametests against a real level — 14, run by `runGameTestServer`
- [ ] Real textures — the ones in the repo are Reborn Storage's placeholders (#7)
- [ ] Refined Storage's colouring system (#8), which is not cosmetic in RS: differently
      coloured blocks refuse to connect
- [ ] Pattern encoding built into the manager screen (#10)

## Building

```
./gradlew build              # compiles, and runs all four headless suites
./gradlew shapeCheck         # structure rules, no Minecraft launch
./gradlew assetCheck         # every block has its models, loot table and lang
./gradlew recipeCheck        # every recipe ingredient exists in the real RS jar
./gradlew refreshCheck       # the change-driven rescan schedule
./gradlew runGameTestServer  # structure rules against a real level
./gradlew runClient          # dev client, with Refined Storage staged into run/client/mods
```

## Prior art, stated plainly

The structure and much of the feel follow **[Reborn Storage](https://github.com/modmuss50/RebornStorage)**'s
multiblock crafter — the shell of frames and casing around a core of CPUs and pattern storage, and
behaviours like *clicking any block in the structure opens the GUI*. Reborn Storage was itself
openly inspired by **Applied Energistics'** crafting CPU, so the whole family looks alike. That is
the genre.

**This is a recode, not a port.** No Reborn Storage code is here and none will be. The block
textures currently in the repo *are* theirs, as temporary placeholders, marked as such in
`assets/rsmbac/textures/block/PLACEHOLDERS.md` and tracked by issue #7.

### And the recode is the point

Reborn Storage's multiblock is a persistent controller object with an assembly state machine
(`Disassembled` / `Assembled` / `Paused`), a per-world controller registry, controllers that merge
with each other, and — the part that matters — **the pattern inventories stored on the controller**.

That shape has a failure mode built in: the controller's belief about the structure can disagree
with the world. Chunks load in an order nobody controls, parts attach out of order, two controllers
meet and one swallows the other. When the object that can drift is also the one holding your
patterns, a desync is data loss.

rsmbac has **no controller object, no assembly state, no registry and no merge**:

- the structure is **derived from the world** every time it is needed, never remembered, so there is
  no stored belief that can be wrong
- patterns live in the **Pattern Storage block entities**, so breaking the structure cannot lose
  them — they never left the blocks

The cost is recomputation, bounded at 4096 positions and run on change rather than per tick.

Refined Storage, Cable Tiers and Reborn Storage are all MIT. See
[ATTRIBUTION.md](ATTRIBUTION.md).

## Licence

All Rights Reserved. Issues are welcome; forks and PRs are not currently permitted.
