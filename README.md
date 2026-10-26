# MirageLab Helper

**Versão:** 10.0.3 · **Plataforma:** Windows 64 bits · **Cliente de referência:** Mirage Realms Early Access 0.8.59 · **Java:** 21

O **MirageLab Helper** é um projeto independente de automação e assistência para *Mirage Realms*. Foi desenvolvido em Java, com um iniciador nativo para Windows, e disponibilizado para que outros desenvolvedores possam estudar a arquitetura, a interação com um cliente de jogo e a lógica de decisões em tempo real.

> **Aviso:** este projeto não é oficial nem afiliado aos desenvolvedores de Mirage Realms. Automação pode contrariar as regras do jogo ou de seus servidores. Consulte essas regras antes de usar. O MirageLab **não inclui o cliente do jogo** nem seu arquivo proprietário `Obfuscated.jar`.

---

## 1. Como funciona

O MirageLab é composto por duas partes:

- **`MirageLab.exe`** — iniciador portátil do Windows. Localiza o jogo e inicia o cliente com o agente Java; não contém o motor de automação sozinho.
- **`MirageLabAgent.jar`** — agente Java que observa dados disponíveis no cliente, executa as regras dos módulos e apresenta os painéis do Helper.

A ideia central é **usar, sempre que possível, os dados internos que o próprio jogo disponibiliza**, em vez de depender do reconhecimento visual de imagens na tela. A integração foi desenvolvida para as estruturas identificadas no cliente **0.8.59**; atualizações do jogo podem exigir adaptações.

### Funcionalidades

| Módulo | Função |
| --- | --- |
| **Auto Attack** | Encontra criaturas ao redor e escolhe um alvo conforme a prioridade configurada: mais próximo, mais distante, menor HP absoluto ou menor HP percentual. |
| **FOCO e Retarget** | Evita abandonar alvos sem motivo confiável e reavalia mudanças de alvo quando existem dados suficientes. |
| **Chase** | Aproxima o personagem do alvo, com navegação A* de curto alcance, tentativa de contornar obstáculos e limite de perseguição. |
| **Anti-KS e Grupo** | Aplica regras de proteção de alvo e permite considerar membros do grupo. Não identifica automaticamente as intenções de outros jogadores. |
| **Auto Heal** | Consulta HP/mana e aciona as teclas configuradas para consumíveis quando os limites são atingidos. Usa proteção contra repetições excessivas. |
| **AutoFood** | Acompanha o indicador interno de **Nourishment** e utiliza a tecla configurada para alimento, quando o sensor é confiável. |
| **Auto Skills / Mana Training** | Executa teclas previamente configuradas respeitando intervalos e cooldowns. |
| **CaveBot** | Executa rotas gravadas, reage a combates e tenta retomar o percurso; usa caminhos alternativos e pode pausar diante de bloqueios repetidos. |
| **AutoLoot** | Trabalha com o fluxo de bolsas e coleta do cliente, incluindo a solicitação de abertura da bolsa. **Não é um saque instantâneo independente do servidor.** |
| **Segurança** | Alerta sobre outros jogadores detectados nas proximidades. Presença próxima **não significa, por si só, PK ou KS**. |
| **Analyser** | Exibe duração da sessão, XP obtida, XP/h, eventos de XP, corpos observados, alimentação e alertas. Possui botão **RESET**. |

### Interface

O Helper oferece um **overlay** sobre o jogo, que pode ser recolhido ao pequeno botão **ML**. O painel principal fica dividido em:

- **COMBATE:** Auto Attack, alcance, prioridade, Retarget, FOCO, Anti-KS e Chase; inclui os presets **SÓ ATAQUE** e **ASSISTIDO**.
- **SUPORTE:** cura de HP e mana, hotkeys e limites; informações de alimentação e suporte.
- **STATUS:** dados observados, estado dos módulos e indicadores de diagnóstico.

Na parte inferior do overlay ficam acessos rápidos para **XP/H** (Analyser), **GRUPO** e **AJUSTES**. O botão **PARAR TUDO** desativa os módulos automatizados e solicita a parada do CaveBot e da reprodução de rotas; ele **não fecha o jogo**.

O **Analyser** é uma janela separada, semitransparente e arrastável. O botão **RESET** inicia uma nova medição da sessão, sem apagar suas configurações.

**Limitações atuais importantes:**

- A taxa **XP/h** é calculada a partir dos eventos de experiência recebidos do cliente; até o primeiro evento válido, pode aparecer como indisponível.
- **Corpos observados** não equivalem necessariamente a monstros mortos pelo personagem.
- A previsão de **tempo até a próxima skill** ainda não possui mapeamento validado; o campo permanece indisponível.
- AutoFood usa uma **hotkey configurada**: o consumo seletivo de alimentos diretamente do inventário ainda não foi implementado.
- AutoLoot depende das ações e respostas normais do cliente/servidor. Coleta seletiva inteiramente baseada nos itens recebidos não está concluída.
- Sensores indisponíveis, alterações na versão do cliente, terrenos perigosos ou obstáculos não mapeados podem limitar a automação. **Não trate o Helper como garantia de segurança para sessões AFK.**

---

## 2. Como instalar e configurar o Helper

### Requisitos

- **Windows 64 bits**.
- **Java 21**, com `javaw.exe` acessível por `JAVA_HOME` ou pelo `PATH` do Windows.
- Uma instalação legítima do **Mirage Realms 0.8.59** ou acesso local ao **seu próprio** arquivo original `Obfuscated.jar`.

### Instalação portátil

1. Baixe o ZIP da versão **V10.0.3** na área de **Releases** deste repositório.
2. **Extraia o ZIP inteiro** para uma pasta de sua preferência.
3. Confira se os quatro arquivos estão juntos:

   ```text
   MirageLab/
   ├── MirageLab.exe
   ├── MirageLabAgent.jar
   ├── MirageLab_Helper.vbs
   └── README.md
   ```

4. Dê dois cliques em **`MirageLab.exe`**. Não é necessário executar um instalador nem criar atalhos na Área de Trabalho.
5. O iniciador procura `Obfuscated.jar` automaticamente nesta ordem:
   - Na pasta do Helper e em subpastas reconhecidas (`Mirage Realms` / `MirageRealms`);
   - No último caminho válido salvo em `Downloads\MirageLab\client_path.txt`;
   - Nas instalações do Steam e em suas bibliotecas adicionais identificadas.
6. Ao encontrar o cliente, ele inicia o jogo com o agente. **Não é necessário selecionar uma pasta** quando a detecção funciona.

Se a busca automática falhar, verifique a instalação do jogo. Também é possível colocar **uma cópia obtida da sua própria instalação** de `Obfuscated.jar` ao lado do `MirageLab.exe`, desde que você tenha direito de utilizá-la. O jogo não é distribuído junto com o projeto.

> **Importante:** não execute o `.exe` de dentro da visualização do ZIP. Mantenha `.exe`, `.jar` e `.vbs` na mesma pasta. A V10.0.3 não cria nem remove atalhos.

### Configuração recomendada para o primeiro uso

1. Abra o overlay pelo botão **ML**.
2. Entre em **COMBATE** e selecione **SÓ ATAQUE** para testar o comportamento com o menor número possível de módulos ativos.
3. Ajuste o **alcance** e escolha a prioridade desejada. Para menor HP, prefira verificar antes, em **STATUS**, se os valores de vida estão realmente sendo recebidos.
4. Mantenha **FOCO** ligado se quiser reduzir trocas desnecessárias. Ative **Retarget** para permitir reavaliação dos alvos.
5. Ative **Chase** somente se desejar aproximação automática. Ele foi pensado para trajetos curtos e não é garantia contra terreno perigoso.
6. Em **SUPORTE**, configure a tecla da poção de HP e, caso use, a de mana. Confira os limites antes de ligar **Auto Heal**.
7. Em **AJUSTES**, configure as skills, o CaveBot e a seção **Loot & Food**. Associe a hotkey de alimento ao slot realmente utilizado no jogo.
8. Abra **XP/H** para acompanhar a sessão e pressione **RESET** quando desejar começar uma medição nova.

**Sugestão de teste:** valide **Auto Attack**, depois **Chase**, depois **Auto Heal** e **AutoFood** separadamente, com o jogo sob supervisão. Evite ativar todos os módulos pela primeira vez ao mesmo tempo.

### Onde ficam os dados

O Helper guarda configurações e arquivos de execução na pasta do usuário:

```text
%USERPROFILE%\Downloads\MirageLab\
├── client_path.txt             # último diretório do cliente identificado
├── state\                      # opções, configurações e rotas
├── routes\                     # dados de rotas, quando aplicável
└── runtime\                    # diagnósticos e logs
    ├── launcher.log
    ├── launcher_previous.log
    ├── agent_status.txt
    └── hs_err_pid*.log          # apenas se a JVM gerar relatório de falha
```

A localização pode variar em detalhes conforme os arquivos gerados pela sessão. **O botão RESET do Analyser não apaga arquivos de configuração nem rotas.**

### Se algo não funcionar

| Sintoma | O que verificar |
| --- | --- |
| O jogo não inicia | Java 21, presença dos três arquivos operacionais e instalação válida do jogo. |
| `Obfuscated.jar` não é encontrado | Caminho do Steam, arquivo original ao lado do EXE ou último caminho salvo. |
| Overlay não aparece | Estado do botão ML, opção de overlay e mensagens em `agent_status.txt`. |
| HP aparece como `?` | Versão do cliente e disponibilidade do componente ECS; não presuma que a prioridade por HP está ativa de fato. |
| Cura ou alimento não funcionam | Sensor válido, módulo ativado, limiar configurado, hotkey correta e bloqueios de segurança. |
| O jogo fecha inesperadamente | Confira `runtime\launcher.log`, `launcher_previous.log` e, se existir, `hs_err_pid*.log`. |
| XP/h não aparece | Aguarde um evento real de XP; o painel não inventa ganhos ausentes. |

O comportamento foi testado principalmente por compilação, testes automatizados e simulação. **Isso não substitui uma validação prolongada no cliente Windows em cada computador.**

---

## 3. Lógica utilizada — explicação para desenvolvedores

Esta seção descreve a arquitetura presente no código-fonte, para fins de estudo. O objetivo não é demonstrar um algoritmo genérico de “apertar botões”, mas como **observar estados, tomar decisões, executar ações e validar o resultado**.

### 3.1. Inicialização e ligação com o jogo

```text
MirageLab.exe
    ↓
MirageLab_Helper.vbs  →  localização automática do jogo e Java 21
    ↓
DesktopLauncher + -javaagent:MirageLabAgent.jar
    ↓
LabAgent.java / LabHook.java
    ↓
Leitura dos componentes e eventos  →  módulos  →  overlay / Analyser
```

- `launcher/MirageLabLauncher.c` contém o iniciador Windows portátil.
- `MirageLab_Helper.vbs` verifica os arquivos, resolve o caminho do cliente e monta o comando Java. A busca evita uma varredura indiscriminada de todo o computador e utiliza diretórios conhecidos e metadados do Steam.
- `LabAgent.java` utiliza a API `java.lang.instrument` e transformações de bytecode para inserir pontos controlados de observação no cliente.
- `LabHook.java` centraliza a comunicação entre os pontos instrumentados e os módulos do Helper.

Na V10, o tratamento de XP foi corrigido para passar por uma **ponte pública e protegida** no `LabHook`: uma chamada direta anterior a uma classe interna de outro pacote podia produzir `IllegalAccessError` e interromper o jogo. As chamadas instrumentadas agora possuem proteção contra exceções, embora falhas de terceiros e da JVM ainda possam ocorrer.

### 3.2. Leitura direta dos dados — componentes ECS

O Mirage Realms 0.8.59 utiliza componentes associados às entidades. O MirageLab mapeia campos específicos dessa versão por meio de:

- `VitalsAdapter0859.java` — mapeamento dos atributos conhecidos do cliente 0.8.59;
- `GameDirectVitals.java` — acesso e validação dos valores obtidos;
- `AutoAttackEngine.java` — leitura das entidades próximas para escolha de alvos;
- `AutoHealEngine.java` e `AutoFoodEngine.java` — interpretação de recursos do personagem.

O fluxo conceitual de leitura é:

```text
Entidade → Componente de atributos → HP atual / HP máximo
                                         ↓
                                percentual de HP
                                         ↓
                            decisão do módulo de combate
```

A prioridade percentual usa a relação `HP atual / HP máximo × 100`. Quando não é possível confirmar os dois valores, **o resultado não deve ser tratado como um percentual válido**. A mesma regra se aplica às leituras de HP/mana usadas para cura e ao indicador de Nourishment.

**Por que mapear campos diretamente?** Versões anteriores tentavam identificar HP por barras coloridas e por variações estatísticas de campos numéricos. Essas técnicas geravam confusões com coordenadas e elementos de interface. A V10 utiliza mapeamentos direcionados ao cliente estudado. Como os nomes internos são ofuscados, **não há garantia de compatibilidade com versões futuras**.

### 3.3. Seleção de alvos, FOCO e Retarget

A seleção envolve filtros sucessivos, e não uma comparação isolada de distância:

1. Identificar o personagem e as entidades ao redor dentro do raio configurado.
2. Descartar entidades inválidas, fora do alcance ou excluídas pelas regras do usuário.
3. Aplicar, quando disponíveis, as regras de Anti-KS e as exceções do grupo.
4. Ordenar os candidatos por distância ou por HP, conforme o modo ativo.
5. Comparar a vantagem do novo candidato em relação ao alvo atual.
6. Respeitar confirmações, períodos de proteção do FOCO e cooldowns de troca.
7. Enviar a ação de seleção e continuar observando o estado do jogo.

Arquivos principais: `AutoAttackEngine.java`, `CombatFocusPolicy.java`, `TargetPriority.java` e `PartySupport.java`.

**Exemplo:** se a prioridade é “Menor HP (%)”, uma criatura com 15% pode ter preferência sobre outra com 60%, desde que as leituras sejam válidas e a política de troca permita a mudança. Se o HP estiver desconhecido, o FOCO reduz a chance de abandonar o alvo apenas porque outro se aproximou.

### 3.4. Chase e CaveBot: planejamento de movimento

A movimentação utiliza um **A\*** para encontrar um caminho entre a posição atual e uma casa alcançável próxima ao objetivo. O custo de movimento e os obstáculos conhecidos orientam a busca. O Chase foi adaptado para perseguições curtas:

```text
Alvo definido
    ↓
Estamos na distância desejada?
    ├── Sim → continuar combate
    └── Não → consultar obstáculos conhecidos
                ↓
              calcular rota A*
                ↓
              executar um passo
                ↓
              confirmar deslocamento
                ├── Confirmado → atualizar posição e repetir
                └── Bloqueado → memorizar obstáculo e recalcular
```

`ChaseSupport.java` usa `Pathfinder.java` e informações de obstáculos fornecidas por `CaveBotEngine.java`. Para evitar perseguições intermináveis, há restrições de alcance, quantidade de nós e tentativas. Quando uma casa não responde ao movimento, ela pode ser marcada temporariamente como bloqueada.

**Exclusividade de movimento:** Chase não deve competir pelo controle com o CaveBot quando este está ativo. O CaveBot, por sua vez, gerencia estados de rota, combate, saque e retorno, podendo pausar diante de repetidos bloqueios. Nem todos os obstáculos estáticos são representados por entidades acessíveis; por isso, o algoritmo não garante evitar lava, armadilhas ou caminhos perigosos.

### 3.5. Cura, alimentação e ações de teclado

O Auto Heal usa uma política simples de decisão e confirmação:

```text
Ler HP/mana válidos
    ↓
Recurso abaixo do limite?
    ├── Não → nenhuma ação
    └── Sim → cooldown e proteção permitem uso?
                ├── Não → aguardar
                └── Sim → pressionar hotkey
                            ↓
                       observar recuperação
                            ↓
                       bloquear repetição se falhar
```

`AutoHealEngine.java`, `HealSafety.java` e `HealPolicy.java` cuidam das condições de uso. O AutoFood utiliza `AutoFoodEngine.java` e `AutoFoodSupport.java` para acompanhar Nourishment, aplicar um intervalo mínimo e verificar a resposta após a hotkey.

`InputArbiter.java` administra a emissão de teclas para diminuir conflitos entre módulos, enquanto `SkillEngine.java` e `ManaTrainingEngine.java` respeitam seus próprios intervalos. **Ler o inventário e escolher automaticamente um alimento específico ainda não faz parte do comportamento concluído.**

### 3.6. AutoLoot e interação com o servidor

O AutoLoot utiliza o fluxo já existente de corpos, bolsas e coleta. O cliente pode solicitar a abertura da bolsa por uma ação interna sem simular um clique no objeto. Ainda assim, a lista de itens e a confirmação do saque dependem dos eventos e permissões do servidor.

`CaveBotEngine.java` e `AutoLootSupport.java` concentram a maior parte dessa lógica. **Não há implementação confirmada de coleta seletiva instantânea sem a sequência de abertura e resposta do jogo.**

### 3.7. Analyser e segurança

O Analyser contabiliza deltas de experiência recebidos por eventos válidos do próprio cliente. A taxa é calculada a partir do tempo transcorrido desde a última reinicialização da sessão:

```text
XP/h = (XP recebida desde o RESET / tempo decorrido em ms) × 3.600.000
```

Arquivos relacionados:

- `AnalyzerEngine.java` — contadores e cálculo das taxas;
- `AnalyzerWindow.java` — janela semitransparente e botão RESET;
- `SecurityMonitor.java` — detecção de jogadores próximos;
- `ResourceMonitor.java` — resumo de recursos disponíveis.

O Analyser **não transforma corpos observados em mortes confirmadas**. O monitor de segurança também **não declara que um jogador próximo é PK ou está dando KS**: ele apenas informa proximidade quando consegue identificá-la.

### 3.8. Configurações, arquivos e compilação

`HelperConfig.java` mantém as opções do Helper em `state/helper_config.txt`. `SafeStateStore.java` utiliza gravação mais segura para evitar corromper arquivos em substituições. Os dados de rotas e diagnósticos permanecem separados do executável portátil.

No pacote de **código-fonte** estão disponíveis `src/`, `tests/`, `launcher/` e scripts de compilação. Para desenvolvedores com as ferramentas apropriadas:

```bash
# Compilação do agente Java (JDK 21)
bash build.sh

# Compilação do iniciador Windows x64 (Clang/LLD compatíveis)
bash launcher/build_windows_exe.sh
```

Os testes automatizados cobrem decisões de alvo, regras de proteção, tratamento de recursos, persistência, inicialização do agente, navegação e detecção de caminhos. **Passar nos testes não garante compatibilidade com outros clientes, sistemas ou servidores.**

### Direitos e contribuição

O código do MirageLab é um projeto educacional independente. O `Obfuscated.jar`, as imagens, os sons, o nome do jogo e outros recursos proprietários **não fazem parte da distribuição**. Antes de escolher uma licença de código aberto, verifique os direitos de todos os componentes que pretende publicar. Quem contribuir deve documentar a versão do cliente utilizada, incluir testes e distinguir funcionalidades confirmadas de recursos experimentais.

---

*MirageLab Helper V10.0.3 — documentação para uso e estudo da arquitetura. Não é um produto oficial de Mirage Realms.*
