package com.meteocompare.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver déclaré dans le manifest sous le tag `<receiver>`. Le
 * système Android l'appelle pour les événements de lifecycle du widget :
 * ajout, resize, suppression, mise à jour périodique.
 *
 * Rôle du receiver : dire à Glance quel [GlanceAppWidget] rendre. Toute la
 * logique de rendu et de fetch de données vit dans [MeteoWidget] — le
 * receiver ne fait que servir de pont, il n'a AUCUN code métier.
 *
 * Cette séparation permet notamment de tester [MeteoWidget] en isolation
 * (Glance offre un GlanceAppWidget-testing runner) sans avoir à instancier
 * un BroadcastReceiver Android.
 */
class MeteoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeteoWidget()
}
