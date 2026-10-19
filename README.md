# Boss Hit Tracker

A RuneLite plugin that records **every hit you land** on bosses, grouped by boss, by kill, and by the weapon you used.

Only your own hits are counted. Other players' hits, poison, venom, recoil and other damage sources are ignored.

## What it tracks

For each boss, pick a kill from the **Kill** dropdown (or click one under Recent kills) to see that kill on its own:
- Kill number, date, duration, total hits and damage
- **By weapon (this kill)**: hits, zero hits, damage, average hit, max hit, % of hits that landed, and each weapon's share of that kill's damage

The panel opens on your latest kill. Choose **All kills (lifetime)** to see the same weapon breakdown combined across every kill, plus your kill count and average damage per kill.

### Party breakdown

When you and your friends are in the same **RuneLite Party** and all have Boss Hit Tracker installed, each kill also shows **every player's hits per weapon** (Party section in the kill view), with each player's share of the total damage. Damage from anyone not sharing (not in the party, or without the plugin) is shown as **Other players**. Kills with party data are marked "(party)" in Recent kills.

To use it: open RuneLite's **Party** panel, have one person create a party and share the passphrase, and everyone joins.

While fighting, an on-screen overlay shows your hits and damage per weapon for the current kill.

Data is saved per RuneScape account and survives restarts. **Copy CSV** in the side panel copies everything for pasting into a spreadsheet.

## Settings

- **Bosses only** (on by default): only track bosses on the built-in list. Turn off to track every NPC you kill.
- **Extra NPCs to track**: comma-separated names to add, e.g. `Tormented Demon, Wyrm`.
- **Show fight overlay**
- **Track spells** (on by default): magic hits are listed per spell, e.g. `Kodai wand (Ice Barrage)`. Works for spells you click to cast and for autocast.
- **Party breakdown** (on by default): share your hits with your RuneLite Party and record each player's hits per weapon.
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
- A kill is recorded when the boss dies. If you leave, teleport or the boss resets, that fight is discarded.
- Bosses whose forms have different names (e.g. Grotesque Guardians' Dusk and Dawn) are tracked under each name.
- Spells: a manual cast is read from the spell you click; autocast is read from your autocast setting.
- Party: the game doesn't say who dealt other players' hits, so each player's plugin reports their own hits to the party. Lifetime totals and CSV export cover your own hits only.
- Special attacks are counted under the weapon's normal name, not separately.
