# MirageLab V10.0.1 — Relatório de correção final

## Escopo

Base: código-fonte V10 distribuído anteriormente, com correção do evento de experiência, wrapper de segurança de callbacks, Chase A* com recorte local de entidades e launcher portátil Windows x64 sem atalhos.

## Falha reproduzida

A V10 original injetava um `INVOKESTATIC` para `miragelab/AnalyzerEngine`, classe com acesso de pacote. A execução no código ofuscado (outro pacote) pode disparar `java.lang.IllegalAccessError`. O evento XP foi reproduzido em teste isolado. O novo bytecode chama `miragelab/LabHook.recordExperienceEvent(int)`, método público que isola exceções. Também há wrappers `safeTick` e `safeObserveAction` nas chamadas de render e ações. **A reprodução demonstra um defeito concreto, mas não prova que todo fechamento observado pelo usuário tenha a mesma causa**.

## Chase

Planejamento A* cardinal em região local; obstáculos dinâmicos ECS; lista temporária de passos recusados por 20 segundos; pausa quando o caminho não existe, quando se excede um limite em relação ao ponto inicial ou após repetidas falhas. Não usa movimentos simultâneos com CaveBot. Objetos de terreno que não são entidades podem precisar de uma tentativa fracassada antes de serem aprendidos. A movimentação ainda exige supervisão em locais perigosos.

## Verificações

- 34/34 suítes Java aprovadas (33 anteriores + suíte V10.0.1).
- Teste de desserialização do comando **real** `ExperienceGainedCommand` do JAR enviado pelo usuário: delta=266, Analyser=266, processo com saída normal. O JAR proprietário foi usado apenas localmente e não é redistribuído.
- 4.000 cenários de labirintos e obstáculos gerados deterministicamente, mais casos fixos de alcance, alvo móvel e falta de caminho (incluídos na suíte nova).
- Inicialização do Java Agent e testes gráficos em display virtual incluídos nas suítes históricas.
- Executável PE32+ x64, interface Windows GUI, importações apenas KERNEL32/SHELL32/USER32.
- Pacote de release com exatamente `MirageLab.exe`, `MirageLabAgent.jar`, `MirageLab_Helper.vbs`, `README.md`; scripts sem criação de atalhos e sem instalador.
- A VBS usa o JAR ao lado do EXE, preserva o log anterior e escreve `hs_err_pid*.log` se a JVM gerar relatório nativo.

## Limites

Não foi possível executar o EXE com Windows Script Host em Windows nativo, nem reproduzir a sessão completa do Mirage Realms conectada ao servidor. Testes automáticos não garantem ausência de outras falhas, e o script de lançamento depende de uma instalação funcional de Java 21. A versão de cliente alvo é 0.8.59.
