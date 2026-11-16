package pitiupi.pong.net;

/**
 * Host → convidado: a foto do jogo naquele instante (bola, raquetes, placar, fase).
 *
 * <p>O host é a "autoridade": só ele calcula a física. O convidado apenas desenha o que chega.</p>
 */
public class EstadoMessage extends TempoRealMessage {

    private static final long serialVersionUID = 1L;

    public static final int CONTAGEM = 0;
    public static final int JOGANDO = 1;
    public static final int PONTO = 2;
    public static final int FIM = 3;

    private double bolaX;
    private double bolaY;
    private double raqueteHost;
    private double raqueteConvidado;
    private int placarHost;
    private int placarConvidado;
    /** Muda a cada saque: entre dois pacotes de saques diferentes a bola "teletransporta", não se interpola. */
    private int saque;
    private int fase;
    private int contagem;

    public EstadoMessage() {
    }

    public EstadoMessage(String partidaId, String deId, String paraId, int seq, long tempo, long eco, long segurado,
                         double bolaX, double bolaY, double raqueteHost, double raqueteConvidado,
                         int placarHost, int placarConvidado, int saque, int fase, int contagem) {
        super(partidaId, deId, paraId, seq, tempo, eco, segurado);
        this.bolaX = bolaX;
        this.bolaY = bolaY;
        this.raqueteHost = raqueteHost;
        this.raqueteConvidado = raqueteConvidado;
        this.placarHost = placarHost;
        this.placarConvidado = placarConvidado;
        this.saque = saque;
        this.fase = fase;
        this.contagem = contagem;
    }

    public double getBolaX() {
        return bolaX;
    }

    public double getBolaY() {
        return bolaY;
    }

    public double getRaqueteHost() {
        return raqueteHost;
    }

    public double getRaqueteConvidado() {
        return raqueteConvidado;
    }

    public int getPlacarHost() {
        return placarHost;
    }

    public int getPlacarConvidado() {
        return placarConvidado;
    }

    public int getSaque() {
        return saque;
    }

    public int getFase() {
        return fase;
    }

    public int getContagem() {
        return contagem;
    }
}
