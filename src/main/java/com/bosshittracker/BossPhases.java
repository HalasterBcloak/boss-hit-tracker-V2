package com.bosshittracker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.NpcID;

/**
 * Phases of multi-stage bosses, by NPC form. These bosses change form in place between phases;
 * some (e.g. Kalphite Queen, Vet'ion) even "die" at 0 HP before turning into their next form.
 */
final class BossPhases
{
	/** NPC form -> phase label, used for the per-phase breakdown. */
	private static final Map<Integer, String> PHASE = new HashMap<>();
	/**
	 * Forms that are not the boss's last phase (including transition forms). A death on one of
	 * these is the end of a phase, not the end of the fight.
	 */
	private static final Set<Integer> NOT_FINAL = new HashSet<>();

	static
	{
		// Verzik Vitur (normal, entry/story and hard mode)
		phase("P1", true, NpcID.VERZIK_PHASE1, NpcID.VERZIK_PHASE1_STORY, NpcID.VERZIK_PHASE1_HARD);
		notFinal(NpcID.VERZIK_PHASE1_TO2_TRANSITION, NpcID.VERZIK_PHASE1_TO2_TRANSITION_STORY, NpcID.VERZIK_PHASE1_TO2_TRANSITION_HARD);
		phase("P2", true, NpcID.VERZIK_PHASE2, NpcID.VERZIK_PHASE2_STORY, NpcID.VERZIK_PHASE2_HARD);
		notFinal(NpcID.VERZIK_PHASE2_TO3_TRANSITION, NpcID.VERZIK_PHASE2_TO3_TRANSITION_STORY, NpcID.VERZIK_PHASE2_TO3_TRANSITION_HARD);
		phase("P3", false, NpcID.VERZIK_PHASE3, NpcID.VERZIK_PHASE3_STORY, NpcID.VERZIK_PHASE3_HARD);

		// Kalphite Queen: crawling form, then airborne form
		phase("P1", true, NpcID.KALPHITE_QUEEN, NpcID.CLANCUP_KALPHITE_QUEEN, NpcID.LEAGUE_6_KALPHITE_QUEEN);
		phase("P2", false, NpcID.KALPHITE_FLYINGQUEEN, NpcID.CLANCUP_KALPHITE_FLYINGQUEEN, NpcID.LEAGUE_6_KALPHITE_FLYINGQUEEN);

		// Vet'ion and Calvar'ion: first form, a non-attackable transition, then the second form
		phase("P1", true, NpcID.VETION, NpcID.VETION_SINGLE);
		notFinal(NpcID.VETION_TRANS, NpcID.VETION_TRANS_SINGLE);
		phase("P2", false, NpcID.VETION_2, NpcID.VETION_TRANS_2, NpcID.VETION_2_SINGLE, NpcID.VETION_TRANS_2_SINGLE);

		// Alchemical Hydra: four phases with transitions between them
		phase("P1", true, NpcID.HYDRABOSS);
		phase("P2", true, NpcID.HYDRABOSS_4);
		phase("P3", true, NpcID.HYDRABOSS_3);
		phase("P4", false, NpcID.HYDRABOSS_2);
		notFinal(NpcID.HYDRABOSS_P1_TRANSITION, NpcID.HYDRABOSS_P2_TRANSITION, NpcID.HYDRABOSS_P3_TRANSITION);

		// The Nightmare (shield up and shield down forms of each phase)
		phase("P1", true, NpcID.NIGHTMARE_PHASE_01, NpcID.NIGHTMARE_WEAK_PHASE_01);
		phase("P2", true, NpcID.NIGHTMARE_PHASE_02, NpcID.NIGHTMARE_WEAK_PHASE_02);
		phase("P3", false, NpcID.NIGHTMARE_PHASE_03, NpcID.NIGHTMARE_WEAK_PHASE_03);

		// Phosani's Nightmare
		phase("P1", true, NpcID.NIGHTMARE_CHALLENGE_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_01);
		phase("P2", true, NpcID.NIGHTMARE_CHALLENGE_PHASE_02, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_02);
		phase("P3", true, NpcID.NIGHTMARE_CHALLENGE_PHASE_03, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_03);
		phase("P4", true, NpcID.NIGHTMARE_CHALLENGE_PHASE_04, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_04);
		phase("P5", false, NpcID.NIGHTMARE_CHALLENGE_PHASE_05);

		// Ocellus (The Fractured Archive): you hit Ocellus in P1, The Blade in P2 (half its damage
		// carries over to Ocellus), then Ocellus again. The game has no separate form for his last
		// phase, so P3 covers both of the final phases.
		phase("P1", true, NpcID.TFA_OCELLUS_P1);
		phase("P2", true, NpcID.TFA_OCELLUS_P2, NpcID.TFA_OCELLUS_P2_SWORD_HITBOX);
		phase("P3", false, NpcID.TFA_OCELLUS_P3);

		// The Wardens (Tombs of Amascut): P1 the Obelisk, P2 the charging Wardens and Cores, P3 the Warden's new health bar
		phase("P1", true, NpcID.TOA_WARDENS_P1_OBELISK_NPC_INACTIVE, NpcID.TOA_WARDENS_P1_OBELISK_NPC,
			NpcID.TOA_WARDEN_ELIDINIS_PHASE1_INACTIVE, NpcID.TOA_WARDEN_ELIDINIS_PHASE1,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE1_INACTIVE, NpcID.TOA_WARDEN_TUMEKEN_PHASE1);
		phase("P2", true, NpcID.TOA_WARDENS_P2_OBELISK_NPC, NpcID.TOA_WARDEN_TUMEKEN_CORE, NpcID.TOA_WARDEN_ELIDINIS_CORE,
			NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED);
		phase("P3", false, NpcID.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE, NpcID.TOA_WARDEN_ELIDINIS_PHASE3,
			NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE,
			NpcID.TOA_WARDEN_TUMEKEN_PHASE3, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING);
	}

	private BossPhases()
	{
	}

	private static void phase(String label, boolean notFinal, int... ids)
	{
		for (int id : ids)
		{
			PHASE.put(id, label);
			if (notFinal)
			{
				NOT_FINAL.add(id);
			}
		}
	}

	private static void notFinal(int... ids)
	{
		for (int id : ids)
		{
			NOT_FINAL.add(id);
		}
	}

	/** The phase label for this NPC form, or null if the boss has no tracked phases. */
	static String phaseOf(int npcId)
	{
		return PHASE.get(npcId);
	}

	/** True if this form isn't the boss's last phase, so a "death" here only ends the phase. */
	static boolean isNotFinalPhase(int npcId)
	{
		return NOT_FINAL.contains(npcId);
	}
}
