# AnkiDuel

Application Android native (**Kotlin + Jetpack Compose Material 3**) avec backend temps réel (**Ktor + WebSocket + SQLite/Exposed**) qui compare le temps passé sur **AnkiDroid** et les **réseaux sociaux**, et utilise un système de **duel** pour verrouiller les réseaux sociaux des participants selon le **delta de temps**.

## Architecture du projet

- `:shared` — Moteur métier Kotlin pur partagé entre client et serveur :
  - `DuelDeltaCalculator` : score net (`AnkiDroid - Réseaux Sociaux`), référence `LEADER` ou `AVERAGE`, marge de tolérance, plafond quotidien de dette, calcul du verrouillage global multi-duels.
  - `AntiIdleValidator` : vérifie l'activité réelle de révision AnkiDroid (cartes révisées via API AnkiDroid) pour empêcher de laisser l'écran allumé sans réviser.
  - `SocialAppCatalog` & `ApiProtocol` : catalogue des réseaux sociaux et DTOs REST/WebSocket.
- `:server` — Serveur Ktor (JWT, REST, WebSocket `/ws/duels`, SQLite via Exposed, remise à zéro quotidienne automatique à minuit).
- `:androidApp` — Application Android (minSdk 26, targetSdk 35) :
  - `UsageStatsTracker` (`UsageEvents`) + `AnkiDroidTracker` (`ContentResolver` AnkiDroid)
  - `SocialBlockAccessibilityService` + `LockOverlayActivity` (blocage strict des réseaux sociaux tant que la dette > 0)
  - `DuelMonitorService` (service au premier plan) + `DuelSyncWorker` (WorkManager)
  - `DemoBotEngine` : **Mode Démo local** intégré pour tester immédiatement sans serveur distant.

## Lancer le serveur backend

```bash
./gradlew :server:run
# ou avec Docker :
docker compose up --build
```

Le serveur écoute sur `http://0.0.0.0:8080` (sur l'émulateur Android, utilisez `http://10.0.2.2:8080`).

## Compiler l'APK Android et exécuter les tests

```bash
./gradlew :shared:test :server:test :androidApp:testDebugUnitTest :androidApp:assembleDebug
```

L'APK debug est généré dans `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
