package fr.samflix.vaniametrics.module.grim;

import org.bukkit.Bukkit;
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
		collector = new GrimCollector();
		metrics.register(collector);
		Bukkit.getPluginManager().registerEvents(collector, this);
	}

	@Override
	public void onDisable() {
		if (collector != null) {
			VaniaMetricsProvider.find().ifPresent(m -> m.unregister(collector));
		}
	}
}
