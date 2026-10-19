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
	Map<String, WeaponStats> weapons = new LinkedHashMap<>();
	/** Player name -> weapon -> stats. Only kept for kills with other players in them; null otherwise. */
	Map<String, Map<String, WeaponStats>> players;

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
		c.weapons = copyWeapons(getWeapons());
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
