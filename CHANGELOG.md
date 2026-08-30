# Changelog

The version prints at startup (`[rsmc] vX.Y.Z loaded`), so a test result can always be tied to an
exact build.

`VERSIONS.txt` is the short form of this file — one or two lines per version. Both are maintained;
this one carries the reasoning, that one is the index.

## 0.2.2

**The stuck screen gets instrumented, not guessed at a third time.**

Reported again from in game: the Controller looks unpowered while the structure keeps crafting
normally. That pair cannot happen legitimately, and the reasoning is worth writing down because it
is what narrows the search:

- `PatternProviderNetworkNode.doWork` returns immediately unless `isActive()` — verified against
  the 2.0.9 bytecode. So a structure that is **crafting proves `active=true`**.
- `syncNode` sets that flag from `hasEnergy()`, and `updateScreen` picks the block state from
  `hasEnergy()` a few statements later, in the same method, in the same tick.
- The assets were checked and are complete: all 12 blockstate variants, three models, three
  textures. The model path cannot produce this.

So one of the two server halves is lying, or the client is. Nothing on hand could tell which.

### What was added

`updateScreen` now logs **every** transition — old value, new value, position, game tick, the
node's active flag, and the energy numbers:

```
[rsmbac] controller screen inactive -> active at BlockPos{...} (tick 41203, node active=true, energy 4000/812 FE)
```

`/rsmbac info` gains two lines beside the existing `screen (server)`:

```
  screen (server) inactive
  node (server)   active=true  steps/tick=2048  <-- the node and the screen disagree; the screen is the stale half
  energy (server) 4000 stored / 812 needed
```

How to read the pair:

| screen | node | meaning |
|---|---|---|
| `active` | `active=true` | both server halves right — the **client** never got the update |
| `inactive` | `active=true` | the server is inconsistent; the block state is the stale half |
| no log line all session | — | `updateScreen` is not running, which would explain both symptoms at once |

### Why instrumentation and not a fix

This was reported once before and closed in **0.0.8** on a gametest that passed. A gametest has no
client, so it only ever exercised the half that was already correct — and the report came back.
Shipping a second theory on top of the first is how that repeats.

### One correctness tidy

`hasEnergy` and the new diagnostics read the network's stored energy through a single
`energyStored()` method, so the report cannot drift from the decision it reports on. Behaviour is
unchanged: a missing network reads `-1` and is rejected, exactly as the old `network == null`
branch did.


## 0.2.1

**Pattern search matched the wrong thing entirely.**

It filtered on `stack.getHoverName()` — which is **"Crafting Pattern" for every pattern ever
encoded**. So searching for the thing you wanted to craft found nothing, and searching "pattern"
found everything. Reported from in game: a crafter holding every recipe, and "allthemodium solar
sail package" matching none of them.

It now matches the pattern's **outputs** and its **inputs**, through
`RefinedStorageApi.getPattern(stack, level)` and
`RefinedStorageClientApi.getResourceRendering(...).getDisplayName(...)`. Those are the same two
things Refined Storage's own Autocrafter Manager offers as search modes — and RS never matches the
pattern item's own name in any mode either, which is the tell that it was never the right key.

Inputs come free with outputs and answer a real question: *what in here uses iron ingots?*

### The tooltip was already right

`gui.rsmbac.patterns.search_help` read **"Searches the patterns in this crafter by what they
make."** It had said that since the screen shipped. The help text described the intent and the
code did something else — the third time in this session that a comment promised behaviour the
code never had, after the see-through outline in 0.1.7 and `/rsmc info` in 0.1.13.

Now extended to mention inputs, so it is accurate rather than merely aspirational.

### Not covered by a test, and cannot be

The match needs `RefinedStorageClientApi` for display names, which does not exist on the dedicated
server a gametest runs on. Recorded rather than papered over with an assertion that would pass
whatever the code did.

### On resolving patterns while typing

`getPattern` resolves the recipe, and resolving patterns per frame is exactly what caused the
0.1.6 client hitch — 82.7% of the render thread. This is not that: `layout()` runs on a keystroke
or a scroll, never per frame, and RS caches resolved patterns by UUID so a repeat is a map lookup.
The comment in `patternMatches` says what to do if a very large structure ever feels sluggish
(cache the searchable text per slot) and what not to do (go back to matching the item name).

## 0.2.0

**Renamed: `rsmc` → `rsmbac`, "Refined Storage - Multiblock Autocrafter".**

Refined Storage rebranded crafters to *autocrafters* upstream, and this mod's name should follow.
The **blocks keep their names** — Crafter Controller, Crafter Frame, Crafter Casing — on Wraith's
call; only the mod identity moved.

The Java package went with it: `com.wraithhawit.rsmc` → `com.wraithhawit.rsmbac`, class `RSMC` →
`RSMBAC`, `mod_group_id` updated. Nothing anywhere still says the old name. Leaving the package
behind would have meant `RSMC.MODID = "rsmbac"` reading as a leftover forever, and every new file
inheriting it.

### Minor rather than patch, deviating from the usual rule

The standing rule is patch-per-testable-build, minor only for a milestone. This is neither a
feature nor a milestone, but **every block and item id changed namespace** — a hard break for any
existing world — and `0.1.15` would undersell that to the point of being misleading. Recorded as a
deliberate deviation rather than a slip.

### History is not rewritten

`CHANGELOG.md` and `VERSIONS.txt` entries below this one still say `rsmc`, on purpose. They record
what happened when the mod *was* called that. Renaming them would make the history false — a
0.1.12 entry describing a fix to "rsmbac" would be describing something that did not exist yet.
Living documents (`README.md`, `ATTRIBUTION.md`, `SETUP.md`) were updated; records were not.

### Existing worlds

Lose every block, as with the 0.1.11 CPU rename and for the same reason — a namespace no registry
knows is deleted on load. Accepted: there is no playthrough to protect. The mechanism if it ever
matters is in 0.1.11's entry (`DeferredRegister.addAlias`, blocks *and* items separately).

### One thing the mechanical rename nearly got wrong

The first pass used a careful pattern requiring a letter after `rsmc.`, which silently skipped
`"block.rsmc."` and `"itemGroup.rsmc"` — the quote is not a letter. `assetCheck` caught all eight
missing translation keys immediately.

The fix was to stop being clever: `rsmbac` does not contain the substring `rsmc`, so a blanket
replace is both correct and idempotent. Worth remembering — a rename where the new name contains
the old one is the case that needs care, and this was not it.

## 0.1.14

**Tests for #2 before closing it — and an honest account of what they cannot reach.**

Two new gametests, **13 total**:

- **`theStructureSpeedIsTheSumOfItsCpuTiers`** — a 1x + 4x + 16x interior reports **21**. Three
  tiers rather than one because the sum is the claim, and 21 is distinct from every plausible
  mistake: not the count (3), not the maximum (16), not the first (1).
- **`anUnpoweredStructureReportsZeroSteps`** — `getSteps` goes to zero and `canStep` is false,
  rather than `active` being flipped somewhere the task engine never reads.

`ControllerBlockEntity` now records the `StructureStepBehavior` it sets, since
`PatternProviderNetworkNode.stepBehavior` is private with only a setter.

### The limit, verified rather than assumed

**Deleting the `node.setStepBehavior(...)` call entirely leaves both tests green.** That was
checked, not guessed.

So they prove the *computation* — the sum, the active gating, `canStep`/`getSteps` agreeing — and
not the *handoff*. RS keeps the field private with no getter, and `setAccessible` is refused
across its module, so no test in this mod can read what RS actually holds. The first version of
this was called `theStructureTellsRefinedStorageItsSpeed`, which claimed exactly the thing it
could not show; the name now says what is true.

This is the same failure the project has hit before: a check that confirms our own bookkeeping
instead of the effect. Writing it down because it was caught only by reinstating the bug — the
step that turns a test into evidence.

What covers the handoff is the structure demonstrably crafting at speed in game. That is weaker
than a test and it is what exists.

### Two runs of geometry before it went green

The first attempt read `0 steps/tick` twice — both times because the box had not formed, which is
correctly IDLE. `buildShell` gives a **two-slot** interior, and the interior must be *filled*: a
gap is `NOT_SOLID`. The failure message now reports the shape's `failure()` when the speed is
wrong, so the next person gets told "your geometry is wrong, not the mod" instead of rediscovering
it.

## 0.1.13

**`/rsmc info` was lying about the mod's own state.**

On a formed structure it printed:

> It will not craft yet -- the pattern provider is not implemented (issue #2).

True when it was written. **#2 shipped in v0.0.24** — ninety versions ago — and the line stayed.
That is worse than printing nothing: a diagnostic command is the one place a player trusts, and
this one sent them looking for an unfinished feature when the real answer was a missing cable or
an unpowered network.

Replaced with what is actually knowable:

- the structure's **energy draw**, and
- its **real state**. `ACTIVE` says it is running; `INACTIVE` names the two things that stop it
  (no network, not enough energy); `UNFORMED` admits the Controller has not caught up yet.

That last case is deliberate rather than defensive. The command derives the shape here and now,
while the Controller updates on its refresh schedule and joins the network a tick later, so the
two genuinely can disagree for a moment. Saying "try again in a moment" is more honest than
pretending the block already agrees with a shape computed microseconds ago.

### Two more stale pointers, same class

The placeholder textures were said to be "tracked by issue #1" in both `PLACEHOLDERS.md` and the
README. **#1 closed at v0.0.1** when the blocks shipped; textures were split out to **#7** at the
time and neither file was updated.

Worth noting the pattern rather than just the fix: every one of these was a claim about the
project written in a file that has no reason to be re-read once it is correct. A comment that
describes code is checked whenever the code changes. A comment that describes *the roadmap* is
checked never.

## 0.1.12

**An unformed Crafter Controller could not be broken, and nothing could be placed beside it.**

Reported in game as a toast: *"No permission — You are not allowed to break the Crafter
Controller."* That is **Refined Storage's** message, not rsmc's and not vanilla's, which is what
made it worth tracing rather than guessing at.

### What was actually happening

RS registers a global `BlockEvent.BreakEvent` handler that runs over **any** position exposing a
network-node capability — which rsmc's Controller does. It asks the provider for permission, and
that lands in:

```java
Network network = node.getNetwork();
return network == null ? false : isAllowed(player, permission, network);
```

**No network means denied**, unconditionally. There is no owner, no team and nothing to protect,
and the answer is still no.

rsmc initialised its container in exactly one place — `ensureCapacity` — which `syncNode` only
reached *after* its `if (!result.formed()) return;` early exit. So an unformed Controller never
joined a network, and RS refused every build action on it.

`canPlaceNetworkNode` checks all six neighbours of anything being placed in the same way, so it
was a deadlock: the structure could be neither **finished** nor **removed**. Wraith hit it because
the 0.1.11 CPU rename unformed an existing crafter.

### The fix, and why not the other one

Wraith asked whether security could apply only once the structure is formed. It could — but the
**Frame and Casing blocks around it are already always-protected**: `ShellBlockEntity.clearRemoved`
initialises unconditionally, which is exactly why they were never affected and why the Controller
being different went unnoticed. Exempting only the Controller would have made it the odd one out
inside its own structure, and would not have lifted the deadlock for anyone who really did have a
Security Manager — the shell blocks would still refuse.

So the Controller now does what the blocks around it have always done: **joins on placement rather
than on forming**, at zero pattern capacity with an IDLE step behaviour. That is also just more
correct. The Controller is the structure's network face from the moment it is placed; whether the
box around it is finished is a question about crafting, not connectivity.

**Only on the unformed path.** Joining early on an already-formed structure made it join at
capacity zero and then immediately rebuild to resize, which delayed activation by a tick — caught
by `apoweredstructuregoesactive`. A formed structure still joins once, at its real capacity.

### Two things the gametests caught on the way

The first attempt built a *fresh* container provider instead of reusing the one the capability had
already handed to Refined Storage. RS then held a container from the old provider while removal
offered the new one, and crashed on the way out: *"The removed container should be present in the
removed entries, but isn't"* — the exact failure the `joinedNetwork` field was written to prevent.

The second attempt was the activation delay above. Neither would have been visible from a build or
a headless suite.

New gametest, `anUnformedControllerIsStillBreakable` — **11 total**. It asserts on the network,
which is the condition RS actually tests, rather than driving a fake player through a break, which
would be testing RS. Frame and Casing are checked alongside as the control, since they always
passed. Confirmed to fail with the fix removed.

## 0.1.11

**Four changes from playing with 0.1.10.**

### Casing and Frame no longer look alike in the recipe book

They were `ICI/CMC/ICI` and `CCC/CMC/CCC` — one ingredient apart, in a corner-vs-edge arrangement
nobody is going to hold in their head. The Casing now takes **basic processors** in the top and
bottom centre:

```
CPC        C = quartz enriched copper
CMC        P = refinedstorage:basic_processor
CPC        M = refinedstorage:machine_casing
```

Two distinct shapes, and the processors give the wall block a reason to be more than "the frame
without corners".

### The CPU tiers are 1x / 4x / 16x / 64x

`K` was borrowed from Refined Storage's storage ladder on the theory that a player already reads
it as a four-step ×4 progression. It also reads as **capacity**, because that is what K means on
every other block wearing it — and these do not store anything. A 64K CPU is not 64,000 of
something; it is 64 steps per tick. The `x` says the one true thing about it.

The rung ratio is unchanged and the recipes still ladder like RS's storage parts. Only the label
moved.

### Existing worlds do NOT survive the rename, deliberately

A renamed block id makes Minecraft delete the block: it finds something in the chunk no registry
knows and drops it. Every CPU in an existing crafter becomes air, and because the interior is
sealed, nobody watches it happen.

That is normally worth a registry alias, and one was written and working before Wraith pointed
out there is no playthrough to protect — the test world is disposable and cheating blocks back in
is a ten-second job. So it was removed rather than carried: seventy lines of machinery guarding a
failure nobody will experience is a cost with no matching benefit, in a codebase young enough
that every piece should still be earning its keep.

Recorded for whenever it *does* matter: `MissingMappingsEvent` is what a 1.20 mod would use and
**does not exist in NeoForge 1.21** — it was removed. `DeferredRegister.addAlias(old, new)` is
the replacement, and it must be applied to the **block and item registries separately**, since
aliasing only the block still loses every copy sitting in a chest or a Pattern Storage.

### CPU recipes: quartz enriched iron moved to the bottom, redstone became a crafter

```
PSP        P = basic → improved → advanced processor
SCS        S = 3× the tier below
PEP        C = minecraft:crafter,  E = quartz enriched iron
```

Every tier now consumes a vanilla crafter, which reads better than redstone dust for a block
whose whole job is crafting — and matches the 1x CPU, which already used one.

### One near-miss worth recording

The rename was done as `cpu_1k` → `cpu_1x`, never bare `1k` → `1x`. A blanket replace would have
rewritten `refinedstorage:1k_storage_part` in the Pattern Storage recipe into an item that does
not exist — and per 0.1.10, that failure is silent: the recipe is dropped at datapack load and the
block simply cannot be made. `recipeCheck` would have caught it, which is the second time in two
versions that test has earned its place.

## 0.1.10

**#5: every block is craftable.**

Frame and Casing as proposed — quartz enriched iron and copper around a machine casing, **yield
1**, which is the entry cost Wraith already confirmed is deliberate and which is not re-litigated
here. 1K CPU is Reborn Storage's recipe with `minecraft:crafting_table` swapped for
`minecraft:crafter`. Pattern Storage is Reborn's as-is.

### The CPU ladder mirrors RS exactly

Refined Storage's storage parts share one shape from 4k up, each consuming **three** of the tier
below plus four processors of the matching grade:

```
PEP     P = basic → improved → advanced processor
SRS     S = 3× the previous tier
PSP     E = quartz enriched iron,  R = redstone dust
```

The CPUs now use that shape and that ratio. The alternative was 4×, matching the 1/4/16/64 step
weights so material scaled exactly with throughput and tiering up bought only compactness. 3× was
chosen: it makes a 64K about 2.4× more material-efficient than building wide, so higher tiers are
a genuine reward, and anyone who has laddered 1k→64k storage parts already knows the recipe
without reading it.

### The Controller had to be invented

It was not in the proposal. It is a Casing — the block it replaces on a wall — with
`refinedstorage:network_card` set in its face, which is literally the item that carries a network
connection in RS, plus advanced processors behind it.

### What the entry machine actually costs

Worth stating, because it is higher than issue #5's table suggests. That table counted 34 machine
casings for the smallest 3×3×4 (24 frames + 10 casings), but **the CPU and Pattern Storage
recipes each consume 4 frames of their own**, so the real floor is ~42 machine casings — around
340 quartz enriched iron, or roughly 255 iron and 85 quartz before anything else. Still the
intended shape (entry cost dominated by frames, top end by casing and CPUs), just steeper at the
bottom than the issue's arithmetic implied.

### recipeCheck

New, **48 assertions**, wired into `build`. It verifies every ingredient id against the **real
Refined Storage jar** in `libs/`, because a typo'd item id does not crash and is not a compile
error: Minecraft logs one line at datapack load, drops the recipe, and the only symptom is a block
nobody can craft — found by a player, in a world, after shipping. Same class of silent failure
`assetCheck` exists for.

Confirmed to have teeth by typoing `network_card` into `netwrok_card`. It also rejects orphan
recipes for blocks that no longer exist, and any ingredient from a mod that is not Refined Storage
or vanilla, since rsmc depends on RS and nothing else.

The RS half is **skipped rather than failed** when `libs/` is empty, so a fresh clone still passes
`build`.

### Still open on #5

Whether the tier weights are right *now that costs exist* is a play-test question, not a
code one. The numbers to watch: a 3×3×4 with one 1K CPU is 1 step/tick against a bare
autocrafter's 0.1, and a 16³ packed with 64K CPUs is far past anything RS can reach.

## 0.1.9

**#3's mechanism half: the Controller stops recomputing a structure that has not changed.**

`refreshStateOccasionally` ran a full `MultiblockShape.find` — a flood fill and a box walk over up
to 4096 positions — **every 20 ticks, forever, whether or not anything had moved**. Measured in a
survival world at **0.19 ms/tick** for one block, which is the cost of a machine doing nothing.

Two pieces replace it:

- **`StructureChanges`** — one global counter, bumped from every structure block's `onPlace` and
  `onRemove`. Hooked there rather than on a player-facing event because those fire for *every*
  cause: pistons, commands, other mods, world edit. A box broken by a piston is exactly what a
  player-event hook would miss.
- **`RefreshSchedule`** — debounces five ticks after the last change. Placing a 16³ by hand is
  thousands of block updates and a construction stick does it in one gesture; the window restarts
  on every further change, so a burst costs one scan at the end of it rather than one per block.

**Idle cost drops tenfold, and ordinary changes are noticed *faster*** — a quarter second instead
of up to a full one.

### Why the safety scan is not optional

Change-driven alone is not sound. The counter only sees *rsmc* blocks, and a structure can be
broken by something it never hears about — another mod placing a block inside the box, a fill
command, anything exotic. Rare is not never, and a machine silently stuck in a wrong state is a
bug report nobody can reproduce.

So a scan still happens if none has for ten seconds. That is the trade: an order of magnitude less
idle work, in exchange for worst-case staleness on an exotic change going from one second to ten.

### This does not reintroduce the state machine

The design rule from #3 is that rsmc keeps **no belief about the structure**, because Reborn
Storage's remembered assembly state can drift from the world and it holds the patterns. Nothing
here remembers a structure. `StructureChanges` is a single number saying "something, somewhere,
moved" — an invalidation hint. Being wrong about it costs a redundant scan or a slightly late one,
never a wrong answer, because the answer still comes from `MultiblockShape.find` reading the world.

### The tests found a real bug immediately

New `refreshCheck` — **15 assertions**, wired into `build`. On its first run, five failed:
`lastScan` was seeded to `Long.MIN_VALUE`, and `now - Long.MIN_VALUE` **overflows** to a negative
number, so "overdue" was never true and a freshly loaded Controller would have sat there never
deriving its structure at all. It looked obviously correct. It is a boolean flag now.

Also confirmed to have teeth from the other direction: reinstating an always-scan poll fails 8 of
the 15.

## 0.1.8

**Two fixes to 0.1.7, both found by using it.**

### The outline was not see-through

0.1.7's class comment said the highlight was drawn "deliberately without depth testing, so the
outline is visible through the structure's own walls". It was not. `RenderType.lines()` is
depth-tested, and handing geometry to a `MultiBufferSource` means the render type re-applies its
own GL state at `endBatch` — so any `disableDepthTest()` beforehand is overwritten a moment later.

The comment described the intent and the code did the opposite, which is worse than either alone:
it reads as verified. The offending block is very often *inside* the box, which is exactly the
case the highlight exists for, and exactly the case that did not work.

Building a no-depth `RenderType` needs `RenderType.create` plus half of `RenderStateShard`, all
protected — a pile of access transformers for one outline. So the lines state is set up, depth
testing is disabled **after** that setup, and the box is drawn immediately rather than queued.

### The message ate the block placement

The unformed path returned `InteractionResult.CONSUME`, which consumes the whole interaction —
including the block placement that came with it. So right-clicking a structure block to place
another one against it silently did nothing, precisely when a player is placing the most blocks.

It returns `PASS` now. An unformed structure has no screen to open and nothing to protect, so the
interaction should carry on to whatever the held item wanted to do.

That makes one right-click both place a block and report the failure, and placing a wall by hand
is a right-click per block — so the message is rate-limited to **once a second per player**.
Right-clicking the **Controller** always speaks, because that is a deliberate question rather
than a side effect of building. The highlight is never rate-limited: it is aimed, and it replaces
itself rather than accumulating.

## 0.1.7

**#3's player-facing half: showing which block is wrong instead of describing it.**

Right-clicking the **Controller** of an unformed structure now draws a red outline around the
offending position for fifteen seconds. Every other block in the structure keeps explaining in
chat, as it already did.

The split is Wraith's, and it is better than what this was going to be. A coordinate is a poor
answer when you are standing inside a 16³ box — reading three numbers and then finding that
position by eye is exactly the friction the message was meant to remove. But a highlight on
*every* block would fire constantly while building, so: the Controller is the block you
deliberately placed to be the machine's face, and it is the one that answers "which block is
wrong?".

The outline is drawn at `AFTER_TRANSLUCENT_BLOCKS` and deliberately **without depth testing**, so
it is visible through the structure's own walls. Someone asking which block is wrong is usually
outside a box whose bad position is on the far side; an outline they cannot see through the
machine answers nothing.

### Nothing pops up while building

Surfacing is entirely **interaction-driven** — there is no message on placement at all. A
construction stick putting down hundreds of blocks stays silent, which is what Wraith asked for
and which sidesteps the debounce problem the issue anticipated rather than solving it.

### The chat message now says what the position needed

`WRONG_BLOCK` used to read "wrong block for that position", which names the problem and withholds
the answer. `Result.expected` has carried the required `Role` all along and nothing used it:

```
Not formed: wrong block for that position -- it needs a Casing, or the Controller
  at 144 175 142  (right-click the Controller to highlight it)
```

And the flush-neighbour case gets its own line, because it is the one that reads like a bug:

```
  If two crafters are touching, they count as one shape. Leave a gap between them.
```

That rule is deliberate — a box gives every failure a coordinate where a blob cannot — so the
thing that makes it survivable is saying it out loud at the moment it bites.

### First packet in the mod

`HighlightBlockPayload`, registered `optional()` so a client without rsmc can still connect;
everything real is server-authoritative and the highlight is a convenience.

Its handler is held in `ClientHighlightHandler` as a swappable field rather than named directly
from `RsmcPayloads`. A client class named inside the registration lambda — even inside a lambda —
lands in that class's constant pool and crashes a dedicated server at class load: a failure that
never appears in single-player and always appears for the first person to run a server.

## 0.1.6

**The client hitch was 82.7% of the render thread, and it was mine.**

0.0.19 backed the client menu with Refined Storage's `PatternInventory` so the client would refuse
a non-pattern exactly as the server does, instead of predicting a shift-clicked cobblestone into a
slot and having it bounce. Right instinct, wrong implementation: RS's filter is
`PatternProviderItem.isValid`, which resolves the pattern's **recipe** -- a full
`RecipeManager.getRecipeFor` scan across every recipe in the pack.

Something asks a menu's container whether an item fits on every frame, so that scan ran
continuously while the screen was open.

The client now asks the cheap half of the same question -- is it a pattern item at all -- which is
an `instanceof`, and rejects everything a player will realistically try. **The server still runs
the full check and is still the authority**, so nothing invalid can actually get in.

The one thing this predicts wrongly is an *unencoded* pattern: the client lets it move and the
server puts it back. A corrected prediction, not a lost item, and a fair price for not scanning
every recipe in the game twenty times a second.

### Four server profiles before anyone looked at the client

The hitch was reported as client-side from the start. It was chased through the Step Requester,
pattern plan copying, and two rstweaks changes first, because an early client profile showed the
render thread 65.6% parked -- genuinely blocked on an overloaded server at the time. Once the
server was fixed the parking stayed (it is ordinary frame pacing) and the real work underneath it
was visible.

**Read a parked render thread as "blocked" only when the server is actually busy.** Otherwise it is
just a client waiting for its next frame, and the interesting frames are the small ones.

## 0.1.5

**Patterns are handed to the network a few at a time**, instead of the whole backlog in one tick.

`setPattern` is far more than a store. It calls remove and then add on the network's autocrafting
component, and `add` ends with
`patternListeners.forEach(listener -> listener.onAdded(pattern))` -- Refined Storage keeps **four**
calculator listeners on that path, so one pattern means four notifications and whatever
recalculation each of them decides to do.

Draining every dirty slot in one refresh therefore landed the entire cost of a shift-click in a
single tick. Eight per refresh fills a storage block in under seven seconds -- faster than anyone
fills one by hand -- and never lands the listener storm at once.

### What the profiles say after rstweaks 0.2.109

| | worst | now |
|---|---|---|
| `CraftingTree.calculate` | 96.2% | 25.0% |
| `MutablePatternPlan.copy` (self) | 26.2% | absent from the top 12 |
| `MutableTaskPlan.copy` -> `MutablePatternPlan.copy` | -- | 0.70% |

**And the client lag was never client lag.** The render thread is 65.6% `Unsafe.park` -- blocked
waiting on the integrated server. Worth knowing before reaching for `/sparkc`: on a single-player
world a client freeze is usually a server freeze seen from the other side.

## 0.1.4

**Slot lookup is O(1).** `StructurePatterns.getItem` appeared at 2.90% of the server thread in a
profile taken with the pattern screen open -- up from 0.19% in one taken with it closed.

The menu calls `getItem` on every slot every tick while a screen is open, and each call did
**two** linear walks over the storage blocks to work out which one owned the slot. Fifty-four
slots per storage, twenty times a second.

The mapping is now built once, in the constructor, as two arrays. One pass to build, an array read
per lookup after that.

### What the three profiles have shown, in order

| | profile 1 | profile 2 | profile 3 |
|---|---|---|---|
| `canPlaceItem` | **16.3%** | 0.13% | -- |
| Step Requester | 22.8% | **96.2%** | 26.5% |
| `MutablePatternPlan.copy` self | 6.1% | 26.2% | 6.9% |
| `getItem` | 0.19% | -- | **2.90%** |

Each profile has found a different real cost, and only the first two were ours. The Step Requester
figure moving from 96% to 26% between profiles suggests a large part of that spike depends on what
is on the network rather than on Step Crafter alone -- a structure full of patterns is more
branches for RS's crafting calculator to explore on *every* craft, not only ours.

## 0.1.3

**`/rsmc info` now prints the mod version first**, on every path including the failures.

Asked for while chasing a lag spike, and it is the right instinct: a result that cannot be tied to
a build is not evidence. rstweaks learned this the expensive way, mistaking a report from a jar
three versions old for a confirmation.

It also prints **how many patterns the structure holds**, used of capacity. That number is not
trivia: every pattern on a network is another branch Refined Storage's crafting calculator may
explore, on any craft, not only ours. A structure full of patterns makes planning more expensive
for the whole network -- which is exactly the shape of the problem the profiles are showing.

## 0.1.2

**`StructurePatterns.canPlaceItem` was 16.3% of the server thread.** Found in Wraith's first spark
profile, and it is the largest single cost the mod has had.

`ItemHandlerHelper.insertItem` walks **every slot** looking for one that will take the item,
calling `canPlaceItem` on each. The filter behind ours is RS's `PatternProviderItem.isValid`,
which parses the pattern. So one shift-click into a 54-slot structure validated the same pattern
54 times, and more storage blocks multiplied it again.

The slot cannot change the answer -- `FilteredContainer.canPlaceItem` ignores it and tests the
stack alone -- so the result is now remembered for as long as the same stack keeps being offered,
which is exactly the length of one insert.

Compared **by identity**, deliberately: `ItemStack` has no meaningful `equals`, so a value
comparison is not on offer, and identity is what is wanted anyway. The guarantee being relied on
is that `insertItem` passes the same instance down its loop; any other instance re-checks.

### What else the profile said

Our per-second refresh -- the thing predicted to dominate -- is **0.18%**, and
`MultiblockShape.find` inside it is 0.15%. The prediction was wrong, and wrong in a useful
direction: the O(volume) scan #3 exists to remove is not currently worth removing.

The largest cost on that server is not this mod at all: **22.8% is Step Crafter's step requester**
starting tasks, through RS's crafting calculator, with `MutablePatternPlan.copy` alone at 6.1%
self time.

Worth recording as a method note: the guess was reasonable and measurement disagreed within one
profile. It would have been quicker to ask for one earlier.

## 0.1.1

**The multi-second freeze while shift-clicking patterns in.**

`PatternProviderNetworkNode.setPattern` is not a store. It tells the autocrafting component to
remove the old pattern and add the new one, which invalidates Refined Storage's crafting indexes
-- so re-pushing every slot because one changed redid that work for the entire structure. Once per
shift-click, and a shift-click can move a lot of patterns.

The old guard was a version *sum* across the storage blocks: enough to tell that something had
changed, useless for telling **what**. It now tracks dirty slots per block as a `BitSet`, and the
Controller pushes exactly those. Putting one pattern in costs one `setPattern`, not fifty-four.

The offsets live in `StructurePatterns` because that is the only thing that knows them -- a storage
block knows its own slot 0 but not that it is the structure's slot 54. Draining rather than
reading is deliberate too: a separate clear step is a step an early return can skip.

> **On the patterns that went missing:** the likely cause is the 78 -> 54 nerf in 0.0.25. Blocks
> saved at 78 slots keep patterns in slots 54-77 that the smaller inventory has nowhere to put, and
> they are dropped on load. That was flagged when the nerf shipped, and it only bites once, on a
> world that predates it. If patterns vanish again in a world created after 0.0.25, that is a
> different and much more serious bug -- worth saying so it is not written off as the same thing.

## 0.1.0 -- it crafts

**The minor digit, because the crafter crafts.** That was the milestone named for `0.1.0` when the
versioning rule was written, and it has been reached: patterns go in, appear in the Refined
Storage system, and craft.

**One optimisation before performance testing, so the numbers mean something.** The Controller
found the structure and then called `StructurePatterns.of`, which found it again -- so the
once-a-second refresh scanned the box **twice**, up to 4,096 positions each time, every one of
them a chunk lookup as well as a block read. It now hands the result it already has along.

### Where the remaining cost is, stated before measuring it

Per formed structure, per second: one `MultiblockShape.find` over the whole volume, plus one
`getBlockEntity` per interior position. For a 16x16x16 that is 4,096 block reads and 2,744 block
entity lookups. Every craft step itself is a normal RS pattern provider tick and costs what an
autocrafter costs.

That refresh is the poll #3 exists to remove, and it is O(volume) -- so it should show up in a
profile as `refreshStateOccasionally`, growing with structure size and with the number of
structures, not with how much crafting is happening. If a profile shows something else on top,
that is the more interesting result.

## 0.0.25

**Pattern Storage nerfed from 78 patterns to 54.** Wraith's call.

54 also happens to be the better number for the screen: it is six full rows of nine, so one
storage block's slots fill the grid exactly, where 78 left a ragged part-row at every block
boundary. Still six times a stock autocrafter's nine.

One constant, and everything follows from it -- the pattern inventory size, the menu, the screen,
the node's capacity and the tests all read `StructurePower.PATTERNS_PER_STORAGE` rather than
carrying their own copy.

## 0.0.24

**The stall was not expected -- nothing was ever asking the node to take a step.**

Refined Storage does not drive a pattern provider from the network. The provider's own block
entity is ticked and calls `doWork()`, which steps its tasks -- `NetworkNodeBlockEntityTicker`
does exactly this for an autocrafter, every tick. Our ticker only refreshed the screen, once a
second, and never called `doWork()` at all.

So the node joined the network, accepted patterns, reported its speed, showed up in the crafting
preview, and then every craft stalled forever. **Everything looked correct except that nothing
happened** -- which is exactly the failure that gets mistaken for "I must be missing ingredients".

`tickNode()` now runs every tick with no throttling, alongside the once-a-second structure
refresh. Two jobs at two rates, for two different reasons: stepping is the crafting itself and
must be immediate, while re-reading the structure is expensive and only decides which of three
pictures the screen shows.

## 0.0.23

**Copied the Autocrafter Manager properly instead of imitating it piece by piece** -- and the very
first thing that fell out was the shift-click bug.

**Patterns could not be put in because the server menu had no player inventory slots at all.**
`AutocrafterManagerContainerMenu` adds them in its **constructor** and again in
`initializeGroups`; mine only added them in `resized()`, which is called by the screen and
therefore **only ever runs on the client**. So the client had 36 more slots than the server, every
index a click sent referred to something else, and shift-click silently did nothing.

Slots are now built in the constructor and rebuilt on resize, exactly as RS does it. Nothing about
that is obvious from the outside -- it is only obvious from their constructor.

**The scrollbar had no background because the window was 17px too narrow.** RS uses
`imageWidth = 193`, not 176: the extra width *is* the scrollbar track, and the texture already
contains it. Since the texture is theirs unmodified, this was never a number to choose.

**The title is a `TextMarquee(title, 70)`**, which is why RS's own long titles scroll rather than
run under the search field. "MultiBlock Crafter" is wider than the gap, so it now scrolls.

Three symptoms, one cause: reimplementing what the manager already does. That is now five rounds
of it, and the wholesale copy found in one pass what piecemeal porting had missed each time.

> **No, you do not need to rebuild the structure.** Nothing about the shift-click failure was to do
> with the blocks -- the menu was wrong on the server, whatever the structure had been built with.

## 0.0.22

**The slots are RS's slot sprite now**, not two rectangles drawn by hand -- which is where the
hard black grid lines came from. A Minecraft slot is a bevelled sprite; a border drawn with
`graphics.fill` looks like a spreadsheet.

`graphics.blitSprite(Sprites.SLOT, x, y, 18, 18)` -- the same call
`AutocrafterManagerScreen.renderGroup` makes. Found by actually reading how the manager draws its
grid instead of assuming the row texture carried it: the rows are plain, and RS blits a slot
sprite per slot.

Which is the standing instruction again, and the fifth time it has been the answer: **look for the
RS class first.** Every hand-rolled piece of this screen has now been replaced by the RS one it
was imitating -- the base screen, the search field, the search icon, the inventory layout,
shift-click, the pattern output rendering, and now the slot.

## 0.0.21

**0.0.20 crashed the server.** It shipped with a failing gametest because the command that built and
installed it did not stop on the failure. That is fixed here, and the sequence is worth recording
because three separate theories were wrong before the real one.

**A container cannot be removed while its block still stands.** `NetworkBuilderImpl.remove` walks
the network from a neighbouring container and requires the removed one to be *absent* from that
rescan -- but the capability at our position keeps answering as long as the block entity is there.
RS finds it, fails its own validation, and throws. It is not about initialisation order, and not
about RS deferring network changes to its task queue; both were checked and neither was it.

So a node whose size must change is handled by **dropping and rebuilding the block entity** on the
next tick. With the block entity gone the capability answers nothing, which is exactly the state
RS expects during a removal. Deferred rather than inline because this runs from inside the block
entity tick.

**Nothing is lost in that rebuild**, and that is the design paying for itself: capacity, patterns
and speed are all derived from the world, and the patterns live in the Pattern Storage blocks.
There is nothing on the controller worth keeping.

Two further crashes fell out of the fix:

- **Writing patterns past the node's size.** The node is still the old size until the rebuild
  lands, so the push is now bounded by what the node actually has.
- **An endless rebuild loop.** The block entity joined the network before the structure had been
  read, so it joined at size zero, found the real size a tick later, rebuilt -- into another
  size-zero node. It showed up as a structure that never powered on. It now joins on the first
  refresh, once the size is known; an unformed structure is not on the network at all, which is
  the more honest answer anyway.

## 0.0.20

**The pattern provider node.** (#2, the last step.) The Controller now hosts a real
`PatternProviderNetworkNode`, fed the structure's patterns, its speed and its energy draw -- which
is everything Refined Storage needs to craft through it.

- **Speed** is `StructureStepBehavior`: N steps every tick, N being the summed CPU tier weight.
- **Capacity** rebuilds the node when it changes, because `PatternProviderNetworkNode` fixes its
  slot count at construction. Nothing is lost in the swap -- the patterns are in the Pattern
  Storage blocks, not on the node, and get pushed straight back into the new one. Had they lived
  on the node this would be a migration with somewhere to drop them.
- **Patterns** are pushed only when they change, guarded by a version sum across the storage
  blocks. Turning an item into a `Pattern` parses it, and doing that for hundreds of slots once a
  second to usually learn nothing is real work for no result.
- **A broken structure crafts nothing** rather than crafting slowly or continuing with whatever it
  was last told.

### And a probe, because reasoning was not converging

Shift-clicking real patterns in stopped working after 0.0.18 moved shift-click onto RS's
`TransferManager`. The difference is which question gets asked: the old hand-rolled path went
through `Slot.mayPlace`, which `PatternSlot` implements; `TransferManager` goes through
`Container.canPlaceItem` on our own `StructurePatterns`.

That is a testable predicate, except for one thing -- **an encoded pattern cannot be conjured in a
gametest**, and a blank pattern is refused by RS's filter anyway, so a test can only prove the
negative case. So `/rsmc info` now reports what the structure says about the item in your hand,
and distinguishes "not a pattern" from "a pattern item, but not an encoded one", which look
identical from the outside.

## 0.0.19

**Shift-clicking a non-pattern into the crafter looked like it worked.** It never actually did --
the server refused it every time -- but the client predicted it in and only a correction took it
back out.

The client menu was backed by a plain `SimpleContainer`, which accepts anything. It is now a
`PatternInventory` of the same size, so the client filters with RS's own
`PatternProviderItem.isValid` and the prediction matches the answer. More RS reuse, and it removes
a class of bug rather than one instance: any future slot restriction is now enforced identically
on both sides because it is literally the same container type.

**The test that found this was wrong in an interesting way.** It asserted a blank pattern item
would be accepted -- and it is not. RS's filter wants an *encoded* pattern, not merely the right
item, so an unstamped pattern is refused like any other junk. That is now asserted rather than
assumed: it is the difference between "only patterns fit" and "only patterns that actually make
something fit".

Cobblestone was correctly refused all along, including through the two-storage-block index
arithmetic, which is what narrowed this to the client in one step.

## 0.0.18

**Three more things RS already did, deleted from here.** Prompted by asking whether the Autocrafter
Manager could simply be copied wholesale -- the honest answer being that most of what I had written
by hand was already sitting in the base class.

| was | is |
|---|---|
| four rows of inventory positioned by hand, with a hotbar-gap constant | `addPlayerInventory(inventory, x, y)` |
| `quickMoveStack` hand-rolled with `moveItemStackTo` and index ranges | `transferManager.addBiTransfer(playerInventory, patterns)` |
| a `playerSlots` list kept only to reposition them | gone -- the helper owns them |

The shift-click case is the one worth noting. `AbstractBaseContainerMenu.quickMoveStack` already
delegates to a `TransferManager`; a menu just declares what moves where. One line replaces twenty,
and it is the same code path every other RS screen uses -- so shift-clicking a pattern behaves
exactly as it does in an Autocrafter.

### On copying the manager wholesale

It is ~550 lines of menu and screen plus ~200 of supporting types, and it is structured around
**groups**: `AutocrafterManagerData` is a list of named groups of slot counts, and
`initializeGroups` lays them out top to bottom.

That maps onto this mod almost exactly -- **one group per Pattern Storage block** instead of one
per autocrafter -- and would bring named headers per storage block for free. What would not carry
over is the network-wide machinery: view types, the visible-to-the-manager flag, and search modes
tied to autocrafter names.

Worth doing, but after the crafter actually crafts. Recorded on #4.

## 0.0.17

**Patterns render as what they make**, not as a pattern icon.

RS already had the answer and it is one interface. `PatternRendering` asks the open screen -- if it
is a `PatternOutputRenderingScreen` -- whether a given stack should draw as its output; the
Autocrafter Manager, the Autocrafter and the Pattern Grid all implement it. So does this now.

**`containsPattern` is copied from RS reference-equality and all**, which looks like a bug and is
not: it identifies the one stack instance being drawn, so a pattern in a structure slot renders as
its output while the very same pattern sitting in the player's inventory below still renders as a
pattern. Comparing by value would turn the inventory into outputs too.

Also lifted `renderSlotContents` and added RS's `SearchIconWidget`. The slot rendering matters
more than it looks: the base screen scissors the row area and draws slots outside it, so without
redrawing them the contents of a stretched, scrolled list land in the wrong place. The pose
translate is theirs too.

### The standing instruction

Wraith: use as many RS classes as possible. Reimplementing what RS already solves has now cost
four bugs across two versions -- the blank panel, the floating inventory, the dead scrollbar, and
pattern icons -- every one of them a behaviour their code already had. **Look for the RS class
first.** Copy it if it is not public. Only write something new when RS genuinely does not do it.

## 0.0.16

**Four bugs from the first look at the real screen**, all of them from inheriting RS's stretching
screen without inheriting the jobs that come with it.

**Player inventory floated in the middle of the pattern area.** 0.0.15 left `resized()` empty,
reasoning that the screen owns layout. That is true of the pattern slots -- the screen moves those
to scroll and filter -- and exactly wrong for the player's inventory. A stretching screen has no
fixed height, so slots positioned at construction land wherever the window happened to be that
size, and `resized()` is the only notification that it changed. It now places them properly, with
the hotbar in its gap.

**The pattern area was blank.** RS's row texture is plain -- the Autocrafter Manager's list really
is empty grey until it has autocrafters, and it paints a well per slot as it draws each group. So
inheriting their texture means inheriting the job of drawing slots. `renderRows` now draws a well
behind every visible slot, taken from the slots themselves rather than a fixed grid, so a filtered
half-row shows the wells it has and no more.

**The scrollbar moved and nothing happened.** Setup was in `init()`, which runs before
`AbstractStretchingScreen` has worked out the row count, sized the window or built the scrollbar.
Moved to `init(int rows)`, which is called with the row count once all of that exists.

**The title is "MultiBlock Crafter"** rather than "Crafter Patterns".

The common thread: `AbstractStretchingScreen` is not a base class you extend and fill in the
blanks of. It hands out responsibilities -- position the inventory, draw the slots, lay out after
init -- and quietly renders something plausible-looking when you decline them.

## 0.0.15

**The pattern screen is now Refined Storage's stretching screen, not an imitation of it.**

0.0.14 drew its own flat panels because coupling to RS's GUI internals looked like a risk worth
avoiding. Checking what other addons actually do settled that: **Step Crafter** -- same author as
Cable Tiers -- extends `AbstractBaseScreen`, `AbstractBaseContainerMenu`, `AbstractBaseBlock` and
`AbstractBaseNetworkNodeContainerBlockEntity`, and mixins into `AbstractGridScreen`. Deep coupling
is simply how RS addons are written, and breaking on an RS update is the accepted cost of looking
and behaving like part of the mod you are adding to.

So the menu extends `AbstractBaseContainerMenu` and implements `ScreenSizeListener`, and the screen
extends `AbstractStretchingScreen` with RS's `SearchFieldWidget`. That brings the window that
stretches to your screen height, the real scrollbar, and the same chrome as every other RS screen.

The texture is RS's `autocrafter_manager.png` unmodified, and the magic numbers around it are
theirs too -- row bands at v=19/37/55, bottom at v=73 for 99px. Those are not numbers to pick,
they are numbers to match, which is exactly why the texture is worth keeping unedited.

`resized` is deliberately empty: the screen owns layout, and re-laying out from the menu would be a
second thing moving slots. RS's own menus use it because their layout lives in the menu; ours
does not.

## 0.0.14

**Right-clicking any block in the structure opens the pattern screen.** Search field at the top,
every pattern slot the structure has, scrolling.

Frame, Casing, Controller, CPU and Pattern Storage all behave identically -- the whole box is one
machine, and asking which block is the "real" one is friction with no upside. Shared from one
place rather than repeated on five blocks, because five copies of an interaction is five chances
for one to drift.

**A block that is not part of a structure does not open an empty window.** An empty window is the
least informative thing it could do: it looks like a bug and hides the real answer. It reports the
failure and the exact position in chat instead, in the same words `/rsmc info` uses, from the same
`MultiblockShape.find` call.

### Scrolling and searching are one operation

Both move slots rather than rebuilding the menu. `layout()` decides which slots are visible --
filtered by the search text, then offset by the scroll row -- and puts them where they go;
everything else goes off-screen where it cannot be clicked or drawn. One method moves slots, so
there is one answer to "why is this slot here".

That works because **slot positions are client-side only**: a click travels as a slot index, never
a coordinate. The server never has to agree about layout, so filtering cannot desync and a search
that hides a slot cannot lose the pattern in it.

An empty query shows empty slots too -- an empty slot is where you put a pattern, and a screen
that only showed occupied ones would have nowhere to put the first.

### An access transformer, for a good reason

`Slot.x` and `Slot.y` are final in 1.21.1. RS hits this too and ships a `Platform.setSlotY`
helper -- but it only scrolls vertically, and a filtered list re-flows horizontally as well, so
there is no y-only version of this. `public-f` on both fields, justified in the file: safe
precisely because nothing the server believes depends on where a slot is drawn.

The two sides are backed by different things on purpose. On the server the slots read straight
through `StructurePatterns` into the real block entities; on the client they are backed by a plain
container of the same size that vanilla slot syncing fills. The client is told **only the slot
count**, because that is the one thing it could not work out for itself.

The chrome is plain for now -- flat panels and slot wells, not the Autocrafter Manager's exact
look. Matching that needs `AbstractStretchingScreen` and its sprite plumbing, which is real
coupling to RS's GUI internals and the part most likely to break on an RS update. Worth doing as a
second pass, after the thing can be opened and used.

## 0.0.13

**`StructurePatterns`: every pattern slot in one structure, as a single container.** The screen will
be a view over this, and the provider node will read patterns out of it.

It is a view, not an owner -- the patterns stay in the Pattern Storage blocks the whole time. What
lives here is the arithmetic mapping a screen slot onto "which block, which slot inside it", in
one place rather than scattered across the menu and the node.

**Ordered by position, not by discovery order.** A player's patterns must not move around in the
screen because a chunk reloaded, and "which slot is slot 79" has to mean the same thing on the
server and the client -- which agree on nothing except the world. Sorting by coordinates is the
only ordering both can compute.

It scans the **interior only**. A Pattern Storage cannot be anywhere else, and skipping the shell
avoids 1,352 positions on a 16x16x16 that can never contain one.

The test drives the two slots either side of a storage boundary -- the last of the first block and
the first of the second -- because that is where the arithmetic goes wrong if it is going to, and
an off-by-one there looks completely normal until someone breaks a block and the wrong patterns
fall out.

Also worth recording: two large multi-line `perl -0pi` edits corrupted a Java file this session.
Structured edits for Java from here.

## 0.0.12

**Pattern Storage blocks actually hold patterns now** -- 78 each, saved with the block and dropped
when it breaks. The first half of #4, and the half everything else reads from.

The inventory is Refined Storage's own `PatternInventory`, so a slot accepts what RS considers a
pattern and nothing else. No second idea of validity to drift from theirs.

**Breaking one takes its patterns with it**, dropped like any container's contents. Intended, not
a leak: the alternative is patterns outliving the block they were in, which is exactly the
ownership question this whole design exists to avoid having.

A loot table cannot do that -- it drops the block, not the contents -- and RS's own container
blocks handle it in `AbstractBaseBlock.onRemove` for the same reason. Copied rather than
inherited, because inheriting would drag in their menu, naming and configuration-card behaviour.
The block-changed guard in there matters: without it the patterns would be dropped every time the
structure merely changed block state.

Tested both ways an inventory loses things quietly -- a save/load round trip, and the drop list --
because neither shows up as an error. A pattern that failed to save is simply gone next session.

**The GUI design is settled and recorded on #4**: RS's Autocrafter Manager is built entirely from
classes rsmc already compiles against (`AbstractStretchingScreen`, `SearchFieldWidget`,
`SearchIconWidget`, `AbstractBaseContainerMenu`, and a ready-made `PatternSlot`), so the screen
can match it rather than approximate it. What is genuinely ours is where the slots come from:
the Pattern Storage blocks inside one structure, gathered at open time.

## 0.0.11

**The powered branch is now tested, rather than taken on trust.**

Every state had a test except the one that matters most: a structure cabled to a real, powered
Refined Storage network reading ACTIVE. The gametest world had no RS controller in it, so
"formed and unpowered" was provable and "formed and running" was not.

That gap is exactly where the last bug lived. When powered meant `getNetwork() != null`, every
Controller ever placed satisfied it -- so ACTIVE was reachable for entirely the wrong reason, and
a test asserting only "goes blue eventually" would have agreed with the bug. It also matters more
than the others going forward, because the pattern provider will only run when the structure
reads as powered: get this wrong and the crafter is silently dead while every other test passes.

So the test builds a real network -- RS's own Creative Controller and a cable, plugged into the
Controller's exposed face -- and asserts ACTIVE. Confirmed by removing the power source and
watching it fail with INACTIVE.

`buildShell` takes an x offset now, because the Controller lands on the -x face and a cable needs
that column free, and the template has no negative coordinates.

## 0.0.10

**The screen lit up whether or not the structure was connected to anything.**

The cause is an assumption that reads as obvious and is simply false: **"has a network" is not a
test of anything.** RS's `NetworkBuilderImpl` creates a network for a lone container when there
is nothing to merge with, so `node.getNetwork() != null` is true the moment the Controller
initialises -- cabled or not. Every Controller ever placed was, by that measure, connected.

Now it asks RS's own question, the one `calculateActive` asks of every RS machine: is energy
required at all, and if so does the network hold at least what this structure draws. A one-node
network of our own making stores nothing, so it fails that on its own -- there is no special case
for "not really connected", because a network with nothing in it cannot power anything.

**Which is why the node's energy draw had to become real in the same change.** With a draw of
zero, `stored >= usage` is true of an empty network too and the bug returns intact. So
`StructurePower.energyUsage` is now wired in: 8 for the Controller, 1 per shell block, 4 per
Pattern Storage, and **each CPU costs exactly its tier weight** -- the same number it contributes
to steps/tick. Energy tracks throughput rather than volume, and the tier ladder stays
energy-neutral: four 1K CPUs and one 4K cost the same and do the same.

The gametest that should have caught this only asserted "not UNFORMED", which the bug satisfied.
It now asserts **INACTIVE exactly** for a formed, unpowered structure, and was confirmed by
restoring the old check and watching it fail. A loose assertion is how a bug passes review twice.

## 0.0.9

**The Controller has its own face.** Three of them, generated from the Casing texture: a dead grey
panel when unformed, a dark screen when formed but unplugged, a light blue one when live.

The old placeholder was Refined Storage's literal `grid/front.png`, and it looked enough like a
Grid to be mistaken for one -- the stuck-screen report in 0.0.8 turned out to be a Grid behaving
perfectly. **Looking like our own block is a correctness property here, not decoration**, because
the failure mode is a player debugging the wrong machine.

Generated by `tools/GenerateControllerTextures.java` rather than drawn, because all three are the
same Casing texture with one inset screen and the only difference is what the screen is doing.
Three near-identical hand-edited 16x16 images is how they drift apart -- one gets a bezel tweak the
others do not and nobody notices for a month. Building on `casing.png` also means the bezel is
literally the same metal as the rest of the block.

The layered cutout model is gone with them: three plain `orientable` models, one per state, and
RS's grid textures are out of the repo entirely.

`assetCheck` now covers all three faces and all three models -- `textureOf()` only names the
unformed one, so without that the other two could be deleted and nothing would notice until a
structure formed in game and the face went missing. 71 checks.

## 0.0.8

**`/rsmc info` now prints the server's own view of the Controller screen**, and prints it even when
the structure does not form -- which is exactly when it is wanted.

Prompted by a report that breaking blocks out of a finished crafter left the screen lit while
breaking the cable correctly turned it off. A gametest was written to reproduce it -- build,
settle, break a CPU, a Pattern Storage, a Casing and a Frame at once, wait out the poll -- and it
**passed**, proving the server-side path correct for exactly those steps.

That test reads `helper.getBlockState`, which is the *server* level, so it is blind to a client
drawing a stale model. Rather than guess which half was wrong, the command now reports both: what
`MultiblockShape.find` says, and what the block state actually is. If they disagree the client is
stale; if they agree and the screen still looks wrong, the update never arrived.

The gametest is kept. It reproduces nothing today, but it pins the behaviour: if the refresh ever
regresses -- and #3 is going to rewrite it from a poll into change-driven updates -- this is the
case that catches it.

## 0.0.7

**The active screen is light blue**, the colour a Refined Storage Grid ships with, rather than the
saturated blue dye colour. One texture swap -- `grid/cutouts/light_blue.png` instead of
`blue.png`.

Filed #8 for the rest of RS's colour system, with the finding that decides it: **RS colours are
not cosmetic.** `ColoredConnectionStrategy.canAcceptIncomingConnection` refuses a connection
between differently-coloured blocks -- that is how players build isolated sub-networks with
coloured cable. So adopting it means choosing whether an rsmc Controller's colour is paint or
wiring, and that is a decision rather than a freebie.

We currently use the default connection strategy, so all six faces connect and colour is ignored.

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

