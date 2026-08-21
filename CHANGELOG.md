# Changelog

The version prints at startup (`[rsmc] vX.Y.Z loaded`), so a test result can always be tied to an
exact build.

`VERSIONS.txt` is the short form of this file — one or two lines per version. Both are maintained;
this one carries the reasoning, that one is the index.

## 0.0.6

**You can now tell why nothing is happening.** Two answers to the same complaint: a Controller
screen with three states, and a command.

### The screen

| state | look | means |
|---|---|---|
| unformed | bare grey panel | the box is not a valid structure |
| inactive | dark screen | valid structure, not attached to a network |
| active | blue screen | valid and attached |

Three because there are exactly three things worth telling apart, and until now they looked
identical -- "I built it and nothing happened" was indistinguishable from "I built it wrong".

Built as a layered model rather than composited art: the base cube plus a second element on the
front face carrying RS's grid cutout, `render_type: minecraft:cutout`. No image was edited.

This adds **the only ticker in the mod**, one per structure, running once a second rather than
every tick -- all it decides is which of three pictures to show, and re-reading the structure
walks up to 4,096 positions. The shell blocks still have none. Polling at all is a placeholder:
#3 replaces it with updates driven by the block changes that can actually change the answer.

### `/rsmc info`

Look at any rsmc block and it explains the structure that block is part of: size, corner,
controller position, CPU and pattern storage counts, and steps/tick against the 2.5 an RS
autocrafter tops out at. When it does not form, it names the failure, the exact position, and
what that position wanted instead.

It calls `MultiblockShape.find` -- the same call the mod uses, not a reimplementation. A
diagnostic that computes the answer a second way tells you about itself rather than about the
thing it is diagnosing. No permission level either: a tool that needs op to answer "why is my
machine not working" is not a tool most people will get to use.

It also says plainly that a formed structure **will not craft yet**, because that is the single
most confusing state this mod can currently be in.

## 0.0.5

**Cable to any face again.** 0.0.4 traded connect-anywhere for one block entity; it turns out that
trade was not necessary, because the cost it was avoiding does not exist.

The worry was per-tick lag from ~2,000 nodes. There is no per-tick path:

- RS ticks a node only through a `BlockEntityTicker` **the block itself opts into** (
  `AutocrafterBlock` declares one). `ShellBlock` declares none, so its block entities cost
  literally nothing per tick.
- RS's own server tick handler, `ServerListener.tick`, is a queued-action drain. It never
  iterates the network's nodes.
- Zero-energy nodes contribute nothing to the energy component.

What connect-anywhere actually costs is one small non-ticking object per shell block in memory,
and a network rebuild scan proportional to container count that runs **when blocks change**
rather than continuously -- and placing a 16x16x16 is already thousands of block updates.

So Frame and Casing carry a connection relay again: no ticker, no saved data, no energy, no
knowledge of the structure. A doorway and nothing else.

**The Controller stays**, and keeps everything that made it worth adding: it alone hosts the
pattern provider, the GUI and the energy draw, and it is still the structure's identity. What it
no longer has to be is the only place you can plug in.

A dedicated port block was offered as the compromise and is not needed -- it would have been a
ninth block and a ninth shape rule bought to save something that was never being spent.

The gametest now checks all three shell blocks resolve the capability, and was confirmed by
dropping the shell registration and watching it fail. The symptom in game would have been
"cabling only works at the Controller" -- exactly the thing this release removes.

## 0.0.4

**A Controller block, and the connectivity tax goes away.** Wraith's call: give the structure a
central block that does the talking, the way most multiblocks work.

It replaces one **Casing** on a wall -- never an edge, so the Frame outline stays unbroken -- and
there must be exactly one. Temporary texture is a Casing with Refined Storage's grid face on the
front, built as an `orientable` model with per-face textures rather than a composited image, so
no art was edited to get it.

**What it actually buys is not ticking.** Nothing here ticks -- RS drives the node -- and the host
position was already derived. What it removes is the cost of being reachable: because RS walks its
graph outgoing-only, a cable only finds the structure where a container physically lives, so
every shell block needed a block entity *and* a network node purely so any face could be cabled.

| structure | block entities before | after |
|---|---|---|
| 5x5x5 | 98 | 1 |
| 16x16x16 | 2,168 | 1 |

Frame and Casing are plain blocks again, as the CPUs already were. `ShellBlockEntity` is deleted.

**The trade is that a cable must touch the Controller specifically**, rather than any face. That
is the normal bargain for a multiblock with a controller, and the player picks where by choosing
which Casing to replace.

**It does not cost the click-anywhere GUI** (#4): a block can handle a right-click without a block
entity, derive the structure, and open the Controller's menu.

`Result.controllerPos` is now the structure's host -- found by looking for the block rather than
computed from a rule like "the minimum corner", so the host is something the player built. Two
new failures: `NO_CONTROLLER`, and `TOO_MANY_CONTROLLERS`, which reports the **second** one found
because the first is very likely the one they meant to keep. Six new shape cases, 30 total.

A note on the risk this takes on: a controller *block* is not the same mistake as a controller
*object that remembers*, but it is the thing that invites it. `ControllerBlockEntity` says so in
as many words.

## 0.0.3

**The structure connects to a Refined Storage network.** (Issue #2, step 1 of 4.) Shell block
entities now expose network node containers, so a cable touching any face of the box joins it.

**The design was settled by reading RS rather than guessing, and the obvious approach is wrong.**
The tempting one is a single node for the whole structure declaring every surface-adjacent
position as a connection point -- `ConnectionStrategy.addOutgoingConnections` is `@API STABLE`
and takes arbitrary positions, so it looks available.

But `ConnectionProviderImpl.findConnectionsAt` walks the graph **outgoing-only**: it asks a
container where it reaches, then looks for containers *at those positions*. A cable reaches its
six neighbours, so a cable against one of our wall blocks probes that wall position -- and if
nothing lives there, the walk never arrives. Our own outgoing connections cannot help, because
nothing ever gets to us to ask for them. One node at the corner would connect at the corner and
nowhere else.

So every shell block hosts a container with a trivial connectivity node, exactly as an RS cable
does: 98 for a 5x5x5, 2,168 for a 16x16x16. The interior hosts none -- a sealed box cannot be
probed from outside, which is the other half of why a CPU has no block entity.

**The lookup is a NeoForge capability**, not an interface on the block entity.
`PlatformImpl.getContainerProviderSafely` resolves
`RefinedStorageNeoForgeApi.getNetworkNodeContainerProviderCapability()`. Miss that registration
and the block entities exist, hold nodes, and are invisible to the network while every block
still places, renders and breaks perfectly with nothing logged.

Which is why the gametest asks the question *the way RS asks it* -- resolve the capability at the
position -- and was confirmed by disabling the registration and watching it fail. The energy
model is deliberately not split across these nodes: they draw zero, and the whole structure will
be charged once on the pattern provider node, because the interior blocks have no block entity
to charge and a per-node split would leave half the structure running free.

## 0.0.2

**The creative tab sits next to Refined Storage's now** instead of wherever registration order
dropped it. In a pack the size of ATM10 the creative menu runs to about 28 pages, and a tab
appended to the end is a tab nobody finds -- next to RS is where someone looking for an RS addon
actually looks.

`withTabsAfter`, with the id asked of `RefinedStorageApi` rather than written as a literal so it
cannot drift if they rename it. Safe at registry time because rsmc declares `ordering="AFTER"`
on refinedstorage, so RS's constructor has already installed the API delegate.

## 0.0.1

The whole of the first day, in the order it happened. Originally numbered 0.1.0 through
0.4.1 -- five versions in an afternoon, because the patch-per-build rule was borrowed from
rstweaks without the patch-digit half of it. Nothing had been published, so it was
renumbered to say what it is: a scaffold with blocks and no working crafter.

From here the patch digit moves per testable build, and the minor digit only for a milestone --
0.1.0 when the thing actually crafts.

### Project set up

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
### Shell and core, following Reborn Storage

**The structure became a shell with a core**, following Reborn Storage's multiblock crafter rather
than the solid box the scaffold was built around.

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

### No controller state machine, and placeholder textures

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

### The blocks exist

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

### The blocks dropped nothing when mined

**All seven blocks dropped nothing when mined.** Written that way earlier the same day; found by asking what a
human would still have to test by hand.

They are all `requiresCorrectToolForDrops`, and the only thing that makes any tool the *correct*
one is membership of a `minecraft:mineable/*` tag. There was no tag. So no tool was correct, so
nothing dropped — with any tool, forever — while the loot tables stayed perfectly valid, nothing
logged an error, and `assetCheck` was satisfied that every file it knew about existed.

Fixed by adding `data/minecraft/tags/block/mineable/pickaxe.json`, and guarded twice:

- `assetCheck` now asserts every block appears in that tag (57 checks, up from 50)
- a gametest asks whether an iron pickaxe is actually the correct tool for each block

**The gametest was vacuous on its first attempt, and that is the more useful lesson.** It asked
`Block.getDrops` for the drops while passing a pickaxe — and passed happily with the tag file
emptied out. `getDrops` only runs the loot table, and the loot table has nothing to say about tool
correctness; the gate lives in `ServerPlayerGameMode`, which checks the tool and only then calls the
drop path at all. Asking the loot table about a tool requirement it never sees is a test that can
never fail.

It now asserts the two halves separately — `ItemStack.isCorrectToolForDrops` for the gate, and
`getDrops` for the loot table naming the right block — and was confirmed by emptying the tag and
watching it fail, then restoring it and watching it pass. A test nobody has seen fail is not
evidence.

