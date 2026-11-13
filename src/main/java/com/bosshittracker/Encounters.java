package com.bosshittracker;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.NpcID;

/**
 * Boss fights made of several NPCs, recorded as one encounter with a per-target breakdown.
 * Members are matched by exact NPC ID, so NPCs that share a name (the two Dagon'hai Monks) stay apart.
 */
final class Encounters
{
	/** One NPC's place in an encounter. */
	static final class Member
	{
		final String encounter;
		final String label;
		/** A form shared by several members (e.g. a Vanguard walking): hits go to whichever member this NPC was last seen as. */
		final boolean shared;

		Member(String encounter, String label, boolean shared)
		{
			this.encounter = encounter;
			this.label = label;
			this.shared = shared;
		}
	}

	private static final Map<Integer, Member> MEMBERS = new HashMap<>();
	/** Encounter -> the members that must all die for it to count as a kill. */
	private static final Map<String, Set<String>> REQUIRED = new HashMap<>();
	/** NPC forms whose death ends their encounter on its own (e.g. a Warden dying in phase 3). */
	private static final Set<Integer> FINAL_FORMS = new java.util.HashSet<>();
	/**
	 * Encounters with a varying number of NPCs (e.g. Lizardman Shamans): won once every member that
	 * appeared has died.
	 */
	private static final Set<String> ALL_SEEN = new java.util.HashSet<>();

	static
	{
		// The Fractured Archive: all three must die
		member("Dagon'hai Trio", "Elder", true, NpcID.TFA_DAGONHAI_LEADER, NpcID.TFA_DAGONHAI_LEADER_UNSHIELDED);
		member("Dagon'hai Trio", "Monk 1", true, NpcID.TFA_DAGONHAI_MINION_1, NpcID.TFA_DAGONHAI_MINION_1_UNSHIELDED);
		member("Dagon'hai Trio", "Monk 2", true, NpcID.TFA_DAGONHAI_MINION_2, NpcID.TFA_DAGONHAI_MINION_2_UNSHIELDED);

		// The Fractured Archive: Bork is the kill; the Smorks fight alongside him
		member("Bork and the Smorks", "Bork", true, NpcID.TFA_BORK);
		member("Bork and the Smorks", "The Smorks", false, NpcID.TFA_SMORK);

		// Royal Titans: both must die
		member("Royal Titans", "Eldric the Ice King", true, NpcID.RT_ICE_KING, NpcID.RT_ICE_KING_INACTIVE);
		member("Royal Titans", "Branda the Fire Queen", true, NpcID.RT_FIRE_QUEEN, NpcID.RT_FIRE_QUEEN_INACTIVE);

		// Grotesque Guardians: both must die
		member("Grotesque Guardians", "Dawn", true,
			NpcID.GARGBOSS_DAWN_SPAWN, NpcID.GARGBOSS_DAWN_PHASE1, NpcID.GARGBOSS_DAWN_PHASE1_TRANSITION,
			NpcID.GARGBOSS_DAWN_PHASE3, NpcID.GARGBOSS_DAWN_DEATH,
			NpcID.LEAGUE_6_GG_DAWN_ALIVE, NpcID.LEAGUE_6_GG_DAWN_DEATH);
		member("Grotesque Guardians", "Dusk", true,
			NpcID.GARGBOSS_DUSK_SPAWN, NpcID.GARGBOSS_DUSK_PHASE1_DEFENSIVE, NpcID.GARGBOSS_DUSK_PHASE1_TRANSITION,
			NpcID.GARGBOSS_DUSK_PHASE1_FLYTRANSITION, NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING, NpcID.GARGBOSS_DUSK_PHASE3_DEFENSIVE,
			NpcID.GARGBOSS_DUSK_PHASE3_TRANSITION, NpcID.GARGBOSS_DUSK_PHASE4_SPAWN, NpcID.GARGBOSS_DUSK_PHASE4,
			NpcID.GARGBOSS_DUSK_DEATH, NpcID.LEAGUE_6_GG_DUSK_ALIVE, NpcID.LEAGUE_6_GG_DUSK_DEATH);

		// Great Olm: the head is the kill; the claws die and come back every phase
		member("Great Olm", "Head", true, NpcID.OLM_HEAD, NpcID.OLM_HEAD_SPAWNING);
		member("Great Olm", "Left claw", false, NpcID.OLM_HAND_LEFT, NpcID.OLM_HAND_LEFT_SPAWNING, NpcID.OLM_HAND_LEFT_DYING);
		member("Great Olm", "Right claw", false, NpcID.OLM_HAND_RIGHT, NpcID.OLM_HAND_RIGHT_SPAWNING, NpcID.OLM_HAND_RIGHT_DYING);

		// Chambers of Xeric rooms
		// Vanguards: three of them, each with its own combat form, sharing a walking form
		member("Vanguards", "Melee", false, NpcID.RAIDS_VANGUARD_MELEE);
		member("Vanguards", "Ranged", false, NpcID.RAIDS_VANGUARD_RANGED);
		member("Vanguards", "Magic", false, NpcID.RAIDS_VANGUARD_MAGIC);
		shared("Vanguards", "Vanguard", NpcID.RAIDS_VANGUARD_DORMANT, NpcID.RAIDS_VANGUARD_WALKING);
		ALL_SEEN.add("Vanguards");
		// Vespula: the room is won by destroying the Abyssal portal
		member("Vespula", "Vespula", false, NpcID.RAIDS_VESPULA_FLYING, NpcID.RAIDS_VESPULA_ENRAGED, NpcID.RAIDS_VESPULA_WALKING);
		member("Vespula", "Abyssal portal", true, NpcID.RAIDS_VESPULA_PORTAL);
		// Vasa Nistirio and the glowing crystals it heals from
		member("Vasa Nistirio", "Vasa Nistirio", true, NpcID.RAIDS_VASANISTIRIO_WALKING, NpcID.RAIDS_VASANISTIRIO_HEALING);
		member("Vasa Nistirio", "Glowing crystal", false, NpcID.RAIDS_VASANISTIRIO_CRYSTAL);
		// Muttadiles: the room is won when the big one dies
		member("Muttadiles", "Small Muttadile", false, NpcID.RAIDS_DOGODILE_JUNIOR);
		member("Muttadiles", "Big Muttadile", true, NpcID.RAIDS_DOGODILE_SUBMERGED, NpcID.RAIDS_DOGODILE);
		// Guardians: both must die
		member("Guardians", "Left Guardian", true, NpcID.RAIDS_STONEGUARDIANS_LEFT, NpcID.RAIDS_STONEGUARDIANS_LEFT_DEAD);
		member("Guardians", "Right Guardian", true, NpcID.RAIDS_STONEGUARDIANS_RIGHT, NpcID.RAIDS_STONEGUARDIANS_RIGHT_DEAD);
		// Skeletal Mystics, Lizardman Shamans and the tightrope's Deathly rangers/mages: every one that appears must die
		member("Skeletal Mystics", "Mystic 1", false, NpcID.RAIDS_SKELETONMYSTIC_A);
		member("Skeletal Mystics", "Mystic 2", false, NpcID.RAIDS_SKELETONMYSTIC_B);
		member("Skeletal Mystics", "Mystic 3", false, NpcID.RAIDS_SKELETONMYSTIC_C);
		ALL_SEEN.add("Skeletal Mystics");
		member("Lizardman Shamans", "Shaman", false, NpcID.RAIDS_LIZARDSHAMAN_A, NpcID.RAIDS_LIZARDSHAMAN_B);
		ALL_SEEN.add("Lizardman Shamans");
		member("Deathly Rangers & Mages", "Deathly ranger", false, NpcID.RAIDS_TIGHTROPE_RANGER);
		member("Deathly Rangers & Mages", "Deathly mage", false, NpcID.RAIDS_TIGHTROPE_MAGE);
		ALL_SEEN.add("Deathly Rangers & Mages");

		// The Hueycoatl: the head is the kill; you also damage its body and tail
		member("The Hueycoatl", "Head", true, NpcID.HUEY_HEAD, NpcID.HUEY_HEAD_RESPAWN_PLACEHOLDER,
			NpcID.HUEY_HEAD_INVULNERABLE, NpcID.HUEY_HEAD_DEFEATED, NpcID.HUEY_HEAD_ENRAGED);
		member("The Hueycoatl", "Body", false, NpcID.HUEY_BODY_PART);
		member("The Hueycoatl", "Tail", false, NpcID.HUEY_TAIL, NpcID.HUEY_TAIL_BROKEN);

		// Nex: Nex is the kill; her four generals are fought along the way
		member("Nex", "Nex", true, NpcID.NEX, NpcID.NEX_SPAWNING, NpcID.NEX_SOULSPLIT, NpcID.NEX_DEFLECT, NpcID.NEX_DYING);
		member("Nex", "Fumus", false, NpcID.NEX_SMOKEMAGE);
		member("Nex", "Umbra", false, NpcID.NEX_SHADOWMAGE);
		member("Nex", "Cruor", false, NpcID.NEX_BLOODMAGE);
		member("Nex", "Glacies", false, NpcID.NEX_ICEMAGE);

		// The Wardens (Tombs of Amascut): P1 the Obelisk, P2 the charging Wardens and their Cores,
		// P3 a Warden on a new health bar. The kill is a Warden dying in phase 3.
		member("The Wardens", "Obelisk", false,
			NpcID.TOA_WARDENS_P1_OBELISK_NPC_INACTIVE, NpcID.TOA_WARDENS_P1_OBELISK_NPC, NpcID.TOA_WARDENS_P2_OBELISK_NPC);
		member("The Wardens", "Core", false, NpcID.TOA_WARDEN_TUMEKEN_CORE, NpcID.TOA_WARDEN_ELIDINIS_CORE);
		member("The Wardens", "Elidinis' Warden", false,
			NpcID.TOA_WARDEN_ELIDINIS_PHASE1_INACTIVE, NpcID.TOA_WARDEN_ELIDINIS_PHASE1,
			NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED,
			NpcID.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE, NpcID.TOA_WARDEN_ELIDINIS_PHASE3, NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING,
			NpcID.TOA_WARDEN_P3_DEATH_ELIDINIS);
		member("The Wardens", "Tumeken's Warden", false,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE1_INACTIVE, NpcID.TOA_WARDEN_TUMEKEN_PHASE1,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE, NpcID.TOA_WARDEN_TUMEKEN_PHASE3, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING,
			NpcID.TOA_WARDEN_P3_DEATH_TUMEKEN);
		endsEncounter(NpcID.TOA_WARDEN_ELIDINIS_PHASE3, NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING, NpcID.TOA_WARDEN_P3_DEATH_ELIDINIS,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE3, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING, NpcID.TOA_WARDEN_P3_DEATH_TUMEKEN);
	}

	private Encounters()
	{
	}

	private static void shared(String encounter, String label, int... ids)
	{
		Member m = new Member(encounter, label, true);
		for (int id : ids)
		{
			MEMBERS.put(id, m);
		}
	}

	/** True for encounters won once every member that appeared has died. */
	static boolean allSeenMustDie(String encounter)
	{
		return ALL_SEEN.contains(encounter);
	}

	private static void member(String encounter, String label, boolean required, int... ids)
	{
		Member m = new Member(encounter, label, false);
		for (int id : ids)
		{
			MEMBERS.put(id, m);
		}
		Set<String> required0 = REQUIRED.computeIfAbsent(encounter, e -> new LinkedHashSet<>());
		if (required)
		{
			required0.add(label);
		}
	}

	private static void endsEncounter(int... ids)
	{
		for (int id : ids)
		{
			FINAL_FORMS.add(id);
		}
	}

	/** True if this form dying ends its encounter by itself (e.g. a Warden in phase 3). */
	static boolean endsEncounter(int npcId)
	{
		return FINAL_FORMS.contains(npcId);
	}

	/** This NPC's place in an encounter, or null if it isn't part of one. */
	static Member member(int npcId)
	{
		return MEMBERS.get(npcId);
	}

	/** True once every member that must die for this encounter has died. */
	static boolean isComplete(String encounter, Collection<String> deadMembers)
	{
		Set<String> required = REQUIRED.get(encounter);
		return required != null && !required.isEmpty() && deadMembers.containsAll(required);
	}
}
