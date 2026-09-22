# Resultados de validação — etapas 1 a 5

Registro das execuções do [roteiro em aparelho](device-test-plan.md). Legenda:
✅ aprovado · ❌ reprovado · ⚠️ aprovado com limitação · ⏳ não executado.

## Situação do ponto de decisão

**Em aberto.** Nenhum ensaio em celular real foi registrado até agora.

## Verificações automatizadas

| Data | Verificação | Ambiente | Resultado |
| --- | --- | --- | --- |
| 2026-09-22 | `:core:test` (45 testes) | Windows 11, JBR 25, pasta com acentos | ✅ |
| 2026-09-22 | `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleDebugAndroidTest` | Windows 11, JBR 25, pasta com acentos | ✅ |
| 2026-09-22 | `connectedDebugAndroidTest` (6 testes) | Emulador Pixel 10 Pro XL, API 37 | ✅ |

O emulador só valida a interface e o ciclo de vida. Os resultados de áudio abaixo
precisam de celular real.

## Aparelhos

| Aparelho | Modelo | Android / build | Observações |
| --- | --- | --- | --- |
| A | ⏳ | | |

## Resultados por seção

Preencha uma linha por rota testada. Rotas: fone com fio, USB, alto-falante, Bluetooth.

| Seção do roteiro | Aparelho | Rota | Resultado | Valores medidos | Observações |
| --- | --- | --- | --- | --- | --- |
| A. Abertura, permissões e ciclo de vida (1–8) | | | ⏳ | | |
| B. Metrônomo (9–16) | | | ⏳ | | |
| C. Timestamps e relógio (17–18) | | | ⏳ | Relógio entrada/saída; desvio ppm em 1 e 10 min | |
| D. Detector (19–22) | | | ⏳ | Detectadas / perdidas / extras de 50; falsos positivos em 30 s | |
| E. Interferência do clique (23) | | | ⏳ | Sons no silêncio / no clique e cauda | |
| F. Medição de tempo (24–25) | | | ⏳ | Média, p95, máximo, deriva; incerteza da referência | |
| G. Sessão longa (26) | | | ⏳ | Underruns, temperatura, bateria em 30 min | |
| H. Acessibilidade (27–29) | | | ⏳ | | |

## Conclusão por rota

| Rota | Conclusão | Justificativa |
| --- | --- | --- |
| Fone com fio | ⏳ | |
| USB | ⏳ | |
| Alto-falante | ⏳ | |
| Bluetooth | ⏳ | |

## Limitações conhecidas

- A avaliação das batidas ainda não existe (etapa 7): o detector só conta candidatos.
- O metrônomo toca 6/8, tercinas e semicolcheias; a etapa 7 decide se esses modos serão
  avaliados ou exibidos como "sem avaliação".
- O app usa somente tema escuro nesta versão.
