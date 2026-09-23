package fr.samflix.vaniametrics.module.grim;

import java.util.Locale;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import ac.grim.grimac.api.events.FlagEvent;

import fr.samflix.vaniametrics.api.Collector;
import fr.samflix.vaniametrics.api.Counter;
import fr.samflix.vaniametrics.api.Histogram;
import fr.samflix.vaniametrics.api.MetricRegistry;

/**
 * GrimAC — anticheat violations.
 *
 * <p>This is the most valuable collector in the set, because it gives information no other
 * source has: {@code mc_anticheat_flags_total{check="…"}} shows the server's defenses being
 * exercised, and its RATE is a natural alert. A legitimate player occasionally triggers a
 * check — network jitter, clock drift — ten in a minute on the same check does not.
 *
 * <p>No player label here, and this is a case where the strict rule applies without nuance:
 * a flag is a HIGH-FREQUENCY event, and the player it's about is exactly the one who can
 * produce thousands of them. To find out WHO, GrimAC's own logs are the right tool.
 *
 * <p>{@code ignoreCancelled = true}: a flag cancelled by another plugin — an exemption, a
 * player in creative mode — never happened. Counting it would measure suspicion, not fact.
 */
// @SuppressWarnings("removal"): GrimAC's entire Bukkit event API is deprecated since its
// 1.2.1.0 — FlagEvent, CompletePredictionEvent, GrimJoinEvent, checked one by one — in favor
// of a platform-independent API that doesn't exist yet in the installed version. There's no
// alternative to pick, and the day GrimAC removes them, the build will fail loudly: that's
// exactly the right failure mode.
@SuppressWarnings("removal")
public final class GrimCollector implements Collector, Listener {

	private Counter flags;
	private Counter setbacks;
	private Histogram violations;

	@Override
	public String name() {
		return "anticheat";
	}

	@Override
	public String source() {
		return "GrimAC";
	}

	@Override
	public void declare(MetricRegistry r) {
		flags = r.counter("anticheat_flags_total",
				"Violations flagged by GrimAC, per check. Its RATE is the signal: a few "
						+ "scattered flags are normal, a burst on the same check is not.",
				"check");
		setbacks = r.counter("anticheat_setbacks_total",
				"Violations that caused a player SETBACK — GrimAC actually corrected it, "
						+ "not just logged it.",
				"check");
		violations = r.histogram("anticheat_violation_level",
				"Accumulated violation level at the time of the flag. A rising level "
						+ "distinguishes an isolated incident from sustained behavior.",
				new double[] {1, 2, 5, 10, 20, 50, 100, 500});
	}

	@Override
	public void collect(MetricRegistry r) {
		// Nothing to collect: everything is counted in the listener. A flag is an EVENT,
		// and querying GrimAC at scrape time would only return an uninteresting snapshot.
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onFlag(FlagEvent e) {
		String check = checkName(e);
		flags.inc(check);
		violations.observe(e.getViolations());
		if (e.isSetback()) {
			setbacks.inc(check);
		}
	}

	/**
	 * The check name, bounded by construction.
	 *
	 * <p>GrimAC declares a few dozen and the list doesn't depend on players: the label is
	 * therefore safe. {@code getCheckName()} can be null on a check misdeclared by an
	 * extension — hence the fallback.
	 */
	private static String checkName(FlagEvent e) {
		var check = e.getCheck();
		if (check == null) {
			return "unknown";
		}
		String n = check.getCheckName();
		return n == null || n.isBlank() ? "unknown" : n.toLowerCase(Locale.ROOT);
	}
}
