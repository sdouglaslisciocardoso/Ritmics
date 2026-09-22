# Contrato temporal atual

## Unidades e BPM

- BPM representa semínimas/minuto nos compassos 2/4, 3/4 e 4/4.
- Subdivisão 2 representa duas colcheias por pulso; não muda o BPM indicado.
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

Mudar compasso, acento e subdivisão exige parar nesta tela técnica. Iniciar novamente
cria nova agenda, iniciada no primeiro tempo após a antecedência silenciosa.

## Ganho e silêncio

Alterar volume ou silêncio usa rampa de 5 ms, sem reiniciar a agenda. Durante silêncio
continuam sendo produzidos frames e comprometidos eventos. Timbres têm 10 ms, menos
que os 125 ms entre eventos na configuração mais rápida (240 BPM, subdivisão 2).

## O que ainda não é medido

- Instante acústico de apresentação.
- Latência de saída.
- Drift entre hardware e relógio monotônico.
- Latência de captura (não há captura nesta entrega).
- Erro de uma batida do usuário.

Handler de UI, FPS e instante de conclusão de `write()` não são referências musicais.
