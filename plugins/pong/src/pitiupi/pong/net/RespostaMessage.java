package pitiupi.pong.net;

/**
 * Resposta ao convite. Se ela se perder, o convite chega de novo e a resposta é repetida.
 */
public class RespostaMessage extends PongMsg {

    private static final long serialVersionUID = 1L;

    private boolean aceito;
    private String deNome;
    private String motivo;

    public RespostaMessage() {
    }

    public RespostaMessage(String partidaId, String deId, String paraId, boolean aceito, String deNome, String motivo) {
        super(partidaId, deId, paraId);
        this.aceito = aceito;
        this.deNome = deNome;
        this.motivo = motivo;
    }

    public boolean isAceito() {
        return aceito;
    }

    public String getDeNome() {
        return deNome;
    }

    public String getMotivo() {
        return motivo;
    }
}
