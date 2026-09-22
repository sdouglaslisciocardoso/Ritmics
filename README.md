# Ritmics — etapa 6

Aplicativo Android nativo em **Java e XML**, com um metrônomo técnico funcional.

## Escopo desta entrega

- Projeto Android Studio com módulos `app` e `core`.
- Gradle Wrapper versionado e distribuição protegida por SHA-256.
- Saída PCM contínua por `AudioTrack`, em uma thread de áudio dedicada.
- Captura PCM mono por `AudioRecord`, em uma thread de captura dedicada.
- Andamento inteiro de **30 a 240 BPM**, com entrada numérica, slider e botões.
- Compassos **2/4, 3/4, 4/4 e 6/8** (em 6/8 o BPM conta colcheias), acento por tempo e
  subdivisões de uma a quatro notas por tempo (semínimas, colcheias, tercinas, semicolcheias).
- Iniciar/parar, volume e silêncio mantendo a fase musical.
- Mudança de BPM no próximo início de compasso ainda não renderizado.
- Interrupção ao sair da tela, perder foco ou mudar a rota; sem retomada automática.
- Diagnóstico de taxa, buffers, frames escritos/lidos, eventos, underruns e fonte de entrada.
- Medidor de pico, RMS, dBFS, clipping e sinal silenciado pelo sistema.
- Solicitação contextual de `RECORD_AUDIO`, modo visual sem microfone e acesso às configurações quando a permissão é bloqueada.
- Timestamps de bloco por índice de amostra, `AudioTimestamp` monotônico quando válido e fallback explicitamente estimado.
- Detector de transientes PCM em tempo real, com filtro passa-altas leve, limiar adaptativo,
  janela refratária e sensibilidade ajustável.
- Prova guiada de interferência do próprio clique, com fases de silêncio, clique e cauda,
  resultado inconclusivo para clipping/silenciamento/volume desligado e recomendação de fones.
- Calibração de ruído com sensibilidade sugerida, medição acústica robusta com rejeição de
  outliers e ajuste manual separado.
- Perfis locais por rota de entrada/saída, taxa e fonte, com confiança e dispersão explícitas.
- Testes do agendamento, geração PCM, escritas parciais, cancelamento e medição de nível.
- Testes determinísticos do detector, do ensaio de interferência e do mapeamento robusto de relógio.

**Ainda não faz parte desta versão:** associação das detecções às notas, avaliação musical,
pista de notas, sessões com duração, histórico ou preferências musicais permanentes.
O manifesto não pede internet ou armazenamento. A tela segue o protótipo visual de treino (tema escuro);
o modo **Gravado** aparece como “Em breve” e não simula funções futuras. Volume, microfone, sensibilidade,
teste de interferência e diagnóstico ficam no painel de configurações (engrenagem).

## Abrir e compilar

Requisitos fixados:

| Ferramenta | Versão |
| --- | --- |
| JDK | 17 |
| Android Gradle Plugin | 9.4.1 |
| Gradle Wrapper | 9.6.0 |
| Android SDK de compilação | API 37, plataforma 37.0 |
| Target SDK | 36 |
| Android mínimo | 8.0 / API 26 |
| SDK Build Tools | 36.0.0, padrão do AGP |

1. No Android Studio compatível com AGP 9.4, abra a pasta raiz que contém `settings.gradle`.
2. Configure o Gradle JDK para JDK 17 e instale a plataforma SDK 37 pelo SDK Manager.
3. Deixe o Android Studio criar `local.properties` com `sdk.dir` do seu SDK.
4. Aguarde a sincronização e execute a configuração `app` em um aparelho Android 8 ou superior.

O projeto também foi validado com o JDK embutido do Android Studio (JBR 25). No Windows, se o
caminho da pasta tiver acentos (por exemplo `Programação`), use um JDK 18 ou superior: o
`-Dfile.encoding=COMPAT` do `gradle.properties` só é reconhecido a partir dele e evita que os
testes do `core` falhem com `ClassNotFoundException`.

A primeira compilação precisa de acesso aos repositórios Google Maven, Maven Central
e à distribuição oficial do Gradle. O **aplicativo instalado funciona offline**.

No PowerShell, com `JAVA_HOME` e o SDK configurados:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

Em macOS/Linux:

```sh
sh ./gradlew :core:test :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Relatórios:

- Unitários: `core/build/reports/tests/test/index.html`.
- Lint: `app/build/reports/lint-results-debug.html`.

Os testes de interface podem ser compilados separadamente:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```

Com celular conectado, depuração USB autorizada ou emulador iniciado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

**Nesta máquina:** as ferramentas portáteis foram preparadas em `.tools/`, ignorada
pelo Git. Para usá-las em um novo terminal nesta pasta:

```powershell
$env:JAVA_HOME = Join-Path $PWD '.tools/jdk'
$env:ANDROID_HOME = Join-Path $PWD '.tools/android-sdk'
$env:ANDROID_USER_HOME = Join-Path $PWD '.tools/android-user'
$env:GRADLE_USER_HOME = Join-Path $PWD '.tools/gradle-home'
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

Não copie `local.properties` ou `.tools/` para outro computador como se fossem
requisitos versionados. Em outra máquina, configure seu próprio JDK/SDK.

## Utilização

1. Escolha BPM, compasso e subdivisão. Toque nos tempos da **Acentuação** para
   acentuá-los; ao trocar de compasso os acentos voltam ao padrão (1, ou 1 e 4 em 6/8).
2. Ajuste um volume confortável na engrenagem e pressione **Iniciar Treino**.
3. Digite o BPM e confirme no teclado, ou use o slider ou os botões. Durante a
   reprodução a alteração fica pendente até um início de compasso disponível.
4. Na engrenagem, ative **Capturar pelo microfone** somente se quiser medir o sinal de
   entrada; o feedback ao vivo aparece na tela principal. A
   permissão aparece ao iniciar, nunca na abertura do aplicativo. O áudio é processado
   em memória e descartado; ainda não há detecção ou avaliação.
5. Em **Calibração de tempo**, calibre primeiro o ruído. Para medir o caminho acústico,
   deixe o ambiente silencioso e não bata enquanto os cliques tocam. Bluetooth é recusado
   para calibração de precisão por ter atraso variável.
6. Silencie os cliques para manter a geração musical sem som. Isso ainda não é um
   exercício de treino visual.
7. Pressione **Parar Treino** antes de alterar compasso, acento ou subdivisão.
8. Ao iniciar novamente, a contagem recomeça no primeiro tempo; não há retomada de
   uma sessão parcialmente tocada nesta etapa.

O volume de mídia do sistema também afeta a saída. Controles são preservados na
recriação da tela, mas a reprodução para. Preferências entre aberturas ficam para
a etapa 10.

## Arquitetura

`core` é Java puro: configuração musical, agenda racional em frames, síntese de
cliques, renderização PCM, contrato de escrita parcial e medição de nível. `app`
adapta isso ao Android e fornece a tela de treino. A UI apenas envia comandos
e lê snapshots a cada 100 ms; esse callback **não dispara cliques nem lê o PCM**.

Detalhes: [arquitetura](docs/architecture.md), [contrato temporal](docs/timing-contract.md),
[roteiro físico](docs/device-test-plan.md), [validação](docs/validation-results.md)
e [próximas etapas](docs/stages.md).

## Limite de precisão nesta entrega

O agendamento evita deriva de **arredondamento acumulado** no fluxo PCM. Isso não
prova ausência de drift do relógio físico, latência, jitter acústico ou efeitos do
sistema. Frames escritos e eventos gerados **não são timestamps de apresentação**.
O mapeamento de captura e saída expõe timestamps monotônicos do sistema quando disponíveis
e uma estimativa identificada quando não estão. O ensaio da etapa 6 mede o caminho completo
entre clique esperado e som capturado; não isola latência de saída, ar, microfone e captura.
O perfil só será aplicado à avaliação na etapa 7. O detector informa eventos candidatos,
mas ainda não atribui notas ou resultados musicais.

## Dependências

- AppCompat 1.7.1: Activity, controles, tema automático e integração com insets.
- JUnit 4.13.2: testes determinísticos do domínio.
- AndroidX Test Runner 1.6.2 e Ext JUnit 1.2.1: smoke tests instrumentados.
- Dependências transitivas são resolvidas por esses artefatos. Não há código Kotlin
  de aplicação, mesmo que o AGP ou o AndroidX tragam ferramentas/bibliotecas Kotlin.

Não há serviços de terceiros em execução no app, NDK, banco de dados ou biblioteca de DSP.
