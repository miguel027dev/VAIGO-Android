# VAIGO Android

APK Android do VAIGO usando WebView para a aplicação e **navegador externo para Google OAuth**.

## Por que o Google não roda dentro do WebView

O APK intercepta `/login/google` e `/auth/google`. Em vez de abrir o Google no WebView, mostra um aviso nativo e abre o fluxo no navegador do aparelho. Depois do login, o servidor retorna um **código de uso único** para `vaigo://auth/callback`; o APK troca esse código por uma sessão persistente e grava a sessão no `CookieManager` do WebView.

O access token do Google nunca é colocado no deep link.

## Fluxo

1. Splash por 3 segundos.
2. APK abre `/mobile/entry`.
3. Se o cookie persistente ainda for válido, entra direto no VAIGO.
4. Se não houver login, o site abre a página de login.
5. Ao tocar em Google, o APK exibe um popup.
6. `Continuar no site` abre o navegador real.
7. Google autentica no navegador e volta ao site.
8. O site gera um código de uso único e chama `vaigo://auth/callback`.
9. O APK valida `state` + verificador PKCE, troca o código em `/mobile/auth/exchange`, grava o cookie no WebView e abre o app autenticado.

## URL do site

O padrão está em `gradle.properties`:

```properties
VAIGO_BASE_URL=https://vaigo.online
```

Também é possível criar no GitHub **Settings → Secrets and variables → Actions → Variables**:

- `VAIGO_BASE_URL` = URL de produção do VAIGO

O workflow usa essa variável quando existir.

## Gerar APK pelo GitHub

1. Crie um repositório vazio.
2. Envie todo o conteúdo deste ZIP para a raiz do repositório.
3. Abra **Actions → Build VAIGO APK → Run workflow**.
4. Ao terminar, abra o run e baixe o artifact `vaigo-debug-apk`.

## Backend obrigatório

O site precisa conter as rotas de mobile auth presentes no ZIP de backend que acompanha esta entrega:

- `/mobile/entry`
- `/mobile/auth/google/start`
- `/mobile/auth/finish`
- `/mobile/auth/exchange`

No Render adicione:

```text
MOBILE_AUTH_RETURN_URI=vaigo://auth/callback
MOBILE_AUTH_TTL_SECONDS=300
```

Mantenha também `DATABASE_URL`, `SECRET_KEY`, Google e Mapbox como já estão configurados.
