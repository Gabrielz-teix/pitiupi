package pitiupi.pong.net;

/**
 * "Quer jogar Pong comigo?" Reenviado a cada 500 ms até chegar a resposta.
 */
public class ConviteMessage extends PongMsg {

    private static final long serialVersionUID = 1L;

    private String deNome;

    public ConviteMessage() {
    }

    public ConviteMessage(String partidaId, String deId, String paraId, String deNome) {
        super(partidaId, deId, paraId);
        this.deNome = deNome;
    }

    public String getDeNome() {
        return deNome;
    }
}
