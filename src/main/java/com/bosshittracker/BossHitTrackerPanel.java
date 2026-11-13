package com.bosshittracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel: pick a boss, then pick one kill (or all kills) to see your hits per weapon.
 */
class BossHitTrackerPanel extends PluginPanel
{
	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance();
	private static final Color FAILED_COLOR = new Color(230, 90, 80);
	private static final String ALL_KILLS = "All kills (lifetime)";
	/** Kill selection meaning "lifetime totals" rather than one kill. */
	private static final int LIFETIME = -1;

	private final BossHitTrackerPlugin plugin;
	private final JComboBox<String> bossSelect = new JComboBox<>();
	private final JComboBox<String> killSelect = new JComboBox<>();
	private final JPanel content = new JPanel();
	private boolean updatingCombos;
	private final JButton deleteButton = new JButton("Delete this fight");
	/** The fights shown for the selected boss, newest first (copies). */
	private List<KillRecord> shownRecords = new ArrayList<>();

	/** Index into the boss's recent kills (0 = newest), or LIFETIME. */
	private int selectedKill = 0;
	/** Kill count when the panel was last drawn, to notice new kills. */
	private int lastKillCount = -1;
	private String lastBoss;

	BossHitTrackerPanel(BossHitTrackerPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Boss Hit Tracker");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		header.add(title);
		header.add(Box.createVerticalStrut(6));

		header.add(fieldLabel("Boss"));
		bossSelect.setAlignmentX(LEFT_ALIGNMENT);
		header.add(bossSelect);
		header.add(Box.createVerticalStrut(6));

		header.add(fieldLabel("Kill"));
		killSelect.setAlignmentX(LEFT_ALIGNMENT);
		killSelect.setToolTipText("Show one kill, or all kills combined");
		header.add(killSelect);
		header.add(Box.createVerticalStrut(6));

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		JButton copy = new JButton("Copy CSV");
		copy.setToolTipText("Copy your data to the clipboard as CSV (paste into a spreadsheet)");
		javax.swing.JPopupMenu csvMenu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem lifetimeCsv = new javax.swing.JMenuItem("Lifetime totals (per boss and weapon)");
		lifetimeCsv.addActionListener(e -> copyToClipboard(plugin.exportCsv()));
		javax.swing.JMenuItem fightsCsv = new javax.swing.JMenuItem("Every fight (weapons, phases, targets, party)");
		fightsCsv.addActionListener(e -> copyToClipboard(plugin.exportFightsCsv()));
		csvMenu.add(lifetimeCsv);
		csvMenu.add(fightsCsv);
		copy.addActionListener(e -> csvMenu.show(copy, 0, copy.getHeight()));
		JButton reset = new JButton("Reset boss");
		reset.setToolTipText("Delete all tracked hits for the selected boss");
		reset.addActionListener(e -> confirmReset());
		buttons.add(copy);
		buttons.add(reset);
		header.add(buttons);
		header.add(Box.createVerticalStrut(6));
		deleteButton.setToolTipText("Delete the selected fight from the history and from the totals");
		deleteButton.setAlignmentX(LEFT_ALIGNMENT);
		deleteButton.addActionListener(e -> confirmDeleteFight());
		header.add(deleteButton);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Stop BoxLayout from stretching these vertically
		for (javax.swing.JComponent c : new javax.swing.JComponent[]{bossSelect, killSelect, buttons, deleteButton})
		{
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		}

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);

		bossSelect.addActionListener(e ->
		{
			if (!updatingCombos)
			{
				selectedKill = 0;
				showSelectedBoss();
			}
		});
		killSelect.addActionListener(e ->
		{
			if (!updatingCombos)
			{
				int index = killSelect.getSelectedIndex();
				selectedKill = index <= 0 ? LIFETIME : index - 1;
				showSelectedBoss();
			}
		});

		rebuild();
	}

	/** Reload the boss list and redraw. Call on the Swing thread. */
	void rebuild()
	{
		String selected = (String) bossSelect.getSelectedItem();
		List<String> names = plugin.getBossNames();

		updatingCombos = true;
		bossSelect.removeAllItems();
		for (String name : names)
		{
			bossSelect.addItem(name);
		}
		if (selected != null && names.contains(selected))
		{
			bossSelect.setSelectedItem(selected);
		}
		updatingCombos = false;

		showSelectedBoss();
	}

	private void showSelectedBoss()
	{
		content.removeAll();
		String boss = (String) bossSelect.getSelectedItem();
		BossStats stats = boss == null ? null : plugin.getBossStats(boss);

		if (stats == null)
		{
			updatingCombos = true;
			killSelect.removeAllItems();
			updatingCombos = false;
			deleteButton.setVisible(false);
			content.add(textLine("No fights tracked yet. Fight a boss and your hits will show up here.", ColorScheme.LIGHT_GRAY_COLOR));
			finishContent();
			return;
		}

		List<KillRecord> kills = stats.getRecentKills();
		shownRecords = kills;

		// When a new kill comes in, keep showing the newest kill if that's what you were looking at;
		// otherwise shift the selection so it stays on the same kill.
		if (boss.equals(lastBoss) && lastKillCount >= 0 && stats.getTotalRecorded() > lastKillCount && selectedKill > 0)
		{
			selectedKill += stats.getTotalRecorded() - lastKillCount;
		}
		if (selectedKill >= kills.size())
		{
			selectedKill = kills.isEmpty() ? LIFETIME : kills.size() - 1;
		}
		lastBoss = boss;
		lastKillCount = stats.getTotalRecorded();

		String[] labels = recordLabels(stats, kills);
		fillKillSelect(kills, labels);

		deleteButton.setVisible(selectedKill != LIFETIME);
		if (selectedKill == LIFETIME)
		{
			showLifetime(stats);
		}
		else
		{
			showKill(kills.get(selectedKill), labels[selectedKill]);
		}

		content.add(Box.createVerticalStrut(6));
		content.add(sectionTitle("Recent fights"));
		for (int i = 0; i < kills.size(); i++)
		{
			content.add(killRow(kills.get(i), labels[i], i));
		}
		if (!kills.isEmpty())
		{
			content.add(textLine("Click a fight to see its weapons.", ColorScheme.MEDIUM_GRAY_COLOR));
		}

		finishContent();
	}

	/**
	 * Names for each recorded fight, newest first: "Kill #12" for kills (numbered by kill count)
	 * and "Failed #3" for failed attempts (numbered separately).
	 */
	private static String[] recordLabels(BossStats stats, List<KillRecord> records)
	{
		String[] labels = new String[records.size()];
		int kill = stats.getKills();
		int failed = stats.getFailedAttempts();
		for (int i = 0; i < records.size(); i++)
		{
			labels[i] = records.get(i).isFailed() ? "Failed #" + failed-- : "Kill #" + kill--;
		}
		return labels;
	}

	private void fillKillSelect(List<KillRecord> kills, String[] labels)
	{
		updatingCombos = true;
		killSelect.removeAllItems();
		killSelect.addItem(ALL_KILLS);
		for (int i = 0; i < kills.size(); i++)
		{
			KillRecord k = kills.get(i);
			String label = labels[i] + "  (" + duration(k) + ")";
			if (i == 0)
			{
				label += "  (latest)";
			}
			killSelect.addItem(label);
		}
		killSelect.setSelectedIndex(selectedKill == LIFETIME ? 0 : selectedKill + 1);
		updatingCombos = false;
	}

	private void showLifetime(BossStats stats)
	{
		boolean withFailed = plugin.includeFailedInTotals();
		Map<String, WeaponStats> weapons = stats.getWeapons(withFailed);
		long totalDamage = 0;
		int totalHits = 0;
		for (WeaponStats w : weapons.values())
		{
			totalDamage += w.getDamage();
			totalHits += w.getHits();
		}
		long killDamage = KillRecord.totalDamage(stats.getWeapons());

		content.add(sectionTitle("All kills"));
		content.add(textLine("Kills: " + NUMBER.format(stats.getKills()), Color.WHITE));
		content.add(textLine("Hits: " + NUMBER.format(totalHits) + "   Damage: " + NUMBER.format(totalDamage), Color.WHITE));
		if (stats.getKills() > 0)
		{
			content.add(textLine("Avg damage per kill: " + NUMBER.format(killDamage / stats.getKills()), Color.WHITE));
		}
		if (stats.getFailedAttempts() > 0)
		{
			content.add(textLine("Failed attempts: " + NUMBER.format(stats.getFailedAttempts())
				+ "   Damage: " + NUMBER.format(stats.getFailedDamage()), FAILED_COLOR));
			content.add(textLine(withFailed ? "Included in the totals." : "Not counted in the totals below.", ColorScheme.MEDIUM_GRAY_COLOR));
		}

		if (stats.getBestTimeMs() > 0 || stats.getBestDamage() > 0 || stats.getBestHit() > 0)
		{
			content.add(Box.createVerticalStrut(6));
			content.add(sectionTitle("Personal bests"));
			if (stats.getBestTimeMs() > 0)
			{
				content.add(textLine("Fastest solo kill: " + BossStats.formatTime(stats.getBestTimeMs()), Color.WHITE));
			}
			if (stats.getBestDamage() > 0)
			{
				content.add(textLine("Most damage in a kill: " + NUMBER.format(stats.getBestDamage()), Color.WHITE));
			}
			if (stats.getBestHit() > 0)
			{
				content.add(textLine("Highest hit: " + NUMBER.format(stats.getBestHit()), Color.WHITE));
			}
		}
		content.add(Box.createVerticalStrut(6));

		content.add(sectionTitle(withFailed ? "By weapon (kills and failed)" : "By weapon (all kills)"));
		addWeaponCards(weapons, totalDamage);
	}

	private void showKill(KillRecord kill, String label)
	{
		SimpleDateFormat date = new SimpleDateFormat("MMM d, yyyy h:mm a");
		JLabel title = sectionTitle(kill.isFailed() ? label + " (boss not killed)" : label);
		if (kill.isFailed())
		{
			title.setForeground(FAILED_COLOR);
		}
		content.add(title);
		content.add(textLine(date.format(new Date(kill.getTimestamp())) + "   Time: " + duration(kill), Color.WHITE));
		content.add(textLine("Hits: " + NUMBER.format(kill.getTotalHits()) + "   Damage: " + NUMBER.format(kill.getTotalDamage()), Color.WHITE));
		content.add(Box.createVerticalStrut(6));

		String scope = kill.isFailed() ? "this attempt" : "this kill";
		content.add(sectionTitle(kill.hasOtherPlayers() ? "Your weapons (" + scope + ")" : "By weapon (" + scope + ")"));
		addWeaponCards(kill.getWeapons(), kill.getTotalDamage());

		if (!kill.getPhases().isEmpty())
		{
			content.add(Box.createVerticalStrut(6));
			content.add(sectionTitle("By phase (" + scope + ")"));
			content.add(phaseCard(kill));
		}

		if (!kill.getTargets().isEmpty())
		{
			content.add(Box.createVerticalStrut(6));
			content.add(sectionTitle("By target (" + scope + ")"));
			content.add(targetCard(kill));
		}

		if (kill.hasOtherPlayers())
		{
			content.add(Box.createVerticalStrut(6));
			content.add(sectionTitle("Party (" + scope + ")"));
			long everyone = kill.getEveryoneDamage();
			List<Map.Entry<String, Map<String, WeaponStats>>> players = new ArrayList<>(kill.getPlayers().entrySet());
			players.sort((a, b) -> Long.compare(KillRecord.totalDamage(b.getValue()), KillRecord.totalDamage(a.getValue())));
			for (Map.Entry<String, Map<String, WeaponStats>> e : players)
			{
				content.add(playerCard(e.getKey(), e.getValue(), everyone));
				content.add(Box.createVerticalStrut(4));
			}
		}
	}

	/** Your damage in each phase of a multi-stage boss kill, with how long each phase took. */
	private JPanel phaseCard(KillRecord kill)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		card.setAlignmentX(LEFT_ALIGNMENT);

		List<Map.Entry<String, PhaseStats>> phases = new ArrayList<>(kill.getPhases().entrySet());
		phases.sort((a, b) -> Long.compare(a.getValue().getFirstHitMs(), b.getValue().getFirstHitMs()));
		long killEnd = kill.getTimestamp() + kill.getDurationMs();
		long totalDamage = kill.getTotalDamage();
		for (int i = 0; i < phases.size(); i++)
		{
			PhaseStats p = phases.get(i).getValue();
			// A phase lasts from your first hit in it until your first hit in the next one
			long end = i + 1 < phases.size() ? phases.get(i + 1).getValue().getFirstHitMs() : killEnd;
			long secs = Math.max(0, end - p.getFirstHitMs()) / 1000;
			double share = totalDamage == 0 ? 0 : 100.0 * p.getDamage() / totalDamage;

			JLabel name = new JLabel(String.format("%s   %d:%02d", phases.get(i).getKey(), secs / 60, secs % 60));
			name.setFont(FontManager.getRunescapeBoldFont());
			name.setForeground(ColorScheme.BRAND_ORANGE);
			if (i > 0)
			{
				name.setBorder(new EmptyBorder(4, 0, 0, 0));
			}
			card.add(leftAligned(name));
			card.add(leftAligned(small(String.format("Dmg %s (%.0f%%)", NUMBER.format(p.getDamage()), share))));
			card.add(leftAligned(small(String.format("Hits %d  ·  Max %d", p.getHits(), p.getMaxHit()))));
		}
		return fullWidth(card);
	}

	/** Your damage on each NPC of a multi-NPC boss (e.g. the Elder and each Monk of the Dagon'hai Trio). */
	private JPanel targetCard(KillRecord kill)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		card.setAlignmentX(LEFT_ALIGNMENT);

		List<Map.Entry<String, PhaseStats>> targets = new ArrayList<>(kill.getTargets().entrySet());
		// Most damaged target first
		targets.sort((a, b) -> Long.compare(b.getValue().getDamage(), a.getValue().getDamage()));
		long totalDamage = kill.getTotalDamage();
		for (int i = 0; i < targets.size(); i++)
		{
			PhaseStats t = targets.get(i).getValue();
			double share = totalDamage == 0 ? 0 : 100.0 * t.getDamage() / totalDamage;
			JLabel name = new JLabel(targets.get(i).getKey());
			name.setFont(FontManager.getRunescapeBoldFont());
			name.setForeground(ColorScheme.BRAND_ORANGE);
			if (i > 0)
			{
				name.setBorder(new EmptyBorder(4, 0, 0, 0));
			}
			card.add(leftAligned(name));
			card.add(leftAligned(small(String.format("Dmg %s (%.0f%%)", NUMBER.format(t.getDamage()), share))));
			card.add(leftAligned(small(String.format("Hits %d  ·  Max %d", t.getHits(), t.getMaxHit()))));
		}
		return fullWidth(card);
	}

	/** One player's damage in a kill, with a line per weapon. */
	private JPanel playerCard(String player, Map<String, WeaponStats> weapons, long everyoneDamage)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		card.setAlignmentX(LEFT_ALIGNMENT);

		long damage = KillRecord.totalDamage(weapons);
		double share = everyoneDamage == 0 ? 0 : 100.0 * damage / everyoneDamage;
		boolean unshared = KillRecord.OTHER_PLAYERS.equals(player);

		JLabel name = new JLabel(player);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(unshared ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.BRAND_ORANGE);
		card.add(leftAligned(name));
		card.add(leftAligned(small(String.format("Dmg %s (%.0f%%)  ·  Hits %s", NUMBER.format(damage), share,
			NUMBER.format(KillRecord.totalHits(weapons))))));

		if (unshared)
		{
			card.add(leftAligned(small("Not sharing their hits")));
			return fullWidth(card);
		}

		List<Map.Entry<String, WeaponStats>> sorted = new ArrayList<>(weapons.entrySet());
		sorted.sort((a, b) -> Long.compare(b.getValue().getDamage(), a.getValue().getDamage()));
		for (Map.Entry<String, WeaponStats> e : sorted)
		{
			WeaponStats w = e.getValue();
			JLabel line = small(String.format("<html>%s<br>&nbsp;&nbsp;%s dmg · %d hits · max %d</html>",
				e.getKey(), NUMBER.format(w.getDamage()), w.getHits(), w.getMaxHit()));
			line.setBorder(new EmptyBorder(3, 0, 0, 0));
			card.add(leftAligned(line));
		}
		return fullWidth(card);
	}

	private void addWeaponCards(Map<String, WeaponStats> weaponMap, long totalDamage)
	{
		List<Map.Entry<String, WeaponStats>> weapons = new ArrayList<>(weaponMap.entrySet());
		weapons.sort((a, b) -> Long.compare(b.getValue().getDamage(), a.getValue().getDamage()));
		for (Map.Entry<String, WeaponStats> e : weapons)
		{
			content.add(weaponCard(e.getKey(), e.getValue(), totalDamage));
			content.add(Box.createVerticalStrut(4));
		}
	}

	private JPanel weaponCard(String weapon, WeaponStats w, long totalDamage)
	{
		JPanel card = new JPanel(new GridLayout(0, 1, 0, 1));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JLabel name = new JLabel(weapon);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(ColorScheme.BRAND_ORANGE);
		card.add(name);

		double share = totalDamage == 0 ? 0 : 100.0 * w.getDamage() / totalDamage;
		card.add(small(String.format("Hits %s  ·  Dmg %s (%.0f%%)", NUMBER.format(w.getHits()), NUMBER.format(w.getDamage()), share)));
		card.add(small(String.format("Avg %.1f  ·  Max %d", w.getAverageHit(), w.getMaxHit())));
		if (w.hasMultiHitAttacks())
		{
			// e.g. the Scythe: its three hits are one attack, and accuracy is per attack
			card.add(small(String.format("Attacks %s  ·  Accuracy %.0f%%", NUMBER.format(w.getAttacks()), w.getAccuracy())));
		}
		else
		{
			card.add(small(String.format("Misses %s  ·  Accuracy %.0f%%", NUMBER.format(w.getZeros()), w.getAccuracy())));
		}
		return card;
	}

	private JLabel killRow(KillRecord k, String label, int index)
	{
		boolean selected = index == selectedKill;
		Color color = selected ? ColorScheme.BRAND_ORANGE : k.isFailed() ? FAILED_COLOR : ColorScheme.LIGHT_GRAY_COLOR;
		JLabel row = textLine(String.format("%s  %s  %s dmg  %d hits%s",
			label.replace("Kill ", ""), duration(k), NUMBER.format(k.getTotalDamage()), k.getTotalHits(),
			k.hasOtherPlayers() ? "  (party)" : ""),
			color);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				selectedKill = index;
				showSelectedBoss();
			}
		});
		return row;
	}

	private void finishContent()
	{
		content.revalidate();
		content.repaint();
	}

	private static String duration(KillRecord k)
	{
		long secs = k.getDurationMs() / 1000;
		return String.format("%d:%02d", secs / 60, secs % 60);
	}

	private static void copyToClipboard(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	private void confirmDeleteFight()
	{
		String boss = (String) bossSelect.getSelectedItem();
		if (boss == null || selectedKill < 0 || selectedKill >= shownRecords.size())
		{
			return;
		}
		KillRecord record = shownRecords.get(selectedKill);
		String what = (String) killSelect.getSelectedItem();
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete " + (what == null ? "this fight" : what.replaceAll("\\s*\\(.*", "")) + " of " + boss + "?\n"
				+ "It's removed from the history and from the totals.", "Delete fight", JOptionPane.YES_NO_OPTION);
		if (choice == JOptionPane.YES_OPTION)
		{
			plugin.deleteFight(boss, selectedKill, record.getTimestamp());
		}
	}

	private void confirmReset()
	{
		String boss = (String) bossSelect.getSelectedItem();
		if (boss == null)
		{
			return;
		}
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete all tracked hits for " + boss + "?", "Reset boss", JOptionPane.YES_NO_OPTION);
		if (choice == JOptionPane.YES_OPTION)
		{
			plugin.resetBoss(boss);
		}
	}

	private static JPanel fullWidth(JPanel panel)
	{
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	private static JLabel leftAligned(JLabel label)
	{
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel fieldLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel sectionTitle(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		l.setBorder(new EmptyBorder(0, 0, 3, 0));
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel textLine(String text, Color color)
	{
		JLabel l = new JLabel("<html>" + text + "</html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel small(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.TEXT_COLOR);
		return l;
	}
}
