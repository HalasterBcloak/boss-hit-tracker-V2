package com.bosshittracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything tracked for one boss: kill count, lifetime per-weapon totals, personal bests and
 * recent fights (newest first).
 */
public class BossStats
{
	int kills;
	int failedAttempts;
	long failedDamage;
	/** Lifetime weapon totals from kills. */
	Map<String, WeaponStats> weapons = new LinkedHashMap<>();
	/** Lifetime weapon totals from failed attempts, kept apart so they can be shown or left out. */
	Map<String, WeaponStats> failedWeapons = new LinkedHashMap<>();
	List<KillRecord> recentKills = new ArrayList<>();

	// Personal bests (0 = none yet)
	long bestTimeMs;
	long bestDamage;
	int bestHit;

	/** A fight where the boss wasn't killed. Kept in the history, but not in the kill count or (by default) lifetime totals. */
	void addFailed(KillRecord attempt, int historySize)
	{
		failedAttempts++;
		failedDamage += attempt.getTotalDamage();
		if (failedWeapons == null)
		{
			failedWeapons = new LinkedHashMap<>();
		}
		addWeapons(failedWeapons, attempt);
		addToHistory(attempt, historySize);
	}

	void addKill(KillRecord kill, int historySize)
	{
		kills++;
		if (weapons == null)
		{
			weapons = new LinkedHashMap<>();
		}
		addWeapons(weapons, kill);
		addToHistory(kill, historySize);
	}

	private static void addWeapons(Map<String, WeaponStats> totals, KillRecord record)
	{
		for (Map.Entry<String, WeaponStats> e : record.getWeapons().entrySet())
		{
			totals.computeIfAbsent(e.getKey(), w -> new WeaponStats()).merge(e.getValue());
		}
	}

	private void addToHistory(KillRecord record, int historySize)
	{
		if (recentKills == null)
		{
			recentKills = new ArrayList<>();
		}
		recentKills.add(0, record);
		while (recentKills.size() > historySize)
		{
			recentKills.remove(recentKills.size() - 1);
		}
	}

	/**
	 * Check a new kill against the personal bests and update them.
	 *
	 * @return a description of each earlier personal best this kill beat (empty if none; a first kill
	 *         sets the bests without announcing them)
	 */
	List<String> updateBests(KillRecord kill)
	{
		List<String> beaten = new ArrayList<>();
		if (kill.isFailed())
		{
			return beaten;
		}
		// Kill time runs from your first hit, so only a solo kill gives a fair time
		long time = kill.othersHelped ? 0 : kill.getDurationMs();
		if (time > 0 && (bestTimeMs == 0 || time < bestTimeMs))
		{
			if (bestTimeMs != 0)
			{
				beaten.add("fastest solo kill " + formatTime(time) + " (was " + formatTime(bestTimeMs) + ")");
			}
			bestTimeMs = time;
		}
		long damage = kill.getTotalDamage();
		if (damage > bestDamage)
		{
			if (bestDamage != 0)
			{
				beaten.add("most damage " + String.format("%,d", damage) + " (was " + String.format("%,d", bestDamage) + ")");
			}
			bestDamage = damage;
		}
		int hit = maxHit(kill.getWeapons());
		if (hit > bestHit)
		{
			if (bestHit != 0)
			{
				beaten.add("highest hit " + hit + " (was " + bestHit + ")");
			}
			bestHit = hit;
		}
		return beaten;
	}

	/** Fill in personal bests for data saved before they existed, from the kills still in the history. */
	void backfillBests()
	{
		if (bestTimeMs != 0 || bestDamage != 0 || bestHit != 0)
		{
			return;
		}
		recalculateBests();
		// Lifetime totals keep the highest hit of every kill, including ones no longer in the history
		bestHit = Math.max(bestHit, maxHit(getWeapons()));
	}

	/** Work out the personal bests again from the kills in the history (e.g. after deleting one). */
	private void recalculateBests()
	{
		bestTimeMs = 0;
		bestDamage = 0;
		bestHit = 0;
		for (KillRecord k : getRecentKills())
		{
			if (k.isFailed())
			{
				continue;
			}
			if (!k.othersHelped && k.getDurationMs() > 0 && (bestTimeMs == 0 || k.getDurationMs() < bestTimeMs))
			{
				bestTimeMs = k.getDurationMs();
			}
			bestDamage = Math.max(bestDamage, k.getTotalDamage());
			bestHit = Math.max(bestHit, maxHit(k.getWeapons()));
		}
	}

	/** Fill in failed-attempt weapon totals for data saved before they were kept, from the history. */
	void backfillFailedWeapons()
	{
		if (failedWeapons != null && !failedWeapons.isEmpty())
		{
			return;
		}
		failedWeapons = new LinkedHashMap<>();
		for (KillRecord k : getRecentKills())
		{
			if (k.isFailed())
			{
				addWeapons(failedWeapons, k);
			}
		}
	}

	/** Remove one fight from the history and take it out of the counts, totals and personal bests. */
	void removeRecord(KillRecord record)
	{
		if (!getRecentKills().remove(record))
		{
			return;
		}
		Map<String, WeaponStats> totals = record.isFailed() ? failedWeapons : weapons;
		if (record.isFailed())
		{
			failedAttempts = Math.max(0, failedAttempts - 1);
			failedDamage = Math.max(0, failedDamage - record.getTotalDamage());
		}
		else
		{
			kills = Math.max(0, kills - 1);
		}
		if (totals != null)
		{
			for (Map.Entry<String, WeaponStats> e : record.getWeapons().entrySet())
			{
				WeaponStats total = totals.get(e.getKey());
				if (total != null)
				{
					total.subtract(e.getValue());
					if (total.hits == 0)
					{
						totals.remove(e.getKey());
					}
				}
			}
		}
		if (!record.isFailed() && (record.getDurationMs() == bestTimeMs || record.getTotalDamage() == bestDamage
			|| maxHit(record.getWeapons()) == bestHit))
		{
			// The deleted kill held a personal best: work them out again from the kills that are left
			recalculateBests();
		}
	}

	static int maxHit(Map<String, WeaponStats> weapons)
	{
		int max = 0;
		for (WeaponStats w : weapons.values())
		{
			max = Math.max(max, w.maxHit);
		}
		return max;
	}

	static String formatTime(long ms)
	{
		long secs = ms / 1000;
		return String.format("%d:%02d", secs / 60, secs % 60);
	}

	public int getFailedAttempts()
	{
		return failedAttempts;
	}

	public long getFailedDamage()
	{
		return failedDamage;
	}

	/** Kills plus failed attempts, i.e. every fight ever recorded for this boss. */
	public int getTotalRecorded()
	{
		return kills + failedAttempts;
	}

	public int getKills()
	{
		return kills;
	}

	public Map<String, WeaponStats> getWeapons()
	{
		return weapons == null ? new LinkedHashMap<>() : weapons;
	}

	public Map<String, WeaponStats> getFailedWeapons()
	{
		return failedWeapons == null ? new LinkedHashMap<>() : failedWeapons;
	}

	/** Lifetime weapon totals from kills, plus failed attempts if asked for. */
	public Map<String, WeaponStats> getWeapons(boolean includeFailed)
	{
		if (!includeFailed)
		{
			return getWeapons();
		}
		Map<String, WeaponStats> all = new LinkedHashMap<>();
		for (Map<String, WeaponStats> source : List.of(getWeapons(), getFailedWeapons()))
		{
			for (Map.Entry<String, WeaponStats> e : source.entrySet())
			{
				all.computeIfAbsent(e.getKey(), w -> new WeaponStats()).merge(e.getValue());
			}
		}
		return all;
	}

	public List<KillRecord> getRecentKills()
	{
		return recentKills == null ? new ArrayList<>() : recentKills;
	}

	public long getBestTimeMs()
	{
		return bestTimeMs;
	}

	public long getBestDamage()
	{
		return bestDamage;
	}

	public int getBestHit()
	{
		return bestHit;
	}

	BossStats copy()
	{
		BossStats c = new BossStats();
		c.kills = kills;
		c.failedAttempts = failedAttempts;
		c.failedDamage = failedDamage;
		c.bestTimeMs = bestTimeMs;
		c.bestDamage = bestDamage;
		c.bestHit = bestHit;
		for (Map.Entry<String, WeaponStats> e : getWeapons().entrySet())
		{
			c.weapons.put(e.getKey(), e.getValue().copy());
		}
		for (Map.Entry<String, WeaponStats> e : getFailedWeapons().entrySet())
		{
			c.failedWeapons.put(e.getKey(), e.getValue().copy());
		}
		for (KillRecord k : getRecentKills())
		{
			c.recentKills.add(k.copy());
		}
		return c;
	}
}
