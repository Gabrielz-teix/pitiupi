package pitiupi.batata.net;

import pitiupi.models.Messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Cabeçalho comum a todas as mensagens do jogo.
 *
 * <p>Não existe servidor guardando o estado da partida. Cada mensagem carrega
 * a partida, a rodada e quem ainda está vivo. Assim quem perdeu um pacote anterior
 * (UDP não garante entrega nem ordem) se atualiza com o próximo que chegar.</p>
 */
public abstract class RodadaInfo extends Message {

    private static final long serialVersionUID = 1L;

    private String partidaId;
    private int rodada;
    private String deId;
    private String deNome;
    private ArrayList<Jogador> vivos = new ArrayList<>();
    private ArrayList<Jogador> todos = new ArrayList<>();

    protected RodadaInfo() {
    }

    protected RodadaInfo(String partidaId, int rodada, String deId, String deNome,
                         List<Jogador> vivos, List<Jogador> todos) {
        this.partidaId = partidaId;
        this.rodada = rodada;
        this.deId = deId;
        this.deNome = deNome;
        this.vivos = new ArrayList<>(vivos);
        this.todos = new ArrayList<>(todos);
    }

    public String getPartidaId() {
        return partidaId;
    }

    public int getRodada() {
        return rodada;
    }

    /** instanceId de quem enviou. */
    public String getDeId() {
        return deId;
    }

    public String getDeNome() {
        return deNome;
    }

    public List<Jogador> getVivos() {
        return vivos;
    }

    public List<Jogador> getTodos() {
        return todos;
    }
}
