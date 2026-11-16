# Plugin Pong em rede

O Pong clássico para duas pessoas, cada uma no seu PC. O objetivo didático é mostrar o que acontece com um jogo em **tempo real** sobre UDP: latência, perda, pacotes fora de ordem, jitter, e as técnicas que os jogos usam para esconder isso.

## Como jogar

1. Os dois abrem o Pitiupi **em PCs diferentes**, na **mesma porta** e na mesma rede, e abrem a aba **Pong em rede**.
2. Um escolhe o adversário e clica em **Desafiar** (ou botão direito na pessoa da lista lateral → *Desafiar para Pong*).
3. O outro clica em **Aceitar**. Contagem de 3 s e a bola sai.
4. Mova o mouse sobre o campo (ou use W/S, ↑/↓). Quem fizer 5 pontos vence.

Quem desafiou é o **host** (raquete azul à esquerda na tela dele). Quem aceitou é o **convidado**.

## Controles de rede (embaixo do campo)

| Controle | O que faz |
| --- | --- |
| Envios/s | Quantos pacotes por segundo *este* PC manda (60 a 5) |
| Interpolação | Liga/desliga a suavização (ver abaixo) |
| Mostrar bola recebida | No convidado, desenha em vermelho a última posição que chegou, sem suavização |
| Simular neste PC: perda / atraso / jitter | Piora de propósito os pacotes que *este* PC envia |

A faixa embaixo do campo mostra RTT, SRTT, jitter, pacotes recebidos por segundo, perda, pacotes fora de ordem e quantos bytes/s este PC está enviando.

## Mensagens

| Mensagem | Sentido | Para que serve |
| --- | --- | --- |
| `ConviteMessage` | quem desafia → desafiado | Repetido a cada 500 ms até vir a resposta (até 12 s) |
| `RespostaMessage` | desafiado → quem desafia | Aceito ou recusado. Se se perder, o convite chega de novo e a resposta é repetida |
| `EstadoMessage` | host → convidado | Bola, raquetes, placar e fase, várias vezes por segundo |
| `EntradaMessage` | convidado → host | Posição da raquete do convidado |
| `SairMessage` | qualquer um | Encerrou a partida (3 cópias; se todas se perderem, o silêncio de 5 s encerra) |

Todas vão por **UDP unicast** para o IP do adversário (exceto quando os dois estão no mesmo PC: aí vai por multicast, com o id do destinatário na mensagem).

## Conceitos de Redes no código

**Por que UDP e sem retransmissão.** O estado do jogo muda o tempo todo. Se um pacote se perde, reenviá-lo não adianta: quando chegasse, a bola já estaria em outro lugar, e o próximo pacote já traz a posição nova. O TCP faria exatamente o que não queremos: seguraria os pacotes novos esperando o velho ser retransmitido (*head-of-line blocking*). Compare com o plugin Batata Quente, em que *cada* pacote importa e por isso há ACK e retransmissão.

**Número de sequência.** Cada pacote de tempo real tem um `seq`. Quem recebe:
- descarta o que chega com `seq` menor que o último aceito (chegou fora de ordem, é informação velha);
- conta os buracos na sequência como **perda**.

**Servidor autoritativo.** Só o host calcula a física. O convidado manda a raquete e desenha o que recebe. Assim os dois nunca discordam de onde a bola está. O custo: o convidado vê tudo com atraso (metade do RTT), e o host decide as batidas com a posição da raquete do convidado que chegou *por último* — com rede ruim, o convidado jura que pegou a bola e o host diz que não. É a "vantagem do host" dos jogos online.

**Predição local.** A raquete de cada um é desenhada na hora, a partir do próprio mouse, sem esperar ida e volta pela rede. Sem isso, a própria raquete pareceria "pesada".

**Interpolação.** Com poucos pacotes por segundo, desenhar só o último que chegou faz a bola andar aos pulos. Com a interpolação ligada, a tela desenha 100 ms no passado e desliza entre dois pacotes recebidos: o movimento fica liso, ao custo de 100 ms a mais de atraso. Para ver: 5 ou 10 envios/s no host, e ligue/desligue a caixa no convidado (com *Mostrar bola recebida* fica claro: o círculo vermelho pula, a bola branca desliza atrás dele).

**RTT sem relógio sincronizado.** Cada pacote leva o horário de quem enviou (`tempo`), o último horário recebido do outro (`eco`) e quanto tempo ficou parado antes de responder (`segurado`). Quem recebe o eco calcula `RTT = agora − eco − segurado`, sempre no próprio relógio. Os relógios dos dois PCs nunca são comparados.

**SRTT e jitter.** O SRTT é a média móvel do TCP (RFC 6298): `SRTT = 7/8·SRTT + 1/8·RTT`. O jitter é calculado como no RTP (RFC 3550): compara o intervalo entre duas chegadas com o intervalo entre os dois envios; `J = J + (|D| − J)/16`.

**Banda.** Cada `EstadoMessage` tem ~560 bytes (serialização Java, que inclui nome de classe e campos). A 30 envios/s isso dá ~130 kbit/s. Um jogo de verdade codificaria os mesmos números em ~40 bytes — bom ponto para discutir o custo do formato.

**Simulador de rede ruim.** O atraso com jitter é feito por um agendador: um pacote pode ser agendado para sair depois do seguinte, e eles chegam fora de ordem, como numa rede congestionada.

## Experimentos para a apresentação

1. **Rede limpa:** RTT baixo, perda 0, fora de ordem 0.
2. **Poucos pacotes:** host em 5 envios/s. No convidado, desligue a interpolação (bola aos pulos) e ligue de novo (bola lisa).
3. **Perda:** 20% no host. A perda aparece no convidado, o jogo continua sem travar.
4. **Jitter:** 100 ms de jitter no host. O contador "fora de ordem" sobe no convidado.
5. **Atraso:** 300 ms no convidado. O host vê a raquete do convidado atrasada e o convidado começa a "perder bolas que pegou".

## Arquivos

| Arquivo | Papel |
| --- | --- |
| `net/*.java` | As mensagens |
| `control/Fisica.java` | Bola, raquetes e placar (só o host usa) |
| `control/PongController.java` | Convite, laço do jogo, envio e recepção |
| `control/Rede.java` | Envio UDP e simulação de perda/atraso/jitter |
| `control/Estatisticas.java` | Perda, fora de ordem, RTT, SRTT, jitter |
| `control/Interpolador.java` | Suavização entre pacotes |
| `GUI/Campo.java`, `GUI/PongPanel.java` | Desenho do campo e controles |
| `plugin/PongPlugin.java` | Liga tudo ao núcleo |
