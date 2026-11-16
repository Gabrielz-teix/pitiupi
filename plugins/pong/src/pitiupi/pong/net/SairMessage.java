package pitiupi.pong.net;

/**
 * "Saí da partida." Vai em algumas cópias; se todas se perderem, o outro lado percebe
 * pelo silêncio (nada chega por 5 s).
 */
public class SairMessage extends PongMsg {

    private static final long serialVersionUID = 1L;

    public SairMessage() {
    }

    public SairMessage(String partidaId, String deId, String paraId) {
        super(partidaId, deId, paraId);
    }
}
