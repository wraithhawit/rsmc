# rsmbac's block textures

**Drawn by lavasurf**, as of 0.11.0. The one borrowed texture left is the pattern screen's GUI (see
the end of this file), which is Refined Storage's.

Until 0.11.0 this file was `PLACEHOLDERS.md` and every block texture was a Reborn Storage placeholder.
The last three of those — `cpu.png`, `casing_formed.png` and `frame_formed.png`, none referenced by
anything — were deleted when the real art landed.

## What is drawn, and what is generated

| block | drawn — edit these | generated — never edit |
|---|---|---|
| Frame | `frame.png`, `ctm/frame/*` | — |
| Casing | `casing.png`, `ctm/casing/*` | — |
| CPU tiers | `cpu_1x.png`, `cpu_4x.png`, `cpu_16x.png`, `cpu_64x.png` | — |
| Pattern Storage | `pattern_storage.png` (sides), `pattern_storage_top.png` (top **and** bottom) | — |
| Controller, Port | `tools/faces/<face>.png`, `tools/overlays/<face>_s.png` | `<face>.png`, `<face>_s.png`, all of `ctm/<face>/` |

`<face>` is `controller_front_unformed`, `controller_front_inactive`, `controller_front_active` or
`port`. After changing a casing tile, a drawn face or a glow map, run
`java tools/GenerateTextures.java` from the repo root.

### How a drawn face joins the wall

The artist drew each screen and the Port as one whole face. The generator turns that into five
tiles:

- the flat texture and the lone `particle` tile are **the drawn face, byte for byte**;
- the four joining tiles are **the casing's tile, with the face's shared interior copied in** — the
  pixels that are identical in all five casing tiles. With this casing that is exactly the 10x10
  at x/y 3–12, which is also where every panel sits.

So border detail — the corner screws on the screens, the cracked frame on the unformed one — shows
only on a face that is not joined. It cannot go on the joining tiles: the border band is precisely
what they vary, so it would land where the wall meets.

The shared interior is derived from the casing art on every run, not declared. A casing whose tiles
break the identical-interiors rule shrinks it, and the generator prints the pixel count
(`casing tiles share 100 interior pixels` today).

### The four CPU tiers

`cpu_1x.png` … `cpu_64x.png`, one per tier since 0.9.1; the item models parent to the block models
and follow for free. They ladder orange / yellow / green / cyan, the same as Refined Storage's
1k / 4k / 16k / 64k storage blocks, which our tier names deliberately mirror.

### Connected textures

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

The Port and the Controller screens' tiles are **generated** by `tools/GenerateTextures.java`, in
order of preference from: a drawn whole face in `tools/faces/` (what ships today — see the top of
this file); a drawn overlay in `tools/overlays/<face>.png`, 16x16 and transparent where the casing
shows, inset into every tile; or the procedural panel the placeholders used. `--export-overlays
<dir>` writes the procedural panels out. Either way, new casing tiles need only a re-run.

**A resource pack with its own `athena/casing.json` overrides the mod's.** The 0.9.x test pack
shipped one with `sameBlock`, which connects Casing only to Casing — delete it from the pack.

### Pattern Storage has an end texture of its own

`pattern_storage_top.png` on the top and, since 0.11.0, the bottom too, via `cube_column`; the sides
are `pattern_storage.png`. (0.10.0 put it on the top only, with `cube_bottom_top`. The artist wanted
both ends; the filename stayed.)

### The running screen glows under shaders (0.10.3)

`controller_front_active_s.png` and `ctm/controller_front_active/<tile>_s.png` are **labPBR 1.3
specular maps**. Iris pairs `foo_s.png` with `foo.png` by name, so every Athena tile needs its own
copy. Channels: red smoothness, green reflectance (F0, 0–229), blue porosity, **alpha emission —
0–254 is brightness, 255 is none**. Checked against Complementary r5.8.1's `GetCustomEmission`,
which reads `a < 1.0 ? a : 0.0`.

The shipped map is the artist's (`tools/overlays/controller_front_active_s.png`, copied byte for
byte onto all six): the screen's cells at 254, the dividers dimmer at 176, and 255 elsewhere. A
drawn map is used whole, not composited, because in labPBR the alpha is the emission and cannot
also mean transparency. Any face can glow that way, the Port included. Without a drawn map the
generator falls back to 254 over the whole inside of the panel — uniform rather than per dot,
because Complementary caps emission at the full-resolution value and a checkerboard averages away
with distance.

**Players have to opt in.** Complementary: *RP Support* = labPBR, or *IPBR+ Emissive Mode* =
"labPBR > IPBR+". BSL: *Advanced Materials* on. With Complementary's default Integrated PBR+ only
the shader pack's own `block.properties` decides what glows (Euphoria Patches lists AE2's
controller; RS and rsmbac are not on it). Without shaders nothing reads these maps.

### The Frame connects to Frames only (0.10.2)

`athena/frame.json` with `"connect_to": { "type": "sameBlock" }` and five tiles in `ctm/frame/` —
decided by Wraith, with the artist drawing the tiles. Each edge of the box reads as one continuous
beam, and because the Frame is in no tag, Frame and Casing keep their borders against each other:
the walls stay visibly framed rather than melting into one blob. Its rivets sit on the lone and
`center` tiles only, each wholly inside one quadrant, so a beam shows them at its ends and never
splits one across a seam.

## The Controller faces must not look like a Refined Storage Grid

The first placeholder Controller used Refined Storage's actual `grid/front.png`, which made it look
enough like a Grid to be mistaken for one: a bug report about a crafter screen staying lit turned
out to be a Refined Storage Grid, which was behaving perfectly. Looking like our own block is a
correctness property, not a decoration — worth re-checking whenever the screens are redrawn.

## The pattern screen's GUI texture

`assets/rsmbac/textures/gui/patterns.png` is Refined Storage's `autocrafter_manager.png`, unmodified.
Kept as-is on purpose: `AbstractStretchingScreen` blits fixed offsets out of it (the row bands at
v=19/37/55, the bottom at v=73 for 99px), so a redrawn texture has to match that layout anyway. MIT,
covered by `ATTRIBUTION.md`.
