package pitiupi.batata.control;

import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.batata.net.Jogador;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Random;

/**
 * Decide como cada mensagem sai e, se pedido, joga fora algumas de propósito.
 *
 * <ul>
 *   <li>Batata e ACK: UDP unicast para o IP do jogador.</li>
 *   <li>Exceção: se o jogador está neste mesmo PC, vai por multicast. Dois programas no
 *       mesmo PC abrem a mesma porta, e o sistema operacional entrega um datagrama
 *       unicast para só um deles (nem sempre o certo). O multicast chega nos dois, e cada
 *       um descarta o que não tem o seu id no campo "para".</li>
 *   <li>Avisos para a sala (posse, fim de rodada): multicast.</li>
 * </ul>
 */
final class Transporte {

    static final double TAXA_PERDA = 0.4;

    private final ConnectionManager connectionManager;
    private final Random random = new Random();
    private volatile boolean simularPerda;

    Transporte(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    void setSimularPerda(boolean simularPerda) {
        this.simularPerda = simularPerda;
    }

    boolean isSimularPerda() {
        return simularPerda;
    }

    /**
     * Envia direto para um jogador.
     *
     * @return como foi enviado, para o registro na tela
     */
    String enviarPara(Message message, Jogador alvo, boolean podePerder) {
        if (podePerder && simularPerda && random.nextDouble() < TAXA_PERDA) {
            return "PERDIDO de propósito (simulação)";
        }
        InetAddress endereco = resolver(alvo.getIp());
        if (endereco == null) {
            return "falhou: IP inválido " + alvo.getIp();
        }
        if (mesmoComputador(endereco)) {
            MessageDispatcher.send(connectionManager, message, SendTarget.broadcastAll(), TransportMode.MULTICAST_UDP);
            return "multicast (mesmo PC)";
        }
        int enviados = MessageDispatcher.send(connectionManager, message, SendTarget.singlePeer(endereco), TransportMode.UNICAST_UDP);
        return enviados > 0 ? "UDP unicast → " + alvo.getIp() : "falhou ao enviar para " + alvo.getIp();
    }

    /** Aviso para a sala inteira, por multicast. */
    void anunciar(Message message) {
        MessageDispatcher.send(connectionManager, message, SendTarget.broadcastAll(), TransportMode.MULTICAST_UDP);
    }

    private static InetAddress resolver(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(ip);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean mesmoComputador(InetAddress endereco) {
        if (endereco.isLoopbackAddress() || endereco.isAnyLocalAddress()) {
            return true;
        }
        try {
            return NetworkInterface.getByInetAddress(endereco) != null;
        } catch (Exception ex) {
            return false;
        }
    }
}
