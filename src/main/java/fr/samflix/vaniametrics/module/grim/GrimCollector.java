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
 * GrimAC — les violations d'anticheat.
 *
 * <p>C'EST LE CONNECTEUR LE PLUS RENTABLE DU LOT, parce qu'il donne une information qu'aucune autre
 * source n'a : {@code mc_anticheat_flags_total{check="…"}} dit qu'on teste les défenses du serveur,
 * et son TAUX est une alerte naturelle. Un joueur légitime déclenche un contrôle de temps en temps
 * — réseau, décalage d'horloge — ; dix par minute sur un même contrôle, non.
 *
 * <p>PAS D'ÉTIQUETTE DE JOUEUR ICI, et c'est un cas où la règle stricte s'applique sans nuance :
 * un flag est un événement à HAUTE FRÉQUENCE, et le joueur qui en est l'objet est justement celui
 * qui peut en produire des milliers. Pour savoir QUI, les journaux de GrimAC sont faits pour ça.
 *
 * <p>{@code ignoreCancelled = true} : un flag annulé par un autre plugin — une exemption, un
 * joueur en mode créatif — n'a pas eu lieu. Le compter mesurerait les soupçons, pas les faits.
 */
//
// @SuppressWarnings("removal") : TOUTE l'API d'événements Bukkit de GrimAC est dépréciée depuis sa
// 1.2.1.0 — FlagEvent, CompletePredictionEvent, GrimJoinEvent, vérifié une par une — au profit
// d'une API indépendante de la plateforme qui n'existe pas encore dans la version installée. Il n'y
// a donc pas d'alternative à choisir, et le jour où GrimAC les retirera, la compilation échouera
// bruyamment : c'est exactement le bon mode de panne.
@SuppressWarnings("removal")
public final class GrimCollector implements Collector, Listener {

	private Counter flags;
	private Counter setbacks;
	private Histogram violations;

	@Override
	public String nom() {
		return "anticheat";
	}

	@Override
	public String origine() {
		return "GrimAC";
	}

	@Override
	public void declarer(MetricRegistry r) {
		flags = r.counter("anticheat_flags_total",
				"Violations relevées par GrimAC, par contrôle. Son TAUX est le signal : quelques "
						+ "flags dispersés sont normaux, une rafale sur un même contrôle ne l'est "
						+ "pas.",
				"check");
		setbacks = r.counter("anticheat_setbacks_total",
				"Violations qui ont provoqué un RECUL du joueur — GrimAC l'a effectivement "
						+ "corrigé, et pas seulement noté.",
				"check");
		violations = r.histogram("anticheat_violation_level",
				"Niveau de violation accumulé au moment du flag. Un niveau qui monte distingue "
						+ "l'incident isolé du comportement soutenu.",
				new double[] {1, 2, 5, 10, 20, 50, 100, 500});
	}

	@Override
	public void relever(MetricRegistry r) {
		// Rien à relever : tout se compte dans l'écouteur. Un flag est un ÉVÉNEMENT, et
		// interroger GrimAC au scrape ne rendrait qu'un état instantané sans intérêt.
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onFlag(FlagEvent e) {
		String controle = nom(e);
		flags.inc(controle);
		violations.observe(e.getViolations());
		if (e.isSetback()) {
			setbacks.inc(controle);
		}
	}

	/**
	 * Le nom du contrôle, borné par construction.
	 *
	 * <p>GrimAC en déclare quelques dizaines et la liste ne dépend pas des joueurs : l'étiquette
	 * est donc sûre. {@code getCheckName()} peut être nul sur un contrôle mal déclaré par une
	 * extension — d'où le repli.
	 */
	private static String nom(FlagEvent e) {
		var controle = e.getCheck();
		if (controle == null) {
			return "unknown";
		}
		String n = controle.getCheckName();
		return n == null || n.isBlank() ? "unknown" : n.toLowerCase(Locale.ROOT);
	}
}
