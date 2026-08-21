# Changelog

The version prints at startup (`[rsmc] vX.Y.Z loaded`), so a test result can always be tied to an
exact build.

`VERSIONS.txt` is the short form of this file — one or two lines per version. Both are maintained;
this one carries the reasoning, that one is the index.

## 0.2.0

**The structure became a shell with a core**, following Reborn Storage's multiblock crafter rather
than the solid box 0.1.0 shipped with.

The prompt was noticing that a solid cube of two block types is essentially AE2's crafting CPU.
Checking what Reborn Storage actually does — from the jar, not from a wiki — settled it: it is
**four** blocks, not two, and hollow, not solid.

- edges and corners: **Frame**
- flat wall panels: **Casing**
- interior: **CPU** or **Pattern Storage**, at least one of each

A position's role is decided by *how many of its coordinates sit at an extreme of the box* — three
for a corner, two for an edge, one for a wall, none for the interior. Counting extremes is the
whole rule, so "corners are frames too" needs no special case.

**The 3×3×4 minimum is derived, not declared.** Nothing in the code states a minimum size. A box
needs 3 on every axis before it has an interior at all, and the core needs a CPU *and* a pattern
storage, so an interior of one is not enough — the smallest legal structure is the smallest box
with an interior of two. `NO_CPU` and `NO_PATTERN_STORAGE` enforce it on their own, which means the
rule cannot drift out of step with a constant that claims to state it.

Confirmed against Reborn Storage 5.0.7: `getMinimumNumberOfBlocksForAssembledMachine()` returns 27
— a 3×3×3 floor — and its effective minimum is 3×3×4 for exactly this reason. Its min and max sizes
are configurable, and it charges energy per block by type (`FRAME_COST`, `HEAT_COST`, `CPU_COST`,
`STORAGE_COST`), which is worth copying when the node lands.

**One test stopped pinning its failure**, deliberately. An overhang widens the bounding box, which
moves *every* position's role — so the enlarged box is wrong in many places at once, and which
fault surfaces first is scan order. Asserting one would be asserting the order of three nested
loops. It now asserts only that nothing forms, with the reasoning written next to it.

**On AE2:** the resemblance is not a problem to design around. Reborn Storage was itself openly
inspired by AE2's crafting CPU and shipped alongside it for years. The genre looks like this.

Nothing in this release is code or assets from Reborn Storage — the shape only, which is an idea
rather than a work. See `ATTRIBUTION.md`, and note that Refined Storage, Cable Tiers and Reborn
Storage are all MIT.

## 0.1.0

**Project set up, and the design question answered before any of it was built.**

Issue #13 on rstweaks proposed a multiblock crafter and was honest that it might be too big: a
multiblock means blocks, block entities, models, a validation system, a GUI and network sync, and
rstweaks registers no content at all. It also asked the right question — *is the goal throughput,
or fewer blocks placed?* — and noted that if it is throughput, the cheaper answer might be
improving how tasks spread across existing autocrafters.

Reading RS's autocrafting settled it. `StepBehavior` is a two-method `@API STABLE` interface that
`PatternProvider` extends, and it is RS's *entire* throughput model:

| autocrafter | steps per action | tick gate | steps/tick |
|---|---|---|---|
| no upgrades | 1 | every 10 | 0.10 |
| 4 speed upgrades | 5 | every 2 | 2.50 |

So the ceiling on stock RS crafting speed is 2.5 steps/tick, and the whole speed-upgrade ladder is
two numbers a pattern provider reports about itself. A multiblock crafter therefore needs **no task
engine, no scheduler and no mixins** — it needs to be a network node that answers those two methods
with numbers derived from the structure's size. Planning, task execution and the autocrafting
monitor all stay RS's, untouched. That is also what makes this a separate mod rather than an
rstweaks feature: rstweaks is a pure mixin mod, and this one is blocks.

**The shape rule: a solid rectangular box, 1 to 16 per axis.** Built from CPU blocks (speed, four
tiers) and pattern storage blocks (capacity, one tier), mixed freely.

Solid box rather than any connected blob, because a shape rule has to answer two questions a
player will ask — did my structure form, and which block is wrong — and an arbitrary blob answers
neither. There is no such thing as a missing block in a shape with no expected form. A filled box
gives every failure a coordinate.

The consequence is deliberate and is pinned as a test so nobody quietly "fixes" it: **two boxes
placed flush against each other are one connected region, that region is not a box, and both stop
working.** Leave a gap.

**Speed is a plain sum**, in steps per tick: 1K = 1, 4K = 4, 16K = 16, 64K = 64 per block. Named
and weighted after RS's storage blocks because the blocks are retextured RS storage blocks — a
player already reads that ladder. A sum rather than anything cleverer so that counting blocks tells
you the answer and adding a block is never a downgrade. One 1K CPU is 1 step/tick: ten times a bare
autocrafter, still under a maxed one, which is where the smallest structure should sit.

**Detection is free of Minecraft types.** The level is reached through a one-method `BlockSource`,
so the whole of it runs in a plain JVM under `./gradlew shapeCheck` — 25 cases, wired into `build`.
This is rstweaks' `plannerCheck` pattern adopted from the start rather than after the fact: the
awkward cases here (the hole, the overhang, the flush neighbour, the 17-long box) are exactly the
ones that get checked once by hand in game and then never again.

Not built yet: blocks, block entities, models, textures, the network node, the pattern GUI,
recipes.
