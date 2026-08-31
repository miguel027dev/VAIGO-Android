# Render / backend — VIENNA Android

O APK precisa dos endpoints de autenticação mobile presentes em `backend-patch/`.

No serviço web configure:

```env
MOBILE_AUTH_RETURN_URI=vienna://auth/callback
```

O `GOOGLE_REDIRECT_URI` continua sendo o callback HTTPS do site. Não cadastre `vienna://...` no Google Cloud; o custom scheme é usado apenas no retorno final do backend para o aplicativo.

Se o domínio do backend mudar, altere `VIENNA_BASE_URL` no projeto Android ou nas variables do GitHub Actions.
