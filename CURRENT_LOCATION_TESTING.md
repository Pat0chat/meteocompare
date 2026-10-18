# v1.19.0 — Ma position : protocole de validation

L'implémentation requiert **un essai sur Android**. Les contrôles statiques n'établissent pas la qualité de l'acquisition réelle de position.

## Tests locaux

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
python -m pytest -q scripts/test_current_location_privacy.py
```

## À tester sur émulateur et téléphone

1. Installation/mise à jour depuis v1.18.1 : les favoris et les widgets existants sont conservés. Aucune permission demandée au démarrage ni sur un simple rafraîchissement Home.
2. Home → Ajouter → « Utiliser ma position » : seule la permission *approximative* est demandée. Refus : aucune carte créée, villes fixes inchangées. Autorisation : création d'une carte « Ma position » avec toutes les prévisions ; elle peut être repliée/dépliée et ouvrir les détails.
3. Émulateur avec position simulée (`adb emu geo fix 2.34880 48.85341`, ordre longitude latitude), ouvrir l'app et attendre. Vérifier l'ID de zone, l'absence de GPS quand l'app est fermée.
4. Changer l'émulateur vers Lyon (`adb emu geo fix 4.83 45.76`) et revenir dans l'application. La carte doit changer de zone, sans utiliser les prévisions en cache de Paris. Les anciennes lignes du cache météo doivent être purgées.
5. Créer les **deux** widgets en choisissant « Ma position » : tailles/affichages identiques aux widgets normaux, et rechargement dans la nouvelle zone après passage dans l'app. Sans ouvrir l'app, les widgets continuent d'utiliser la dernière zone mémorisée. Vérifier le message de position ancienne après 12 heures.
6. Désactiver la localisation système, révoquer la permission, passer en mode avion, redémarrer. Les autres villes restent utilisables ; la dernière zone persiste localement (mais peut être périmée), aucun crash.
7. Supprimer la carte « Ma position » : état local désactivé et cellule supprimée, pas de recollecte au prochain démarrage. Les widgets configurés pour cette source indiquent l'absence de localité jusqu'à nouvelle activation.
8. Contrôler la sauvegarde Android et `PRIVACY.md`. Aucune permission `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION`, aucune activité/worker en arrière-plan ne demande le GPS. Les requêtes Open-Meteo portent la cellule arrondie. Inspecter les logs Debug et les traces HTTP afin de ne pas divulguer les coordonnées de l'appareil.
9. Profilage sur appareil physique : comparer consommation batterie, temps d'acquisition et mémoire entre 1.18.1 et 1.19.0 ; tester également multi-fenêtres et rotation.

## Risques connus

- La géolocalisation approximative varie selon les fournisseurs Android disponibles (notamment les émulateurs sans fournisseur réseau fonctionnel). Un échec doit afficher « Position indisponible ».
- Les widgets ne suivent pas les déplacements lorsque l'application est fermée ; c'est un choix explicite de confidentialité, pas un bug.
- Les fichiers de cache HTTP radar peuvent encore contenir des tuiles de zones précédemment consultées jusqu'à leur expiration/éviction. Ils sont exclus des sauvegardes. Ces données ne forment pas un historique GPS applicatif, mais restent des informations géographiques temporaires : vérifier la politique de confidentialité.
- Une ancienne requête météo en cours de téléchargement est empêchée d'écrire son cache lorsque sa cellule n'est plus active. La suppression physique des caches précédents reste best-effort si la base locale échoue temporairement.
