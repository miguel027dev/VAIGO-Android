# Render — variáveis necessárias para o APK

Além das variáveis atuais do site:

```text
DATABASE_URL=...
SECRET_KEY=...
MAPBOX_ACCESS_TOKEN=...
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GOOGLE_REDIRECT_URI=https://SEU-DOMINIO/login/google/callback
```

adicione:

```text
MOBILE_AUTH_RETURN_URI=vaigo://auth/callback
MOBILE_AUTH_TTL_SECONDS=300
```

O `GOOGLE_REDIRECT_URI` continua sendo HTTPS do SITE. Não coloque `vaigo://...` no console do Google. O custom scheme é usado somente depois que o Google já retornou ao seu backend.
