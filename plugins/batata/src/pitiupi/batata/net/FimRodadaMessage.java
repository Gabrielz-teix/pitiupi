package pitiupi.batata.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Aviso por multicast: a batata explodiu na mão de quem enviou.
 *
 * <p>{@code getVivos()} já vem sem o perdedor. Como multicast também é UDP,
 * o aviso é mandado algumas vezes e quem recebe ignora as cópias repetidas.</p>
 */
public class FimRodadaMessage extends RodadaInfo {

    private static final long serialVersionUID = 1L;

    private ArrayList<String> caminho = new ArrayList<>();

    public FimRodadaMessage() {
    }

    public FimRodadaMessage(String partidaId, int rodada, String deId, String deNome,
                            List<Jogador> vivos, List<Jogador> todos, List<String> caminho) {
        super(partidaId, rodada, deId, deNome, vivos, todos);
        this.caminho = new ArrayList<>(caminho);
    }

    public List<String> getCaminho() {
        return caminho;
    }
}
