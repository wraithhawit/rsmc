# Refined Storage Multiblock Crafter

A crafting structure for [Refined Storage](https://github.com/refinedmods/refinedstorage) 2.
Build a solid box of CPU and pattern storage blocks — anywhere from 1×1×1 to 16×16×16 — and it
crafts as one machine. Faster than a wall of autocrafters, and it is one shape instead of a
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

A **solid rectangular box**, 1 to 16 blocks on each axis, built from two block types:

- **CPU** — adds crafting speed. Four tiers, weighted like RS's storage blocks.
- **Pattern storage** — holds patterns. One tier.

Mix them however you like. The box must be *completely* filled: no holes, no bumps.

### Speed

A structure's rate is the plain sum of its CPU blocks' tier weights, in steps per tick:

| CPU tier | steps/tick each |
|---|---|
| 1K | 1 |
| 4K | 4 |
| 16K | 16 |
| 64K | 64 |

A plain sum, deliberately: you can count blocks and know the answer, and adding a block is never
a downgrade. For scale — one 1K CPU is 1 step/tick, ten times a bare autocrafter and still under
a fully upgraded one; three of them pass it. A 4×4×4 half filled with 1K CPUs is 32 steps/tick,
about thirteen maxed autocrafters.

### One rule that surprises people

Two separate boxes placed **flush against each other are one connected region**, that region is
not a box, and so *both* stop working. Leave a gap.

This falls out of the shape rule rather than being a special case, and it is kept on purpose. A
"any connected blob counts" rule cannot answer the two questions a player actually asks — did my
structure form, and which block is wrong — because there is no such thing as a missing block in a
shape with no expected form. A filled box gives every failure a coordinate to point at.

## Scope

rsmc accelerates **crafting patterns** — the ones RS runs internally.

**Processing patterns** push ingredients into a machine and wait for it, so their speed is the
machine's, not the crafter's. Those still want an ordinary pattern provider next to the machine.
You cannot parallelise a furnace by building a bigger cube.

## What works today

- [x] Structure detection and validation — flood fill, bounding box, solidity, size limits
- [x] Throughput model — CPU tiers, weight summing
- [x] Headless test suite for both, 25 cases (`./gradlew shapeCheck`)
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

Refined Storage's jar is not in this repository — it is not ours to redistribute. See
[SETUP.md](SETUP.md) for the one file you need to copy in.

## Licence

All Rights Reserved. Issues are welcome; forks and PRs are not currently permitted.
