package com.bosshittracker;

/**
 * A boss you are currently fighting, before it dies.
 */
class ActiveFight
{
	final String boss;
	final KillRecord record;
	/** Set when the NPC dies; the kill is saved when it despawns. */
	boolean died;
	/** When the boss left your view alive (or a phase ended); used to resume the fight if you come back. */
	long pausedAt;
	/** How long after pausedAt the fight can still be resumed. */
	long pauseLimitMs;
	/** You died during this fight. If the boss then leaves your view alive, the fight is over (failed). */
	boolean youDied;
	/** For multi-NPC encounters: which members have died so far. */
	final java.util.Set<String> deadMembers = new java.util.HashSet<>();

	// Hits you saw from other players, and how much of that party members reported.
	// The difference is damage from players who weren't sharing.
	int othersSeenHits;
	long othersSeenDamage;
	int partyReportedHits;
	long partyReportedDamage;

	ActiveFight(String boss, long startTime)
	{
		this(boss, new KillRecord(startTime));
	}

	ActiveFight(String boss, KillRecord record)
	{
		this.boss = boss;
		this.record = record;
	}
}
