package pitiupi.batata.control;

import java.util.List;

/**
 * O que o controller entrega para a tela. A tela só desenha; quem decide é o controller.
 */
public interface BatataView {

    /** Como a tela deve estar agora. */
    record Estado(
            Fase fase,
            int rodada,
            String titulo,
            String detalhe,
            List<String> caminho,
            List<JogadorNaTela> jogadores,
            boolean podePassar,
            long segurandoMs
    ) {
    }

    record JogadorNaTela(String id, String nome, boolean vivo, boolean online, boolean eu, boolean comBatata) {
    }

    enum Fase {
        SEM_PARTIDA,
        COM_OUTRO,
        COMIGO,
        PASSANDO,
        EXPLODIU,
        FIM_DE_JOGO
    }

    void mostrar(Estado estado);

    void registrar(String linha);
}
