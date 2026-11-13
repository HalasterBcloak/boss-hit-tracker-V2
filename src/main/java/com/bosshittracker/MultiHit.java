package com.bosshittracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Weapons whose single attack (or special attack) shows up as several hitsplats on one target,
 * e.g. the Scythe of Vitur's three hits or Dragon claws' four. For accuracy, those hits count as one attack.
 * Based on the OSRS Wiki's multi-hit weapons and special attacks lists.
 */
final class MultiHit
{
	/**
	 * Lower-case part of the weapon name (every version contains it, e.g. "Holy Scythe of Vitur",
	 * "Torag's hammers 75") -> how many game ticks after an attack's first hit its other hits can land.
	 */
	private static final Map<String, Integer> WEAPONS = new LinkedHashMap<>();

	static
	{
		// Normal attacks that hit more than once
		window(1, "scythe of vitur", "dual macuahuitl", "tonalztics of ralos", "dark bow",
			"sulphur blades", "glacial temotli", "earthbound tecpatl", "infernal tecpatl",
			"torag's hammers", "twinflame staff", "karil's crossbow");
		// Special attacks that hit more than once (claws land over two ticks)
		window(1, "dragon claws", "burning claws", "dragon dagger", "abyssal dagger", "dragon knife",
			"magic shortbow", "webweaver bow", "crystal halberd", "dragon halberd", "dinh's bulwark");
		// Both hits land on the same tick. The Rosewood blowpipe attacks fast, so its normal shots a
		// tick apart must stay separate attacks.
		window(0, "rosewood blowpipe", "saradomin sword");
		// The Bounty Hunter Vesta's spear's second hit lands two ticks after the first
		window(2, "vesta's spear (bh)");
	}

	private MultiHit()
	{
	}

	private static void window(int ticks, String... names)
	{
		for (String name : names)
		{
			WEAPONS.put(name, ticks);
		}
	}

	/**
	 * How many ticks after an attack's first hit its other hits can land, or -1 if this weapon's
	 * hits are always separate attacks. The label may include a spell, e.g. "Twinflame staff (Fire Wave)".
	 */
	static int windowTicks(String weaponLabel)
	{
		String name = weaponLabel.toLowerCase();
		for (Map.Entry<String, Integer> e : WEAPONS.entrySet())
		{
			if (name.contains(e.getKey()))
			{
				return e.getValue();
			}
		}
		return -1;
	}
}
