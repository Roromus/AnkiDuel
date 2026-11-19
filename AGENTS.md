# Instructions pour les agents (AnkiDuel)

- **Push automatique obligatoire** : à la fin de chaque tâche modifiant le code, tu DOIS commiter et pousser tes changements sur `origin main` (`git add`, `git commit`, `git push origin main`).
- **Versioning Android** : si tu modifies `:androidApp` ou `:shared`, incrémente `versionCode` dans `androidApp/build.gradle.kts` avant de commiter afin que le workflow GitHub Actions publie une nouvelle mise à jour sur Firebase App Distribution.
