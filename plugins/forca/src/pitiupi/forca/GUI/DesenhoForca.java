package pitiupi.forca.GUI;

import pitiupi.forca.control.ForcaJogo;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Desenha a forca e o boneco. Cada erro desenha uma parte:
 * 1 cabeça, 2 corpo, 3 braço esq., 4 braço dir., 5 perna esq., 6 perna dir.
 */
public class DesenhoForca extends JPanel {

    private int erros = 0;

    public DesenhoForca() {
        setPreferredSize(new Dimension(260, 300));
        setBackground(Color.WHITE);
    }

    public void setErros(int erros) {
        this.erros = erros;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int base = h - 30;
        int poste = w / 2 - 70;
        int topo = 30;
        int corda = w / 2 + 30;

        // a forca
        g2.setColor(new Color(110, 70, 40));
        g2.setStroke(new BasicStroke(6));
        g2.drawLine(poste - 40, base, poste + 80, base);
        g2.drawLine(poste, base, poste, topo);
        g2.drawLine(poste, topo, corda, topo);
        g2.setStroke(new BasicStroke(3));
        g2.drawLine(corda, topo, corda, topo + 30);

        // o boneco
        g2.setColor(erros >= ForcaJogo.MAX_ERROS ? Color.RED : Color.BLACK);
        g2.setStroke(new BasicStroke(4));
        int cabeca = 40;
        int yCabeca = topo + 30;
        int yOmbro = yCabeca + cabeca + 12;
        int yQuadril = yCabeca + cabeca + 75;

        if (erros >= 1) {
            g2.drawOval(corda - cabeca / 2, yCabeca, cabeca, cabeca);
        }
        if (erros >= 2) {
            g2.drawLine(corda, yCabeca + cabeca, corda, yQuadril);
        }
        if (erros >= 3) {
            g2.drawLine(corda, yOmbro, corda - 30, yOmbro + 35);
        }
        if (erros >= 4) {
            g2.drawLine(corda, yOmbro, corda + 30, yOmbro + 35);
        }
        if (erros >= 5) {
            g2.drawLine(corda, yQuadril, corda - 25, yQuadril + 45);
        }
        if (erros >= 6) {
            g2.drawLine(corda, yQuadril, corda + 25, yQuadril + 45);
        }
    }
}
