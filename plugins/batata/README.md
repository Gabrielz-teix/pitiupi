# Plugin Batata Quente

Jogo para a rede local: uma batata (um **token**) passa de PC em PC. O pavio é sorteado e ninguém sabe quanto falta. Quem estiver com a batata quando o pavio acabar sai da partida. As rodadas seguem até sobrar um vencedor.

A ideia é mostrar, num jogo, problemas clássicos de redes: passagem de token, confiabilidade em cima de UDP, perda e duplicação de token, e eleição sem servidor central.

## Como jogar

1. Cada pessoa abre o Pitiupi **no seu próprio PC**, todos na **mesma porta** (ex.: 5000) e na mesma rede.
2. Abra a aba **Batata Quente** (galeria de plugins, categoria Jogos).
3. Alguém clica em **Nova partida**. Entram todos que estão na lista "Usuários nesta rede".
4. Quando a batata estiver com você, clique no nome de alguém à direita para passá-la. Também dá para clicar com o botão direito numa pessoa da lista lateral → **Passar a batata para esta pessoa**.

> Dois Pitiupi no mesmo PC não conseguem usar a mesma porta (o núcleo abre um servidor TCP nela), e com portas diferentes eles não se enxergam. Para jogar são precisos PCs diferentes (ou uma máquina virtual em modo bridge).

A caixa **Simular perda de pacotes (40%)** joga fora, de propósito, parte das batatas e ACKs que *aquele* PC envia. Serve para ver a retransmissão funcionando na apresentação. O painel "O que passou pela rede" mostra cada envio, ACK, retransmissão e RTT.

## Mensagens

| Mensagem | Transporte | Para que serve |
| --- | --- | --- |
| `BatataMessage` | UDP unicast para quem recebe | O token. Leva o número do passe (`seq`), o pavio restante, o caminho e a lista de jogadores vivos |
| `AckMessage` | UDP unicast de volta | "Recebi o passe `seq`" |
| `PosseMessage` | Multicast para a sala | "A batata agora está comigo". Os outros mostram com quem ela está e vigiam se essa pessoa some |
| `FimRodadaMessage` | Multicast (3 cópias, e repetido a cada 2 s por 20 s) | "Explodiu comigo". Já leva a lista de vivos sem o perdedor |

Não há servidor guardando a partida: cada mensagem carrega partida, rodada e jogadores vivos. Quem perdeu uma mensagem anterior se atualiza com a próxima que chegar.

## Conceitos de Redes no código

**Token (Token Ring).** Só quem tem a batata pode agir, como só a estação com o token transmite. O pavio viaja *dentro* do pacote (`pavioRestanteMs`): quem recebe continua a contagem de onde o anterior parou, sem precisar de relógio sincronizado entre os PCs.

**Confiabilidade sobre UDP (ACK + timeout + retransmissão).** UDP não avisa se o datagrama chegou. Quem passa a batata espera o ACK por 400 ms; sem ACK, reenvia (até 6 vezes). É o mesmo mecanismo que o TCP faz por baixo. Detalhes que aparecem no registro:
- **Duplicata:** se o ACK se perde, quem recebeu ganha a mesma batata de novo. O `seq` repetido mostra que é retransmissão: ela é ignorada e só o ACK é reenviado.
- **RTT ambíguo (problema de Karn):** quando houve retransmissão, não dá para saber a qual envio o ACK responde, e o registro avisa isso.
- **ACK implícito:** se o aviso de posse (multicast) chega antes do ACK, ele já prova que a batata chegou.

**Desistência e token duplicado.** Depois de 6 tentativas sem ACK, a batata volta para quem tentou passar. Mas pode ser que ela tenha chegado e só os ACKs se perderam (o problema dos dois generais: não há como ter certeza). Por isso o número do passe abandonado é "gasto": o próximo passe sai com `seq` maior. Se aparecerem duas batatas, vale a de `seq` maior e a outra é descartada.

**Perda do token e eleição.** A presença vem dos heartbeats do núcleo. Se quem está com a batata some da lista (fechou o programa ou caiu a rede), a batata se perdeu. O jogador vivo com o **menor id** (instanceId) cria uma nova — todos aplicam a mesma regra sobre a mesma lista, então chegam ao mesmo eleito sem trocar mensagens. O mesmo coordenador começa a rodada seguinte. Quem fecha o programa normalmente avisa na hora (goodbye); quem cai sem avisar só sai da lista depois de ~15 s (timeout do heartbeat).

**Multicast não é confiável.** O aviso de fim vai em várias cópias e é repetido por um tempo. Sem isso, um PC que perdesse todas as cópias ficaria achando que a rodada não acabou — e se fosse o coordenador, o jogo travaria.

**Unicast × mesmo PC.** Se o destino tem o IP deste PC, a batata vai por multicast com o id do destinatário no campo `paraId`, e cada programa descarta o que não é seu. Dois sockets na mesma porta UDP dividem os datagramas unicast de um jeito que o sistema operacional escolhe; o multicast chega nos dois.

## Arquivos

| Arquivo | Papel |
| --- | --- |
| `src/META-INF/services/pitiupi.models.Plugin` | Diz ao núcleo qual é a classe do plugin |
| `net/*.java` | As mensagens (`Jogador`, `RodadaInfo`, `BatataMessage`, `AckMessage`, `PosseMessage`, `FimRodadaMessage`) |
| `control/BatataController.java` | Regras do jogo e do protocolo (ACK, retransmissão, eleição). Não desenha nada |
| `control/Transporte.java` | Escolhe unicast ou multicast e simula a perda |
| `control/BatataView.java` | O que o controller entrega para a tela |
| `GUI/BatataPanel.java`, `GUI/BatataDesenho.java` | A aba e a batata desenhada |
| `plugin/BatataPlugin.java` | Liga tudo ao núcleo e registra as ações da lista de pessoas |

Compilar só este plugin: `cd plugins/batata && ant jar` (o núcleo precisa estar compilado antes). O `scripts\pitiupi.cmd run --all` já compila e carrega ele junto com os outros.
