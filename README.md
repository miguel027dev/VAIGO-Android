# VANO MAPS Android 2.1

Android WebView otimizado para `https://vanomaps.online`.

## Principais mudanças
- domínio de produção VANO MAPS por padrão;
- mantém `applicationId = app.vienna.navigation` para preservar compatibilidade com a publicação existente;
- splash nativo leve em vetor, sem banners PNG pesados;
- AdMob inicializado sob demanda, fora do caminho crítico de startup;
- WebView com aceleração por hardware, cache HTTP padrão, DOM Storage e GPS;
- R8 + shrinkResources no release;
- ponte JavaScript nova `VanoNative`, mantendo `ViennaNative` como alias temporário;
- modo Black sincronizado sem darkening automático do WebView;
- rede HTTPS restrita ao domínio de produção;
- workflow de CI apontando para `vanomaps.online`.

## Configuração
`VANO_BASE_URL=https://vanomaps.online`

O retorno Google permanece `vienna://auth/callback` por compatibilidade. Só altere quando o backend e o OAuth estiverem migrados juntos.

## Build
```bash
gradle --no-daemon clean assembleDebug
```

Para release, configure sua chave de assinatura antes de publicar na Play Store.
