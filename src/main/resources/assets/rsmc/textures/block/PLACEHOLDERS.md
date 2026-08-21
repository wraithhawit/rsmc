# These textures are placeholders

Every `.png` in this folder is Reborn Storage's, copied unchanged so the blocks have *something*
to render while the mod is built. **They are not the final art** and are tracked by issue #1.

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

Four CPU tiers are needed (`cpu_1k`, `cpu_4k`, `cpu_16k`, `cpu_64k`), not the single `cpu.png` here.
Reference material from Cable Tiers and Refined Storage is unpacked in `texture-refs/` at the repo
root.
