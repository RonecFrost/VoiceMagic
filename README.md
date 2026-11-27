# Voice Magic - Ars Nouveau Incantations v0.7.0

Minecraft **1.21.1**, **NeoForge**. Requires Ars Nouveau (tested at compile time against **5.13.5**).
Install Ars Nouveau and its dependencies via CurseForge. Place only one `voicemagic` JAR in mods.

## Features

- **Complete incantations:** Save a spell using the Ars Nouveau spell crafting menu. Click its new **Voice** button, open the **Saved Spells** tab, and train a unique utterance five times. Hold Left Alt with the book in hand, speak your phrase, and release to cast the saved Ars spell.
- **Improvised glyph casting:** In **Individual Glyphs**, train your own pronunciations for each unlocked Ars glyph. In Improvised Glyph mode, hold Left Alt, speak **one glyph word at a time**, and pause briefly (~0.45 seconds) after each word. Each recognized word adds exactly one glyph. **Left Ctrl** removes the last accepted glyph so you can say it again. Release Alt to cast the assembled Ars spell.
- **Crosshair-centered waveform:** The circular HUD sits directly around the screen crosshair and consists of two connected waveform semicircles. Each half ripples strongly with microphone input and gently shifts hue over time. Saved spells show their initial form glyph and spell name; improvised spells show recognized glyph icons clockwise around the circle. The HUD vanishes immediately on releasing Left Alt.
- **Deliberate voice training (new in v0.6):** Each of the five training samples is recorded separately. Press **Start Recording**, say the complete word or phrase, then press **Stop Recording**. Inspect the take, choose **Save & Next** to commit it, or **Retry Take** to discard it. Repeat for all five. Silence never automatically stops or splits a training take, and the microphone reads the final audio frame before stopping. Recordings keep a generous buffer around the word for playback; acoustic matching removes only extra idle space, not the original sample. **Forget All & Retrain** resets a finished enrollment.
- **Recording playback:** Each newly saved take stores compressed 16 kHz audio **locally**; **Play Saved** randomly plays one of the saved samples. Old v0.4 voice profiles stored only acoustic templates and cannot replay until retrained. Existing trained words remain usable without retraining.

## Controls

- **V**: open voice training with an Ars Nouveau spellbook in your hand
- **Hold Left Alt**: listen, pausing briefly after each glyph word
- **Release Left Alt**: cast the recognized spell through Ars Nouveau
- **Left Ctrl while holding Alt**: remove the last accepted glyph; then speak it again
- **Semicolon (;)**: toggle Saved Spell and Improvised Glyph casting modes

Keys can be rebound in Minecraft controls.

## Technical and privacy notes

The recognizer uses fully offline, locally trained spectral patterns. This update **removes the continuous-speech segmentation experiment** and restores single-word matching using short pauses; separate spoken glyphs are generally easier to distinguish than overlapping connected speech. It never sends microphone audio over the network; only a recognized spell ID or glyph sequence travels to the server.

Profiles: `config/voicemagic/profiles/<player-uuid>.json`
New playback recordings (compressed raw audio): `config/voicemagic/recordings/<player-uuid>/`

The user can delete voice recordings via **Forget & retrain**; data stays on their computer. Existing v0.4 profiles with no audio files can continue casting normally. Make a backup before upgrading if you value recorded samples.

The server verifies the held spellbook, glyph knowledge and tier, and defers execution, costs and validity to Ars Nouveau. For multiplayer, install the add-on on both server and client. A physical Ars Nouveau book must still be held in either hand.

The voice GUI draws a single opaque panel with correctly layered controls and captions. The manual trainer does not use the casting microphone's silence-based utterance detection. The manual training system from v0.6 remains unchanged. Improvised casting again uses individual trained-word matches separated by short pauses. UI scaling and microphone latency still need real in-game testing.
