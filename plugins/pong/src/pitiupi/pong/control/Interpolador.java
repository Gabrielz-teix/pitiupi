package pitiupi.pong.control;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Guarda os últimos estados recebidos e responde "onde as coisas estavam no instante t".
 *
 * <p>A tela pede um instante um pouco no passado (agora − 100 ms). Assim quase sempre existe
 * um pacote antes e outro depois desse instante, e a posição é a média ponderada dos dois.
 * O movimento fica liso mesmo recebendo poucos pacotes por segundo — ao custo de mostrar
 * tudo 100 ms atrasado. Sem pacote depois do instante, mantém o último (não adivinha).</p>
 */
final class Interpolador {

    private record Amostra(long nanos, double[] valores, int geracao) {
    }

    private static final int MAXIMO = 120;
    private final ArrayDeque<Amostra> amostras = new ArrayDeque<>();

    void adicionar(long nanos, double[] valores, int geracao) {
        amostras.addLast(new Amostra(nanos, valores.clone(), geracao));
        while (amostras.size() > MAXIMO) {
            amostras.removeFirst();
        }
    }

    void limpar() {
        amostras.clear();
    }

    boolean vazio() {
        return amostras.isEmpty();
    }

    double[] ultimo() {
        return amostras.isEmpty() ? null : amostras.peekLast().valores();
    }

    double[] em(long nanos) {
        if (amostras.isEmpty()) {
            return null;
        }
        Amostra anterior = null;
        Iterator<Amostra> it = amostras.iterator();
        while (it.hasNext()) {
            Amostra atual = it.next();
            if (atual.nanos() >= nanos) {
                if (anterior == null) {
                    return atual.valores();
                }
                if (anterior.geracao() != atual.geracao()) {
                    // houve um saque entre os dois: não faz sentido "deslizar" a bola entre eles
                    return atual.valores();
                }
                double f = (double) (nanos - anterior.nanos()) / Math.max(1, atual.nanos() - anterior.nanos());
                double[] r = new double[atual.valores().length];
                for (int i = 0; i < r.length; i++) {
                    r[i] = anterior.valores()[i] + (atual.valores()[i] - anterior.valores()[i]) * f;
                }
                return r;
            }
            anterior = atual;
        }
        return amostras.peekLast().valores();
    }
}
