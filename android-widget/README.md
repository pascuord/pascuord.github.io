# Mis Commits (widget Android)

Widget para la pantalla de inicio de Android con el gráfico de contribuciones de GitHub.

- Se actualiza solo cada hora en segundo plano.
- Con un token classic con permiso `read:user` incluye repositorios privados y de organizaciones (API GraphQL oficial). Sin token usa datos públicos.
- Cada push a `main` compila el APK con GitHub Actions y lo publica en Releases.

Instalar: descarga `MisCommits.apk` desde la última versión en *Releases* y ábrelo en el móvil.

`app/commits.keystore` es una clave de firma propia de esta app, para que las nuevas versiones se instalen encima de la anterior. No sirve para nada más.
