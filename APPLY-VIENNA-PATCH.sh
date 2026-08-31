#!/usr/bin/env bash
set -euo pipefail
# Execute na raiz do projeto antigo depois de extrair este patch por cima dele.
rm -rf app/src/main/java/online/vaigo
rm -f app/src/main/res/drawable/ic_vaigo_logo.xml
rm -f app/src/main/res/drawable/splash_background.xml
printf 'VIENNA Android rebrand aplicado.\n'
