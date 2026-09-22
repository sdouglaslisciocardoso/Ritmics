# Roteiro em aparelho — etapa 3

Registrar modelo, versão/build do Android, rota e volume em cada execução. Estes
ensaios não devem ser marcados como aprovados apenas porque o APK compilou.

1. Instalar `app-debug.apk` e abrir. Confirmar que não pede microfone ou internet na abertura.
2. Ativar **Capturar pelo microfone**, iniciar e conceder `RECORD_AUDIO`. Confirmar
   que a entrada muda para captura, o medidor reage à voz/palmas e a permissão não
   é solicitada novamente enquanto já estiver concedida.
3. Recusar a permissão na primeira tentativa. Confirmar mensagem contextual, modo
   visual ainda disponível após desativar o microfone e botão para abrir configurações
   quando a permissão for bloqueada permanentemente.
4. Testar microfone ocupado, alternância do botão durante a sessão, silêncio do
   sistema, clipping aproximando uma fonte sonora e desconexão/troca de rota.
5. Tocar a 30, 80, 137 e 240 BPM por ao menos um minuto em cada andamento.
6. Conferir 2/4, 3/4 e 4/4 com acento ligado/desligado; primeiro tempo deve se repetir
   na quantidade correta de pulsos.
7. Ativar dois cliques por pulso; a 240 BPM devem existir oito eventos por segundo.
8. Mudar BPM durante o compasso. Confirmar aplicação no primeiro tempo disponível,
   sem clique duplicado ou compasso truncado.
9. Silenciar por dois compassos e reativar. A fase deve continuar; testar também
   volume zero, máximo e alterações rápidas.
10. Parar/iniciar repetidamente, incluindo parar durante a preparação.
11. Sair para Home, apagar a tela e girar o aparelho. O som deve parar e não voltar
   sozinho. Voltar à tela e iniciar explicitamente.
12. Provocar perda de foco com outro player ou uma chamada. O som deve parar.
13. Desconectar fones, trocar saída ou conectar USB. Verificar interrupção e ausência
    de reprodução inesperada no alto-falante.
14. Rodar por 30 minutos, monitorar underruns, calor e consumo. Verificar liberação
    dos recursos ao parar e ao sair.
15. Testar fonte ampliada, tema do sistema e TalkBack. Todos os controles devem ser
    alcançáveis; os contadores não devem ser anunciados continuamente.

## Medição de tempo

Gravar a saída em referência externa independente, localizar os onsets dos cliques
e comparar os intervalos com `60 / (BPM × subdivisões)`. Registrar média, p95, máximo
e deriva ao longo da sessão. A referência também tem incerteza e drift: documentá-los.
Não concluir precisão acústica a partir apenas dos contadores ou do emulador.

## Testes instrumentados disponíveis

`connectedDebugAndroidTest` cobre abertura/recriação, BPM inválido e declaração de
`RECORD_AUDIO` sem solicitação durante a abertura. Os ensaios de áudio e permissão
acima continuam necessários em aparelho real.
