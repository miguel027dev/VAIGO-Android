# Rebranding VIENNA — Android v2.0

Aplicado no projeto Android:

- app name e labels: VIENNA
- package + applicationId: `app.vienna.navigation`
- custom scheme: `vienna://auth/callback`
- ícones launcher para todas as densidades
- adaptive icon (Android 8+) e monochrome themed icon (Android 13+)
- splash Android 12+ com o ícone oficial
- loading overlay com banner VIENNA em alta resolução
- loading claro/Black com paleta oficial
- sincronização do Black mode do site com o splash nativo da próxima abertura
- User-Agent VIENNA
- offline screen VIENNA
- workflow GitHub e artifact renomeados
- documentação e backend patch atualizados

## Host do backend

O nome VAIGO não existe mais na identidade do APK. O único texto legado preservado é o hostname atual `vaigo.online`, usado como endpoint de infraestrutura para o aplicativo continuar funcionando enquanto o domínio do backend não for migrado. Ele está atrás da configuração `VIENNA_BASE_URL` e pode ser trocado sem alterar o código.

## Importante sobre Play Store

O `applicationId` foi alterado para `app.vienna.navigation`. Isso é o rebranding mais completo para um app ainda não publicado. Se o antigo APK já estiver publicado na Play Store e a intenção for lançar a VIENNA como atualização do mesmo app, o `applicationId` precisa permanecer exatamente igual ao ID publicado anteriormente.
