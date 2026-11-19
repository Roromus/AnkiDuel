---
trigger: always_on
---

# Règle obligatoire : Commit & Push en fin de tâche

À la fin de chaque tâche qui modifie des fichiers dans ce dépôt :
1. Si du code dans `:androidApp` ou `:shared` a été modifié, incrémente `versionCode` (et `versionName` si pertinent) dans `androidApp/build.gradle.kts` afin que Firebase App Distribution détecte une nouvelle version.
2. Vérifie `git status`, puis exécute `git add`, `git commit` avec un message clair, et `git push origin main` pour déclencher automatiquement la compilation et la distribution de l'APK via GitHub Actions.
3. Ne laisse jamais de modifications non poussées à la fin de ton travail (sauf demande explicite contraire de l'utilisateur).
