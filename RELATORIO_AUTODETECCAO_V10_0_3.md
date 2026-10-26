# MirageLab V10.0.3 — verificação do launcher portátil

Data: 26/10/2026.

## Alterações

- Elimina `Shell.Application.BrowseForFolder` do fluxo normal e da saída de erro.
- Encontra `Obfuscated.jar` no diretório de `MirageLab.exe`, em `Mirage Realms`/`MirageRealms` logo abaixo dele, no `client_path.txt` persistente ou em instalações Steam.
- Steam: consulta instalações usuais, chaves de Registro `Valve\Steam`, `libraryfolders.vdf` e manifestos `appmanifest_*.acf` pertinentes.
- Valida a existência de `Obfuscated.jar` e rejeita arquivos abaixo de 1 MiB.
- Salva a pasta selecionada automaticamente em `client_path.txt`, sem alterar rotas e configurações.
- Não inclui o cliente proprietário no release. Quando não houver cliente válido, mostra aviso claro, em vez do seletor de pastas.
- `MirageLab.exe` e `MirageLabAgent.jar` mantêm os bytes da V10.0.2.

## Testes

- 5.188 verificações de consistência do script e cenários modelados (incluindo precedência de pastas, configurações existentes, Steam, ausência de cliente, aspas no VBScript e cabeçalhos de EXE Windows x64).
- Java 21 + JavaAgent original: carregamento em tela virtual `Xvfb`, seguido por classe de teste com `System.exit(0)` e retorno 0.
- Integridade de ZIP, igualdade de binários com V10.0.2 e ausência do arquivo proprietário nos pacotes.

## Limitações

A interpretação real do VBScript pelo Windows Script Host, a busca pela instalação do Steam real e a execução completa no Windows não foram testadas neste ambiente Linux. Os casos de detecção são verificações estruturais e simulações do fluxo; não garantem que qualquer instalação fora dos caminhos reconhecidos seja encontrada. Requer Windows x64, Java 21 e jogo 0.8.59 já instalado ou JAR original obtido legitimamente.
