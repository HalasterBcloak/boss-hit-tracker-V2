package com.bosshittracker;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * One hit a party member landed on a tracked NPC, shared with the rest of the party.
 */
public class BossHitPartyHit extends PartyMemberMessage
{
	private String boss;
	private int npcIndex;
	private String weapon;
	private int amount;

	/** Used by Gson when the message arrives. */
	@SuppressWarnings("unused")
	public BossHitPartyHit()
	{
	}

	public BossHitPartyHit(String boss, int npcIndex, String weapon, int amount)
	{
		this.boss = boss;
		this.npcIndex = npcIndex;
		this.weapon = weapon;
		this.amount = amount;
	}

	public String getBoss()
	{
		return boss;
	}

	public int getNpcIndex()
	{
		return npcIndex;
	}

	public String getWeapon()
	{
		return weapon;
	}

	public int getAmount()
	{
		return amount;
	}
}
