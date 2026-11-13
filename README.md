# Boss Hit Tracker

A RuneLite plugin that records **every hit you land** on bosses, grouped by boss, by kill, and by the weapon you used.

Only your own hits are counted. Other players' hits, poison, venom, recoil and other damage sources are ignored.

## What it tracks

For each boss, pick a kill from the **Kill** dropdown (or click one under Recent kills) to see that kill on its own:
- Kill number, date, duration, total hits and damage
- **By weapon (this kill)**: hits, misses, damage, average hit, max hit, accuracy, and each weapon's share of that kill's damage

For multi-stage bosses (Verzik Vitur, Kalphite Queen, Vet'ion, Calvar'ion, Alchemical Hydra, The Nightmare, Phosani's Nightmare, Ocellus and The Wardens), each kill also has a **By phase** section showing your damage, hits, max hit and time in each phase.

### Multi-NPC bosses

Bosses made of several NPCs are recorded as **one encounter**, with a **By target** section showing your damage, hits and max hit on each NPC:

| Encounter | Targets | Counts as a kill when |
|---|---|---|
| Dagon'hai Trio | Elder, Monk 1, Monk 2 | all three die |
| Royal Titans | Eldric the Ice King, Branda the Fire Queen | both die |
| Grotesque Guardians | Dawn, Dusk | both die |
| Bork and the Smorks | Bork, The Smorks | Bork dies |
| Great Olm | Head, Left claw, Right claw | the head dies |
| The Hueycoatl | Head, Body, Tail | the head dies |
| Nex | Nex, Fumus, Umbra, Cruor, Glacies | Nex dies |
| The Wardens (ToA) | Obelisk, Core, Elidinis' Warden, Tumeken's Warden | a Warden dies in phase 3 |
| Vanguards (CoX) | Melee, Ranged, Magic | all three die |
| Vespula (CoX) | Vespula, Abyssal portal | the portal is destroyed |
| Vasa Nistirio (CoX) | Vasa Nistirio, Glowing crystal | Vasa dies |
| Muttadiles (CoX) | Small Muttadile, Big Muttadile | the big one dies |
| Guardians (CoX) | Left Guardian, Right Guardian | both die |
| Skeletal Mystics (CoX) | Mystic 1–3 | every one that appeared dies |
| Lizardman Shamans (CoX) | Shaman | every one that appeared dies |
| Deathly Rangers & Mages (CoX) | Deathly ranger, Deathly mage | every one that appeared dies |

Tekton and the Ice demon are tracked as single bosses. Every Chambers of Xeric combat room is covered.

A member killed by a teammate counts too, even if you never hit it.

### Failed attempts

Fights where the boss isn't killed are saved too, as **failed attempts**, with all the damage you dealt (per weapon, per phase and per party member). A fight counts as failed when:
- you die and the boss then leaves your view alive (e.g. your team wipes and you're sent out of the room),
- you leave the boss and don't come back within the **Resume fight within** time,
- you hop worlds or log out mid-fight.

If you die but your team finishes the boss, it's still a kill. Failed attempts are listed in red alongside your kills ("Failed #3"), and the lifetime view shows how many you've had and their total damage. They don't count towards your kill count or lifetime weapon totals.

The panel opens on your latest kill. Choose **All kills (lifetime)** to see the same weapon breakdown combined across every kill, plus your kill count and average damage per kill.

### Party breakdown

When you and your friends are in the same **RuneLite Party** and all have Boss Hit Tracker installed, each kill also shows **every player's hits per weapon** (Party section in the kill view), with each player's share of the total damage. Damage from anyone not sharing (not in the party, or without the plugin) is shown as **Other players**. Kills with party data are marked "(party)" in Recent kills.

To use it: open RuneLite's **Party** panel, have one person create a party and share the passphrase, and everyone joins.

While fighting, an on-screen overlay shows your hits and damage per weapon for the current kill.

### Personal bests

Each boss's lifetime view shows your **fastest solo kill**, **most damage in a kill** and **highest hit**. When a kill beats one of them, you get a chat message. Fastest kill only counts kills where nobody else damaged the boss, since the time runs from your first hit.

### Managing your data

- **Delete this fight** removes one fight from the history and from the totals and personal bests.
- **Reset boss** deletes everything for the selected boss.
- **Copy CSV** offers **Lifetime totals** (per boss and weapon) or **Every fight** (one row per weapon, phase, target and party member's weapon in every fight), for pasting into a spreadsheet.

Data is saved per RuneScape account in `.runelite/plugin-data/boss-hit-tracker/`, and survives restarts. Data from older versions, kept in RuneLite's settings, is moved there automatically. Kills recorded under single NPC names before multi-NPC encounters existed (e.g. Dusk and Dawn) are merged into their encounter, joining records from the same fight. Old "Lizardman shaman" records are left as they are, since they can't be told apart from ordinary slayer shamans.

## Settings

- **Bosses only** (on by default): only track bosses on the built-in list, which covers every boss on the OSRS hiscores plus each raid's bosses (Chambers of Xeric, Theatre of Blood, Tombs of Amascut and The Fractured Archive). Turn off to track every NPC you kill.
- **Extra NPCs to track**: comma-separated names to add, e.g. `Tormented Demon, Wyrm`.
- **Show fight overlay**
- **Track spells** (on by default): magic hits are listed per spell, e.g. `Kodai wand (Ice Barrage)`. Works for spells you click to cast and for autocast.
- **Party breakdown** (on by default): share your hits with your RuneLite Party and record each player's hits per weapon.
- **Record failed attempts** (on by default): save fights where the boss wasn't killed.
- **Failed attempts in totals** (off by default): include failed attempts' damage in the lifetime weapon totals and lifetime CSV.
- **Personal best messages** (on by default): a chat message when a kill beats a personal best.
- **Resume fight within** (default 120 seconds): how long a fight is kept after the boss leaves your view alive, so coming back continues it. 0 always starts a new fight.
- **Kills kept per boss**: how many individual kills are kept in history (lifetime totals are always kept).

## Running it

You need **IntelliJ IDEA** (the free Community edition is fine) and **Java 11 or newer** (IntelliJ can download one for you).

1. Unzip the project and open the `boss-hit-tracker` folder in IntelliJ (File → Open). Let it finish importing Gradle.
2. Open `src/test/java/com/bosshittracker/BossHitTrackerPluginTest.java` and click the green ▶ next to `main`.
3. The first run will fail to fully start. Edit the run configuration (Run → Edit Configurations):
   - VM options: `-ea`
   - Program arguments: `--developer-mode`
4. Run it again. A RuneLite window opens with the plugin loaded. Log in, and look for the red hitsplat icon in the sidebar.

Alternatively, from a terminal in the project folder: `./gradlew run` (Windows: `gradlew.bat run`).

## How it works and known limits

- Hits come from hitsplats marked as yours by the game (`Hitsplat.isMine()`), so each hit of a multi-hit attack (e.g. Scythe, Dragon claws) counts separately.
- The weapon credited is the one you were holding when you **started** the attack, so switching weapons while a projectile is still in the air credits the right weapon. If you start a new attack with a different weapon before an earlier projectile lands, that earlier hit may be credited to the new weapon.
- A kill is recorded when the boss dies. If the boss leaves your view mid-fight (e.g. you're sent to Sotetseg's shadow realm, or you walk out of range) and you hit it again within the **Resume fight within** time (2 minutes by default), it continues as the same fight. If you don't come back, it's saved as a failed attempt.
- The Wardens: P1 is the Obelisk, P2 the charging Wardens and their Cores, P3 the Warden's new health bar. A Warden's health bar emptying in P2 doesn't end the fight.
- Ocellus: your hits on The Blade in his second phase count towards the Ocellus kill as P2. The game has no separate form for his final phase, so his last two phases show together as P3.
- Multi-stage bosses count as one kill from your first hit to their final death, even when an earlier phase "dies" at 0 HP (e.g. Verzik P1, Kalphite Queen's first form). Phase times run from your first hit in a phase to your first hit in the next one.
- Members of multi-NPC bosses are matched by their exact NPC ID, so NPCs that share a name (the two Dagon'hai Monks) are kept apart. Kills recorded before version 1.6 stay under their old names (e.g. Dusk, Dawn). Alternate forms of the same boss (Tekton enraged, Doom of Mokhaiotl shielded/burrowed, the enraged Moons) are recorded under the main boss.
- Spells: a manual cast is read from the spell you click; autocast is read from your autocast setting.
- Party: the game doesn't say who dealt other players' hits, so each player's plugin reports their own hits to the party. Lifetime totals and CSV export cover your own hits only.
- Misses: since May 2024 a player's successful hit always does at least 1 damage, so a 0 is a miss (or a splash). Accuracy is the share of attacks that weren't misses.
- Special attacks are counted under the weapon's normal name, not separately.
