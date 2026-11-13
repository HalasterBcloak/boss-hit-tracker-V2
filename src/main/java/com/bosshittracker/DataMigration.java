package com.bosshittracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time upgrades of saved data from older versions of the plugin.
 */
final class DataMigration
{
	/** The current saved-data format. */
	static final int CURRENT_VERSION = 2;

	/** Records from different NPCs that start within this long of each other's end were the same fight. */
	private static final long SAME_FIGHT_GAP_MS = 60_000;

	/**
	 * Before version 1.6, multi-NPC bosses were saved per NPC (e.g. "Dusk" and "Dawn" separately).
	 * Old name (lower case) -> { encounter name, target label }.
	 * "Lizardman shaman" is left alone: old records can't tell raid shamans from ordinary slayer ones.
	 */
	private static final Map<String, String[]> OLD_NAMES = new LinkedHashMap<>();

	static
	{
		OLD_NAMES.put("dusk", new String[]{"Grotesque Guardians", "Dusk"});
		OLD_NAMES.put("dawn", new String[]{"Grotesque Guardians", "Dawn"});
		OLD_NAMES.put("eldric the ice king", new String[]{"Royal Titans", "Eldric the Ice King"});
		OLD_NAMES.put("branda the fire queen", new String[]{"Royal Titans", "Branda the Fire Queen"});
		OLD_NAMES.put("dagon'hai elder", new String[]{"Dagon'hai Trio", "Elder"});
		OLD_NAMES.put("dagon'hai monk", new String[]{"Dagon'hai Trio", "Monk"});
		OLD_NAMES.put("bork", new String[]{"Bork and the Smorks", "Bork"});
		OLD_NAMES.put("the smorks", new String[]{"Bork and the Smorks", "The Smorks"});
		OLD_NAMES.put("tumeken's warden", new String[]{"The Wardens", "Tumeken's Warden"});
		OLD_NAMES.put("elidinis' warden", new String[]{"The Wardens", "Elidinis' Warden"});
		OLD_NAMES.put("vanguard", new String[]{"Vanguards", "Vanguard"});
		OLD_NAMES.put("skeletal mystic", new String[]{"Skeletal Mystics", "Mystic"});
		OLD_NAMES.put("muttadile", new String[]{"Muttadiles", "Muttadile"});
	}

	private DataMigration()
	{
	}

	/**
	 * Bring saved data up to the current format.
	 *
	 * @return true if anything changed (so it should be saved)
	 */
	static boolean migrate(HitData data, int historySize)
	{
		if (data.version >= CURRENT_VERSION)
		{
			return false;
		}
		if (data.version < 2)
		{
			mergeOldNames(data, historySize);
		}
		for (BossStats stats : data.bosses.values())
		{
			stats.backfillBests();
			stats.backfillFailedWeapons();
		}
		data.version = CURRENT_VERSION;
		return true;
	}

	/** Move per-NPC records into their encounter, joining records from the same fight into one. */
	private static void mergeOldNames(HitData data, int historySize)
	{
		// Encounter -> old boss entries that belong to it, with each one's target label
		Map<String, List<Object[]>> byEncounter = new LinkedHashMap<>();
		for (Map.Entry<String, BossStats> e : new ArrayList<>(data.bosses.entrySet()))
		{
			String[] target = OLD_NAMES.get(e.getKey().toLowerCase());
			if (target != null)
			{
				byEncounter.computeIfAbsent(target[0], k -> new ArrayList<>()).add(new Object[]{e.getKey(), target[1], e.getValue()});
			}
		}

		for (Map.Entry<String, List<Object[]>> e : byEncounter.entrySet())
		{
			String encounter = e.getKey();
			BossStats merged = data.bosses.getOrDefault(encounter, new BossStats());
			List<Object[]> records = new ArrayList<>();
			int oldKills = 0;
			int oldFailed = 0;
			for (Object[] old : e.getValue())
			{
				BossStats stats = (BossStats) old[2];
				// Each old kill was one NPC of the encounter, so the encounter was killed about as
				// many times as its most-killed NPC
				oldKills = Math.max(oldKills, stats.getKills());
				oldFailed = Math.max(oldFailed, stats.getFailedAttempts());
				merged.failedDamage += stats.getFailedDamage();
				addAll(merged.getWeapons(), stats.getWeapons(), merged, false);
				addAll(merged.getFailedWeapons(), stats.getFailedWeapons(), merged, true);
				for (KillRecord k : stats.getRecentKills())
				{
					records.add(new Object[]{old[1], k});
				}
				data.bosses.remove((String) old[0]);
			}
			merged.kills += oldKills;
			merged.failedAttempts += oldFailed;

			// Join records that were part of the same fight
			records.sort(Comparator.comparingLong(r -> ((KillRecord) r[1]).getTimestamp()));
			List<KillRecord> fights = new ArrayList<>();
			KillRecord current = null;
			long currentEnd = 0;
			for (Object[] r : records)
			{
				String label = (String) r[0];
				KillRecord k = (KillRecord) r[1];
				if (current == null || k.getTimestamp() > currentEnd + SAME_FIGHT_GAP_MS)
				{
					current = new KillRecord(k.getTimestamp());
					current.failed = true;
					fights.add(current);
				}
				mergeInto(current, k, label);
				currentEnd = Math.max(currentEnd, k.getTimestamp() + k.getDurationMs());
				current.durationMs = currentEnd - current.getTimestamp();
			}

			List<KillRecord> history = new ArrayList<>(merged.getRecentKills());
			history.addAll(fights);
			history.sort(Comparator.comparingLong(KillRecord::getTimestamp).reversed());
			while (history.size() > historySize)
			{
				history.remove(history.size() - 1);
			}
			merged.recentKills = history;
			merged.bestTimeMs = 0;
			merged.bestDamage = 0;
			merged.bestHit = 0;
			data.bosses.put(encounter, merged);
		}
	}

	private static void addAll(Map<String, WeaponStats> into, Map<String, WeaponStats> from, BossStats owner, boolean failed)
	{
		Map<String, WeaponStats> target = into;
		if (failed && owner.failedWeapons == null)
		{
			owner.failedWeapons = new LinkedHashMap<>();
			target = owner.failedWeapons;
		}
		else if (!failed && owner.weapons == null)
		{
			owner.weapons = new LinkedHashMap<>();
			target = owner.weapons;
		}
		for (Map.Entry<String, WeaponStats> e : from.entrySet())
		{
			target.computeIfAbsent(e.getKey(), w -> new WeaponStats()).merge(e.getValue());
		}
	}

	/** Add one old per-NPC record to a joined fight, as a target. */
	private static void mergeInto(KillRecord into, KillRecord from, String label)
	{
		// The joined fight is a kill if any part of it was
		into.failed = into.failed && from.isFailed();
		for (Map.Entry<String, WeaponStats> e : from.getWeapons().entrySet())
		{
			into.weapons.computeIfAbsent(e.getKey(), w -> new WeaponStats()).merge(e.getValue());
		}
		PhaseStats target = new PhaseStats();
		target.hits = from.getTotalHits();
		target.damage = from.getTotalDamage();
		target.maxHit = BossStats.maxHit(from.getWeapons());
		target.firstHitMs = from.getTimestamp();
		if (into.targets == null)
		{
			into.targets = new LinkedHashMap<>();
		}
		into.targets.computeIfAbsent(label, t -> new PhaseStats()).merge(target);
		for (Map.Entry<String, PhaseStats> e : from.getPhases().entrySet())
		{
			if (into.phases == null)
			{
				into.phases = new LinkedHashMap<>();
			}
			into.phases.computeIfAbsent(e.getKey(), p -> new PhaseStats()).merge(e.getValue());
		}
		for (Map.Entry<String, Map<String, WeaponStats>> p : from.getPlayers().entrySet())
		{
			if (into.players == null)
			{
				into.players = new LinkedHashMap<>();
			}
			Map<String, WeaponStats> player = into.players.computeIfAbsent(p.getKey(), n -> new LinkedHashMap<>());
			for (Map.Entry<String, WeaponStats> w : p.getValue().entrySet())
			{
				player.computeIfAbsent(w.getKey(), n -> new WeaponStats()).merge(w.getValue());
			}
		}
	}
}
