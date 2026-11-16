package pitiupi.pong.control;

/**
 * Medidas da rede, calculadas sobre os pacotes de tempo real que chegam.
 *
 * <ul>
 *   <li><b>Perda</b>: buracos na sequência (chegou o 10 depois do 7 → 8 e 9 perdidos).</li>
 *   <li><b>Fora de ordem</b>: chegou um seq menor que o último aceito. É descartado.</li>
 *   <li><b>RTT / SRTT</b>: SRTT é a média móvel que o TCP usa (RFC 6298):
 *       SRTT = 7/8 · SRTT + 1/8 · RTT.</li>
 *   <li><b>Jitter</b>: variação do atraso entre pacotes, como no RTP (RFC 3550):
 *       J = J + (|D| − J) / 16, onde D compara o intervalo de chegada com o intervalo de envio.</li>
 * </ul>
 */
final class Estatisticas {

    private int ultimoSeq = -1;
    private long recebidos;
    private long perdidos;
    private long foraDeOrdem;

    private int recebidosJanela;
    private int perdidosJanela;
    private long inicioJanelaNanos = System.nanoTime();
    private double pacotesPorSegundo;
    private double perdaPct;

    private double rtt = -1;
    private double srtt = -1;
    private double jitter;
    private long chegadaAnteriorMs = -1;
    private long envioAnteriorMs;

    void reiniciar() {
        ultimoSeq = -1;
        recebidos = 0;
        perdidos = 0;
        foraDeOrdem = 0;
        recebidosJanela = 0;
        perdidosJanela = 0;
        pacotesPorSegundo = 0;
        perdaPct = 0;
        rtt = -1;
        srtt = -1;
        jitter = 0;
        chegadaAnteriorMs = -1;
        inicioJanelaNanos = System.nanoTime();
    }

    /**
     * @return {@code false} se o pacote está fora de ordem e deve ser descartado
     */
    boolean receber(int seq, long tempoRemotoMs, long chegadaMs) {
        if (seq <= ultimoSeq) {
            foraDeOrdem++;
            return false;
        }
        if (ultimoSeq >= 0) {
            int buraco = seq - ultimoSeq - 1;
            perdidos += buraco;
            perdidosJanela += buraco;
        }
        ultimoSeq = seq;
        recebidos++;
        recebidosJanela++;

        if (chegadaAnteriorMs >= 0) {
            double d = (chegadaMs - chegadaAnteriorMs) - (tempoRemotoMs - envioAnteriorMs);
            jitter += (Math.abs(d) - jitter) / 16.0;
        }
        chegadaAnteriorMs = chegadaMs;
        envioAnteriorMs = tempoRemotoMs;
        return true;
    }

    void amostraRtt(double amostra) {
        if (amostra < 0) {
            return;
        }
        rtt = amostra;
        srtt = srtt < 0 ? amostra : 0.875 * srtt + 0.125 * amostra;
    }

    /** Fecha a janela de 1 s para calcular pacotes/s e perda recente. */
    void tick() {
        long agora = System.nanoTime();
        long decorrido = agora - inicioJanelaNanos;
        if (decorrido < 1_000_000_000L) {
            return;
        }
        pacotesPorSegundo = recebidosJanela / (decorrido / 1e9);
        int total = recebidosJanela + perdidosJanela;
        perdaPct = total == 0 ? 0 : 100.0 * perdidosJanela / total;
        recebidosJanela = 0;
        perdidosJanela = 0;
        inicioJanelaNanos = agora;
    }

    double rtt() {
        return rtt;
    }

    double srtt() {
        return srtt;
    }

    double jitter() {
        return jitter;
    }

    double perdaPct() {
        return perdaPct;
    }

    double pacotesPorSegundo() {
        return pacotesPorSegundo;
    }

    long recebidos() {
        return recebidos;
    }

    long perdidos() {
        return perdidos;
    }

    long foraDeOrdem() {
        return foraDeOrdem;
    }
}
