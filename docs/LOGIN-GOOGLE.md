# Login Google — VANO MAPS Android

O login Google abre no navegador externo e retorna ao aplicativo por deep link com PKCE.

O retorno atual continua:

```text
vienna://auth/callback
```

Não troque esse esquema isoladamente: backend, OAuth e Android precisam migrar juntos.
