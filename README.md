# Voice Magic - Ars Nouveau Incantations (v0.4.0)

Minecraft **1.21.1**, **NeoForge** add-on. **Ars Nouveau is mandatory** (install it and its dependencies from CurseForge).

All speech is recorded and recognized locally. Five pronunciation samples teach a personal phrase or glyph word. Voice audio is **not uploaded**; derived acoustic templates are stored in `config/voicemagic/profiles/<player-uuid>.json`.

## The two casting modes

**Saved spells:** Use Ars Nouveau's normal spell crafting GUI to build and save a spell in your book. Click the small **Voice** button above the open book, switch to the **Saved Spells** tab, select that spell, and record your phrase five times. Hold the Ars Nouveau book in your main hand or offhand, hold **Left Alt**, speak the entire trained phrase, and release Alt to cast. Changing the saved spell's glyph recipe invalidates the old voice binding; retrain it.

**Improvised glyphs:** From the same **Voice** screen choose **Individual Glyphs**. You can train each glyph that you have actually unlocked in Ars Nouveau. Change casting mode to **IMPROVISED GLYPHS** with the button at the bottom of the screen or the keybind below. Hold Alt for the whole phrase and speak each known glyph word in order (brief pause between words). Release Alt to cast the constructed Ars spell. Spell shapes, mana, validators and glyph limits still belong to Ars.

## Controls

* **V** - Opens voice training with an Ars spellbook in hand.
* **Left Alt (hold)** - Listen and populate glyph/phrase slots; **release** to cast.
* **Semicolon (;)** - Toggle Saved Spell vs Improvised Glyph mode.
* **Backspace** - Remove the most recently recognized word while listening.

All keys are rebindable in Minecraft controls.

## Technical notes / test scope

* Only a spellbook actually **held** in main or offhand works; items elsewhere in inventory and Curios slots are not supported in this first add-on release.
* Only glyphs Ars Nouveau says you have learned appear in the improvised training tab.
* Every voice spell request is validated on the server against the held book, glyph knowledge, spell tier, normal Ars Nouveau spell validation, and Ars mana.
* Voice recognizer is an experimental locally trained MFCC-like / DTW matcher. It may confuse similar sounds, especially across large vocabularies. Start with clearly different words.
* The add-on adds a **Voice** button to the real Ars spell crafting screen. A dedicated voice-mode key replaces the proposed extra radial-wheel slice for v0.4; adding a native wheel segment is a later enhancement.
* Legacy v0.3 command spells and standalone projectile were removed from this add-on.
* Install the add-on on both server and client for multiplayer.
* The source builds with Java 21 through GitHub Actions. The normal downloadable artifact is `voicemagic-0.4.0.jar`.

## Licensing

Ars Nouveau is a separate mod. This repository contains only Voice Magic's own code; it does not redistribute the Ars Nouveau JAR or assets.
