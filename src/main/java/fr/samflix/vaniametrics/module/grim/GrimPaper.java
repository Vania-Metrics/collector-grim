package fr.samflix.vaniametrics.module.grim;

import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.api.VaniaMetricsProvider;

/**
 * GrimAC anticheat metrics.
 *
 * <p>A listener above all: violations are counted as they happen, not when Prometheus scrapes.
 */
public final class GrimPaper extends JavaPlugin {

	private GrimCollector collector;

	@Override
	public void onEnable() {
		VaniaMetrics metrics = VaniaMetricsProvider.get();
		collector = new GrimCollector(this);
		metrics.register(collector);
		collector.subscribe();
	}

	@Override
	public void onDisable() {
		if (collector != null) {
			// Unregistering closes the collector, which unsubscribes it from GrimAC; without the
			// core, close it directly.
			VaniaMetricsProvider.find().ifPresentOrElse(m -> m.unregister(collector), collector::close);
		}
	}
}
