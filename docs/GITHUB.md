# GitHub — VANO MAPS Android

O workflow usa `https://vanomaps.online` por padrão.

Variáveis aceitas:

```text
VANO_BASE_URL=https://vanomaps.online
VANO_MOBILE_RETURN_URI=vienna://auth/callback
```

O esquema `vienna://` permanece temporariamente por compatibilidade com o OAuth já existente.

Para build local:

```bash
gradle --no-daemon clean assembleDebug
```
