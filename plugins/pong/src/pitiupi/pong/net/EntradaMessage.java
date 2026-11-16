package pitiupi.pong.net;

/**
 * Convidado → host: onde está a raquete do convidado.
 */
public class EntradaMessage extends TempoRealMessage {

    private static final long serialVersionUID = 1L;

    private double raquete;

    public EntradaMessage() {
    }

    public EntradaMessage(String partidaId, String deId, String paraId, int seq, long tempo, long eco, long segurado,
                          double raquete) {
        super(partidaId, deId, paraId, seq, tempo, eco, segurado);
        this.raquete = raquete;
    }

    public double getRaquete() {
        return raquete;
    }
}
