package com.bosshittracker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.NpcID;

/**
 * Built-in list of boss NPC names (lower case), matching each boss's in-game name exactly.
 * Covers every boss on the OSRS hiscores plus the bosses inside each raid.
 * Anything missing can be added with the "Extra NPCs to track" setting.
 */
final class BossList
{
	private static final Set<String> BOSSES = new HashSet<>(Arrays.asList(
		// Slayer bosses
		"abyssal sire", "alchemical hydra", "araxxor", "cerberus", "kraken", "thermonuclear smoke devil",
		"dusk", "dawn",

		// Wilderness bosses
		"artio", "callisto", "calvar'ion", "vet'ion", "spindel", "venenatis",
		"chaos elemental", "chaos fanatic", "crazy archaeologist", "scorpia", "king black dragon",

		// God Wars Dungeon
		"general graardor", "commander zilyana", "kree'arra", "k'ril tsutsaroth", "nex",

		// Desert Treasure II
		"duke sucellus", "the leviathan", "the whisperer", "vardorvis",

		// Varlamore
		"sol heredit", "amoxliatl", "the hueycoatl", "doom of mokhaiotl", "yama",
		"eldric the ice king", "branda the fire queen",
		"blood moon", "blue moon", "eclipse moon",

		// Other bosses
		"ahrim the blighted", "dharok the wretched", "guthan the infested", "karil the tainted",
		"torag the corrupted", "verac the defiled",
		"bryophyta", "obor", "corporeal beast", "kalphite queen", "giant mole",
		"dagannoth prime", "dagannoth rex", "dagannoth supreme", "deranged archaeologist",
		"hespori", "phantom muspah", "sarachnis", "scurrius", "skotizo", "vorkath", "zulrah", "zalcano",
		"the nightmare", "phosani's nightmare", "tztok-jad", "tzkal-zuk",
		"crystalline hunllef", "corrupted hunllef", "the mimic",
		"brutus", "demonic brutus", "mad angel", "maggot king", "shellbane gryphon",

		// Chambers of Xeric (the multi-NPC rooms are matched by NPC ID in Encounters, so that e.g.
		// ordinary Lizardman shamans outside the raid aren't treated as a boss)
		"great olm", "tekton", "ice demon",

		// Theatre of Blood
		"the maiden of sugadinti", "pestilent bloat", "nylocas vasilias", "sotetseg", "xarpus", "verzik vitur",

		// Tombs of Amascut
		"ba-ba", "kephri", "zebak", "akkha", "tumeken's warden", "elidinis' warden",

		// The Fractured Archive
		"archive guardian", "ezekiel", "bork", "the smorks", "moia", "dreadnoth the jellied",
		"xarth", "dagon'hai elder", "dagon'hai monk", "ocellus"
	));

	/**
	 * Alternate forms that have a different in-game name, mapped to the boss they belong to, so a
	 * fight that passes through them stays one fight under one name.
	 */
	private static final Map<String, String> ALIASES = new HashMap<>();

	static
	{
		ALIASES.put("tekton (enraged)", "Tekton");
		ALIASES.put("doom of mokhaiotl (shielded)", "Doom of Mokhaiotl");
		ALIASES.put("doom of mokhaiotl (burrowed)", "Doom of Mokhaiotl");
		ALIASES.put("enraged blood moon", "Blood Moon");
		ALIASES.put("enraged blue moon", "Blue Moon");
		ALIASES.put("enraged eclipse moon", "Eclipse Moon");
	}

	/**
	 * NPCs that are part of a boss fight but have a different name, matched by their exact NPC ID
	 * (not by name, since other NPCs can share the name).
	 */
	private static final Map<Integer, String> PART_OF_BOSS = new HashMap<>();

	static
	{
		// The Blade, which you damage during Ocellus's second phase
		PART_OF_BOSS.put(NpcID.TFA_OCELLUS_P2_SWORD_HITBOX, "Ocellus");
	}

	private BossList()
	{
	}

	static boolean isBoss(String lowerCaseName)
	{
		return BOSSES.contains(lowerCaseName) || ALIASES.containsKey(lowerCaseName);
	}

	/** The boss this NPC is part of, if it's one of those matched by NPC ID; otherwise null. */
	static String bossForNpcId(int npcId)
	{
		return PART_OF_BOSS.get(npcId);
	}

	/** The name a boss is recorded under: its main name for alternate forms, otherwise the in-game name. */
	static String displayName(String name)
	{
		String main = ALIASES.get(name.toLowerCase());
		return main == null ? name : main;
	}
}
