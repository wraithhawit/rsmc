# Refined Storage Multiblock Crafter

A crafting structure for [Refined Storage](https://github.com/refinedmods/refinedstorage) 2.
Build a hollow box of frame, casing, CPU and pattern storage blocks — up to 16×16×16 — and it
crafts as one machine. Faster than a wall of autocrafters, and it is one structure instead of a
hundred blocks.

Minecraft 1.21.1, NeoForge. Requires Refined Storage 2.0.9+.

> **Status: early.** The shape rules and the throughput model are implemented and tested; the
> blocks, the pattern GUI and the network node are not written yet. See
> [What works today](#what-works-today).

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
| 1K | 1 |
| 4K | 4 |
| 16K | 16 |
| 64K | 64 |

A plain sum, deliberately: you can count blocks and know the answer, and adding a CPU is never a
downgrade. For scale — one 1K CPU is 1 step/tick, ten times a bare autocrafter and still under a
fully upgraded one; three of them pass it. A 5×5×5 with a 3×3×3 interior of 1K CPUs is 27
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

rsmc accelerates **crafting patterns** — the ones RS runs internally.

**Processing patterns** push ingredients into a machine and wait for it, so their speed is the
machine's, not the crafter's. Those still want an ordinary pattern provider next to the machine.
You cannot parallelise a furnace by building a bigger cube.

## What works today

- [x] Structure detection and validation — flood fill, bounding box, per-position roles, size limits
- [x] Throughput model — CPU tiers, weight summing
- [x] Headless test suite for both, 24 cases (`./gradlew shapeCheck`)
- [ ] Blocks, block entities, models, textures
- [ ] The network node — `PatternProvider` with the structure's `StepBehavior`
- [ ] Pattern GUI
- [ ] Recipes

## Building

```
./gradlew build          # compiles, and runs the shape checks
./gradlew shapeCheck     # just the shape checks, no Minecraft launch
./gradlew runClient      # dev client, with Refined Storage staged into run/client/mods
```

Refined Storage's jar is not in this repository — a jar is a build input, not source. See
[SETUP.md](SETUP.md) for the one file you need to copy in.

## Prior art, stated plainly

The structure follows **[Reborn Storage](https://github.com/modmuss50/RebornStorage)**'s multiblock
crafter — the shell of frames and casing around a core of CPUs and pattern storage. Reborn Storage
was itself openly inspired by **Applied Energistics'** crafting CPU, so the whole family looks
alike. That is the genre.

What is *not* shared: **no code and no assets**. This is written from scratch against Refined
Storage 2's `StepBehavior` API — Reborn Storage is an RS1 addon with its own multiblock framework,
and none of it is here. Textures are drawn for this mod. See [ATTRIBUTION.md](ATTRIBUTION.md) for
what the art derives from and the licences that permit it.

Reborn Storage is MIT, as are Refined Storage and Cable Tiers.

## Licence

All Rights Reserved. Issues are welcome; forks and PRs are not currently permitted.
