Bring professional stagecraft to Minecraft! Check out all of our feature blocks here:

## Spotlight

The spotlight block emits a pyramidal light beam. The stronger the redstone signal, the more opaque the beam becomes — and the more focused it gets.

The beam can be dyed with dye items, and is blocked by solid blocks.

When mounted on a contraption, the spotlight automatically emits a beam equivalent to a redstone signal strength of 4, while retaining its dyed color.

## Microphone & Speaker

The microphone must be placed on a tripod. It automatically "captures" sounds within its listening radius (a global config value, 16 blocks by default) and transmits them to its bound speaker.

The speaker then plays back whatever the microphone picks up — but only while it is redstone-powered. Multiple speakers placed adjacent to each other cluster into a single sound source (clustering range is configurable).

Both the microphone and the speaker are compatible with contraptions: a mounted microphone keeps listening, and a mounted speaker keeps playing (it has no redstone of its own to read while assembled).

## Truss

Professional stage rigging blocks.

The truss is also a casing, and it works exactly like Create's own: right-click a placed shaft, cogwheel or large cogwheel with a truss in hand and the truss wraps it. The result is a truss-encased shaft, cogwheel or large cogwheel — the truss frame standing around the part, with the part itself still turning inside it, still taking a shaft or a bracket where it did before, and visible through the frame. A sneak-wrench swaps it back to the bare part, exactly as it does with an andesite casing.

Like Create's andesite and brass casings, the three encased forms are blocks in their own right: breaking one drops the shaft or cogwheel it was wrapped around, and the casing itself is not recovered, just as Create's is not.

## Sprinkler

WIP...

## Fog Machine

WIP...

## Cold Spark Machine

The cold spark machine is a stage cabinet that shoots a fountain of cold sparks out of its top face. It turns in 90-degree steps with a wrench, which brings the control face — and the value box on it — to whichever side of the stage you are working from.

Its spray height (how far the fountain reaches) is set on that face with the same value box the spotlight uses for its beam length, from 1 to 16 blocks.

Inside is a single slot that takes nothing but Cold Spark Fuel, and it is fed from the sides: the top face is the spray and the bottom is the machine's base, so neither accepts or gives anything. One fuel is spent per shot.

Right-click the machine to open that slot by hand and load fuel straight from your inventory — shift-clicking moves fuel either way. Anything that has its own business with the block keeps the click instead: hold a block and you place it against the cabinet, hold a wrench and you turn the control face, exactly as before.

Redstone fires it: a rising signal starts a two-second spray, and the machine ignores the line for those two seconds — a bare flicker of redstone mid-spray is dropped rather than queued. A line held high therefore gives exactly one shot: to fire again, let it drop and rise once more. An unpowered or empty machine simply stays quiet.

It also works mounted on a Create: Aeronautics physics structure: assemble it onto a ship and it keeps its fuel, keeps its timing and sprays from wherever the ship has carried it.

The spray itself is a genuine fountain of individual sparks — hundreds of tiny white points thrown up to the configured height, drifting apart as they climb. They come out of a small aperture rather than a single point, and a spark leaves from the side it is about to drift towards, so the spray opens outwards like a real nozzle's instead of the two directions cancelling. A spark dies just after it reaches the top rather than falling back through the spray, so the fountain stops in the air. The sparks are light they make themselves: they are not lit by, and do not light, the world around them.

Each spark is drawn from a three-cell sprite strip, and its age picks the cell: it leaves the nozzle as a long streak, shortens as it climbs, and is a brief ember by the time it fades out at the top. Every stage is visible at once, on the sparks at that point in their flight — the sparks do not flicker between shapes together.

Each spark's sprite is turned to point along the way it was fired, so a spark that leaves the nozzle leaning outwards stays leaning that way all the way up — the spray reads as a cone thrown outwards rather than as a column of upright dots. The lean is deliberately small, a few degrees at most: it follows the spread of the cone itself, which is narrow. Seeing it at all needs an elongated spark sprite; a round one hides it.