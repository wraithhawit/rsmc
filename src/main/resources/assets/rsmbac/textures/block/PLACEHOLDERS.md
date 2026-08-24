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

Four CPU tiers are needed (`cpu_1x`, `cpu_4x`, `cpu_16x`, `cpu_64x`), not the single `cpu.png` here.
Reference material from Cable Tiers and Refined Storage is unpacked in `texture-refs/` at the repo
root.

## The Controller faces are no longer anybody else's

`controller_front_unformed.png`, `controller_front_inactive.png` and `controller_front_active.png`
are generated from `casing.png` by `tools/GenerateControllerTextures.java` — run
`java tools/GenerateControllerTextures.java` from the repo root.

The first attempt used Refined Storage's actual `grid/front.png`, which made an rsmbac Controller look
enough like a Grid to be mistaken for one: a bug report about a crafter screen staying lit turned
out to be a Refined Storage Grid, which was behaving perfectly. Looking like our own block is a
correctness property, not a decoration.

Generated rather than drawn because all three are the Casing texture with one inset screen, and the
only difference is what the screen is doing. Three near-identical hand-edited 16x16 images is how
they drift apart.

## The pattern screen's GUI texture

`assets/rsmbac/textures/gui/patterns.png` is Refined Storage's `autocrafter_manager.png`, unmodified.
Kept as-is on purpose: `AbstractStretchingScreen` blits fixed offsets out of it (the row bands at
v=19/37/55, the bottom at v=73 for 99px), so a redrawn texture has to match that layout anyway. MIT,
covered by `ATTRIBUTION.md`.
