# Patch do backend para VIENNA Android

Este diretório documenta o handoff seguro entre o OAuth do navegador e o APK.

No backend configure:

```env
MOBILE_AUTH_RETURN_URI=vienna://auth/callback
```

O redirect do Google continua HTTPS. O backend só usa `vienna://auth/callback` depois de concluir a autenticação e criar um código temporário de uso único.

Arquivos:

- `app.py.diff`: endpoints `/mobile/entry`, `/mobile/auth/google/start`, `/mobile/auth/finish` e `/mobile/auth/exchange`;
- `db_backend.py.diff`: tabela de códigos mobile;
- `templates/mobile_auth_complete.html`: tela que devolve o usuário ao app.
