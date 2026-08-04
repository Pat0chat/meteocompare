package com.meteocompare.app.domain.model

import java.time.Duration

/**
 * Intervalle entre deux rafraîchissements automatiques des données météo.
 *
 * S'applique à deux consommateurs :
 *   - **Widget** : seuil de fraîcheur du cache réseau. Le tick d'affichage
 *     reste planifié toutes les 15 minutes afin de faire avancer les heures ;
 *     en MANUAL il ne déclenche aucun fetch automatique.
 *   - **App** : seuil de fraîcheur du cache. Si le dernier fetch est plus
 *     récent que cet intervalle, on saute la requête réseau au chargement
 *     d'un écran. L'utilisateur peut toujours forcer via pull-to-refresh.
 *
 * ─── Contraintes WorkManager ────────────────────────────────────────────
 * `PeriodicWorkRequest` a un **minimum** de 15 minutes. On expose cette
 * borne inférieure comme premier palier (au lieu d'un "5 min" trompeur qui
 * serait silencieusement remonté à 15 min par le système).
 *
 * ─── Choix des paliers ──────────────────────────────────────────────────
 * On limite à 5 valeurs discrètes (+ MANUAL) plutôt qu'un slider continu :
 *  - Suffisant pour couvrir les cas d'usage (économie de batterie vs
 *    fraîcheur perçue).
 *  - Choix ergonomique : SegmentedButton ou Dropdown avec 6 items reste
 *    lisible ; un slider "combien de minutes" oblige l'utilisateur à
 *    réfléchir en unité arbitraire.
 *  - Chaque palier a une motivation :
 *      · MINUTES_15 : fréquence par défaut du système Android — assez frais
 *        pour un widget de bureau consulté toutes les 30 min.
 *      · MINUTES_30 : compromis plus réactif que le défaut. Les modèles ont
 *        des cadences de publication différentes ; ce palier ne garantit pas
 *        qu'un nouveau run soit disponible à chaque passage.
 *      · HOUR_1 : défaut retenu — offre un bon compromis entre fraîcheur perçue et consommation, sans
 *        supposer que tous les modèles publient au même rythme.
 *      · HOURS_3 / HOURS_6 : profil "économie de batterie", accepté quand
 *        on regarde la météo une ou deux fois par jour.
 *      · MANUAL : aucune requête périodique ; un premier chargement sans
 *        cache reste autorisé pour éviter un écran définitivement vide.
 */
enum class RefreshInterval(val duration: Duration) {
    MINUTES_15(Duration.ofMinutes(15)),
    MINUTES_30(Duration.ofMinutes(30)),
    HOUR_1(Duration.ofHours(1)),
    HOURS_3(Duration.ofHours(3)),
    HOURS_6(Duration.ofHours(6)),
    /**
     * Aucun rafraîchissement réseau périodique. Le worker widget continue
     * ses ticks cache-only pour actualiser les libellés temporels, et les
     * écrans de l'app réutilisent le cache. Exception volontaire : si aucun
     * cache n'existe encore pour une ville, un chargement initial est permis
     * afin d'amorcer l'affichage. Ensuite l'utilisateur doit pull-to-refresh
     * ou appuyer sur le bouton refresh.
     */
    MANUAL(Duration.ZERO);

    /** Millisecondes équivalentes — utile pour les comparaisons de fraîcheur cache. */
    val millis: Long get() = duration.toMillis()

    companion object {
        /**
         * Défaut : 1 heure. Compromis batterie/fraîcheur ; ce n'est pas la
         * cadence native de tous les modèles, qui varie selon le fournisseur.
         */
        val DEFAULT = HOUR_1

        /** Conversion sûre depuis la chaîne DataStore. Inconnu → défaut. */
        fun fromString(value: String?): RefreshInterval = when (value) {
            MINUTES_15.name -> MINUTES_15
            MINUTES_30.name -> MINUTES_30
            HOUR_1.name -> HOUR_1
            HOURS_3.name -> HOURS_3
            HOURS_6.name -> HOURS_6
            MANUAL.name -> MANUAL
            else -> DEFAULT
        }
    }
}
