# VAIGO Android

APK Android leve do VAIGO usando WebView para a aplicação e **navegador externo para Google OAuth**.

## O que esta versão melhora

- Remove a espera artificial de 3 segundos na splash: o WebView começa a carregar imediatamente.
- Usa a splash nativa do Android e um loader curto, clean e integrado enquanto o servidor responde.
- Faz fade suave para o conteúdo assim que o primeiro frame real da página fica visível.
- Mantém aceleração por hardware, cache padrão do WebView e barras de rolagem invisíveis.
- Trata Android 15/target 35 para evitar conteúdo atrás das barras do sistema.
- Mantém o layout do backend intacto e aplica apenas ajustes seguros de toque/viewport/tipografia.
- Simplifica o login Google: tocar em Google já abre o navegador seguro, sem popup intermediário.
- Melhora a tela offline e o comportamento de retry.

## Login Google

O APK intercepta `/login/google` e `/auth/google`. Em vez de abrir o Google dentro do WebView, inicia o navegador do aparelho. Depois do login, o servidor retorna um **código de uso único** para `vaigo://auth/callback`; o APK troca esse código por uma sessão persistente e grava a sessão no `CookieManager` do WebView.

O access token do Google nunca é colocado no deep link.

## Fluxo

1. O APK abre e começa a carregar `/mobile/entry` imediatamente.
2. Se o cookie persistente ainda for válido, entra direto no VAIGO.
3. Se não houver login, o site abre a página de login.
4. Ao tocar em Google, o navegador real é aberto.
5. Google autentica no navegador e volta ao site.
6. O site gera um código de uso único e chama `vaigo://auth/callback`.
7. O APK valida `state` + verificador PKCE, troca o código em `/mobile/auth/exchange`, grava o cookie no WebView e abre o app autenticado.

## URL do site

O padrão está em `gradle.properties`:

```properties
VAIGO_BASE_URL=https://vaigo.online
```

Também é possível criar no GitHub **Settings → Secrets and variables → Actions → Variables**:

- `VAIGO_BASE_URL` = URL de produção do VAIGO

## Gerar APK pelo GitHub

1. Envie este projeto para a raiz do repositório.
2. Abra **Actions → Build VAIGO APK → Run workflow**.
3. Ao terminar, abra o run e baixe o artifact `vaigo-debug-apk`.

## Backend

O backend continua usando as mesmas rotas:

- `/mobile/entry`
- `/mobile/auth/google/start`
- `/mobile/auth/finish`
- `/mobile/auth/exchange`

No Render mantenha:

```text
MOBILE_AUTH_RETURN_URI=vaigo://auth/callback
MOBILE_AUTH_TTL_SECONDS=300
```

Mantenha também `DATABASE_URL`, `SECRET_KEY`, Google e Mapbox como já estão configurados.
