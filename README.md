# VoiceMagic v0.3.0 - NeoForge 1.21.1

An experimental Minecraft mod that recognizes player-trained spoken sounds locally. No language model or online speech service is required.

## Installation

Install `voicemagic-0.3.0.jar` in the `mods` folder of a NeoForge Minecraft 1.21.1 profile. For dedicated multiplayer, install the same mod on server and clients.

## Controls

* **V**: open spellbook. All concepts are available immediately in tabs: Spells, Targets, Modes, Effects.
* Click any concept, choose a personal label, and record **five** separate pronunciations, pausing between each.
* **Hold Left Alt** while speaking each trained concept separately. Each recognized sound fills one of three slots.
* **Backspace** while holding Alt removes the most recently recognized concept.
* **Release Left Alt** to cast your assembled spell. You don't need to carry a spellbook item.

## Spell grammar

* `Projectile`: launch the original pale rotating bolt. Does not require operator privileges.
* `Gamemode + Target + Mode`: example `Gamemode + Myself + Creative`.
* `Effect + Target + Potion Effect`: example `Effect + Myself + Saturation`. Effects are level I for 30 seconds, except instant effects, which apply immediately.

Targets: Myself (@s), Nearest Player (@p), Random Player (@r), All Players (@a), All Entities (@e). Gamemode cannot be used with All Entities.

For command-based test spells, Minecraft **cheats/operator level 2 are required**. The server uses its own allowlisted command builder rather than executing arbitrary client text. No custom effect IDs are accepted yet. The game uses vanilla server rules for target selection, effects, and permissions.

## Voice recordings

Voice training is based on local audio fingerprints and dynamic time warping, not English speech-to-text. Avoid very similar sounds across concepts. Acoustic templates are saved at `config/voicemagic/profiles/<uuid>.json`; raw mic audio is not saved. Using many trained concepts may slow recognition; this prototype needs real-world accuracy testing.

## Building

GitHub Actions will build with Java 21 and Gradle, then upload the compiled JAR under the successful workflow run's Artifacts section.

## Scope

This version is intended for command-word recognition and multi-concept test casting. The concept research, crystal discovery, alchemy, modifier grammar and personalized understanding progression are intentionally deferred.
