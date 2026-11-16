package pitiupi.pong.net;

/**
 * Parte comum dos pacotes de tempo real (estado do host e raquete do convidado).
 *
 * <ul>
 *   <li>{@code seq}: número do pacote. Quem recebe descarta seq velho (chegou fora de ordem)
 *       e conta os buracos como perda. Não há retransmissão: em tempo real, um pacote
 *       atrasado já não interessa, o próximo traz o estado novo.</li>
 *   <li>{@code tempo}: relógio de quem enviou, em ms.</li>
 *   <li>{@code eco} e {@code segurado}: medem o RTT. Cada lado devolve o último {@code tempo}
 *       que recebeu do outro, e quanto tempo ficou com ele antes de responder. Quem recebe o eco
 *       faz: RTT = agora − eco − segurado. Os dois relógios nunca são comparados entre si.</li>
 * </ul>
 */
public abstract class TempoRealMessage extends PongMsg {

    private static final long serialVersionUID = 1L;

    private int seq;
    private long tempo;
    private long eco;
    private long segurado;

    protected TempoRealMessage() {
    }

    protected TempoRealMessage(String partidaId, String deId, String paraId,
                               int seq, long tempo, long eco, long segurado) {
        super(partidaId, deId, paraId);
        this.seq = seq;
        this.tempo = tempo;
        this.eco = eco;
        this.segurado = segurado;
    }

    public int getSeq() {
        return seq;
    }

    public long getTempo() {
        return tempo;
    }

    public long getEco() {
        return eco;
    }

    public long getSegurado() {
        return segurado;
    }
}
