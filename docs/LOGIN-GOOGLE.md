# Login Google — VIENNA Android

A VIENNA usa navegador externo para OAuth, evitando login Google dentro de WebView.

Fluxo seguro:

1. o APK cria `state`, verifier PKCE e challenge;
2. abre `/mobile/auth/google/start` no navegador;
3. o backend redireciona para o Google usando o callback HTTPS normal;
4. após autenticar, o backend gera um código temporário de uso único;
5. o navegador chama `vienna://auth/callback`;
6. o APK valida `state`, envia o verifier e troca o código em `/mobile/auth/exchange`;
7. a sessão persistente é salva no `CookieManager`.

Configuração do backend:

```env
MOBILE_AUTH_RETURN_URI=vienna://auth/callback
```

O callback no Google Cloud permanece HTTPS.
