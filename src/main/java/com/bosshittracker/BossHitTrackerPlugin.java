package com.bosshittracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.Filepath;
import net.runelite.api.ChatMessageType;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Boss Hit Tracker",
	internalName = "boss-hit-tracker",
	description = "Tracks every hit you land on bosses, broken down by boss, kill and weapon",
	tags = {"boss", "hit", "hitsplat", "damage", "weapon", "dps", "tracker", "pvm"}
)
public class BossHitTrackerPlugin extends Plugin
{
	private static final String DATA_KEY = "data";
	private static final String AUTOCAST_NAMES_KEY = "autocastNames";
	/** Words in the autocast menu that aren't spell names. */
	private static final Set<String> NOT_SPELL_NAMES = new HashSet<>(Arrays.asList(
		"autocast", "select", "choose", "cancel", "ok", "close", "spells", "defensive", "back"));
	private static final String UNARMED = "Unarmed";
	/** How long (in game ticks) an attack animation's weapon is used for the hitsplats that follow it. */
	private static final int ATTACK_WEAPON_TICKS = 8;
	/** Added to the weapon name for special attack hits, e.g. "Dragon claws (spec)". */
	static final String SPEC_SUFFIX = " (spec)";
	/**
	 * How long a multi-stage boss's fight is kept between phases, if the next phase appears as a new NPC
	 * (e.g. Ocellus leaving while you fight The Blade). Longer than the normal resume time, since a phase can take a while.
	 */
	private static final long PHASE_GAP_MS = 300_000;
	/** COM_MODE value used for autocast on staves. */
	private static final int AUTOCAST_STYLE = 4;

	@Inject
	private Client client;

	@Inject
	private BossHitTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BossHitTrackerOverlay overlay;

	@Inject
	private Gson gson;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ChatMessageManager chatMessageManager;

	/** The next snapshot of saved data waiting to be written to disk, and which account it belongs to. */
	private final AtomicReference<String[]> pendingWrite = new AtomicReference<>();
	/** Bumped on every load, so a slow load for an account you've since switched away from is ignored. */
	private final AtomicInteger loadGeneration = new AtomicInteger();

	private final Object lock = new Object();
	private HitData data = new HitData();
	private final Map<NPC, ActiveFight> activeFights = new HashMap<>();
	private ActiveFight currentFight;
	/** Fights whose boss left your view alive, by boss name, waiting to be resumed. */
	private final Map<String, ActiveFight> pausedFights = new HashMap<>();
	/** Encounter -> member -> when it died, for members that died before you hit anything in that encounter. */
	private final Map<String, Map<String, Long>> earlyMemberDeaths = new HashMap<>();
	/** For encounters with a varying number of NPCs (e.g. Lizardman Shamans): the members still alive. */
	private final Map<String, Set<NPC>> encounterAlive = new HashMap<>();
	/** Which member each encounter NPC was last seen as, for forms shared by several members (Vanguards walking). */
	private final Map<NPC, String> memberLabels = new HashMap<>();
	private Set<String> extraNames = new HashSet<>();

	// Weapon held when you last started an attack, so a weapon switch while a
	// projectile is in flight still credits the weapon that made the attack.
	private String lastAttackWeapon;
	private int lastAttackTick = -100;
	// Special attack energy last seen, and the tick it last dropped (you used a special attack)
	private int specEnergy = -1;
	private int specTick = -100;
	// Spell you clicked to cast on an NPC, used for your next attack
	private String pendingCast;
	// The attack your latest hit belonged to, so a multi-hit attack (e.g. the Scythe) counts once for accuracy
	private NPC attackNpc;
	private String attackWeapon;
	private ActiveFight attackFight;
	private int attackTick;
	private boolean attackLanded;
	// Spell clicked in the autocast menu, waiting for the game to confirm its number
	private String pendingAutocastName;
	private int pendingAutocastTick = -100;
	// Autocast spell number -> spell name, learned when you pick a spell to autocast
	private Map<Integer, String> autocastNames = new HashMap<>();

	private BossHitTrackerPanel panel;
	private NavigationButton navButton;

	@Provides
	BossHitTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossHitTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		parseExtraNames();
		loadAutocastNames();

		panel = new BossHitTrackerPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Boss Hit Tracker")
			.icon(createIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		wsClient.registerMessage(BossHitPartyHit.class);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadData();
		}
	}

	@Override
	protected void shutDown()
	{
		saveData();
		wsClient.unregisterMessage(BossHitPartyHit.class);
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		synchronized (lock)
		{
			activeFights.clear();
			pausedFights.clear();
			currentFight = null;
			data = new HitData();
		}
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadData();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BossHitTrackerConfig.GROUP.equals(event.getGroup()) && "extraNpcs".equals(event.getKey()))
		{
			parseExtraNames();
		}
		if (BossHitTrackerConfig.GROUP.equals(event.getGroup()) && "includeFailedInTotals".equals(event.getKey()))
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			specEnergy = -1;
			specTick = -100;
			// Any fight in progress can't finish now: save it as a failed attempt
			boolean recorded = false;
			long now = System.currentTimeMillis();
			synchronized (lock)
			{
				// A fight can be listed under more than one NPC (a boss and its parts): save it once
				Set<ActiveFight> inProgress = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
				inProgress.addAll(activeFights.values());
				for (ActiveFight fight : inProgress)
				{
					recorded |= recordFight(fight, true, now);
				}
				for (ActiveFight fight : pausedFights.values())
				{
					recorded |= recordFight(fight, true, fight.pausedAt);
				}
				activeFights.clear();
				pausedFights.clear();
				encounterAlive.clear();
				memberLabels.clear();
				currentFight = null;
			}
			if (recorded)
			{
				saveData();
				refreshPanel();
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player local = client.getLocalPlayer();
		if (local == null || event.getActor() != local || local.getAnimation() == -1)
		{
			return;
		}
		if (local.getInteracting() instanceof NPC)
		{
			int tick = client.getTickCount();
			// The game lowers your special energy just before the special attack's animation
			if (config.separateSpecs() && tick - specTick <= 1)
			{
				pendingCast = null;
				lastAttackWeapon = currentWeapon() + SPEC_SUFFIX;
				specTick = -100;
			}
			else
			{
				lastAttackWeapon = attackLabel();
			}
			lastAttackTick = tick;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (WidgetUtil.componentToInterface(event.getParam1()) == InterfaceID.AUTOCAST)
		{
			// Picking a spell in the autocast menu: remember its name until the game confirms the choice
			Widget widget = event.getWidget();
			String name = firstSpellName(widget == null ? null : widget.getName(), event.getMenuTarget(), event.getMenuOption());
			log.debug("Autocast menu click: option={} target={} -> {}", event.getMenuOption(), event.getMenuTarget(), name);
			if (name != null)
			{
				pendingAutocastName = name;
				pendingAutocastTick = client.getTickCount();
			}
			return;
		}

		if (action == MenuAction.WIDGET_TARGET_ON_NPC && "Cast".equalsIgnoreCase(Text.removeTags(event.getMenuOption())))
		{
			// Target looks like "Ice Barrage -> Vorkath  (level-732)"
			String target = Text.removeTags(event.getMenuTarget());
			int arrow = target.indexOf("->");
			String spell = (arrow > 0 ? target.substring(0, arrow) : target).trim();
			pendingCast = spell.isEmpty() ? null : spell;
		}
		else if (action == MenuAction.WALK
			|| action == MenuAction.NPC_FIRST_OPTION
			|| action == MenuAction.NPC_SECOND_OPTION
			|| action == MenuAction.NPC_THIRD_OPTION
			|| action == MenuAction.NPC_FOURTH_OPTION
			|| action == MenuAction.NPC_FIFTH_OPTION)
		{
			// Walking away or a normal attack cancels a manual cast that never happened
			pendingCast = null;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() == VarPlayerID.SA_ENERGY)
		{
			int energy = event.getValue();
			boolean used = specEnergy >= 0 && energy < specEnergy;
			specEnergy = energy;
			if (used && config.separateSpecs())
			{
				int tick = client.getTickCount();
				if (lastAttackTick == tick && lastAttackWeapon != null && !lastAttackWeapon.endsWith(SPEC_SUFFIX))
				{
					// The attack's animation came first this tick: it was the special attack
					lastAttackWeapon = currentWeapon() + SPEC_SUFFIX;
				}
				else
				{
					specTick = tick;
				}
			}
			return;
		}
		// The game confirmed a new autocast spell right after you clicked one
		if (event.getVarbitId() == VarbitID.AUTOCAST_SPELL && pendingAutocastName != null
			&& client.getTickCount() - pendingAutocastTick <= 10)
		{
			learnAutocastName(event.getValue());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		expirePausedFights();

		// Re-picking the spell that's already set doesn't change the game value, so also check
		// shortly after the click, once the autocast menu has closed (it closes on a successful pick).
		if (pendingAutocastName == null || client.getTickCount() - pendingAutocastTick < 2)
		{
			return;
		}
		Widget menu = client.getWidget(InterfaceID.Autocast.UNIVERSE);
		if (menu == null || menu.isHidden())
		{
			learnAutocastName(client.getVarbitValue(VarbitID.AUTOCAST_SPELL));
		}
		else if (client.getTickCount() - pendingAutocastTick > 10)
		{
			pendingAutocastName = null;
		}
	}

	private void learnAutocastName(int spellIndex)
	{
		String name = pendingAutocastName;
		pendingAutocastName = null;
		if (spellIndex <= 0 || name == null)
		{
			return;
		}
		autocastNames.put(spellIndex, name);
		configManager.setConfiguration(BossHitTrackerConfig.GROUP, AUTOCAST_NAMES_KEY, gson.toJson(autocastNames));
		log.debug("Learned autocast spell {} = {}", spellIndex, name);
		renameUnknownSpell(spellIndex, name);
	}

	/** Relabel hits saved before the spell's name was known, e.g. "Staff (Autocast spell 1)". */
	private void renameUnknownSpell(int spellIndex, String name)
	{
		String oldSuffix = "(Autocast spell " + spellIndex + ")";
		String newSuffix = "(" + name + ")";
		boolean changed = false;
		synchronized (lock)
		{
			for (BossStats stats : data.bosses.values())
			{
				changed |= renameKeys(stats.weapons, oldSuffix, newSuffix);
				for (KillRecord kill : stats.getRecentKills())
				{
					changed |= renameKeys(kill.weapons, oldSuffix, newSuffix);
					for (Map<String, WeaponStats> playerWeapons : kill.getPlayers().values())
					{
						changed |= renameKeys(playerWeapons, oldSuffix, newSuffix);
					}
				}
			}
			for (ActiveFight fight : activeFights.values())
			{
				renameKeys(fight.record.weapons, oldSuffix, newSuffix);
				for (Map<String, WeaponStats> playerWeapons : fight.record.getPlayers().values())
				{
					renameKeys(playerWeapons, oldSuffix, newSuffix);
				}
			}
			if (lastAttackWeapon != null && lastAttackWeapon.endsWith(oldSuffix))
			{
				lastAttackWeapon = lastAttackWeapon.substring(0, lastAttackWeapon.length() - oldSuffix.length()) + newSuffix;
			}
		}
		if (changed)
		{
			saveData();
			refreshPanel();
		}
	}

	private static boolean renameKeys(Map<String, WeaponStats> weapons, String oldSuffix, String newSuffix)
	{
		if (weapons == null)
		{
			return false;
		}
		List<String> matches = new ArrayList<>();
		for (String key : weapons.keySet())
		{
			if (key.endsWith(oldSuffix))
			{
				matches.add(key);
			}
		}
		for (String key : matches)
		{
			WeaponStats stats = weapons.remove(key);
			String renamed = key.substring(0, key.length() - oldSuffix.length()) + newSuffix;
			WeaponStats existing = weapons.get(renamed);
			if (existing == null)
			{
				weapons.put(renamed, stats);
			}
			else
			{
				existing.merge(stats);
			}
		}
		return !matches.isEmpty();
	}

	private static String firstSpellName(String... candidates)
	{
		for (String c : candidates)
		{
			if (c == null)
			{
				continue;
			}
			String name = Text.removeTags(c).trim();
			if (!name.isEmpty() && !"null".equalsIgnoreCase(name) && !NOT_SPELL_NAMES.contains(name.toLowerCase()))
			{
				return name;
			}
		}
		return null;
	}

	private void loadAutocastNames()
	{
		Map<Integer, String> loaded = null;
		String json = configManager.getConfiguration(BossHitTrackerConfig.GROUP, AUTOCAST_NAMES_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				loaded = gson.fromJson(json, new TypeToken<Map<Integer, String>>(){}.getType());
			}
			catch (RuntimeException e)
			{
				log.warn("Couldn't read saved autocast spell names", e);
			}
		}
		autocastNames = loaded == null ? new HashMap<>() : new HashMap<>(loaded);
	}

	/** What to call the attack you're starting now: the weapon, plus the spell if it's a spell. */
	private String attackLabel()
	{
		String weapon = currentWeapon();
		if (!config.trackSpells())
		{
			pendingCast = null;
			return weapon;
		}
		String spell = pendingCast;
		pendingCast = null;
		if (spell == null)
		{
			spell = autocastSpell();
		}
		return spell == null ? weapon : weapon + " (" + spell + ")";
	}

	/** The spell being autocast, or null when you aren't autocasting. */
	private String autocastSpell()
	{
		// Attack style slot 4 on a staff is autocast (normal or defensive)
		if (client.getVarpValue(VarPlayerID.COM_MODE) != AUTOCAST_STYLE)
		{
			return null;
		}
		int spellIndex = client.getVarbitValue(VarbitID.AUTOCAST_SPELL);
		String learned = autocastNames.get(spellIndex);
		if (learned != null)
		{
			return learned;
		}
		int spellObj = client.getVarpValue(VarPlayerID.AUTOCAST_SPELL_OBJ);
		if (spellObj > 0)
		{
			String name = itemManager.getItemComposition(spellObj).getName();
			if (name != null)
			{
				// Strip any leading spell-number prefix, e.g. "94 Ice Barrage"
				name = Text.removeTags(name).replaceFirst("^\\d+\\s+", "").trim();
				if (!name.isEmpty() && !"null".equalsIgnoreCase(name))
				{
					return name;
				}
			}
		}
		return spellIndex > 0 ? "Autocast spell " + spellIndex : null;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!(event.getActor() instanceof NPC))
		{
			return;
		}
		Hitsplat hitsplat = event.getHitsplat();
		boolean mine = hitsplat.isMine();
		// Other players' hits: counted (not attributed) for the party breakdown and to tell solo kills
		// apart; poison, venom, recoil etc. are ignored
		boolean others = !mine && hitsplat.isOthers();
		if (!mine && !others)
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		String name = npcName(npc);
		if (name == null || !shouldTrack(npc, name))
		{
			return;
		}

		// Save any fight that ran out of resume time first, so it isn't continued or lost
		expirePausedFights();

		int amount = hitsplat.getAmount();
		if (mine)
		{
			String weapon = weaponForHit();
			synchronized (lock)
			{
				ActiveFight fight = fightFor(npc, name);
				fight.record.addHit(weapon, amount);
				// Count attacks for accuracy: all hits of one multi-hit attack on this target are one attack
				int tick = client.getTickCount();
				int window = MultiHit.windowTicks(weapon);
				boolean sameAttack = window >= 0 && npc == attackNpc && fight == attackFight
					&& weapon.equals(attackWeapon) && tick - attackTick <= window;
				if (!sameAttack)
				{
					fight.record.startAttack(weapon);
					attackNpc = npc;
					attackWeapon = weapon;
					attackFight = fight;
					attackTick = tick;
					attackLanded = false;
				}
				if (amount > 0 && !attackLanded)
				{
					fight.record.attackLanded(weapon);
					attackLanded = true;
				}
				String phase = BossPhases.phaseOf(npc.getId());
				if (phase != null)
				{
					fight.record.addPhaseHit(phase, amount, System.currentTimeMillis());
				}
				Encounters.Member member = Encounters.member(npc.getId());
				if (member != null)
				{
					noteMember(npc, member);
					String label = member.shared ? memberLabels.getOrDefault(npc, member.label) : member.label;
					fight.record.addTargetHit(label, amount, System.currentTimeMillis());
				}
				if (config.partyBreakdown())
				{
					fight.record.addPlayerHit(localName(), weapon, amount);
				}
				currentFight = fight;
			}
			if (config.partyBreakdown() && partyService.isInParty())
			{
				partyService.send(new BossHitPartyHit(name, npc.getIndex(), weapon, amount));
			}
		}
		else
		{
			synchronized (lock)
			{
				ActiveFight fight = fightFor(npc, name);
				fight.othersSeenHits++;
				fight.othersSeenDamage += amount;
			}
		}
	}

	/** A party member's hit, sent by their copy of the plugin. */
	@Subscribe
	public void onBossHitPartyHit(BossHitPartyHit hit)
	{
		// Party messages arrive on a network thread; handle them on the game thread
		clientThread.invokeLater(() -> handlePartyHit(hit));
	}

	private void handlePartyHit(BossHitPartyHit hit)
	{
		if (!config.partyBreakdown() || hit.getWeapon() == null)
		{
			return;
		}
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == hit.getMemberId())
		{
			return;
		}
		PartyMember member = partyService.getMemberById(hit.getMemberId());
		String player = member == null || member.getDisplayName() == null ? "Party member" : member.getDisplayName();

		synchronized (lock)
		{
			ActiveFight fight = null;
			for (Map.Entry<NPC, ActiveFight> e : activeFights.entrySet())
			{
				if (e.getKey().getIndex() == hit.getNpcIndex())
				{
					fight = e.getValue();
					break;
				}
			}
			if (fight == null)
			{
				// You're away from the boss (e.g. in Sotetseg's shadow realm): add to the paused fight
				ActiveFight paused = pausedFights.get(hit.getBoss());
				if (paused != null && System.currentTimeMillis() - paused.pausedAt <= paused.pauseLimitMs)
				{
					fight = paused;
				}
			}
			if (fight == null)
			{
				// They hit the boss before you did: start tracking it if it's nearby
				NPC npc = client.getTopLevelWorldView().npcs().byIndex(hit.getNpcIndex());
				String name = npc == null ? null : npcName(npc);
				if (name == null || !name.equals(hit.getBoss()))
				{
					return;
				}
				fight = fightFor(npc, name);
			}
			fight.record.addPlayerHit(player, hit.getWeapon(), hit.getAmount());
			fight.partyReportedHits++;
			fight.partyReportedDamage += hit.getAmount();
		}
	}

	/** The fight for this NPC, starting one if needed. Call while holding the lock. */
	private ActiveFight fightFor(NPC npc, String name)
	{
		ActiveFight fight = activeFights.get(npc);
		if (fight == null && (BossList.bossForNpcId(npc.getId()) != null || Encounters.member(npc.getId()) != null))
		{
			// A part of a boss fight (e.g. Ocellus's Blade) joins that boss's fight if it's in progress
			for (ActiveFight f : activeFights.values())
			{
				if (f.boss.equals(name))
				{
					fight = f;
					break;
				}
			}
		}
		if (fight == null)
		{
			// Same boss came back into view (you returned from a teleport, e.g. Sotetseg's shadow realm):
			// continue the earlier fight instead of starting a new one
			fight = takePausedFight(name);
			if (fight == null)
			{
				fight = new ActiveFight(name, System.currentTimeMillis());
				// Count encounter members that died just before your first hit
				Map<String, Long> early = earlyMemberDeaths.remove(name);
				if (early != null)
				{
					long now = System.currentTimeMillis();
					for (Map.Entry<String, Long> e : early.entrySet())
					{
						if (now - e.getValue() <= PHASE_GAP_MS)
						{
							fight.deadMembers.add(e.getKey());
						}
					}
				}
			}
			activeFights.put(npc, fight);
		}
		return fight;
	}

	/** A paused fight for this boss that's still within the resume window, removed from the paused list. Call while holding the lock. */
	private ActiveFight takePausedFight(String boss)
	{
		ActiveFight fight = pausedFights.remove(boss);
		if (fight == null || System.currentTimeMillis() - fight.pausedAt > fight.pauseLimitMs)
		{
			return null;
		}
		// The boss is back (or its next phase started), so the fight isn't over
		fight.died = false;
		return fight;
	}

	/**
	 * How long a fight is kept after the boss leaves your view while still alive (e.g. Sotetseg's
	 * shadow realm, walking out of range). Hitting the same boss again within this time continues the fight.
	 */
	private long resumeWindowMs()
	{
		return Math.max(0, config.resumeWindow()) * 1000L;
	}

	/** Keep a fight aside so it can be continued. Call while holding the lock. */
	private void pause(ActiveFight fight, long limitMs)
	{
		fight.pausedAt = System.currentTimeMillis();
		fight.pauseLimitMs = limitMs;
		pausedFights.put(fight.boss, fight);
	}

	private void expirePausedFights()
	{
		long now = System.currentTimeMillis();
		boolean recorded = false;
		synchronized (lock)
		{
			for (java.util.Iterator<ActiveFight> it = pausedFights.values().iterator(); it.hasNext(); )
			{
				ActiveFight fight = it.next();
				if (now - fight.pausedAt > fight.pauseLimitMs)
				{
					// You never came back to this boss: the fight ended when it left your view
					it.remove();
					recorded |= recordFight(fight, true, fight.pausedAt);
				}
			}
		}
		if (recorded)
		{
			saveData();
			refreshPanel();
		}
	}

	private String localName()
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getDisplayName() != null && !local.getDisplayName().startsWith("<"))
		{
			return local.getDisplayName();
		}
		Player player = client.getLocalPlayer();
		return player == null || player.getName() == null ? "You" : player.getName();
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer())
		{
			// You died: if the boss then leaves your view alive, that fight failed
			synchronized (lock)
			{
				for (ActiveFight fight : activeFights.values())
				{
					fight.youDied = true;
				}
			}
			return;
		}
		if (!(actor instanceof NPC))
		{
			return;
		}
		Encounters.Member member = Encounters.member(((NPC) actor).getId());
		if (member != null)
		{
			memberDied((NPC) actor, member);
			return;
		}
		// A multi-stage boss "dying" at the end of an earlier phase (e.g. Kalphite Queen, Vet'ion,
		// Verzik P1) is only a phase change: the fight goes on in the boss's next form.
		if (BossPhases.isNotFinalPhase(((NPC) actor).getId()))
		{
			return;
		}
		// Only mark the fight as won here. Hits can still land after the death (projectiles in
		// flight, multi-hit attacks), so the kill is saved once, when the NPC despawns.
		synchronized (lock)
		{
			ActiveFight fight = activeFights.get(actor);
			if (fight != null)
			{
				fight.died = true;
				// Kill time runs from your first hit to the boss's final death
				fight.record.durationMs = System.currentTimeMillis() - fight.record.timestamp;
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		Encounters.Member member = Encounters.member(npc.getId());
		if (member != null)
		{
			synchronized (lock)
			{
				noteMember(npc, member);
			}
		}
	}

	/** Remember an encounter NPC that's in view. Call while holding the lock. */
	private void noteMember(NPC npc, Encounters.Member member)
	{
		if (!member.shared)
		{
			memberLabels.put(npc, member.label);
		}
		if (Encounters.allSeenMustDie(member.encounter) && !npc.isDead())
		{
			encounterAlive.computeIfAbsent(member.encounter, e -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		Encounters.Member member = Encounters.member(npc.getId());
		if (member != null)
		{
			memberDespawned(npc, member);
			return;
		}
		ActiveFight fight;
		boolean recorded;
		synchronized (lock)
		{
			fight = activeFights.remove(npc);
			if (fight == null)
			{
				return;
			}
			if (activeFights.containsValue(fight))
			{
				// Only one part of the fight left (e.g. Ocellus's Blade); the boss itself is still here
				return;
			}
			if (currentFight == fight)
			{
				currentFight = null;
			}
			// The boss left your view alive (you were teleported away, walked out of range, it reset).
			// Keep the fight for a while in case you come back to it; it's dropped if you don't.
			boolean killed = fight.died || npc.isDead();
			boolean earlierPhase = BossPhases.isNotFinalPhase(npc.getId());
			// A multi-stage boss left during an earlier phase: the phase ended, or the boss stepped away
			// mid-phase (e.g. Ocellus while you fight The Blade). Keep the fight so its next phase continues
			// it, unless you died and the boss is simply gone (a wipe), which is handled below.
			if (earlierPhase && (killed || !fight.youDied))
			{
				pause(fight, Math.max(resumeWindowMs(), PHASE_GAP_MS));
				return;
			}
			if (!killed)
			{
				if (fight.youDied || resumeWindowMs() <= 0)
				{
					// You died and the boss is gone (e.g. your team wiped and you were sent out),
					// or resuming is turned off: the fight is over without a kill
					recorded = recordFight(fight, true, System.currentTimeMillis());
				}
				else
				{
					pause(fight, resumeWindowMs());
					return;
				}
			}
			else
			{
				recorded = recordFight(fight, false, System.currentTimeMillis());
			}
		}
		if (recorded)
		{
			saveData();
			refreshPanel();
		}
	}

	/** The fight for this encounter, whether it's being fought, paused, or not yet hit by you. Call while holding the lock. */
	private ActiveFight encounterFight(NPC npc, String encounter)
	{
		ActiveFight fight = activeFights.get(npc);
		if (fight != null)
		{
			return fight;
		}
		for (ActiveFight f : activeFights.values())
		{
			if (f.boss.equals(encounter))
			{
				return f;
			}
		}
		return pausedFights.get(encounter);
	}

	/** A member of a multi-NPC boss died (even one you never hit, e.g. killed by a teammate). */
	private void memberDied(NPC npc, Encounters.Member member)
	{
		boolean recorded = false;
		synchronized (lock)
		{
			Set<NPC> alive = encounterAlive.get(member.encounter);
			if (alive != null)
			{
				alive.remove(npc);
			}
			ActiveFight fight = encounterFight(npc, member.encounter);
			if (fight == null)
			{
				// e.g. a teammate killed Branda before you hit Eldric: remember it for when your fight starts
				earlyMemberDeaths.computeIfAbsent(member.encounter, e -> new HashMap<>())
					.put(member.label, System.currentTimeMillis());
				return;
			}
			fight.deadMembers.add(member.label);
			boolean allSeenDead = Encounters.allSeenMustDie(member.encounter) && (alive == null || alive.isEmpty());
			if (!fight.died && (Encounters.endsEncounter(npc.getId()) || allSeenDead
				|| Encounters.isComplete(member.encounter, fight.deadMembers)))
			{
				fight.died = true;
				fight.record.durationMs = System.currentTimeMillis() - fight.record.timestamp;
				// If nothing of the encounter is left in view (e.g. the last member died while you
				// were away), save it now; otherwise it's saved when the last member despawns
				if (!activeFights.containsValue(fight))
				{
					pausedFights.remove(fight.boss);
					recorded = recordFight(fight, false, System.currentTimeMillis());
				}
			}
		}
		if (recorded)
		{
			saveData();
			refreshPanel();
		}
	}

	/** A member of a multi-NPC boss left your view. */
	private void memberDespawned(NPC npc, Encounters.Member member)
	{
		if (npc.isDead())
		{
			// In case its death wasn't reported (it died out of view, or was killed by a teammate)
			memberDied(npc, member);
		}
		boolean recorded = false;
		synchronized (lock)
		{
			memberLabels.remove(npc);
			ActiveFight fight = activeFights.remove(npc);
			if (fight == null)
			{
				return;
			}
			if (fight.died)
			{
				// The encounter is won: save it once, and let go of any members still around (e.g. Smorks, Olm's claws)
				activeFights.values().removeIf(f -> f == fight);
				pausedFights.remove(fight.boss, fight);
				if (currentFight == fight)
				{
					currentFight = null;
				}
				recorded = recordFight(fight, false, System.currentTimeMillis());
			}
			else if (activeFights.containsValue(fight))
			{
				// Other members are still being fought
				return;
			}
			else
			{
				if (currentFight == fight)
				{
					currentFight = null;
				}
				if (fight.youDied)
				{
					// You died and the whole encounter is gone (a wipe)
					recorded = recordFight(fight, true, System.currentTimeMillis());
				}
				else
				{
					// Between waves or phases (e.g. Olm moving to the other side), or you stepped away:
					// keep the fight so the encounter's next member continues it
					pause(fight, Math.max(resumeWindowMs(), PHASE_GAP_MS));
					return;
				}
			}
		}
		if (recorded)
		{
			saveData();
			refreshPanel();
		}
	}

	/**
	 * Save a finished fight as a kill, or as a failed attempt if the boss wasn't killed.
	 * Only fights where you landed at least one hit are saved. Call while holding the lock.
	 *
	 * @param endMs when the fight ended, used for a failed attempt's duration
	 * @return true if something was saved
	 */
	private boolean recordFight(ActiveFight fight, boolean failed, long endMs)
	{
		if (fight.record.getWeapons().isEmpty() || (failed && !config.recordFailed()))
		{
			return false;
		}
		if (failed || !fight.died)
		{
			fight.record.durationMs = Math.max(0, endMs - fight.record.timestamp);
		}
		fight.record.failed = failed;
		fight.record.othersHelped = fight.othersSeenHits > 0 || fight.record.hasOtherPlayers();
		encounterAlive.remove(fight.boss);
		finishPartyBreakdown(fight);
		if (fight.record.phases != null && fight.record.phases.size() < 2)
		{
			// Only one phase seen (e.g. you joined late): the phase breakdown adds nothing
			fight.record.phases = null;
		}
		BossStats stats = data.bosses.get(fight.boss);
		if (stats == null)
		{
			stats = new BossStats();
			data.bosses.put(fight.boss, stats);
		}
		if (failed)
		{
			stats.addFailed(fight.record, config.historySize());
		}
		else
		{
			stats.addKill(fight.record, config.historySize());
			List<String> beaten = stats.updateBests(fight.record);
			if (!beaten.isEmpty() && config.pbMessages())
			{
				announceBests(fight.boss, beaten);
			}
		}
		log.debug("Recorded {} {}: {} hits, {} damage", fight.boss, failed ? "failed attempt" : "kill",
			fight.record.getTotalHits(), fight.record.getTotalDamage());
		return true;
	}

	/** Add damage from players who weren't sharing, and drop the breakdown for solo kills. */
	private void finishPartyBreakdown(ActiveFight fight)
	{
		KillRecord record = fight.record;
		if (record.players == null)
		{
			return;
		}
		int unsharedHits = fight.othersSeenHits - fight.partyReportedHits;
		long unsharedDamage = fight.othersSeenDamage - fight.partyReportedDamage;
		if (unsharedDamage > 0)
		{
			record.addOtherPlayers(Math.max(0, unsharedHits), unsharedDamage);
		}
		if (!record.hasOtherPlayers())
		{
			// Only your own hits: the normal weapon breakdown already covers it
			record.players = null;
		}
	}

	private boolean shouldTrack(NPC npc, String name)
	{
		if (Encounters.member(npc.getId()) != null)
		{
			return true;
		}
		String lower = name.toLowerCase();
		if (extraNames.contains(lower))
		{
			return true;
		}
		if (config.bossesOnly())
		{
			return BossList.isBoss(lower);
		}
		return npc.getCombatLevel() > 0;
	}

	private static String npcName(NPC npc)
	{
		// Parts of a boss fight with a different name (e.g. Ocellus's Blade) count as that boss
		String partOf = BossList.bossForNpcId(npc.getId());
		if (partOf != null)
		{
			return partOf;
		}
		// Members of a multi-NPC boss (e.g. a Dagon'hai Monk) are recorded under the encounter
		Encounters.Member member = Encounters.member(npc.getId());
		if (member != null)
		{
			return member.encounter;
		}
		NPCComposition comp = npc.getTransformedComposition();
		if (comp == null)
		{
			comp = npc.getComposition();
		}
		if (comp == null || comp.getName() == null)
		{
			return null;
		}
		String name = Text.removeTags(comp.getName()).trim();
		if (name.isEmpty() || "null".equalsIgnoreCase(name))
		{
			return null;
		}
		// Alternate forms (e.g. "Tekton (enraged)") are recorded under the main boss's name
		return BossList.displayName(name);
	}

	private String weaponForHit()
	{
		if (lastAttackWeapon != null && client.getTickCount() - lastAttackTick <= ATTACK_WEAPON_TICKS)
		{
			return lastAttackWeapon;
		}
		return currentWeapon();
	}

	private String currentWeapon()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return UNARMED;
		}
		Item item = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (item == null || item.getId() <= 0)
		{
			return UNARMED;
		}
		String name = itemManager.getItemComposition(item.getId()).getMembersName();
		return name == null ? UNARMED : name;
	}

	private void parseExtraNames()
	{
		Set<String> names = new HashSet<>();
		for (String part : config.extraNpcs().split(","))
		{
			String trimmed = part.trim().toLowerCase();
			if (!trimmed.isEmpty())
			{
				names.add(trimmed);
			}
		}
		extraNames = names;
	}

	// ---- Saving and loading (stored per RuneScape account) ----

	/** Load this account's saved data, from its file (or from RuneLite's settings, where older versions kept it). */
	private void loadData()
	{
		String profileKey = configManager.getRSProfileKey();
		String legacyJson = configManager.getRSProfileConfiguration(BossHitTrackerConfig.GROUP, DATA_KEY);
		int historySize = config.historySize();
		int generation = loadGeneration.incrementAndGet();
		synchronized (lock)
		{
			data = new HitData();
			activeFights.clear();
			pausedFights.clear();
			currentFight = null;
		}
		refreshPanel();
		if (profileKey == null)
		{
			return;
		}
		// Reading the file happens off the game thread
		executor.execute(() ->
		{
			HitData loaded = readFile(profileKey);
			boolean fromSettings = false;
			if (loaded == null && legacyJson != null && !legacyJson.isEmpty())
			{
				loaded = parse(legacyJson);
				fromSettings = loaded != null;
			}
			if (loaded == null || loaded.bosses == null)
			{
				loaded = new HitData();
			}
			boolean upgraded = DataMigration.migrate(loaded, historySize);
			if (generation != loadGeneration.get())
			{
				return;
			}
			String json;
			synchronized (lock)
			{
				// Keep anything recorded while the file was loading
				for (Map.Entry<String, BossStats> e : data.bosses.entrySet())
				{
					BossStats into = loaded.bosses.computeIfAbsent(e.getKey(), k -> new BossStats());
					for (KillRecord k : e.getValue().getRecentKills())
					{
						if (k.isFailed())
						{
							into.addFailed(k, historySize);
						}
						else
						{
							into.addKill(k, historySize);
							into.updateBests(k);
						}
					}
				}
				data = loaded;
				json = gson.toJson(data);
			}
			if (upgraded || fromSettings)
			{
				if (writeFile(profileKey, json) && fromSettings)
				{
					// Now safely in the file: remove the old copy from RuneLite's settings
					configManager.unsetRSProfileConfiguration(BossHitTrackerConfig.GROUP, DATA_KEY);
				}
			}
			refreshPanel();
		});
	}

	private HitData parse(String json)
	{
		try
		{
			return gson.fromJson(json, HitData.class);
		}
		catch (RuntimeException e)
		{
			log.warn("Couldn't read saved Boss Hit Tracker data", e);
			return null;
		}
	}

	/** This account's data file: .runelite/plugin-data/boss-hit-tracker/[account].json */
	private Filepath dataFile(String profileKey) throws IOException
	{
		Filepath dir = getPluginDirectory();
		// RuneLite doesn't create the plugin's folder itself
		dir.createDirectories();
		return dir.join(profileKey.replaceAll("[^A-Za-z0-9_.-]", "_") + ".json");
	}

	private HitData readFile(String profileKey)
	{
		try
		{
			Filepath file = dataFile(profileKey);
			if (!file.exists())
			{
				return null;
			}
			try (Reader reader = file.openReader())
			{
				return gson.fromJson(reader, HitData.class);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Couldn't read Boss Hit Tracker data file", e);
			return null;
		}
	}

	/** Write the file safely: to a temporary file first, then swap it in, so a crash can't leave it half-written. */
	private boolean writeFile(String profileKey, String json)
	{
		try
		{
			Filepath file = dataFile(profileKey);
			Filepath temp = file.getParent().join(file.getFileName() + ".tmp");
			temp.write(json);
			temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Couldn't save Boss Hit Tracker data", e);
			return false;
		}
	}

	private void saveData()
	{
		String profileKey = configManager.getRSProfileKey();
		if (profileKey == null)
		{
			return;
		}
		String json;
		synchronized (lock)
		{
			json = gson.toJson(data);
		}
		// Writing happens off the game thread. If several saves queue up, only the newest is written.
		if (pendingWrite.getAndSet(new String[]{profileKey, json}) == null)
		{
			executor.execute(() ->
			{
				String[] latest = pendingWrite.getAndSet(null);
				if (latest != null)
				{
					writeFile(latest[0], latest[1]);
				}
			});
		}
	}

	/** Tell the player in chat which personal bests a kill beat. */
	private void announceBests(String boss, List<String> beaten)
	{
		String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("New personal best")
			.append(ChatColorType.NORMAL)
			.append(" for " + boss + ": " + String.join(", ", beaten))
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	private void refreshPanel()
	{
		BossHitTrackerPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::rebuild);
		}
	}

	// ---- Used by the panel and overlay ----

	List<String> getBossNames()
	{
		synchronized (lock)
		{
			List<String> names = new ArrayList<>(data.bosses.keySet());
			names.sort(String.CASE_INSENSITIVE_ORDER);
			return names;
		}
	}

	BossStats getBossStats(String boss)
	{
		synchronized (lock)
		{
			BossStats stats = data.bosses.get(boss);
			return stats == null ? null : stats.copy();
		}
	}

	/** Snapshot of the fight shown in the overlay, or null when you aren't fighting a tracked boss. */
	ActiveFight getCurrentFightSnapshot()
	{
		synchronized (lock)
		{
			if (currentFight == null)
			{
				return null;
			}
			return new ActiveFight(currentFight.boss, currentFight.record.copy());
		}
	}

	void resetBoss(String boss)
	{
		synchronized (lock)
		{
			data.bosses.remove(boss);
		}
		saveData();
		refreshPanel();
	}

	/** Delete one fight (the history entry at this position, newest first), if it's still the same fight. */
	void deleteFight(String boss, int index, long timestamp)
	{
		synchronized (lock)
		{
			BossStats stats = data.bosses.get(boss);
			if (stats == null || index < 0 || index >= stats.getRecentKills().size())
			{
				return;
			}
			KillRecord record = stats.getRecentKills().get(index);
			if (record.getTimestamp() != timestamp)
			{
				return;
			}
			stats.removeRecord(record);
		}
		saveData();
		refreshPanel();
	}

	boolean includeFailedInTotals()
	{
		return config.includeFailedInTotals();
	}

	/** Lifetime totals per boss and weapon, as CSV for a spreadsheet. */
	String exportCsv()
	{
		boolean withFailed = config.includeFailedInTotals();
		StringBuilder sb = new StringBuilder("Boss,Kills,Failed attempts,Weapon,Attacks,Hits,Misses,Accuracy %,Total damage,Average hit,Max hit\n");
		synchronized (lock)
		{
			List<String> names = new ArrayList<>(data.bosses.keySet());
			names.sort(String.CASE_INSENSITIVE_ORDER);
			for (String boss : names)
			{
				BossStats stats = data.bosses.get(boss);
				for (Map.Entry<String, WeaponStats> e : stats.getWeapons(withFailed).entrySet())
				{
					WeaponStats w = e.getValue();
					sb.append(csv(boss)).append(',')
						.append(stats.kills).append(',')
						.append(stats.failedAttempts).append(',')
						.append(csv(e.getKey())).append(',')
						.append(w.attacks == 0 ? "" : String.valueOf(w.attacks)).append(',')
						.append(w.hits).append(',')
						.append(w.zeros).append(',')
						.append(String.format("%.1f", w.getAccuracy())).append(',')
						.append(w.damage).append(',')
						.append(String.format("%.2f", w.getAverageHit())).append(',')
						.append(w.maxHit).append('\n');
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Every fight in the history, as CSV: one row per weapon, phase, target and party member's weapon.
	 * The Section column says which.
	 */
	String exportFightsCsv()
	{
		SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		StringBuilder sb = new StringBuilder("Boss,Fight,Result,Date,Duration (s),Section,Name,Player,Attacks,Hits,Misses,Damage,Max hit\n");
		synchronized (lock)
		{
			List<String> names = new ArrayList<>(data.bosses.keySet());
			names.sort(String.CASE_INSENSITIVE_ORDER);
			for (String boss : names)
			{
				BossStats stats = data.bosses.get(boss);
				int kill = stats.kills;
				int failed = stats.failedAttempts;
				for (KillRecord k : stats.getRecentKills())
				{
					String fight = k.isFailed() ? "Failed #" + failed-- : "Kill #" + kill--;
					String prefix = csv(boss) + ',' + csv(fight) + ',' + (k.isFailed() ? "Failed" : "Kill") + ','
						+ date.format(new Date(k.getTimestamp())) + ',' + (k.getDurationMs() / 1000) + ',';
					for (Map.Entry<String, WeaponStats> e : k.getWeapons().entrySet())
					{
						WeaponStats w = e.getValue();
						sb.append(prefix).append("Weapon,").append(csv(e.getKey())).append(",You,")
							.append(w.attacks == 0 ? "" : String.valueOf(w.attacks)).append(',').append(w.hits).append(',').append(w.zeros).append(',').append(w.damage).append(',').append(w.maxHit).append('\n');
					}
					for (Map.Entry<String, PhaseStats> e : k.getPhases().entrySet())
					{
						PhaseStats p = e.getValue();
						sb.append(prefix).append("Phase,").append(csv(e.getKey())).append(",You,,")
							.append(p.hits).append(",,").append(p.damage).append(',').append(p.maxHit).append('\n');
					}
					for (Map.Entry<String, PhaseStats> e : k.getTargets().entrySet())
					{
						PhaseStats t = e.getValue();
						sb.append(prefix).append("Target,").append(csv(e.getKey())).append(",You,,")
							.append(t.hits).append(",,").append(t.damage).append(',').append(t.maxHit).append('\n');
					}
					for (Map.Entry<String, Map<String, WeaponStats>> p : k.getPlayers().entrySet())
					{
						for (Map.Entry<String, WeaponStats> e : p.getValue().entrySet())
						{
							WeaponStats w = e.getValue();
							sb.append(prefix).append("Party,").append(csv(e.getKey())).append(',').append(csv(p.getKey())).append(",,")
								.append(w.hits).append(',').append(w.zeros).append(',').append(w.damage).append(',').append(w.maxHit).append('\n');
						}
					}
				}
			}
		}
		return sb.toString();
	}

	private static String csv(String s)
	{
		return "\"" + s.replace("\"", "\"\"") + "\"";
	}

	private static BufferedImage createIcon()
	{
		// A small red hitsplat
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(170, 20, 20));
		g.fillOval(1, 1, 14, 14);
		g.setColor(new Color(240, 240, 240));
		g.setStroke(new BasicStroke(1.6f));
		g.drawLine(5, 8, 11, 8);
		g.drawLine(8, 5, 8, 11);
		g.dispose();
		return img;
	}
}
