package com.bosshittracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All saved data, keyed by boss name. Stored as JSON per RuneScape account.
 */
public class HitData
{
	/** Format version, used to run one-time upgrades of older saved data. */
	int version;
	Map<String, BossStats> bosses = new LinkedHashMap<>();
}
