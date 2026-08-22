# Google OAuth no APK

O Google não deve ser autenticado diretamente dentro de um WebView embutido. O VAIGO Android usa o navegador do aparelho e um handoff de sessão de uso único.

## Segurança do handoff

- O APK gera um `state` e um verificador PKCE aleatórios de 256 bits.
- O servidor recebe apenas o challenge PKCE e salva somente hashes do state/código.
- O código de retorno expira por padrão em 5 minutos.
- O código só pode ser usado uma vez.
- O token do Google não é enviado ao APK nem aparece na URL.
- O APK valida o `state` antes de aceitar o retorno.
- O cookie persistente final continua revogável pela tabela `auth_sessions` do VAIGO.
