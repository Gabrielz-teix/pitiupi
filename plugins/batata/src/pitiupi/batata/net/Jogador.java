package pitiupi.batata.net;

import java.io.Serializable;
import java.util.Objects;

/**
 * Um participante da partida, como viaja dentro das mensagens.
 *
 * <p>O {@code id} é o instanceId do heartbeat do núcleo: dois programas no mesmo PC
 * têm o mesmo IP, mas ids diferentes. Por isso o jogo identifica pessoas pelo id,
 * e usa o IP só para saber para onde mandar o datagrama.</p>
 */
public final class Jogador implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String nome;
    private String ip;

    public Jogador() {
    }

    public Jogador(String id, String nome, String ip) {
        this.id = id;
        this.nome = nome;
        this.ip = ip;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getIp() {
        return ip;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Jogador other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return nome;
    }
}
