# Voice Magic - Ars Nouveau Incantations v0.8.0

Minecraft **1.21.1**, **NeoForge**. Requires Ars Nouveau (tested at compile time against **5.13.5**).
Install Ars Nouveau and its dependencies via CurseForge. Place only one `voicemagic` JAR in mods.

## Features

- **Complete incantations:** Create and save spells in any Ars Nouveau book. The **Voice** interface lists spells from **every** book in your normal inventory, including the hotbar and offhand. Train a phrase once for each unique recipe; the book need not be held when you cast. Saved spells are resolved against the actual book containing that spell.
- **Improvised glyph casting:** In **Individual Glyphs**, train your own pronunciations for each unlocked Ars glyph. In Improvised Glyph mode, hold Left Alt, speak **one glyph word at a time**, and pause briefly (~0.45 seconds) after each word. Each recognized word adds exactly one glyph. **Left Ctrl** removes the last accepted glyph so you can say it again. Release Alt to cast the assembled Ars spell.
- **Enlarged crosshair visualizer:** Two joined semicircles, now drawn as a ring of slender radial audio bars rather than a wiggling line. Each bar rises and falls independently with voice intensity, while the ring gently changes color. Spell/glyph icons remain around the center, and the HUD vanishes immediately on releasing Alt.
- **Deliberate voice training (new in v0.6):** Each of the five training samples is recorded separately. Press **Start Recording**, say the complete word or phrase, then press **Stop Recording**. Inspect the take, choose **Save & Next** to commit it, or **Retry Take** to discard it. Repeat for all five. Silence never automatically stops or splits a training take, and the microphone reads the final audio frame before stopping. Recordings keep a generous buffer around the word for playback; acoustic matching removes only extra idle space, not the original sample. **Forget All & Retrain** resets a finished enrollment.
- **Recording playback:** Each newly saved take stores compressed 16 kHz audio **locally**; **Play Saved** randomly plays one of the saved samples. Old v0.4 voice profiles stored only acoustic templates and cannot replay until retrained. Existing trained words remain usable without retraining.

## Controls

- **V**: open voice training while carrying at least one Ars Nouveau spellbook in your inventory
- **Hold Left Alt**: listen, pausing briefly after each glyph word
- **Release Left Alt**: cast the recognized spell through Ars Nouveau
- **Left Ctrl while holding Alt**: remove the last accepted glyph; then speak it again
- **Semicolon (;)**: toggle Saved Spell and Improvised Glyph casting modes

Keys can be rebound in Minecraft controls.

## Technical and privacy notes

**Inventory-book casting (v0.8):** An Ars Nouveau book anywhere in the main inventory, hotbar, or offhand is enough to cast by memory. You can carry multiple books: their saved recipes are all available, and the highest-tier carried book determines the maximum glyph tier for improvised casting. For saved spells, the server finds a book containing that exact recipe and validates it against that book's tier. The book's ItemStack is used as the casting tool context so Ars Nouveau's normal mana checks, effects, and event hooks still apply. The player does not temporarily equip a book. Nested storage containers and Curios slots are not included in this prototype. Multiple copies of the *exact same glyph recipe* share an incantation key, even when stored in different books.

The recognizer uses fully offline, locally trained spectral patterns. This update **removes the continuous-speech segmentation experiment** and restores single-word matching using short pauses; separate spoken glyphs are generally easier to distinguish than overlapping connected speech. It never sends microphone audio over the network; only a recognized spell ID or glyph sequence travels to the server.

Profiles: `config/voicemagic/profiles/<player-uuid>.json`
New playback recordings (compressed raw audio): `config/voicemagic/recordings/<player-uuid>/`

The user can delete voice recordings via **Forget & retrain**; data stays on their computer. Existing v0.4 profiles with no audio files can continue casting normally. Make a backup before upgrading if you value recorded samples.

The server verifies the held spellbook, glyph knowledge and tier, and defers execution, costs and validity to Ars Nouveau. For multiplayer, install the add-on on both server and client. A physical Ars Nouveau book must be carried in the player's ordinary inventory, but does not need to be equipped.

The voice GUI draws a single opaque panel with correctly layered controls and captions. The manual trainer does not use the casting microphone's silence-based utterance detection. The manual training system from v0.6 remains unchanged. Improvised casting again uses individual trained-word matches separated by short pauses. UI scaling and microphone latency still need real in-game testing.

**Test note:** v0.8 uses a custom inventory-aware cast path to pass the carried book as Ars Nouveau's casting-tool context. It has been compiled against Ars Nouveau 5.13.5; verify unusual glyphs and addon integrations in-game, particularly spell effects that inspect hand-held items. The old held-book behavior remains supported.
