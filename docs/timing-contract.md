# Contrato temporal atual

## Unidades e BPM

- BPM conta o pulso escrito do compasso: semínimas/minuto em 2/4, 3/4 e 4/4 e
  colcheias/minuto em 6/8.
- Subdivisões de 1 a 4 dividem cada pulso em partes iguais (em x/4: semínimas, colcheias,
  tercinas e semicolcheias); não mudam o BPM indicado.
- Cada pulso tem seu próprio acento. O padrão é o primeiro tempo; em 6/8, os tempos 1 e 4.
  Só o início do pulso pode ser acentuado; as subdivisões usam o timbre mais suave.
- Um frame do fluxo atual é uma amostra mono de 16 bits.
- Contadores de frames e eventos usam `long`.

Para evento `k` de um segmento:

`frame(k) = origem + arredondar(k × 60 × taxa / (BPM × subdivisões))`

O cálculo utiliza quociente e resto inteiros, sem somar intervalos já arredondados.
Assim, em qualquer posição do segmento a diferença para o valor ideal nominal é
no máximo meio frame. A taxa física pode divergir da taxa nominal: isso permanece
fora da garantia desta etapa.

## Mudança de BPM

O pedido substitui o pedido anterior. Quando o renderizador encontra o próximo
primeiro tempo ainda não renderizado, mantém seu frame, aplica o novo BPM e inicia
um novo segmento. Os intervalos seguintes usam o novo andamento. Um compasso cujo
primeiro tempo já foi gerado não é alterado retroativamente, mesmo se ainda não foi
ouvido. A UI identifica o valor como BPM **gerado**.

Mudar compasso, acento e subdivisão exige parar o treino. Iniciar novamente
cria nova agenda, iniciada no primeiro tempo após a antecedência silenciosa.

## Ganho e silêncio

Alterar volume ou silêncio usa rampa de 5 ms, sem reiniciar a agenda. Durante silêncio
continuam sendo produzidos frames e comprometidos eventos. Timbres têm 10 ms, menos
que os 62,5 ms entre eventos na configuração mais rápida (240 BPM, quatro notas por tempo).

## O que ainda não é medido

- Instante acústico de apresentação.
- Latência de saída e de captura no caminho físico (alto-falante, fone, microfone).
- Drift entre hardware e relógio monotônico validado em aparelho: o diagnóstico só estima
  a taxa a partir dos timestamps disponíveis.
- Erro de uma batida do usuário.

Handler de UI, FPS e instante de conclusão de `write()` não são referências musicais.
