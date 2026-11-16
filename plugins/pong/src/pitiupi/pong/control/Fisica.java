package pitiupi.pong.control;

import java.util.Random;

/**
 * A física do Pong, sem rede e sem tela. Só o host roda isto.
 *
 * <p>Coordenadas num campo fixo de {@value #LARGURA} × {@value #ALTURA}; a tela escala.
 * Raquete do host à esquerda, do convidado à direita. Posição de raquete = centro em Y.</p>
 */
public final class Fisica {

    public static final double LARGURA = 800;
    public static final double ALTURA = 500;
    public static final double RAQUETE_ALTURA = 90;
    public static final double RAQUETE_LARGURA = 12;
    public static final double RAQUETE_MARGEM = 30;
    public static final double BOLA = 12;
    public static final int PONTOS_PARA_VENCER = 5;

    private static final double VELOCIDADE_INICIAL = 330;
    private static final double ACELERACAO = 1.06;
    private static final double VELOCIDADE_MAXIMA = 950;
    private static final double ANGULO_MAXIMO = Math.toRadians(55);

    public enum Evento { NADA, BATIDA, PONTO_HOST, PONTO_CONVIDADO }

    private final Random random = new Random();
    double bolaX = LARGURA / 2;
    double bolaY = ALTURA / 2;
    private double vx;
    private double vy;
    double raqueteHost = ALTURA / 2;
    double raqueteConvidado = ALTURA / 2;
    int placarHost;
    int placarConvidado;

    /** Põe a bola no centro, parada. */
    void centralizar() {
        bolaX = LARGURA / 2;
        bolaY = ALTURA / 2;
        vx = 0;
        vy = 0;
    }

    /** Saca do centro. {@code direcao} = −1 vai para o host (esquerda), +1 para o convidado. */
    void sacar(int direcao) {
        centralizar();
        double angulo = (random.nextDouble() * 2 - 1) * Math.toRadians(30);
        vx = direcao * VELOCIDADE_INICIAL * Math.cos(angulo);
        vy = VELOCIDADE_INICIAL * Math.sin(angulo);
    }

    Evento passo(double dt) {
        double xAntes = bolaX;
        bolaX += vx * dt;
        bolaY += vy * dt;
        double meia = BOLA / 2;

        if (bolaY < meia) {
            bolaY = meia;
            vy = Math.abs(vy);
        } else if (bolaY > ALTURA - meia) {
            bolaY = ALTURA - meia;
            vy = -Math.abs(vy);
        }

        // Testa se a bola CRUZOU a face da raquete neste passo (não só se está encostada),
        // para não atravessar quando está rápida.
        double faceEsquerda = RAQUETE_MARGEM + RAQUETE_LARGURA;
        if (vx < 0 && xAntes - meia >= faceEsquerda && bolaX - meia < faceEsquerda
                && encosta(raqueteHost)) {
            bolaX = faceEsquerda + meia;
            rebater(raqueteHost, +1);
            return Evento.BATIDA;
        }
        double faceDireita = LARGURA - RAQUETE_MARGEM - RAQUETE_LARGURA;
        if (vx > 0 && xAntes + meia <= faceDireita && bolaX + meia > faceDireita
                && encosta(raqueteConvidado)) {
            bolaX = faceDireita - meia;
            rebater(raqueteConvidado, -1);
            return Evento.BATIDA;
        }

        if (bolaX < -BOLA) {
            placarConvidado++;
            centralizar();
            return Evento.PONTO_CONVIDADO;
        }
        if (bolaX > LARGURA + BOLA) {
            placarHost++;
            centralizar();
            return Evento.PONTO_HOST;
        }
        return Evento.NADA;
    }

    private boolean encosta(double centroRaquete) {
        return Math.abs(bolaY - centroRaquete) <= RAQUETE_ALTURA / 2 + BOLA / 2;
    }

    /** Quanto mais longe do centro da raquete, mais inclinada a bola volta. */
    private void rebater(double centroRaquete, int direcao) {
        double desvio = Math.max(-1, Math.min(1, (bolaY - centroRaquete) / (RAQUETE_ALTURA / 2)));
        double velocidade = Math.min(Math.hypot(vx, vy) * ACELERACAO, VELOCIDADE_MAXIMA);
        double angulo = desvio * ANGULO_MAXIMO;
        vx = direcao * velocidade * Math.cos(angulo);
        vy = velocidade * Math.sin(angulo);
    }

    static double limitarRaquete(double y) {
        double meia = RAQUETE_ALTURA / 2;
        return Math.max(meia, Math.min(ALTURA - meia, y));
    }
}
