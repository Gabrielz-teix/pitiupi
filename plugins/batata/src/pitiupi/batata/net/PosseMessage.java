package pitiupi.batata.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Aviso por multicast para a sala: "a batata agora está comigo".
 *
 * <p>Só quem recebeu a batata manda este aviso. Os outros usam para mostrar
 * com quem ela está e, se essa pessoa sumir da rede, perceber que o token se perdeu.</p>
 */
public class PosseMessage extends RodadaInfo {

    private static final long serialVersionUID = 1L;

    private int seq;
    private ArrayList<String> caminho = new ArrayList<>();
    private boolean recriada;

    public PosseMessage() {
    }

    public PosseMessage(String partidaId, int rodada, String deId, String deNome,
                        List<Jogador> vivos, List<Jogador> todos,
                        int seq, List<String> caminho, boolean recriada) {
        super(partidaId, rodada, deId, deNome, vivos, todos);
        this.seq = seq;
        this.caminho = new ArrayList<>(caminho);
        this.recriada = recriada;
    }

    public int getSeq() {
        return seq;
    }

    public List<String> getCaminho() {
        return caminho;
    }

    /** {@code true} quando a batata anterior se perdeu e esta foi criada de novo. */
    public boolean isRecriada() {
        return recriada;
    }
}
