package pitiupi.batata.net;

import pitiupi.models.Messages.Message;

/**
 * Confirmação de recebimento da batata ("chegou o passe número {@code seq}").
 *
 * <p>UDP não confirma nada sozinho. Sem este ACK, quem passou a batata não sabe
 * se ela chegou, e é ele que reenvia quando o ACK não vem.</p>
 */
public class AckMessage extends Message {

    private static final long serialVersionUID = 1L;

    private String partidaId;
    private int rodada;
    private int seq;
    private String deId;
    private String paraId;

    public AckMessage() {
    }

    public AckMessage(String partidaId, int rodada, int seq, String deId, String paraId) {
        this.partidaId = partidaId;
        this.rodada = rodada;
        this.seq = seq;
        this.deId = deId;
        this.paraId = paraId;
    }

    public String getPartidaId() {
        return partidaId;
    }

    public int getRodada() {
        return rodada;
    }

    public int getSeq() {
        return seq;
    }

    /** Quem confirmou (quem recebeu a batata). */
    public String getDeId() {
        return deId;
    }

    /** Quem tinha passado a batata. */
    public String getParaId() {
        return paraId;
    }
}
