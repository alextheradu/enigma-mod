# Enigma

> Turn your Minecraft server into a configurable scavenger hunt.
> Players discover clue items from loot chests and mob drops, redeem them in order at exact world locations, and unlock a final sign-based solution.

## Showcase / Demo Video:
https://www.youtube.com/watch?v=mgZRauLlRfc

## Short Description

`Enigma` is a Fabric mod for Minecraft `1.20.1` that lets server admins run mystery events, treasure hunts, and progression-based clue chains without custom maps or command spaghetti.

## What Makes It Good

- Configurable clue hunt with ordered progression
- Clues can appear in specific loot tables and mob drops
- Each clue points to exact coordinates and reveals the next message only at the right block
- Duplicate clue pickups are blocked per player
- Final completion can trigger any server command
- Admin commands make it easy to start, stop, reset, reload, test, and distribute clues

## How It Plays

1. Start the hunt.
2. Players explore the world, raid configured structures, and defeat configured mobs.
3. Enigma injects clue items into the configured reward sources.
4. A clue item shows coordinates and must be used on the exact target block.
5. When redeemed, the player receives the next hint in the chain.
6. After all clues are redeemed, the player writes the secret phrase on a sign.
7. Enigma runs the configured reward command and finishes the puzzle.

## Why Server Owners Use It

Enigma is built for live events. It works well for:

- seasonal treasure hunts
- SMP mystery arcs
- community puzzle nights
- admin-run competitions
- lore delivery through exploration instead of chat spam

## Admin Control

Available commands:

- `/enigma start`
- `/enigma stop`
- `/enigma reload`
- `/enigma give <player>`
- `/enigma resetAll`
- `/enigma setChest <x y z>`

The included command set covers both production use and quick testing. You can start an event, hand out the full clue chain to a player, reset progress, or generate a demo chest with a guaranteed clue.

## Configurable Systems

Enigma reads its settings from `config/enigma/config.json`.

You can configure:

- whether the hunt is active
- the final secret phrase
- the reward command
- what happens to the finishing sign
- clue item material, name, and lore
- loot table sources and drop chances
- mob drop sources and drop chances
- the full clue list with coordinates and messages

## Built For Real Progression

Enigma tracks which clues each player has:

- received
- redeemed

That means players cannot farm the same clue over and over, and they cannot skip ahead in the sequence. The hunt stays structured even on active multiplayer servers.

## Compatibility

| Loader | Minecraft Version |
| --- | --- |
| Fabric | 1.20.1 |

Fabric API is required.

## Default Vibe

Out of the box, Enigma feels like a secret tucked inside your world:

- mysterious clue items
- coordinate-based discovery
- exact-location redemption
- a final phrase hidden behind full completion

It is lightweight, focused, and built around one thing: making exploration feel deliberate.

## Why?
I wanted to make a tool to help inexperienced server owners organize scavenger hunts easily. Also, I made this for the [Hackcraft Hack Club YSWS](https://hackcraft.hackclub.com/)!

