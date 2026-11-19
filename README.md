# AnkiDuel

Application Android native (**Kotlin + Jetpack Compose Material 3**) avec backend temps réel **Firebase (Auth + Cloud Firestore)** qui compare le temps passé sur **AnkiDroid** et les **réseaux sociaux**, et utilise un système de **duel** pour verrouiller les réseaux sociaux des participants selon le **delta de temps**.

## Architecture du projet

- `:shared` — Moteur métier Kotlin pur :
  - `DuelDeltaCalculator` : score net (`AnkiDroid - Réseaux Sociaux`), référence `LEADER` ou `AVERAGE`, marge de tolérance, plafond quotidien de dette, calcul du verrouillage global multi-duels.
  - `AntiIdleValidator` : vérifie l'activité réelle de révision AnkiDroid (cartes révisées via API AnkiDroid) pour empêcher de laisser l'écran allumé sans réviser.
  - `SocialAppCatalog` & `DuelModels` : catalogue des réseaux sociaux et modèles de duel.
- `:androidApp` — Application Android (minSdk 26, targetSdk 35) :
  - `FirebaseBackendClient` : authentification **Firebase Auth** (Email/Mot de passe) et synchronisation temps réel via **Cloud Firestore** (`addSnapshotListener`).
  - `UsageStatsTracker` (`UsageEvents`) + `AnkiDroidTracker` (`ContentResolver` AnkiDroid)
  - `SocialBlockAccessibilityService` + `LockOverlayActivity` (blocage strict des réseaux sociaux tant que la dette > 0)
  - `DuelMonitorService` (service au premier plan) + `DuelSyncWorker` (WorkManager)
  - `DemoBotEngine` : **Mode Démo local** intégré pour tester immédiatement sans compte Firebase.
- `:server` — Ancien serveur Ktor (conservé à titre d'archive).

## Configuration Firebase (gratuit, plan Spark)

1. Créez un projet sur la [console Firebase](https://console.firebase.google.com/) (plan **Spark** gratuit, sans carte bancaire).
2. Ajoutez une application Android avec le package `com.ankiduel.android`.
3. Téléchargez le fichier `google-services.json` et placez-le dans `androidApp/google-services.json`.
4. Dans **Authentication → Sign-in method**, activez **Email/Password**.
5. Dans **Firestore Database**, créez une base de données (en mode production) et copiez les règles de [firestore.rules](firestore.rules) dans l'onglet **Rules**.

## Compiler l'APK Android et exécuter les tests

```bash
./gradlew :shared:test :androidApp:testDebugUnitTest :androidApp:assembleDebug
```

L'APK debug est généré dans `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
