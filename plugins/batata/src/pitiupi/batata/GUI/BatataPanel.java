package pitiupi.batata.GUI;

import pitiupi.batata.control.BatataController;
import pitiupi.batata.control.BatataView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Aba do jogo: título, batata, botões de passe e o registro do que passou pela rede.
 */
public final class BatataPanel extends JPanel implements BatataView {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_LINHAS = 600;
    private static final Color FUNDO_QUENTE = new Color(0xFF, 0xE4, 0xD6);

    private final BatataController controller;
    private final JPanel topo = new JPanel();
    private final JLabel titulo = new JLabel(" ");
    private final JLabel detalhe = new JLabel(" ");
    private final JLabel caminho = new JLabel(" ");
    private final JLabel segurando = new JLabel(" ");
    private final BatataDesenho desenho = new BatataDesenho();
    private final JPanel botoes = new JPanel();
    private final JTextArea registro = new JTextArea();
    private final Color fundoNormal;

    private List<JogadorNaTela> jogadoresNaTela;
    private boolean podePassarNaTela;

    public BatataPanel(BatataController controller) {
        super(new BorderLayout());
        this.controller = controller;
        this.fundoNormal = topo.getBackground();

        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));
        topo.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 24f));
        detalhe.setFont(detalhe.getFont().deriveFont(15f));
        caminho.setFont(caminho.getFont().deriveFont(Font.ITALIC, 13f));
        for (JLabel l : List.of(titulo, detalhe, caminho)) {
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
            topo.add(l);
            topo.add(Box.createVerticalStrut(4));
        }

        botoes.setLayout(new BoxLayout(botoes, BoxLayout.Y_AXIS));
        JPanel lado = new JPanel(new BorderLayout());
        lado.setBorder(BorderFactory.createTitledBorder("Passar a batata para"));
        JScrollPane rolagemBotoes = new JScrollPane(botoes);
        rolagemBotoes.setBorder(BorderFactory.createEmptyBorder());
        lado.add(rolagemBotoes, BorderLayout.CENTER);
        lado.setPreferredSize(new Dimension(260, 200));

        JPanel meio = new JPanel(new BorderLayout());
        meio.add(desenho, BorderLayout.CENTER);
        meio.add(lado, BorderLayout.EAST);

        JButton nova = new JButton("Nova partida");
        nova.addActionListener(e -> controller.novaPartida());
        JCheckBox perda = new JCheckBox("Simular perda de pacotes (40%)");
        perda.setToolTipText("Joga fora, de propósito, parte das batatas e ACKs que ESTE PC envia. Serve para ver a retransmissão.");
        perda.addActionListener(e -> controller.setSimularPerda(perda.isSelected()));
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controles.add(nova);
        controles.add(perda);
        controles.add(segurando);

        registro.setEditable(false);
        registro.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        registro.setRows(9);
        registro.setLineWrap(true);
        registro.setWrapStyleWord(true);
        JScrollPane rolagemRegistro = new JScrollPane(registro);
        rolagemRegistro.setBorder(BorderFactory.createTitledBorder("O que passou pela rede"));

        JPanel baixo = new JPanel(new BorderLayout());
        baixo.add(controles, BorderLayout.NORTH);
        baixo.add(rolagemRegistro, BorderLayout.CENTER);

        JPanel cima = new JPanel(new BorderLayout());
        cima.add(topo, BorderLayout.NORTH);
        cima.add(meio, BorderLayout.CENTER);

        JSplitPane divisao = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cima, baixo);
        divisao.setResizeWeight(0.65);
        divisao.setBorder(BorderFactory.createEmptyBorder());
        add(divisao, BorderLayout.CENTER);
    }

    @Override
    public void mostrar(Estado e) {
        titulo.setText(e.titulo());
        detalhe.setText(e.detalhe());
        caminho.setText(textoCaminho(e.caminho()));
        segurando.setText(e.segurandoMs() > 0
                ? String.format("Com você há %.1f s", e.segurandoMs() / 1000.0)
                : " ");
        boolean quente = e.fase() == Fase.COMIGO;
        topo.setBackground(quente ? FUNDO_QUENTE : fundoNormal);
        topo.setOpaque(quente);
        titulo.setForeground(quente ? new Color(0xB0, 0x20, 0x10) : detalhe.getForeground());
        desenho.setFase(e.fase());

        if (!e.jogadores().equals(jogadoresNaTela) || e.podePassar() != podePassarNaTela) {
            jogadoresNaTela = e.jogadores();
            podePassarNaTela = e.podePassar();
            montarBotoes(e);
        }
        topo.repaint();
    }

    /** Só os últimos saltos cabem na linha; o caminho inteiro fica no registro quando a rodada acaba. */
    private static String textoCaminho(List<String> c) {
        if (c.isEmpty()) {
            return " ";
        }
        int passes = c.size() - 1;
        int mostrar = 9;
        String trecho = c.size() <= mostrar
                ? String.join(" → ", c)
                : "… → " + String.join(" → ", c.subList(c.size() - mostrar, c.size()));
        return "Caminho (" + passes + (passes == 1 ? " passe" : " passes") + "): " + trecho;
    }

    private void montarBotoes(Estado e) {
        botoes.removeAll();
        for (JogadorNaTela j : e.jogadores()) {
            StringBuilder texto = new StringBuilder(j.nome());
            if (j.eu()) {
                texto.append(" (você)");
            }
            if (!j.online()) {
                texto.append(" — saiu");
            } else if (!j.vivo()) {
                texto.append(" — eliminado");
            }
            if (j.comBatata()) {
                texto.append("  ◄ batata");
            }
            JButton b = new JButton(texto.toString());
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            b.setEnabled(e.podePassar() && !j.eu() && j.vivo() && j.online());
            b.addActionListener(ev -> controller.passarPara(j.id()));
            botoes.add(b);
            botoes.add(Box.createVerticalStrut(6));
        }
        botoes.revalidate();
        botoes.repaint();
    }

    @Override
    public void registrar(String linha) {
        Runnable r = () -> {
            registro.append(LocalTime.now().format(HORA) + "  " + linha + "\n");
            int excesso = registro.getLineCount() - MAX_LINHAS;
            if (excesso > 0) {
                try {
                    registro.replaceRange("", 0, registro.getLineEndOffset(excesso - 1));
                } catch (Exception ignored) {
                    // só limpeza do histórico
                }
            }
            registro.setCaretPosition(registro.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
