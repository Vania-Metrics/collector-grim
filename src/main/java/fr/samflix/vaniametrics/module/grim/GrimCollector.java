package fr.samflix.vaniametrics.module.grim;

import java.util.Locale;
import java.util.function.Supplier;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.event.ListenerPriority;
import ac.grim.grimac.api.event.events.FlagEvent;

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
 * <p>A flag cancelled by another listener — an exemption, a player in creative mode — never
 * happened. Counting it would measure suspicion, not fact.
 *
 * <p>Flags come from GrimAC's own event bus, not from Bukkit's event system. GrimAC deprecated
 * its Bukkit events, and Paper says so on every start ("Server performance will be affected"):
 * each flag would otherwise be bridged into a Bukkit event just for us. The verbose text of a flag
 * is handed over as a supplier and never asked for, so it is never built for us either.
 */
public final class GrimCollector implements Collector {

	private final Object plugin;
	private final FlagEvent.SupplierHandler handler = this::onFlag;
	private FlagEvent.Channel channel;

	private Counter flags;
	private Counter setbacks;
	private Histogram violations;

	/** @param plugin the plugin the subscription belongs to, as GrimAC's event bus expects */
	public GrimCollector(Object plugin) {
		this.plugin = plugin;
	}

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

	/** Listens last, as Bukkit's MONITOR did: whatever the other listeners decided is final. */
	public void subscribe() {
		GrimAbstractAPI api = GrimAPIProvider.get();
		channel = api.getEventBus().get(FlagEvent.class);
		channel.onFlagSupplier(api.getGrimPlugin(plugin), handler, ListenerPriority.MONITOR);
	}

	@Override
	public void close() {
		if (channel != null) {
			channel.unsubscribe(handler);
			channel = null;
		}
	}

	/**
	 * Called on GrimAC's threads, several at once: the instruments are thread-safe.
	 *
	 * @return the cancellation state, unchanged: this listener only counts
	 */
	private boolean onFlag(GrimUser user, AbstractCheck check, Supplier<String> verbose, boolean cancelled) {
		if (cancelled) {
			return true;
		}
		String name = checkName(check);
		flags.inc(name);
		violations.observe(check.getViolations());
		// What GrimAC's own FlagEvent#isSetback() computes.
		if (check.getViolations() > check.getSetbackVL()) {
			setbacks.inc(name);
		}
		return false;
	}

	/**
	 * The check name, bounded by construction.
	 *
	 * <p>GrimAC declares a few dozen and the list doesn't depend on players: the label is
	 * therefore safe. {@code getCheckName()} can be null on a check misdeclared by an
	 * extension — hence the fallback.
	 */
	private static String checkName(AbstractCheck check) {
		if (check == null) {
			return "unknown";
		}
		String n = check.getCheckName();
		return n == null || n.isBlank() ? "unknown" : n.toLowerCase(Locale.ROOT);
	}
}
