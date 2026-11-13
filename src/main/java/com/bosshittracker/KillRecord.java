package com.bosshittracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Your hits on a single boss kill, grouped by weapon. When you were in a party, also each
 * player's hits grouped by weapon.
 */
public class KillRecord
{
	/** Name used for damage from players who weren't sharing hits (not in the party, or no plugin). */
	static final String OTHER_PLAYERS = "Other players";
	static final String UNKNOWN_WEAPON = "Unknown weapon";

	long timestamp;
	long durationMs;
	/** True when the boss wasn't killed (you or your team failed, left, or it reset). */
	boolean failed;
	/** Another player also damaged the boss, so the kill time isn't a solo time. */
	boolean othersHelped;
	Map<String, WeaponStats> weapons = new LinkedHashMap<>();
	/** Player name -> weapon -> stats. Only kept for kills with other players in them; null otherwise. */
	Map<String, Map<String, WeaponStats>> players;
	/** Phase label -> your hits in that phase. Only kept for multi-stage bosses; null otherwise. */
	Map<String, PhaseStats> phases;
	/** Target -> your hits on it, for bosses made of several NPCs (e.g. the Dagon'hai Trio); null otherwise. */
	Map<String, PhaseStats> targets;

	/** Used by Gson when loading saved data. */
	@SuppressWarnings("unused")
	KillRecord()
	{
	}

	KillRecord(long timestamp)
	{
		this.timestamp = timestamp;
	}

	void addHit(String weapon, int amount)
	{
		weapons.computeIfAbsent(weapon, w -> new WeaponStats()).addHit(amount);
	}

	void addPlayerHit(String player, String weapon, int amount)
	{
		if (players == null)
		{
			players = new LinkedHashMap<>();
		}
		players.computeIfAbsent(player, p -> new LinkedHashMap<>())
			.computeIfAbsent(weapon, w -> new WeaponStats())
			.addHit(amount);
	}

	void addPhaseHit(String phase, int amount, long now)
	{
		if (phases == null)
		{
			phases = new LinkedHashMap<>();
		}
		phases.computeIfAbsent(phase, p -> new PhaseStats()).addHit(amount, now);
	}

	void addTargetHit(String target, int amount, long now)
	{
		if (targets == null)
		{
			targets = new LinkedHashMap<>();
		}
		targets.computeIfAbsent(target, t -> new PhaseStats()).addHit(amount, now);
	}

	/** Your hits per target for multi-NPC bosses, or an empty map. */
	public Map<String, PhaseStats> getTargets()
	{
		return targets == null ? new LinkedHashMap<>() : targets;
	}

	/** Your hits per phase for multi-stage bosses, or an empty map. */
	public Map<String, PhaseStats> getPhases()
	{
		return phases == null ? new LinkedHashMap<>() : phases;
	}

	/** Damage from players who weren't sharing, where only totals are known. */
	void addOtherPlayers(int hits, long damage)
	{
		if (players == null)
		{
			players = new LinkedHashMap<>();
		}
		WeaponStats w = players.computeIfAbsent(OTHER_PLAYERS, p -> new LinkedHashMap<>())
			.computeIfAbsent(UNKNOWN_WEAPON, k -> new WeaponStats());
		w.hits += hits;
		w.damage += damage;
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	public long getDurationMs()
	{
		return durationMs;
	}

	public boolean isFailed()
	{
		return failed;
	}

	public Map<String, WeaponStats> getWeapons()
	{
		return weapons == null ? new LinkedHashMap<>() : weapons;
	}

	/** Each player's hits by weapon, or an empty map for solo kills. */
	public Map<String, Map<String, WeaponStats>> getPlayers()
	{
		return players == null ? new LinkedHashMap<>() : players;
	}

	/** True when this kill has damage from anyone besides you. */
	public boolean hasOtherPlayers()
	{
		return players != null && players.size() > 1;
	}

	static long totalDamage(Map<String, WeaponStats> weapons)
	{
		long total = 0;
		for (WeaponStats w : weapons.values())
		{
			total += w.damage;
		}
		return total;
	}

	static int totalHits(Map<String, WeaponStats> weapons)
	{
		int total = 0;
		for (WeaponStats w : weapons.values())
		{
			total += w.hits;
		}
		return total;
	}

	public int getTotalHits()
	{
		return totalHits(getWeapons());
	}

	public long getTotalDamage()
	{
		return totalDamage(getWeapons());
	}

	/** Damage from everyone in the kill (you, party members and other players). */
	public long getEveryoneDamage()
	{
		if (!hasOtherPlayers())
		{
			return getTotalDamage();
		}
		long total = 0;
		for (Map<String, WeaponStats> w : players.values())
		{
			total += totalDamage(w);
		}
		return total;
	}

	KillRecord copy()
	{
		KillRecord c = new KillRecord(timestamp);
		c.durationMs = durationMs;
		c.failed = failed;
		c.othersHelped = othersHelped;
		c.weapons = copyWeapons(getWeapons());
		if (phases != null)
		{
			c.phases = new LinkedHashMap<>();
			for (Map.Entry<String, PhaseStats> e : phases.entrySet())
			{
				c.phases.put(e.getKey(), e.getValue().copy());
			}
		}
		if (targets != null)
		{
			c.targets = new LinkedHashMap<>();
			for (Map.Entry<String, PhaseStats> e : targets.entrySet())
			{
				c.targets.put(e.getKey(), e.getValue().copy());
			}
		}
		if (players != null)
		{
			c.players = new LinkedHashMap<>();
			for (Map.Entry<String, Map<String, WeaponStats>> e : players.entrySet())
			{
				c.players.put(e.getKey(), copyWeapons(e.getValue()));
			}
		}
		return c;
	}

	private static Map<String, WeaponStats> copyWeapons(Map<String, WeaponStats> weapons)
	{
		Map<String, WeaponStats> c = new LinkedHashMap<>();
		for (Map.Entry<String, WeaponStats> e : weapons.entrySet())
		{
			c.put(e.getKey(), e.getValue().copy());
		}
		return c;
	}
}
