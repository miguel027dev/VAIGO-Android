# Patch obrigatório no site VAIGO

O APK depende da ponte de autenticação mobile. O ZIP de backend V67 desta entrega já contém tudo aplicado.

Se preferir aplicar manualmente sobre a V66:

- aplique `app.py.diff` em `app.py`;
- aplique `db_backend.py.diff` em `db_backend.py`;
- copie `templates/mobile_auth_complete.html`;
- adicione no Render:

```text
MOBILE_AUTH_RETURN_URI=vaigo://auth/callback
MOBILE_AUTH_TTL_SECONDS=300
```

Não altere o redirect do Google para `vaigo://`. O callback cadastrado no Google continua sendo o callback HTTPS do seu site.
