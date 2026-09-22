# Arquitetura — etapa 6

## Componentes implementados

| Componente | Responsabilidade | Dono do estado |
| --- | --- | --- |
| `MetronomeConfig` | Validação de BPM, compasso e subdivisão | Imutável |
| `BeatSchedule` | Posições absolutas dos cliques e mudanças no início de compasso | Thread de saída |
| `ClickBank` | Três variantes sintetizadas de um clique de 10 ms | Preparação; somente leitura depois |
| `PcmMetronomeRenderer` | Mistura em PCM e rampa de ganho de 5 ms | Thread de saída |
| `PcmWriter` | Entrega completa, incluindo escritas parciais e cancelamento | Thread de saída |
| `MetronomeEngine` | AudioTrack, negociação, foco, rota, recursos e diagnóstico | Fachada sincronizada; worker de áudio |
| `AudioLevelMeter` | Pico, RMS, dBFS e clipping de blocos PCM sem alocação | Chamador |
| `AudioInputEngine` | AudioRecord, negociação de entrada, callbacks de rota/silenciamento, timestamps e liberação | Fachada; worker `Ritmics-AudioInput` |
| `BeatDetector` | Pré-processamento PCM, piso de ruído, transientes e supressão de duplicatas | Worker de captura |
| `InterferenceProbe` | Ensaio de silêncio/clique/cauda e contagem de candidatos contaminantes | Domínio; UI apenas conduz fases |
| `MonotonicClockMapper` | Mapeia frames para `System.nanoTime`, ajusta taxa e rejeita timestamps inválidos | Worker de áudio |
| `LatencyCalibration` | Mediana, MAD, rejeição de outliers e confiança do ensaio acústico | Java puro; thread principal fora do caminho crítico |
| `CalibrationProfile` | Contrato imutável da compensação e convenção de sinal | Java puro |
| `AudioRouteInfo` | Identidade de entrada/saída, taxa, fonte e restrição Bluetooth | Thread principal |
| `CalibrationRepository` | Perfis locais por rota em `SharedPreferences` | Thread principal |
| `MetronomeActivity` | Controles XML e diagnóstico | Thread principal |

Fluxo de saída: controles → configuração/comandos → renderizador → frames PCM → AudioTrack.
Fluxo de entrada: permissão contextual → AudioRecord → blocos PCM reutilizados → medição
de nível → `BeatDetector` → candidatos com frame e timestamp monotônico. O detector
não conhece BPM, não associa notas e não atribui avaliação musical.

## Concorrência

- Um único worker `Ritmics-AudioOutput`, com prioridade de áudio.
- Writes bloqueantes, orientados pela capacidade do AudioTrack, sem Timer/Handler
  ou busy-wait para tocar cada clique.
- Buffers e timbres alocados antes de `play()`.
- BPM, ganho e silêncio usam valores voláteis: a atualização mais recente substitui
  a anterior. Não existe uma fila ilimitada de movimentos do slider.
- Geração e writes não adquirem locks da UI.
- O lock curto do track protege publicação, início, pausa e liberação; nunca é
  mantido durante write bloqueante.
- Cancelamento pausa o sink nativo para liberar eventual write; somente o worker
  faz flush/release. Um novo start é recusado até a limpeza anterior terminar.
- Snapshots são criados ao serem consultados pela UI, não por amostra.

## Captura de entrada

`AudioInputEngine` tenta PCM mono de 16 bits em 48 kHz, 44,1 kHz e 16 kHz. A fonte
`UNPROCESSED` só é tentada quando o aparelho anuncia suporte; depois usa
`VOICE_RECOGNITION` e `MIC` como fallback. O buffer é pelo menos duas vezes o mínimo
do dispositivo e quatro blocos de aproximadamente 20 ms. Isso não é garantia de
latência ou resposta plana.

O worker entrega blocos ao listener de forma síncrona; a matriz é reutilizada e não
pode ser guardada. O timestamp preferencial é derivado de `AudioRecord.getTimestamp`
com `TIMEBASE_MONOTONIC`. Uma janela robusta de observações estima taxa e rejeita
saltos, duplicatas e timestamps fora da idade válida. Se o hardware não fornecer um
timestamp válido, o bloco mantém índice de amostra e marca um instante estimado por
`System.nanoTime`, explicitamente com precisão acústica desconhecida.

O nível instantâneo é informativo: pico e RMS são calculados no worker e expostos no
snapshot. Clipping e `isClientSilenced()` são mostrados ao usuário, mas não geram
avaliação. O áudio não é salvo nem enviado para fora do aparelho.

## Configuração de saída

Primeiro tenta a taxa anunciada pelo AudioManager, depois 48 kHz e 44,1 kHz.
PCM mono de 16 bits. Solicita baixa latência; tenta modo normal se necessário.
O tamanho inicial respeita `getMinBufferSize`, pelo menos quatro bursts informados
e aproximadamente 40 ms. O buffer é preenchido antes de iniciar. O primeiro clique
fica depois de uma antecedência silenciosa de pelo menos 50 ms.

Tamanho do buffer não é uma medição da latência total. O diagnóstico exibe o modo
de desempenho informado, sem garantir que a rota física seja rápida.

## Interrupções

Perda de foco (incluindo duck), saída da tela, rotação e mudança de rota interrompem
a reprodução. Ganho posterior de foco nunca inicia áudio. Um underrun observado
após o início encerra o fluxo com mensagem; o app não tenta manter uma avaliação
inexistente nem esconde o problema deslocando os cliques.

## Calibração

A calibração de ruído captura três segundos após o detector estabilizar, calcula o piso
médio e sugere sensibilidade conservadora. Ela não produz offset temporal.

O ensaio acústico usa 120 BPM, saída audível e o microfone no mesmo aparelho. Cada onset é
comparado ao frame esperado do clique mapeado ao relógio monotônico. O estimador exige ao
menos oito amostras, usa mediana e MAD e rejeita duplicatas, atrasos fora de 3–400 ms e
outliers. O resultado mede o caminho completo; não é rotulado como latência pura de hardware.
Bluetooth é recusado para calibração de precisão.

Perfis incluem rota, taxas, fonte, atraso acústico, dispersão, qualidade dos timestamps,
ruído, sensibilidade e ajuste manual. A convenção é `corrigido = captura − atraso acústico
+ ajuste manual`. A etapa 7 será a primeira a aplicar esse valor, uma única vez.

## Fronteira da entrega

O detector é deliberadamente candidato: fala, TV, música, batidas de mesa e o próprio
clique podem gerar eventos. O ensaio de interferência não cancela eco e não prova
ausência de falsos positivos; ele apenas revela contaminação provável na rota atual.
Não há ainda associação às notas, avaliação ou estatísticas de treino. O ensaio precisa
ser validado em celulares e rotas reais antes de definir tolerâncias da etapa 7.

Documentação oficial consultada:

- [AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)
- [Foco de áudio](https://developer.android.com/media/optimize/audio-focus)
- [Compatibilidade do AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
