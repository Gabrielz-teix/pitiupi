# Plugin Jogo da Forca

Jogo da forca para duas pessoas, cada uma no seu PC.

## Como jogar

1. Os dois abrem o Pitiupi na mesma rede e na mesma porta.
2. Um abre a aba **Jogo da Forca**, escolhe o adversário, digita uma palavra (e uma dica, se quiser) e clica em **Desafiar**.
3. No PC do outro a aba abre sozinha. Ele clica nas letras para adivinhar.
4. São 6 erros até o boneco ficar completo. Depois é só inverter: quem adivinhou escolhe uma palavra.

Acentos são ignorados (MAÇÃ vira MACA) e pode ter espaço (PÉ DE MOLEQUE).

## Como funciona na rede

Todas as mensagens vão por **TCP** (`MessageDispatcher` com `TransportMode.TCP`). Usei TCP porque no jogo da forca cada mensagem importa: se uma letra se perdesse, ou se o resultado de uma letra chegasse antes do de outra, o jogo ficaria errado. O TCP já garante a entrega e a ordem, então não precisei fazer confirmação nem número de sequência.

A palavra **fica só no PC de quem escolheu**. O outro recebe apenas os tracinhos (`_ A _ A _ A`), manda a letra e recebe de volta como a palavra ficou. Assim não dá para descobrir a palavra olhando a mensagem que chegou. A palavra só é enviada no fim do jogo.

Existe uma classe de mensagem só, `ForcaMessage`, com um campo `tipo`:

| Tipo | De → para | Conteúdo |
| --- | --- | --- |
| `CONVITE` | quem escolheu → quem adivinha | tracinhos e dica |
| `OCUPADO` | quem recebeu → quem convidou | já está jogando com outra pessoa |
| `LETRA` | quem adivinha → quem escolheu | a letra |
| `RESULTADO` | quem escolheu → quem adivinha | tracinhos atualizados, erros, letras erradas e, no fim, a palavra |
| `SAIR` | qualquer um | saiu da partida |

O envio roda numa thread separada (abrir a conexão TCP pode demorar e não pode travar a tela). É uma thread só, para as mensagens saírem na ordem certa.

## Arquivos

| Arquivo | O que faz |
| --- | --- |
| `net/ForcaMessage.java` | A mensagem |
| `control/ForcaJogo.java` | Regras: tracinhos, letras certas e erradas, ganhou ou perdeu |
| `GUI/DesenhoForca.java` | Desenha a forca e o boneco |
| `GUI/ForcaPanel.java` | A tela e o fluxo do jogo (convite, letras, resultado) |
| `plugin/ForcaPlugin.java` | Liga o painel ao Pitiupi |
