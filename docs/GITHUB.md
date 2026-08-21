# GitHub

## Envio inicial

```bash
git init
git add .
git commit -m "VAIGO Android v1"
git branch -M main
git remote add origin URL_DO_REPOSITORIO
git push -u origin main
```

## Build automático

O arquivo `.github/workflows/build-apk.yml` gera `app-debug.apk` a cada push para `main` e também manualmente em `workflow_dispatch`.
