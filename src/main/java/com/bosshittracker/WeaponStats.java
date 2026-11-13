package com.bosshittracker;

/**
 * Totals for the hits you landed with one weapon.
 */
public class WeaponStats
{
	int hits;
	int zeros;
	long damage;
	int maxHit;

	void addHit(int amount)
	{
		hits++;
		damage += amount;
		if (amount == 0)
		{
			zeros++;
		}
		if (amount > maxHit)
		{
			maxHit = amount;
		}
	}

	void merge(WeaponStats other)
	{
		hits += other.hits;
		zeros += other.zeros;
		damage += other.damage;
		maxHit = Math.max(maxHit, other.maxHit);
	}

	/** Take away a fight's hits, e.g. when that fight is deleted. The max hit is left as is. */
	void subtract(WeaponStats other)
	{
		hits = Math.max(0, hits - other.hits);
		zeros = Math.max(0, zeros - other.zeros);
		damage = Math.max(0, damage - other.damage);
	}

	WeaponStats copy()
	{
		WeaponStats c = new WeaponStats();
		c.merge(this);
		return c;
	}

	public int getHits()
	{
		return hits;
	}

	/** Hits that did 0 damage. Since May 2024 a player's successful hit always does at least 1, so these are misses. */
	public int getZeros()
	{
		return zeros;
	}

	public long getDamage()
	{
		return damage;
	}

	public int getMaxHit()
	{
		return maxHit;
	}

	public double getAverageHit()
	{
		return hits == 0 ? 0 : (double) damage / hits;
	}

	/** Accuracy: share of hits that did damage, 0-100. */
	public double getNonZeroPercent()
	{
		return hits == 0 ? 0 : 100.0 * (hits - zeros) / hits;
	}
}
