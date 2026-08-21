# Changelog

The version prints at startup (`[rsmc] vX.Y.Z loaded`), so a test result can always be tied to an
exact build.

`VERSIONS.txt` is the short form of this file — one or two lines per version. Both are maintained;
this one carries the reasoning, that one is the index.

## 0.4.0

**The blocks exist.** (Issue #1.) Seven of them — Frame, Casing, four CPU tiers, Pattern Storage —
with items, a creative tab, loot tables, models and block entities. The mod now boots into a real
level with the structure rules working against real blocks.

**Two blocks carry a block entity and five do not**, which is worth writing down because the
tempting version gives one to everything.

- **Frame and Casing share one type.** They differ in exactly one thing — which position of the box
  they may occupy — and that is a value, not behaviour. They also make up the shell, which is the
  part that touches the outside world: what reaches the RS network (#2) and what a player clicks
  (#4).
- **Pattern Storage has its own**, because it carries the patterns. Where those live is the whole
  reliability argument: a pattern is in a block, the block is in the world, and breaking the
  structure moves nothing.
- **The CPUs have none at all.** A CPU's only state is its tier, and its tier is which block it is.
  The interior of a formed structure is sealed, so it never needs to reach the network or be
  clicked. At the top end that is the difference between 2,168 block entities for a 16³ and 4,096.

The shell entity is documented as staying stateless about the structure, with a note saying that
caching the formed structure there is the mistake this design exists to avoid.

**`LevelBlockSource`**, the one place the level and the shape code meet. Two details:

- **A block counts only if it implements `StructureBlock`.** Not a tag, not a list held elsewhere —
  a block that is part of the structure knows which role it fills, and a lookup table kept in step
  with the block registry is a second source of truth waiting to disagree. There is a gametest for
  an iron block failing to complete a structure, because that `instanceof` is the single line
  between "reads the world correctly" and "any block finishes your multiblock".
- **An unloaded chunk reads as absent, and is never loaded to find out.** `getChunk(x, z, FULL,
  false)` — both convenience helpers for the question are deprecated, and the `false` is the point:
  a structure check must not be the thing that drags a chunk into memory. Without it a structure
  across a chunk border would find a hole in itself, form differently depending on load order, and
  change shape underneath itself later.

**A second headless suite: `assetCheck`, 50 checks.** A block missing its loot table looks fine
until someone breaks it and it drops nothing; a block missing its model is a purple cube nobody
sees until they open the creative tab. Neither is a compile error and neither shows up in any test
that does not launch the game and look — which is exactly the kind of bug that ships. It also
checks each loot table names *its own* block, the one failure that survives every "the file is
there" check and still drops the wrong thing.

`BlockNames` is Minecraft-free so the registry and the check read the same list, and the check
cannot be checking a different set of blocks than the one being registered.

**Three gametests**, deliberately not re-testing the geometry that `shapeCheck` already covers far
better. What only a real level proves: the blocks are registered and carry the role they claim,
`LevelBlockSource` reads them back, and the two halves agree. The 8×8×8 template is rstweaks'
1×1×1 with its size ints patched — the smallest legal structure is 3×3×4 and there was nowhere to
build one.

**Compiler deprecation warnings are now shown in full**, not summarised as "uses a deprecated API".
A deprecated call usually means Mojang moved a method rather than dropped the idea, and silently
keeping the old one is how a mod breaks on the next version bump. It caught the chunk check above
twice over.

## 0.3.0

**Reliability decided as an architecture, not as a to-do.** Plus the first placeholder textures, so
there is something to look at.

Wraith's brief: follow Reborn Storage's *concept* — clicking any block in the structure opens the
GUI, and so on — but **recode it completely**, because the original was buggy and unreliable even
before it was poorly ported forward.

Reading their jar shows where that came from, and it is structural rather than a collection of
mistakes. Reborn Storage's multiblock is a persistent controller object with an assembly state
machine (`AssemblyState { Disassembled, Assembled, Paused }`), a per-world controller registry,
controllers that merge with one another (`onAssimilate`), and the pattern inventories held **on the
controller** (`public Map<Integer, ItemStackHandler> invs`). It is the Big Reactors-derived
framework.

The failure mode is built into that shape: **the controller's belief about the structure can
disagree with the world.** Chunks load in an order nobody controls, parts attach before or after
their neighbours, two controllers meet and one swallows the other. When the object that can drift
is also the one holding your patterns, a desync is data loss rather than a visual glitch.

So rsmc has **no controller object, no assembly state, no registry and no merge**:

- **The structure is derived, never remembered.** `MultiblockShape.find` recomputes it from the
  blocks that are actually there. There is no stored belief that can be wrong because there is no
  stored belief. Bounded at 4096 positions, run on change rather than per tick.
- **Patterns live in the Pattern Storage block entities.** Break the structure and they are still
  in the blocks, because they never left. Nothing owns them that can fail to exist.

Written up on #3, which also gains the test that matters: build, save, reload the chunk, assert it
still works — the case the controller design makes hard and this one should make trivial.

**#4 settled:** clicking anywhere on the structure opens the GUI, which is a view over the pattern
storage blocks rather than a container that owns anything.

**#6 closed, not needed.** It existed to draw a border that wrapped the formed structure, because
under the old solid-box shape nothing in the world marked the outline. Under the shell shape the
Frame blocks *are* the outline. And if the formed/unformed distinction is ever wanted, Reborn
already shows the cheap way: `multiblock_frame_ctm.png` and `multiblock_heat_ctm.png` are
connected-texture tiles it swaps to on assembly — a texture and a blockstate boolean, not
`IDynamicBakedModel` with tint handlers and rotated UVs. Both are kept as placeholders with the
reasoning beside them.

**Placeholder textures added**, Reborn Storage's, copied unchanged and marked as such in
`assets/rsmc/textures/block/PLACEHOLDERS.md`. MIT permits it and `ATTRIBUTION.md` carries the
notice. No Reborn Storage code is used, and none will be.

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
