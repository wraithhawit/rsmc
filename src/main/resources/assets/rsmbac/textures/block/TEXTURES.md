# rsmbac's block textures

**Drawn by lavasurf**, as of 0.11.0. The one borrowed texture left is the pattern screen's GUI (see
the end of this file), which is Refined Storage's.

Until 0.11.0 this file was `PLACEHOLDERS.md` and every block texture was a Reborn Storage placeholder.
The last three of those — `cpu.png`, `casing_formed.png` and `frame_formed.png`, none referenced by
anything — were deleted when the real art landed.

Every PNG here is drawn art; nothing is generated. Replace a file in place and nothing else needs
editing — the item models parent to the block models and follow for free.

| block | files | joins |
|---|---|---|
| Frame | `frame.png`, `ctm/frame/*` | other Frames |
| Casing | `casing.png`, `ctm/casing/*` | Casing, Controller, Port |
| Crafter Controller | `controller_front_{unformed,inactive,active}.png`, `controller_front_active_s.png`; sides are `casing.png` | nothing — keeps its border |
| Pattern Port | `port.png` | nothing — keeps its border |
| CPU tiers | `cpu_1x.png`, `cpu_4x.png`, `cpu_16x.png`, `cpu_64x.png` | nothing |
| Pattern Storage | `pattern_storage.png` (sides), `pattern_storage_top.png` (top **and** bottom) | nothing |

The CPU tiers ladder orange / yellow / green / cyan, the same as Refined Storage's 1k / 4k / 16k /
64k storage blocks, which our tier names deliberately mirror.

## Connected textures

They go through Athena (ATM10 ships it), which reads `assets/rsmbac/athena/<block>.json` and wants
**five plain 16x16 tiles per block, not an atlas sheet**, one folder per block (EnderIO's layout):

    textures/block/ctm/casing/{particle,empty,center,vertical,horizontal}.png

The tile names are counterintuitive and must not be reasoned from:

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
appear mid-wall. No rotated or diagonal pieces exist in this scheme.

Athena is optional and there is no dependency: without it the JSON is ignored and the flat texture
renders.

**A resource pack with its own `athena/*.json` overrides the mod's.** The artist's test packs have
carried stale ones twice; delete them from the pack.

### Casing drops its border; the Controller and the Port keep theirs (0.11.1)

The artist's design: the Controller and the Port sit in the wall as **framed panels**, and the brick
of the Casing runs right up to their frames.

That falls out of one fact about Athena. **Its condition only ever tests the neighbour** — verified
in 4.0.6, where `lambda$parseTagCondition$8` and `lambda$parseStateCondition$7` both call `.is(...)`
on the *second* `BlockState` and never read the first. So *being joined to* and *drawing joined
tiles* are independent switches:

- **Tag membership** decides whether *neighbours* drop their border. Casing, Controller and Port are
  all in `rsmbac:shell`, and `athena/casing.json` connects to that tag — so the Casing drops its
  border against all three.
- **Having a definition** decides whether *that block* draws joined tiles. Only the Casing has one.
  The Controller and the Port have none, so they render their flat, bordered texture always.

`assetCheck` pins both halves: the two stay in the tag, and neither has an Athena definition nor a
model reaching Athena's loader.

**History, for whoever wants them joined again.** 0.10.0–0.11.0 did join them: Port through
`athena/port.json`, and the Controller — which cannot take a per-block definition, since that
replaces all twelve variants with one cube — through Athena's NeoForge geometry loader inside twelve
per-facing models, loaded `optional` so there was no dependency, one model per facing because
Athena ignores blockstate rotation. A generator built their joined tiles from the drawn faces. All
of it was removed in 0.11.1 at the artist's request; it is in git at commit `6632e5d`, and
CHANGELOG 0.10.0 and 0.11.0 carry the reasoning.

### The Frame connects to Frames only (0.10.2)

`athena/frame.json` with `"connect_to": { "type": "sameBlock" }`. Each edge of the box reads as one
beam, and because the Frame is in no tag, Frame and Casing keep their borders against each other.
Its rivets sit on the lone and `center` tiles only, each wholly inside one quadrant, so a beam shows
them at its ends and never splits one across a seam.

### The interior does not connect (decided 0.10.1)

The CPU tiers and Pattern Storage are plain blocks — the artist's call. 0.9.2 had planned every
interior block connecting to every other through an `rsmbac:interior` tag; it was never used and
was removed. Do not add it back without asking. A block joining *its own kind* would be an
`athena/<block>.json` with `sameBlock` and five tiles — no tag.

## Pattern Storage's end texture

`pattern_storage_top.png` on the top and, since 0.11.0, the bottom, via `cube_column`; the sides are
`pattern_storage.png`. (0.10.0 put it on the top only. The artist wanted both ends; the filename
stayed.)

## The running screen glows under shaders (0.10.3)

`controller_front_active_s.png` is a **labPBR 1.3 specular map**, paired with
`controller_front_active.png` by Iris purely by name. Channels: red smoothness, green reflectance
(F0, 0–229), blue porosity, **alpha emission — 0–254 is brightness, 255 is none**. Checked against
Complementary r5.8.1's `GetCustomEmission`, which reads `a < 1.0 ? a : 0.0`, and takes the minimum of
the sampled and full-resolution emission — so glow belongs on whole areas, not single pixels, or it
averages away with distance.

The artist's map lights the screen's cells at 254 and the dividers at 176. Any face can glow the
same way, the Port included, by adding its own `_s.png`.

**Players have to opt in.** Complementary: *RP Support* = labPBR, or *IPBR+ Emissive Mode* =
"labPBR > IPBR+". BSL: *Advanced Materials* on. With Complementary's default Integrated PBR+ only
the shader pack's own `block.properties` decides what glows (Euphoria Patches lists AE2's
controller; RS and rsmbac are not on it). Without shaders nothing reads these maps.

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
