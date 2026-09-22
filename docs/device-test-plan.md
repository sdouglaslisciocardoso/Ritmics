# Roteiro em aparelho — etapas 1 a 6

Este roteiro fecha o **ponto de decisão após a etapa 5**: confirmar, em celular real, se
reprodução, captura, timestamps e detector sustentam a avaliação nas rotas recomendadas.
Registre cada execução em [validation-results.md](validation-results.md).

Nenhum item é aprovado só porque o APK compilou ou porque passou no emulador: o áudio do
emulador tem atraso e cortes próprios e não serve como evidência de tempo.

## Preparação

- Celular com Android 8 ou superior, **Depuração USB** ativada e autorizada.
- Instalar a versão atual: `.\gradlew.bat :app:installDebug` (ou **Run** no Android Studio).
- Rotas a comparar, nesta ordem: fone com fio (P2 ou USB-C), fone/adaptador USB,
  alto-falante do aparelho, Bluetooth (esperado: não recomendado para medir tempo).
- Anotar em cada execução: modelo, versão/build do Android, rota, volume de mídia,
  **Volume do clique**, **Sensibilidade** e se o clique estava silenciado.
- Para a medição de tempo (seção F): um segundo aparelho ou PC gravando o som, por exemplo
  com o Audacity, e um objeto para bater (lápis na mesa) ou palmas.

O app abre com o clique **silenciado**. Para ouvir, toque na engrenagem e desligue
**Silenciar cliques**. Os controles técnicos (volume, microfone, sensibilidade, teste de
interferência e diagnóstico) ficam todos nesse painel.

## A. Abertura, permissões e ciclo de vida

1. Abrir o app: não pode pedir microfone nem internet na abertura.
2. Na engrenagem, ligar **Capturar pelo microfone**, tocar **Iniciar Treino** e conceder a
   permissão. O cartão **Feedback ao vivo** deve mostrar a captura e o medidor deve reagir
   a voz e palmas. A permissão não deve ser pedida de novo enquanto estiver concedida.
3. Recusar a permissão na primeira vez: mensagem contextual, e o metrônomo continua usável
   com o microfone desligado. Recusar de forma permanente: o botão **Abrir configurações do
   microfone** aparece no cartão Feedback ao vivo.
4. Microfone ocupado por outro app, microfone bloqueado pelo sistema (atalho de privacidade
   do Android 12+), sinal saturado com uma fonte alta perto do microfone e troca de rota
   durante a captura: cada caso precisa de mensagem clara e sem travamento.
5. Sair para a tela inicial, apagar a tela e girar o aparelho: o som para e não volta
   sozinho. BPM, compasso, subdivisão e acentos são preservados na rotação.
6. Perda de foco com outro player ou uma chamada: o som para.
7. Desconectar o fone ou trocar a saída: o som para e não passa a tocar no alto-falante.
8. Ao parar ou sair, o indicador de microfone do Android deve sumir.

## B. Metrônomo

9. Tocar a 30, 80, 137 e 240 BPM por pelo menos um minuto em cada andamento.
10. Compassos 2/4, 3/4, 4/4 e 6/8: o primeiro tempo se repete na quantidade certa de pulsos.
    Em 6/8 o BPM conta colcheias, e os acentos padrão são os tempos 1 e 4.
11. Acentuação: marcar e desmarcar tempos. Conferir nenhum tempo acentuado, todos
    acentuados e um padrão alternado (por exemplo 2 e 4 em 4/4).
12. Subdivisões de 1 a 4 notas por tempo: a 240 BPM devem existir 4, 8, 12 e 16 eventos
    por segundo, com os pulsos distinguíveis das subdivisões.
13. Mudar o BPM no meio do compasso pelo slider, pelos botões − e + e digitando o valor e
    confirmando no teclado. O aviso "Alteração para N BPM…" aparece, e a mudança entra no
    próximo início de compasso, sem clique duplicado nem compasso truncado.
14. Silenciar por dois compassos e reativar: a fase continua. Testar também volume zero,
    máximo e alterações rápidas.
15. Parar e iniciar repetidamente, inclusive durante "Preparando áudio…".
16. Durante o treino, compasso, subdivisão e acentos ficam travados, com o aviso para parar
    o treino antes de alterá-los.

## C. Timestamps e relógio

17. Com o microfone ligado, abrir **Diagnóstico de áudio** na engrenagem após 1 minuto e após
    10 minutos de treino, em cada rota. Registrar: qualidade do relógio de entrada e de saída
    (timestamp do sistema ou estimativa), desvio de taxa de entrada e de saída em ppm,
    diferença entrada/saída e timestamps aceitos/rejeitados.
18. Esperado na rota recomendada: timestamp do sistema nos dois sentidos e desvio de taxa
    estável entre 1 e 10 minutos. Uma rota que só oferece estimativa não é descartada
    automaticamente, mas a limitação deve ficar registrada.

## D. Detector

19. Com fone e clique silenciado, bater 50 vezes num ritmo lento (cerca de 60 BPM) e comparar
    **Sons detectados** com as batidas reais: registrar perdidas e extras (duplicatas).
20. Repetir com sensibilidade 25%, 50% e 75%.
21. Ficar 30 segundos sem bater, primeiro em silêncio e depois com fala ou TV ligada:
    registrar os falsos positivos.
22. Bater muito perto do microfone: deve aparecer o aviso de sinal muito alto.

## E. Interferência do clique por rota

23. Em cada rota, usar **Testar interferência do clique** sem bater. Registrar os sons na fase
    de silêncio, os sons durante clique e cauda, ou "inconclusivo". Esperado: o alto-falante
    tende a contaminar; o fone com fio deve ficar sem candidatos. Refazer após mudar rota,
    volume, BPM ou sensibilidade.

## F. Medição de tempo com referência externa

24. Gravar a saída com a referência externa a 80 e a 240 BPM, em 4/4, com 1 e com 4 notas por
    tempo, por 2 minutos cada. Localizar os onsets dos cliques e comparar os intervalos com
    `60 / (BPM × subdivisões)`. A fórmula vale também para 6/8, em que o BPM conta colcheias.
25. Registrar média, p95, máximo e deriva ao longo da gravação. A referência também tem
    incerteza e drift: documentá-los. Não concluir precisão acústica a partir apenas dos
    contadores do diagnóstico.

## G. Sessão longa e recursos

26. Tocar por 30 minutos na rota recomendada, com o microfone ligado. Registrar underruns
    (diagnóstico), temperatura, consumo de bateria e qualquer parada inesperada.

## H. Acessibilidade

27. Fonte do sistema no tamanho máximo: nenhum controle cortado ou sobreposto, inclusive a
    linha de 6 tempos em 6/8.
28. TalkBack: todos os controles alcançáveis e anunciados, com estado (selecionado, marcado);
    os contadores não devem ser anunciados continuamente. O painel de configurações é
    anunciado ao abrir.
29. Nesta versão o app usa apenas o tema escuro; registrar se isso atrapalha o uso.

## I. Calibração por rota

30. Em silêncio, executar **Calibrar ruído e sensibilidade** três vezes. Registrar ruído em
    dBFS e sensibilidade sugerida; resultados consecutivos devem ser coerentes e nunca gerar
    offset temporal.
31. No alto-falante, executar **Medir caminho acústico** sem bater. Registrar atraso,
    dispersão, amostras e confiança. Repetir três vezes e comparar a mediana entre ensaios.
32. Repetir após trocar para fone com fio e USB. O perfil exibido deve mudar com a rota e
    reaparecer ao voltar à configuração anterior.
33. Tentar a medição com Bluetooth: o app deve recusar a calibração de precisão com mensagem,
    sem salvar um valor enganoso.
34. Alterar o ajuste manual para +20 ms e −20 ms, fechar e reabrir o app, iniciar na mesma
    rota e conferir persistência. Validar a convenção `captura − atraso + ajuste manual`.
35. Trocar rota durante a medição, sair para Home e revogar o microfone: o ensaio deve parar
    e nenhum resultado parcial deve substituir o perfil anterior.

## Ponto de decisão

Para cada rota, concluir **sustenta**, **sustenta com restrição** ou **não sustenta** a
avaliação. Critérios provisórios; as tolerâncias de acerto só serão fixadas na etapa 7:

- 30 minutos sem underrun (G).
- Timestamps do sistema na entrada e na saída, ou limitação da estimativa registrada (C).
- Nenhum candidato no clique e na cauda, ou clique silenciado assumido como requisito da
  rota (E).
- Detector sem duplicatas e sem falsos positivos em silêncio, com as perdas registradas (D).
- Jitter e deriva medidos externamente e registrados (F).

Se nenhuma rota sustentar a avaliação, corrigir ou restringir a rota antes da etapa 7.

## Testes automatizados disponíveis

- `:core:test`: agenda, renderização PCM, escrita parcial, medidor de nível, detector,
  ensaio de interferência, mapeamento de relógio e calibração (49 testes).
- `connectedDebugAndroidTest`: abertura e recriação, BPM inválido, declaração de
  `RECORD_AUDIO` sem pedido na abertura, padrão de 6/8 e preservação de compasso,
  subdivisão, acentos e controles de calibração (7 testes). Eles não substituem os ensaios acima.
