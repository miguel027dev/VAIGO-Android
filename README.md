# VANO Native Test

Protótipo Android nativo para testar a arquitetura **UI/mapa/câmera/GPS no celular + inteligência/rotas no servidor VANO**.

## O que já existe
- Kotlin + Jetpack Compose.
- Mapa nativo MapLibre (OpenGL) com OpenFreeMap Liberty.
- GPS nativo sem Google Play Services.
- Puck VANO local.
- Busca usando `GET /api/geocode` do VANO.
- Rotas usando `GET /api/route` com `mobile_compact=1`.
- Perfis Carro e Moto.
- Modos Rápida / Spark / Segura.
- Linha de rota sempre `#FF9500`.
- Câmera nativa adaptativa, com perfil de moto mais reativo.
- Botão de recalibrar.
- Interface local; o servidor entrega dados e rotas.

Servidor configurado em `app/build.gradle.kts`:
`https://vaigo-1.onrender.com`

## Abrir e gerar APK
1. Abra esta pasta no Android Studio.
2. Aguarde o Gradle Sync baixar Compose + MapLibre.
3. Use um celular Android com depuração USB ou um emulador.
4. `Build > Build APK(s)`.

O APK de debug fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Observação
Este é um APK/projeto **de teste**, não substitui ainda todos os recursos do web app. O objetivo é validar a sensação de mapa, HUD, pesquisa, GPS e câmera nativos antes da migração completa.
