package pitiupi.pong.control;

import java.util.List;

/**
 * O que o controller entrega para a tela.
 */
public interface PongView {

    /** Um quadro do campo (chamado ~60 vezes por segundo). Coordenadas no campo 800 × 500. */
    record Quadro(
            double bolaX,
            double bolaY,
            boolean bolaVisivel,
            double raqueteEsquerda,
            double raqueteDireita,
            int placarEsquerda,
            int placarDireita,
            String nomeEsquerda,
            String nomeDireita,
            boolean euNaEsquerda,
            String aviso,
            boolean mostrarCrua,
            double bolaCruaX,
            double bolaCruaY,
            List<String> medidas
    ) {
    }

    record Adversario(String id, String nome) {
        @Override
        public String toString() {
            return nome;
        }
    }

    /** Situação geral (botões, convite, lista de pessoas). Chamado quando muda e uma vez por segundo. */
    record Status(
            String texto,
            boolean livre,
            boolean emPartida,
            String convitePendenteDe,
            List<Adversario> online
    ) {
    }

    void quadro(Quadro quadro);

    void status(Status status);

    void registrar(String linha);
}
