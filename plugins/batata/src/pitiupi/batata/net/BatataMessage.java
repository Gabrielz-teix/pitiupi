package pitiupi.batata.net;

import java.util.ArrayList;
import java.util.List;

/**
 * A batata em si: o token. Vai por UDP unicast para quem vai recebê-la.
 *
 * <p>O pavio viaja dentro do pacote ({@code pavioRestanteMs}): quem recebe continua
 * a contagem de onde o anterior parou. Ninguém precisa de relógio sincronizado.</p>
 *
 * <p>{@code seq} é o número do passe na rodada. Serve para o destinatário reconhecer
 * uma retransmissão (mesmo seq) e não ficar com duas batatas.</p>
 */
public class BatataMessage extends RodadaInfo {

    private static final long serialVersionUID = 1L;

    private int seq;
    private String paraId;
    private long pavioRestanteMs;
    private ArrayList<String> caminho = new ArrayList<>();

    public BatataMessage() {
    }

    public BatataMessage(String partidaId, int rodada, String deId, String deNome,
                         List<Jogador> vivos, List<Jogador> todos,
                         int seq, String paraId, long pavioRestanteMs, List<String> caminho) {
        super(partidaId, rodada, deId, deNome, vivos, todos);
        this.seq = seq;
        this.paraId = paraId;
        this.pavioRestanteMs = pavioRestanteMs;
        this.caminho = new ArrayList<>(caminho);
    }

    public int getSeq() {
        return seq;
    }

    public String getParaId() {
        return paraId;
    }

    public long getPavioRestanteMs() {
        return pavioRestanteMs;
    }

    public List<String> getCaminho() {
        return caminho;
    }
}
