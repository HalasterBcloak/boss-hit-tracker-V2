package com.bosshittracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BossHitTrackerConfig.GROUP)
public interface BossHitTrackerConfig extends Config
{
	String GROUP = "bosshittracker";

	@ConfigItem(
		keyName = "bossesOnly",
		name = "Bosses only",
		description = "Only track hits on bosses. Turn off to track hits on every NPC you kill.",
		position = 0
	)
	default boolean bossesOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "extraNpcs",
		name = "Extra NPCs to track",
		description = "Comma-separated NPC names to track in addition to the built-in boss list (e.g. Tormented Demon, Wyrm)",
		position = 1
	)
	default String extraNpcs()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show fight overlay",
		description = "Show your hits for the current fight in an on-screen overlay",
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackSpells",
		name = "Track spells",
		description = "Track magic hits per spell, e.g. \"Kodai wand (Ice Barrage)\", for both manual casts and autocast",
		position = 3
	)
	default boolean trackSpells()
	{
		return true;
	}

	@ConfigItem(
		keyName = "partyBreakdown",
		name = "Party breakdown",
		description = "When you're in a RuneLite Party, share your hits with the party and record each player's hits per weapon for every kill. Party members need Boss Hit Tracker installed; anyone else is listed as \"Other players\".",
		position = 4
	)
	default boolean partyBreakdown()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recordFailed",
		name = "Record failed attempts",
		description = "Save fights where the boss wasn't killed (you or your team died, you left, or it reset) as failed attempts, with all the damage you dealt. They're listed with your kills but don't count towards kill count or lifetime weapon totals.",
		position = 5
	)
	default boolean recordFailed()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeFailedInTotals",
		name = "Failed attempts in totals",
		description = "Include the damage from failed attempts in each boss's lifetime weapon totals and the lifetime CSV. Kill count and average per kill always count kills only.",
		position = 6
	)
	default boolean includeFailedInTotals()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pbMessages",
		name = "Personal best messages",
		description = "Show a chat message when a kill beats your fastest solo kill, most damage in a kill, or highest hit for that boss",
		position = 7
	)
	default boolean pbMessages()
	{
		return true;
	}

	@Range(min = 0, max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "resumeWindow",
		name = "Resume fight within",
		description = "If a boss leaves your view while still alive (e.g. Sotetseg's shadow realm, walking out of range) and you hit it again within this time, it continues as the same fight. Set to 0 to always start a new fight.",
		position = 8
	)
	default int resumeWindow()
	{
		return 120;
	}

	@Range(min = 1, max = 200)
	@ConfigItem(
		keyName = "historySize",
		name = "Kills kept per boss",
		description = "How many individual kills to keep in each boss's kill history",
		position = 9
	)
	default int historySize()
	{
		return 50;
	}
}
