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
