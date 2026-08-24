# Setting up a build

You need one file that is not in this repository.

## Refined Storage

`libs/` is gitignored. It holds Refined Storage's jar, which is somebody else's code and not ours
to redistribute — so a fresh clone has none of it, and a build will stop with a message pointing
you here.

Copy `refinedstorage-neoforge-2.0.9.jar` out of any Minecraft 1.21.1 instance's `mods` folder
into `libs/`:

```
libs/refinedstorage-neoforge-2.0.9.jar
```

That is the whole of it. The jar is `compileOnly` — never bundled, never shaded — and it is also
what gets staged into `run/<runName>/mods` so a dev launch comes up with RS present.

Any 2.0.x build will do. Unlike rstweaks, rsmbac matches no bytecode: it compiles against
`@API`-annotated interfaces, so the exact build does not have to match the one you play with.

## Then

```
./gradlew build
```

`build` runs `shapeCheck`, the headless structure-detection suite, so a broken shape rule fails
the build rather than waiting for someone to notice in game.

## Running it

```
./gradlew runClient
```

Refined Storage is staged into the run's mods folder automatically. If `libs/` is empty the run
stops first with a readable error instead of a wall of FML output about a missing dependency.
