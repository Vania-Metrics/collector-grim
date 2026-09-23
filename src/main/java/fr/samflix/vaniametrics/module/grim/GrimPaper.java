package fr.samflix.vaniametrics.module.grim;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import fr.samflix.vaniametrics.api.VaniaMetrics;
import fr.samflix.vaniametrics.api.VaniaMetricsProvider;

/**
 * Métriques d'anticheat GrimAC.
 *
 * <p>Un écouteur avant tout : les violations se comptent quand elles arrivent, pas quand Prometheus interroge.
 */
public final class GrimPaper extends JavaPlugin {

	private GrimCollector collecteur;

	@Override
	public void onEnable() {
		VaniaMetrics metriques = VaniaMetricsProvider.get();
		collecteur = new GrimCollector();
		metriques.enregistrer(collecteur);
		Bukkit.getPluginManager().registerEvents(collecteur, this);
	}

	@Override
	public void onDisable() {
		if (collecteur != null) {
			VaniaMetricsProvider.chercher().ifPresent(m -> m.retirer(collecteur));
		}
	}
}
