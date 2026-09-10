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

    textures/block/ctm/cpu_1x/{particle,empty,center,vertical,horizontal}.png

The whole interior connects as one surface, so that is five folders -- twenty-five tiles:
`cpu_1x`, `cpu_4x`, `cpu_16x`, `cpu_64x` and `pattern_storage`. The tile names are counterintuitive
and must not be reasoned from:

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
`cpu_<tier>.png` renders, so the flat tile is needed either way.

### Everything in the interior connects to everything else

Decided: a 1x connects to a 64x connects to a Pattern Storage. This does **not** force the five
blocks to look alike -- each block draws only its own tiles, and a neighbour being a different block
only decides *whether a border is dropped* at the shared edge. So the tiers can be as distinct as
the art wants; what has to agree is only that a borderless junction between two of them reads as
deliberate, which in practice means a shared background and a per-block motif on top of it.

The interior is the set the shape code already calls `Role.INTERIOR`, so it gets a block tag of the
same name, `rsmbac:interior`, at `data/rsmbac/tags/block/interior.json`, and every one of the five
definitions carries the same condition (verified against Athena 4.0.6's `CtmUtils.parseTagCondition`,
which reads the `tag` key as a **block** tag):

```json
{ "athena:loader": "athena:ctm",
  "ctm_textures": {
    "particle":   "rsmbac:block/ctm/cpu_1x/particle",
    "empty":      "rsmbac:block/ctm/cpu_1x/empty",
    "center":     "rsmbac:block/ctm/cpu_1x/center",
    "vertical":   "rsmbac:block/ctm/cpu_1x/vertical",
    "horizontal": "rsmbac:block/ctm/cpu_1x/horizontal"
  },
  "connect_to": { "type": "tag", "tag": "rsmbac:interior" } }
```

**These five JSON files are deliberately not in the repo yet.** Athena renders whatever the JSON
points at, so shipping them before the tiles exist would give every player who has Athena installed
-- which is everyone on ATM10 -- a wall of missing-texture CPUs. They go in with the art, not before.

Worth knowing before spending effort on it: the interior is sealed inside the Casing and Frame shell
once the structure assembles, so a finished machine shows none of this. It is visible while building
one, and in a half-built or deliberately opened structure.

## The Controller faces and the Pattern Port are no longer anybody else's

`controller_front_unformed.png`, `controller_front_inactive.png`, `controller_front_active.png` and
`port.png` are generated from `casing.png` by `tools/GenerateTextures.java` — run
`java tools/GenerateTextures.java` from the repo root.

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
