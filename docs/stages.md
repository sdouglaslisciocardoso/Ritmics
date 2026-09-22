# Etapas de entrega

Escopo concluído nesta rodada: **1 a 5**.

1. Estrutura Android Studio, módulos Java/Android, XML, Wrapper e testes.
2. Metrônomo técnico PCM, 30–240 BPM, compassos, acento, subdivisão e controles.
3. Captura pelo microfone e permissões: `AudioRecord` mono PCM, negociação de taxa/fonte,
   medidor de nível, clipping, timestamps de bloco e liberação no ciclo de vida.
4. Timestamps, sincronização e drift dos relógios reais.
5. Detector de transientes e prova acústica de interferência.
6. Calibração e perfis por rota.
7. Avaliação e estatísticas.
8. Sessões completas, preparação, pausa/retomada e duração.
9. Pista de notas e tela de treino.
10. Jornada completa, preferências, temas e acessibilidade consolidada.
11. Validação física e estabilização do MVP.
12. Entrega final do MVP.

Depois: Tap Tempo/timbres (13), histórico (14), dificuldade avançada (15) e novos
exercícios individualmente planejados (16).

A etapa 5 detecta candidatos de início de sons curtos e mede possível contaminação do
clique, mas ainda não associa eventos a notas nem atribui avaliações. As interrupções
simples do metrônomo protegem seu ciclo de vida; não equivalem ao controlador de sessão da etapa 8.
