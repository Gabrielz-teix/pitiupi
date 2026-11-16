package pitiupi.pong.GUI;

import pitiupi.pong.control.PongController;
import pitiupi.pong.control.PongView;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Aba do Pong: desafio no topo, campo no meio, controles de rede e registro embaixo.
 */
public final class PongPanel extends JPanel implements PongView {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PongController controller;
    private final Campo campo;
    private final JLabel texto = new JLabel(" ");
    private final JComboBox<Adversario> adversarios = new JComboBox<>();
    private final JButton desafiar = new JButton("Desafiar");
    private final JButton sair = new JButton("Sair");
    private final JPanel faixaConvite = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
    private final JLabel textoConvite = new JLabel();
    private final JTextArea registro = new JTextArea(4, 40);
    private List<Adversario> onlineNaTela = List.of();
    private boolean estavaEmPartida;

    public PongPanel(PongController controller) {
        super(new BorderLayout());
        this.controller = controller;
        this.campo = new Campo(controller);

        JPanel desafio = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        desafio.add(new JLabel("Adversário:"));
        desafio.add(adversarios);
        desafio.add(desafiar);
        desafio.add(sair);
        desafio.add(texto);
        desafiar.addActionListener(e -> {
            Adversario a = (Adversario) adversarios.getSelectedItem();
            if (a != null) {
                controller.desafiar(a.id());
            }
        });
        sair.addActionListener(e -> controller.sair());

        JButton aceitar = new JButton("Aceitar");
        JButton recusar = new JButton("Recusar");
        aceitar.addActionListener(e -> {
            controller.aceitar();
            campo.requestFocusInWindow();
        });
        recusar.addActionListener(e -> controller.recusar());
        textoConvite.setFont(textoConvite.getFont().deriveFont(Font.BOLD, 15f));
        faixaConvite.setBackground(new Color(0xFF, 0xF3, 0xC4));
        faixaConvite.add(textoConvite);
        faixaConvite.add(aceitar);
        faixaConvite.add(recusar);
        faixaConvite.setVisible(false);

        JPanel topo = new JPanel(new BorderLayout());
        topo.add(desafio, BorderLayout.NORTH);
        topo.add(faixaConvite, BorderLayout.SOUTH);

        JComboBox<Integer> taxa = new JComboBox<>(new Integer[]{60, 30, 20, 10, 5});
        taxa.setSelectedItem(30);
        taxa.addActionListener(e -> controller.setEnvioHz((Integer) taxa.getSelectedItem()));
        JCheckBox interpolar = new JCheckBox("Interpolação", true);
        interpolar.setToolTipText("Desenha 100 ms no passado, deslizando entre dois pacotes. Desligue com poucos envios/s para ver a diferença.");
        interpolar.addActionListener(e -> controller.setInterpolar(interpolar.isSelected()));
        JCheckBox crua = new JCheckBox("Mostrar bola recebida");
        crua.setToolTipText("Círculo vermelho: a última posição que chegou pela rede, sem interpolação (só para o convidado).");
        crua.addActionListener(e -> controller.setMostrarCrua(crua.isSelected()));
        JComboBox<String> perda = new JComboBox<>(new String[]{"0%", "5%", "20%", "50%"});
        JComboBox<String> atraso = new JComboBox<>(new String[]{"0 ms", "50 ms", "150 ms", "300 ms"});
        JComboBox<String> jitter = new JComboBox<>(new String[]{"0 ms", "30 ms", "100 ms"});
        Runnable aplicarRede = () -> controller.configurarRede(
                numero(perda), numero(atraso), numero(jitter));
        perda.addActionListener(e -> aplicarRede.run());
        atraso.addActionListener(e -> aplicarRede.run());
        jitter.addActionListener(e -> aplicarRede.run());

        JPanel rede = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        rede.add(new JLabel("Envios/s:"));
        rede.add(taxa);
        rede.add(interpolar);
        rede.add(crua);
        rede.add(new JLabel("   Simular neste PC → perda:"));
        rede.add(perda);
        rede.add(new JLabel("atraso:"));
        rede.add(atraso);
        rede.add(new JLabel("jitter:"));
        rede.add(jitter);

        registro.setEditable(false);
        registro.setLineWrap(true);
        registro.setWrapStyleWord(true);
        registro.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane rolagem = new JScrollPane(registro);
        rolagem.setBorder(BorderFactory.createTitledBorder("Eventos"));

        JPanel baixo = new JPanel(new BorderLayout());
        baixo.add(rede, BorderLayout.NORTH);
        baixo.add(rolagem, BorderLayout.CENTER);

        add(topo, BorderLayout.NORTH);
        add(campo, BorderLayout.CENTER);
        add(baixo, BorderLayout.SOUTH);
    }

    private static int numero(JComboBox<String> combo) {
        return Integer.parseInt(((String) Objects.requireNonNull(combo.getSelectedItem())).replaceAll("\\D", ""));
    }

    @Override
    public void quadro(Quadro quadro) {
        campo.mostrar(quadro);
    }

    @Override
    public void status(Status s) {
        texto.setText(s.texto());
        if (!s.online().equals(onlineNaTela)) {
            Adversario selecionado = (Adversario) adversarios.getSelectedItem();
            onlineNaTela = s.online();
            adversarios.removeAllItems();
            for (Adversario a : s.online()) {
                adversarios.addItem(a);
                if (selecionado != null && selecionado.id().equals(a.id())) {
                    adversarios.setSelectedItem(a);
                }
            }
        }
        desafiar.setEnabled(s.livre() && !s.online().isEmpty());
        adversarios.setEnabled(s.livre());
        sair.setEnabled(s.emPartida());
        faixaConvite.setVisible(s.convitePendenteDe() != null);
        if (s.convitePendenteDe() != null) {
            textoConvite.setText(s.convitePendenteDe() + " te desafiou para uma partida de Pong!");
        }
        boolean emPartida = s.emPartida();
        if (emPartida && !estavaEmPartida) {
            campo.requestFocusInWindow();
        }
        estavaEmPartida = emPartida;
        revalidate();
    }

    @Override
    public void registrar(String linha) {
        Runnable r = () -> {
            registro.append(LocalTime.now().format(HORA) + "  " + linha + "\n");
            registro.setCaretPosition(registro.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
