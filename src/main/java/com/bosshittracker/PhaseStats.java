package com.bosshittracker;

/**
 * Your hits during one phase of a multi-stage boss kill.
 */
public class PhaseStats
{
	int hits;
	long damage;
	int maxHit;
	/** When your first hit in this phase landed, used to order phases and time them. */
	long firstHitMs;

	void addHit(int amount, long now)
	{
		if (hits == 0)
		{
			firstHitMs = now;
		}
		hits++;
		damage += amount;
		maxHit = Math.max(maxHit, amount);
	}

	public int getHits()
	{
		return hits;
	}

	public long getDamage()
	{
		return damage;
	}

	public int getMaxHit()
	{
		return maxHit;
	}

	public long getFirstHitMs()
	{
		return firstHitMs;
	}

	void merge(PhaseStats other)
	{
		firstHitMs = hits == 0 ? other.firstHitMs : Math.min(firstHitMs, other.firstHitMs);
		hits += other.hits;
		damage += other.damage;
		maxHit = Math.max(maxHit, other.maxHit);
	}

	PhaseStats copy()
	{
		PhaseStats c = new PhaseStats();
		c.hits = hits;
		c.damage = damage;
		c.maxHit = maxHit;
		c.firstHitMs = firstHitMs;
		return c;
	}
}
