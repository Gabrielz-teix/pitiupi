package pitiupi.pong.GUI;

import pitiupi.pong.control.PongController;
import pitiupi.pong.control.PongView;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * O campo. Desenha o {@link PongView.Quadro} mais recente, escalando o campo 800 × 500 para
 * caber no espaço, e transforma mouse e teclado em posição de raquete.
 */
final class Campo extends JComponent {

    private static final double LARGURA = 800;
    private static final double ALTURA = 500;
    private static final double RAQ_ALT = 90;
    private static final double RAQ_LARG = 12;
    private static final double RAQ_MARGEM = 30;
    private static final double BOLA = 12;

    private static final Color FUNDO = new Color(0x12, 0x1A, 0x24);
    private static final Color LINHA = new Color(0xFF, 0xFF, 0xFF, 60);
    private static final Color MINHA = new Color(0x4F, 0xC3, 0xF7);
    private static final Color DELE = new Color(0xFF, 0xB7, 0x4D);
    private static final Color CRUA = new Color(0xFF, 0x52, 0x52);

    private final PongController controller;
    private PongView.Quadro quadro;
    private double escala = 1;
    private double origemX;
    private double origemY;
    private int teclasCima;
    private int teclasBaixo;

    Campo(PongController controller) {
        this.controller = controller;
        setPreferredSize(new Dimension(800, 500));
        setMinimumSize(new Dimension(400, 250));
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                controller.setMinhaRaquete((e.getY() - origemY) / escala);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                requestFocusInWindow();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        tecla("W", true);
        tecla("UP", true);
        tecla("S", false);
        tecla("DOWN", false);
    }

    private void tecla(String nome, boolean cima) {
        String id = nome + (cima ? "-cima" : "-baixo");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed " + nome), id + "-p");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("released " + nome), id + "-r");
        getActionMap().put(id + "-p", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (cima) {
                    teclasCima = 1;
                } else {
                    teclasBaixo = 1;
                }
                controller.setTeclado(teclasBaixo - teclasCima);
            }
        });
        getActionMap().put(id + "-r", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (cima) {
                    teclasCima = 0;
                } else {
                    teclasBaixo = 0;
                }
                controller.setTeclado(teclasBaixo - teclasCima);
            }
        });
    }

    void mostrar(PongView.Quadro q) {
        quadro = q;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(getParent() != null ? getParent().getBackground() : Color.DARK_GRAY);
        g2.fillRect(0, 0, getWidth(), getHeight());

        PongView.Quadro atual = quadro;
        Font fonteMedidas = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        int linhaMedidas = g2.getFontMetrics(fonteMedidas).getHeight();
        int faixaMedidas = atual == null || atual.medidas().isEmpty() ? 0 : linhaMedidas * 3 + 14;
        desenharMedidas(g2, atual, fonteMedidas, linhaMedidas, faixaMedidas);

        int alturaCampo = getHeight() - faixaMedidas;
        escala = Math.min(getWidth() / LARGURA, alturaCampo / ALTURA);
        origemX = (getWidth() - LARGURA * escala) / 2;
        origemY = (alturaCampo - ALTURA * escala) / 2;
        g2.translate(origemX, origemY);
        g2.scale(escala, escala);
        g2.clip(new Rectangle2D.Double(0, 0, LARGURA, ALTURA));

        g2.setColor(FUNDO);
        g2.fill(new Rectangle2D.Double(0, 0, LARGURA, ALTURA));
        g2.setColor(LINHA);
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{14f, 12f}, 0f));
        g2.drawLine((int) (LARGURA / 2), 0, (int) (LARGURA / 2), (int) ALTURA);

        PongView.Quadro q = quadro;
        if (q == null) {
            g2.dispose();
            return;
        }

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 56));
        g2.setColor(new Color(0xFF, 0xFF, 0xFF, 120));
        centralizado(g2, String.valueOf(q.placarEsquerda()), LARGURA / 4, 70);
        centralizado(g2, String.valueOf(q.placarDireita()), LARGURA * 3 / 4, 70);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        centralizado(g2, q.nomeEsquerda(), LARGURA / 4, 96);
        centralizado(g2, q.nomeDireita(), LARGURA * 3 / 4, 96);

        Color esquerda = q.euNaEsquerda() ? MINHA : DELE;
        Color direita = q.euNaEsquerda() ? DELE : MINHA;
        g2.setColor(esquerda);
        g2.fill(new Rectangle2D.Double(RAQ_MARGEM, q.raqueteEsquerda() - RAQ_ALT / 2, RAQ_LARG, RAQ_ALT));
        g2.setColor(direita);
        g2.fill(new Rectangle2D.Double(LARGURA - RAQ_MARGEM - RAQ_LARG, q.raqueteDireita() - RAQ_ALT / 2, RAQ_LARG, RAQ_ALT));

        if (q.mostrarCrua()) {
            g2.setColor(CRUA);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(q.bolaCruaX() - BOLA / 2, q.bolaCruaY() - BOLA / 2, BOLA, BOLA));
        }
        if (q.bolaVisivel()) {
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(q.bolaX() - BOLA / 2, q.bolaY() - BOLA / 2, BOLA, BOLA));
        }

        if (q.aviso() != null) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, q.aviso().length() <= 2 ? 90 : 34));
            g2.setColor(Color.WHITE);
            centralizado(g2, q.aviso(), LARGURA / 2, ALTURA / 2 + 14);
        }

        g2.dispose();
    }

    /**
     * Medidas da rede numa faixa embaixo do campo (fora da área de jogo, para não esconder a raquete).
     * Primeira linha à esquerda, as demais em duas colunas.
     */
    private void desenharMedidas(Graphics2D g2, PongView.Quadro q, Font fonte, int linha, int faixa) {
        if (faixa == 0) {
            return;
        }
        int topo = getHeight() - faixa;
        g2.setColor(FUNDO);
        g2.fillRect(0, topo, getWidth(), faixa);
        g2.setFont(fonte);
        g2.setColor(new Color(0xE0, 0xE0, 0xE0));
        int ascent = g2.getFontMetrics().getAscent();
        List<String> m = q.medidas();
        int y = topo + 7 + ascent;
        g2.drawString(m.get(0), 12, y);
        int coluna = getWidth() / 2;
        for (int i = 1; i < m.size(); i++) {
            int x = (i % 2 == 1) ? 12 : coluna;
            int yy = y + linha * ((i + 1) / 2);
            g2.drawString(m.get(i), x, yy);
        }
    }

    private static void centralizado(Graphics2D g2, String texto, double x, double y) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(texto, (float) (x - fm.stringWidth(texto) / 2.0), (float) y);
    }
}
