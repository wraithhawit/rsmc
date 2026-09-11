# These textures are placeholders

Every `.png` in this folder is Reborn Storage's, copied unchanged so the blocks have *something*
to render while the mod is built. **They are not the final art** and are tracked by issue #7.

Reborn Storage is MIT with no assets carve-out, so copying and modifying them is permitted; the
condition is that the notice ships with the result, which `ATTRIBUTION.md` does. Nothing here is
taken without a licence that allows it.

| file here | from |
|---|---|
| `frame.png` | `multiblock_frame.png` |
| `casing.png` | `multiblock_heat.png` (their Heat Conductor) |
| `cpu.png` | `multiblock_cpu.png` |
| `pattern_storage.png` | `multiblock_storage.png` |
| `frame_formed.png` | `multiblock_frame_ctm.png` |
| `casing_formed.png` | `multiblock_heat_ctm.png` |

## The `_formed` pair is worth keeping even after the art is redrawn

Those two are **connected-texture tiles Reborn swaps to once the machine assembles** — an
unassembled frame shows a plain bordered block, an assembled one shows a tile that reads as part of
a continuous surface.

That is the same effect as the "border leaves the individual blocks and wraps the whole structure"
idea in the closed issue #6, reached with a texture swap instead of a dynamic baked model. If that
look is wanted later, this is the cheap way to it: one extra texture per shell block and a
blockstate property, no model code at all.

## When drawing the real ones

### The four CPU tiers

Each tier owns its own file as of 0.9.1: `cpu_1x.png`, `cpu_4x.png`, `cpu_16x.png`, `cpu_64x.png`,
all 16x16. They are four copies of `cpu.png` until real art lands — **replace them in place and
nothing else needs editing**; the item models parent to the block models and follow for free.
`cpu.png` is no longer referenced by anything and is kept only as the source they were copied from.

Reference material from Cable Tiers and Refined Storage is unpacked in `texture-refs/` at the repo
root. Refined Storage's own storage blocks ladder orange / yellow / green / cyan for 1k / 4k / 16k /
64k; our tier names deliberately mirror theirs, so a player already reads that ladder.

### If connected textures are drawn

Connected textures go through Athena (ATM10 ships it), which reads
`assets/rsmbac/athena/<block>.json` and wants **five plain 16x16 tiles per block, not an atlas
sheet**. Following EnderIO's shipped layout, one folder per block:

    textures/block/ctm/casing/{particle,empty,center,vertical,horizontal}.png

Only the shell connects — see below. The tile names are counterintuitive and must not be reasoned
from:

| tile | when it is used | what it draws |
|---|---|---|
| `particle` | neither neighbour connects | border on **both** edges — the isolated block, and the break-particle sprite |
| `empty` | both neighbours and the diagonal connect | **no border at all**: the interior of a wall |
| `center` | both connect, the diagonal does not | inner-corner mark only |
| `vertical` | the up/down neighbour connects | borders left and right |
| `horizontal` | the left/right neighbour connects | borders top and bottom |

Athena splits each face into four quadrants and gives each quadrant the matching quadrant of
whichever tile it selected, so every tile is a full self-consistent 16x16 and only a quarter of it
shows at a time. **Interior pixels must be identical across all five tiles of one block** or seams
appear mid-wall. There are no rotated or diagonal pieces in this scheme.

Athena is optional and there is no dependency: with it absent the JSON is ignored and the flat
texture renders, so the flat tile is needed either way.

### The interior does not connect (decided 0.10.1)

The four CPU tiers and Pattern Storage are **plain blocks**, each drawn by its own flat texture —
the artist's call. 0.9.2 had planned the opposite, every interior block connecting to every other
through an `rsmbac:interior` tag; that tag was never used by anything and was removed in 0.10.1.
Do not add it back without asking.

If one of them should ever connect *to its own kind* (a row of 64x CPUs reading as one slab), that
is an `athena/<block>.json` with `"connect_to": { "type": "sameBlock" }` and five tiles — no tag.

### The shell connects, and the Controller is a special case

One group, one tag:

| tag | members |
|---|---|
| `rsmbac:shell` | `casing`, `controller`, `port` |

The mechanic that makes this work is worth stating plainly, because it is not obvious and it decides
the whole layout. **Athena's condition only ever tests the neighbour.** Verified in 4.0.6:
`lambda$parseTagCondition$8` and `lambda$parseStateCondition$7` both call `.is(...)` on the *second*
`BlockState` argument and never look at the first. The block asking the question is not part of the
answer. Two things follow:

1. **Connection is not automatically mutual.** If two blocks carry different conditions, A can drop
   its border against B while B keeps its border against A, and the seam looks half-finished. Every
   member of a group must carry the same condition — which is the real argument for a tag over an
   `or` list, since the tag is one string that cannot drift between five files.

2. **A block can be connected *to* without being connected *from*.** Tag membership is what makes
   *neighbours* drop their borders; having an `athena/<block>.json` is what makes *that block* draw
   connected tiles. They are independent, and only the first is needed to be part of a surface.

A `athena/controller.json` would still be wrong: the Controller has a front panel in three states
across four facings, and Athena's per-block definition **replaces every variant** with one connected
cube. Being a tag member with no definition (0.9.3) was not enough either — the Controller then kept
its own full border, so the wall was seamless up to it and bordered around it.

### What shipped in 0.10.0: the shell connects, Controller included

| block | how | tiles |
|---|---|---|
| `casing` | `athena/casing.json` | `ctm/casing/` — **drawn art, replace these** |
| `frame` | `athena/frame.json`, `sameBlock` (0.10.2) | `ctm/frame/` — **drawn art, replace these** |
| `port` | `athena/port.json` | `ctm/port/` — generated |
| `controller` | twelve models, `controller_<state>_<facing>.json` | `ctm/controller_front_<state>/` — generated, plus `ctm/casing/` on the other five faces |

The Controller uses a second entrance Athena has: a NeoForge **geometry loader**, `athena:athena`,
written inside a block model. Two facts about it decide the layout:

1. **It is loaded optionally.** `"loader": { "id": "athena:athena", "optional": true }` — NeoForge
   21.1 skips an optional loader that is not installed and builds the model from `parent` and
   `textures` like any vanilla model. So there is still no dependency.
2. **It ignores blockstate rotation.** Athena's baked model never reads the `ModelState`, so
   `"y": 90` does nothing. Each facing therefore has its own model with the screen written on that
   face (`ctm_textures` accepts `north`/`east`/…/`up`/`down` entries plus a `default` set), and the
   blockstate has no rotation at all. `assetCheck` pins both.

The Port and the Controller screens are **generated** by `tools/GenerateTextures.java`: it insets a
panel into `casing.png` and into each of the five casing tiles. The panel sits well inside the edge,
so the five results still differ only at the edge, as the format requires. So when new casing tiles
land, re-run the generator and the Port and all three screens follow.

**A hand-drawn panel is one file, not fifteen tiles.** Since 0.10.1 each panel is an overlay — 16x16,
transparent where the casing shows — and `tools/overlays/<face>.png` (`controller_front_unformed`,
`controller_front_inactive`, `controller_front_active`, `port`) replaces the procedural one when
present. `java tools/GenerateTextures.java --export-overlays <dir>` writes the current panels as
overlays to start from. Keep an overlay's outer 1px ring transparent: those are the pixels the five
tiles differ in.

The `ctm/casing/` tiles in the repo are derived from the placeholder `casing.png`, so the "connected"
look is barely visible until real tiles replace them.

**A resource pack with its own `athena/casing.json` overrides the mod's.** The 0.9.x test pack
shipped one with `sameBlock`, which connects Casing only to Casing — delete it from the pack.

### Pattern Storage has a top texture of its own (0.10.0)

`pattern_storage_top.png`, via `cube_bottom_top`; the bottom and sides stay `pattern_storage.png`.
It is a copy of the side until the art lands.

### The Frame connects to Frames only (0.10.2)

`athena/frame.json` with `"connect_to": { "type": "sameBlock" }` and five tiles in `ctm/frame/` —
decided by Wraith, with the artist drawing the tiles. Each edge of the box reads as one continuous
beam, and because the Frame is in no tag, Frame and Casing keep their borders against each other:
the walls stay visibly framed rather than melting into one blob. The `ctm/frame/` tiles in the repo
are derived from the placeholder `frame.png` until real ones replace them.

## The Controller faces and the Pattern Port are no longer anybody else's

`controller_front_unformed.png`, `controller_front_inactive.png`, `controller_front_active.png` and
`port.png` are generated from `casing.png` by `tools/GenerateTextures.java` — run
`java tools/GenerateTextures.java` from the repo root. Since 0.10.0 it also writes their connected
tiles from `ctm/casing/`; see above.

The first attempt used Refined Storage's actual `grid/front.png`, which made an rsmbac Controller look
enough like a Grid to be mistaken for one: a bug report about a crafter screen staying lit turned
out to be a Refined Storage Grid, which was behaving perfectly. Looking like our own block is a
correctness property, not a decoration.

Generated rather than drawn because all four are the Casing texture with one inset panel, and the
only difference is what is in the panel — a dot-matrix screen in three states, or an opening. Four
near-identical hand-edited 16x16 images is how they drift apart, and this way they all follow the
real Casing art for free when it lands.

Reborn Storage has an `io.png` for their IO Port, and it would have been usable under the same
licence as everything else in this table. Ours is generated instead because it makes the Port and
the Controller read as a pair in the same wall: the same inset panel, one with a display in it and
one with a hole.

## The pattern screen's GUI texture

`assets/rsmbac/textures/gui/patterns.png` is Refined Storage's `autocrafter_manager.png`, unmodified.
Kept as-is on purpose: `AbstractStretchingScreen` blits fixed offsets out of it (the row bands at
v=19/37/55, the bottom at v=73 for 99px), so a redrawn texture has to match that layout anyway. MIT,
covered by `ATTRIBUTION.md`.
