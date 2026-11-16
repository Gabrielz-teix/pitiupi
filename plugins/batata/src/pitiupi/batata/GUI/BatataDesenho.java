package pitiupi.batata.GUI;

import pitiupi.batata.control.BatataView.Fase;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Random;

/**
 * A batata desenhada. Treme e solta fumaça quando está com você; vira explosão no BUM.
 */
final class BatataDesenho extends JComponent {

    private static final Color BATATA = new Color(0xC6, 0x8B, 0x59);
    private static final Color BATATA_QUENTE = new Color(0xE8, 0x5D, 0x2A);
    private static final Color MANCHA = new Color(0x8A, 0x5A, 0x34);

    private final Random random = new Random();
    private final Timer animacao = new Timer(50, e -> repaint());
    private Fase fase = Fase.SEM_PARTIDA;
    private long quadro;

    BatataDesenho() {
        setPreferredSize(new Dimension(280, 220));
        setMinimumSize(new Dimension(160, 140));
    }

    void setFase(Fase nova) {
        if (nova == fase) {
            return;
        }
        fase = nova;
        if (fase == Fase.COMIGO || fase == Fase.PASSANDO) {
            animacao.start();
        } else {
            animacao.stop();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        quadro++;

        int w = getWidth();
        int h = getHeight();
        double pw = Math.min(w * 0.75, 240);
        double ph = pw * 0.68;
        double cx = w / 2.0;
        double cy = h / 2.0 + 10;

        if (fase == Fase.EXPLODIU) {
            desenharExplosao(g2, cx, cy, Math.min(w, h) * 0.45);
            g2.dispose();
            return;
        }
        if (fase == Fase.COMIGO) {
            cx += random.nextInt(9) - 4;
            cy += random.nextInt(7) - 3;
        } else if (fase == Fase.PASSANDO) {
            cx += Math.sin(quadro * 0.9) * 6;
        }

        Color cor = fase == Fase.COMIGO ? BATATA_QUENTE : BATATA;
        if (fase == Fase.SEM_PARTIDA) {
            cor = new Color(0xB8, 0xA8, 0x98);
        }
        double x = cx - pw / 2;
        double y = cy - ph / 2;

        g2.setColor(new Color(0, 0, 0, 40));
        g2.fill(new Ellipse2D.Double(x + 8, y + ph - 6, pw - 16, 16));

        g2.setPaint(new GradientPaint((float) x, (float) y, cor.brighter(), (float) (x + pw), (float) (y + ph), cor.darker()));
        Path2D.Double corpo = new Path2D.Double();
        corpo.moveTo(x + pw * 0.10, y + ph * 0.55);
        corpo.curveTo(x, y + ph * 0.10, x + pw * 0.45, y - ph * 0.08, x + pw * 0.70, y + ph * 0.08);
        corpo.curveTo(x + pw * 1.02, y + ph * 0.22, x + pw * 1.02, y + ph * 0.80, x + pw * 0.68, y + ph * 0.95);
        corpo.curveTo(x + pw * 0.40, y + ph * 1.06, x + pw * 0.18, y + ph * 0.95, x + pw * 0.10, y + ph * 0.55);
        corpo.closePath();
        g2.fill(corpo);
        g2.setColor(cor.darker().darker());
        g2.setStroke(new BasicStroke(2f));
        g2.draw(corpo);

        g2.setColor(MANCHA);
        double[][] manchas = {{0.30, 0.35}, {0.55, 0.25}, {0.70, 0.55}, {0.40, 0.70}, {0.22, 0.58}};
        for (double[] m : manchas) {
            g2.fill(new Ellipse2D.Double(x + pw * m[0], y + ph * m[1], pw * 0.045, ph * 0.05));
        }

        if (fase == Fase.COMIGO) {
            g2.setColor(new Color(0xD0, 0x30, 0x20, 170));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 3; i++) {
                double sx = cx - pw * 0.2 + i * pw * 0.2;
                Path2D.Double fumaca = new Path2D.Double();
                double desloc = Math.sin(quadro * 0.4 + i) * 5;
                fumaca.moveTo(sx, y - 6);
                fumaca.curveTo(sx - 10 + desloc, y - 22, sx + 10 + desloc, y - 34, sx + desloc, y - 50);
                g2.draw(fumaca);
            }
        }
        g2.dispose();
    }

    private void desenharExplosao(Graphics2D g2, double cx, double cy, double raio) {
        Polygon estrela = new Polygon();
        int pontas = 14;
        for (int i = 0; i < pontas * 2; i++) {
            double r = i % 2 == 0 ? raio : raio * 0.55;
            double a = Math.PI * i / pontas;
            estrela.addPoint((int) (cx + Math.cos(a) * r), (int) (cy + Math.sin(a) * r));
        }
        g2.setColor(new Color(0xFF, 0xC8, 0x2E));
        g2.fill(estrela);
        g2.setColor(new Color(0xE0, 0x40, 0x20));
        g2.setStroke(new BasicStroke(3f));
        g2.draw(estrela);

        g2.setFont(getFont().deriveFont(Font.BOLD, (float) Math.max(20, raio * 0.42)));
        FontMetrics fm = g2.getFontMetrics();
        String texto = "BUM!";
        g2.setColor(new Color(0xB0, 0x20, 0x10));
        g2.drawString(texto, (float) (cx - fm.stringWidth(texto) / 2.0), (float) (cy + fm.getAscent() / 2.8));
    }
}
