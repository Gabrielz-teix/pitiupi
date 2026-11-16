package pitiupi.pong.control;

import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Envia por UDP e, se pedido, piora a rede de propósito: perde, atrasa e embaralha pacotes.
 *
 * <p>O atraso usa um agendador. Com jitter, um pacote pode ser agendado para sair depois
 * do seguinte, e assim eles chegam fora de ordem, como numa rede real congestionada.</p>
 */
final class Rede {

    private final ConnectionManager connectionManager;
    private final Random random = new Random();
    private final ScheduledExecutorService atrasador = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pong-rede-simulada");
        t.setDaemon(true);
        return t;
    });

    private volatile int perdaPct;
    private volatile int atrasoMs;
    private volatile int jitterMs;

    private long bytesJanela;
    private int pacotesJanela;
    private volatile double pacotesPorSegundo;
    private long inicioJanelaNanos = System.nanoTime();
    private volatile double kbpsEnvio;
    private volatile int bytesUltimoPacote;

    Rede(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    void configurar(int perdaPct, int atrasoMs, int jitterMs) {
        this.perdaPct = perdaPct;
        this.atrasoMs = atrasoMs;
        this.jitterMs = jitterMs;
    }

    /** Envia para o IP do adversário. Chamado na thread do Swing. */
    void enviar(Message message, String ip) {
        contarBytes(message);
        if (perdaPct > 0 && random.nextInt(100) < perdaPct) {
            return;
        }
        int atraso = atrasoMs + (jitterMs > 0 ? random.nextInt(jitterMs + 1) : 0);
        if (atraso <= 0) {
            enviarAgora(message, ip);
        } else {
            atrasador.schedule(() -> enviarAgora(message, ip), atraso, TimeUnit.MILLISECONDS);
        }
    }

    /** Envio de controle (convite, resposta, saída): nunca é perdido nem atrasado de propósito. */
    void enviarSemSimulacao(Message message, String ip) {
        enviarAgora(message, ip);
    }

    private void enviarAgora(Message message, String ip) {
        InetAddress endereco = resolver(ip);
        if (endereco == null) {
            return;
        }
        if (mesmoComputador(endereco)) {
            // Dois programas no mesmo PC dividem a porta UDP; unicast chegaria em só um deles.
            MessageDispatcher.send(connectionManager, message, SendTarget.broadcastAll(), TransportMode.MULTICAST_UDP);
        } else {
            MessageDispatcher.send(connectionManager, message, SendTarget.singlePeer(endereco), TransportMode.UNICAST_UDP);
        }
    }

    private void contarBytes(Message message) {
        try {
            int tamanho = message.toByteArray().length;
            bytesUltimoPacote = tamanho;
            bytesJanela += tamanho;
        } catch (Exception ignored) {
            // só estatística
        }
        pacotesJanela++;
        long agora = System.nanoTime();
        long decorrido = agora - inicioJanelaNanos;
        if (decorrido >= 1_000_000_000L) {
            kbpsEnvio = bytesJanela * 8 / 1000.0 / (decorrido / 1e9);
            pacotesPorSegundo = pacotesJanela / (decorrido / 1e9);
            bytesJanela = 0;
            pacotesJanela = 0;
            inicioJanelaNanos = agora;
        }
    }

    double pacotesPorSegundo() {
        return pacotesPorSegundo;
    }

    double kbpsEnvio() {
        return kbpsEnvio;
    }

    int bytesUltimoPacote() {
        return bytesUltimoPacote;
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
