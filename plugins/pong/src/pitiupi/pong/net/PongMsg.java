package pitiupi.pong.net;

import pitiupi.models.Messages.Message;

/**
 * Cabeçalho comum: de qual partida, quem mandou e para quem.
 *
 * <p>O {@code paraId} existe porque, quando os dois estão no mesmo PC, a mensagem vai por
 * multicast e chega em todo mundo; cada programa descarta o que não é seu.</p>
 */
public abstract class PongMsg extends Message {

    private static final long serialVersionUID = 1L;

    private String partidaId;
    private String deId;
    private String paraId;

    protected PongMsg() {
    }

    protected PongMsg(String partidaId, String deId, String paraId) {
        this.partidaId = partidaId;
        this.deId = deId;
        this.paraId = paraId;
    }

    public String getPartidaId() {
        return partidaId;
    }

    public String getDeId() {
        return deId;
    }

    public String getParaId() {
        return paraId;
    }
}
