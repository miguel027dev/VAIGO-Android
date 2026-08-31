# VIENNA Android

APK Android da VIENNA baseado em WebView, com identidade visual completa da marca, GPS, uploads, links externos e login Google pelo navegador do aparelho.

## Identidade aplicada

- nome do app: **VIENNA**
- package/applicationId: `app.vienna.navigation`
- deep link de autenticação: `vienna://auth/callback`
- ícone, adaptive icon, themed icon e splash VIENNA
- paleta: `#F59A62`, `#FFC39B`, `#D9703F`, `#FFFBF2`, `#27231F`, `#706861`
- splash nativo sincronizado com o modo Black salvo pelo site
- User-Agent: `VIENNA-Android/<versão>`

## Backend

O endereço do backend é configurável por `VIENNA_BASE_URL`. O projeto está apontando para o host atual de produção para não interromper o app durante a migração de domínio.

Em `gradle.properties`:

```properties
VIENNA_BASE_URL=https://SEU-DOMINIO-VIENNA
VIENNA_MOBILE_RETURN_URI=vienna://auth/callback
```

No GitHub Actions, crie as variables:

- `VIENNA_BASE_URL`
- `VIENNA_MOBILE_RETURN_URI` (opcional; padrão `vienna://auth/callback`)

## Login Google

O Google OAuth não roda dentro do WebView. O fluxo é:

1. usuário toca em continuar com Google;
2. o APK abre o navegador do sistema;
3. o backend conclui o OAuth HTTPS;
4. o backend emite um código de uso único;
5. o navegador retorna para `vienna://auth/callback`;
6. o APK troca o código por uma sessão persistente e injeta o cookie no WebView.

O backend precisa aceitar:

```env
MOBILE_AUTH_RETURN_URI=vienna://auth/callback
```

O callback cadastrado no Google continua sendo o callback **HTTPS do site**, nunca o custom scheme.

## Build local

Requisitos: Java 17+, Android SDK 35 e Gradle 8.9.

```bash
gradle --no-daemon clean assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Abra **Actions → Build VIENNA APK → Run workflow**. O artifact gerado se chama `vienna-debug-apk`.
