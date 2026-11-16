package pitiupi.forca.GUI;

import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.application.presence.UserPeerView;
import pitiupi.control.ConnectionManager;
import pitiupi.forca.control.ForcaJogo;
import pitiupi.forca.net.ForcaMessage;
import pitiupi.presentation.shell.PeerIpv4;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela e fluxo do jogo da forca.
 *
 * Quem desafia escolhe a palavra e fica com ela. O outro só recebe os tracinhos.
 * Cada letra chutada vai para o PC de quem escolheu, que confere e devolve o resultado.
 * Tudo por TCP: uma letra não pode se perder nem chegar fora de ordem.
 */
public class ForcaPanel extends JPanel {

    private static final String LIVRE = "LIVRE";
    private static final String ESCOLHI = "ESCOLHI";          // eu escolhi a palavra, o outro adivinha
    private static final String ADIVINHANDO = "ADIVINHANDO";  // eu estou adivinhando

    private final ConnectionManager cm;
    private final String meuId;
    private final Runnable abrirAba;

    // Uma thread só para enviar: conectar por TCP pode demorar e não pode travar a tela.
    // Sendo uma só, as mensagens saem na mesma ordem em que foram pedidas.
    private final ExecutorService envios = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "forca-envio");
        t.setDaemon(true);
        return t;
    });

    private String modo = LIVRE;
    private String advId;
    private String advNome;
    private ForcaJogo jogo;              // só no PC de quem escolheu a palavra
    private String letrasTentadas = "";  // no PC de quem adivinha
    private boolean esperandoResposta;

    // o que aparece na tela
    private String mascaraAtual = "";
    private String dicaAtual = "";
    private String erradasAtual = "";
    private int errosAtual = 0;

    private final JLabel status = new JLabel("Escolha alguém, digite uma palavra e clique em Desafiar.");
    private final JLabel mascara = new JLabel(" ");
    private final JLabel dica = new JLabel(" ");
    private final JLabel erradas = new JLabel(" ");
    private final DesenhoForca desenho = new DesenhoForca();
    private final JPanel letras = new JPanel(new GridLayout(2, 13, 4, 4));
    private final JButton[] botoesLetra = new JButton[26];
    private final JComboBox<Pessoa> pessoas = new JComboBox<>();
    private final JPasswordField campoPalavra = new JPasswordField(12);
    private final JTextField campoDica = new JTextField(14);
    private final JButton desafiar = new JButton("Desafiar");
    private final JButton sair = new JButton("Sair da partida");
    private final JTextArea historico = new JTextArea(5, 40);

    /** Uma pessoa da lista de usuários. */
    private record Pessoa(String id, String nome) {
        @Override
        public String toString() {
            return nome;
        }
    }

    public ForcaPanel(ConnectionManager cm, Runnable abrirAba) {
        super(new BorderLayout(8, 8));
        this.cm = cm;
        this.meuId = cm.getHeartbeatManager().getInstanceId();
        this.abrirAba = abrirAba;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // topo: status
        status.setFont(status.getFont().deriveFont(Font.BOLD, 18f));
        add(status, BorderLayout.NORTH);

        // meio: forca à esquerda, palavra à direita
        mascara.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
        dica.setFont(dica.getFont().deriveFont(16f));
        erradas.setFont(erradas.getFont().deriveFont(16f));
        erradas.setForeground(new Color(180, 30, 30));
        JPanel palavra = new JPanel(new GridLayout(3, 1));
        palavra.add(mascara);
        palavra.add(dica);
        palavra.add(erradas);
        JPanel meio = new JPanel(new BorderLayout(10, 10));
        meio.add(desenho, BorderLayout.WEST);
        meio.add(palavra, BorderLayout.CENTER);
        add(meio, BorderLayout.CENTER);

        // baixo: letras, desafio e histórico
        for (int i = 0; i < 26; i++) {
            char letra = (char) ('A' + i);
            JButton b = new JButton(String.valueOf(letra));
            b.setMargin(new java.awt.Insets(2, 2, 2, 2));
            b.addActionListener(e -> chutar(letra));
            botoesLetra[i] = b;
            letras.add(b);
        }

        JPanel desafio = new JPanel(new FlowLayout(FlowLayout.LEFT));
        desafio.add(new JLabel("Adversário:"));
        desafio.add(pessoas);
        desafio.add(new JLabel("Palavra:"));
        desafio.add(campoPalavra);
        desafio.add(new JLabel("Dica:"));
        desafio.add(campoDica);
        desafio.add(desafiar);
        desafio.add(sair);
        desafiar.addActionListener(e -> desafiar());
        sair.addActionListener(e -> sair());

        historico.setEditable(false);
        historico.setLineWrap(true);
        historico.setWrapStyleWord(true);
        JScrollPane rolagem = new JScrollPane(historico);
        rolagem.setBorder(BorderFactory.createTitledBorder("Histórico"));

        JPanel baixo = new JPanel(new BorderLayout(5, 5));
        baixo.add(letras, BorderLayout.NORTH);
        baixo.add(desafio, BorderLayout.CENTER);
        baixo.add(rolagem, BorderLayout.SOUTH);
        add(baixo, BorderLayout.SOUTH);

        // a lista de pessoas muda quando alguém entra ou sai da rede
        new Timer(2000, e -> atualizarPessoas()).start();
        atualizarPessoas();
        atualizarTela();
    }

    // ------------------------------------------------------------------
    // Botões
    // ------------------------------------------------------------------

    private void desafiar() {
        Pessoa p = (Pessoa) pessoas.getSelectedItem();
        String texto = new String(campoPalavra.getPassword());
        if (p == null) {
            log("Não tem ninguém na rede para desafiar.");
            return;
        }
        if (!ForcaJogo.palavraValida(texto)) {
            log("A palavra precisa ter pelo menos 3 letras.");
            return;
        }
        jogo = new ForcaJogo(texto);
        advId = p.id();
        advNome = p.nome();
        modo = ESCOLHI;
        mostrar(jogo.mascara(), campoDica.getText().trim(), "", 0);

        ForcaMessage m = new ForcaMessage(ForcaMessage.CONVITE, meuId, cm.getUserName(), advId);
        m.setMascara(jogo.mascara());
        m.setDica(dicaAtual);
        enviar(m);

        campoPalavra.setText("");
        campoDica.setText("");
        log("Você desafiou " + advNome + " com a palavra " + jogo.palavra() + ".");
        atualizarTela();
    }

    private void chutar(char letra) {
        if (!modo.equals(ADIVINHANDO) || esperandoResposta) {
            return;
        }
        letrasTentadas += letra;
        esperandoResposta = true;
        ForcaMessage m = new ForcaMessage(ForcaMessage.LETRA, meuId, cm.getUserName(), advId);
        m.setLetra(letra);
        enviar(m);
        atualizarTela();
    }

    private void sair() {
        if (modo.equals(LIVRE)) {
            return;
        }
        enviar(new ForcaMessage(ForcaMessage.SAIR, meuId, cm.getUserName(), advId));
        log("Você saiu da partida.");
        modo = LIVRE;
        atualizarTela();
    }

    // ------------------------------------------------------------------
    // Mensagens que chegam (o plugin chama isto na thread da tela)
    // ------------------------------------------------------------------

    public void receber(ForcaMessage m) {
        if (!meuId.equals(m.getParaId())) {
            return;
        }
        switch (m.getTipo()) {
            case ForcaMessage.CONVITE -> recebeuConvite(m);
            case ForcaMessage.OCUPADO -> {
                if (modo.equals(ESCOLHI) && m.getDeId().equals(advId)) {
                    log(advNome + " já está em outra partida. Tente mais tarde.");
                    modo = LIVRE;
                }
            }
            case ForcaMessage.LETRA -> recebeuLetra(m);
            case ForcaMessage.RESULTADO -> recebeuResultado(m);
            case ForcaMessage.SAIR -> {
                if (!modo.equals(LIVRE) && m.getDeId().equals(advId)) {
                    log(advNome + " saiu da partida.");
                    modo = LIVRE;
                }
            }
            default -> {
            }
        }
        atualizarTela();
    }

    private void recebeuConvite(ForcaMessage m) {
        if (!modo.equals(LIVRE)) {
            // já estou jogando: aviso que estou ocupado
            ForcaMessage resposta = new ForcaMessage(ForcaMessage.OCUPADO, meuId, cm.getUserName(), m.getDeId());
            enviarPara(resposta, m.getDeId(), m.getDeNome());
            return;
        }
        advId = m.getDeId();
        advNome = m.getDeNome();
        modo = ADIVINHANDO;
        letrasTentadas = "";
        esperandoResposta = false;
        mostrar(m.getMascara(), m.getDica(), "", 0);
        abrirAba.run();
        log(advNome + " te desafiou! A palavra tem " + m.getMascara().replace(" ", "").length() + " letras.");
    }

    /** Só acontece no PC de quem escolheu a palavra. */
    private void recebeuLetra(ForcaMessage m) {
        if (!modo.equals(ESCOLHI) || !m.getDeId().equals(advId)) {
            return;
        }
        char letra = m.getLetra();
        jogo.tentar(letra);
        boolean acertou = jogo.palavra().indexOf(letra) >= 0;
        log(advNome + " chutou " + letra + (acertou ? " e acertou." : " e errou."));

        ForcaMessage r = new ForcaMessage(ForcaMessage.RESULTADO, meuId, cm.getUserName(), advId);
        r.setMascara(jogo.mascara());
        r.setErros(jogo.erros());
        r.setLetrasErradas(jogo.erradas());
        r.setFim("");
        if (jogo.ganhou() || jogo.perdeu()) {
            r.setFim(jogo.ganhou() ? "GANHOU" : "PERDEU");
            r.setPalavra(jogo.palavra());
            log(jogo.ganhou() ? advNome + " descobriu a palavra!" : advNome + " não descobriu a palavra. Ponto seu!");
            modo = LIVRE;
        }
        enviar(r);
        mostrar(jogo.mascara(), dicaAtual, jogo.erradas(), jogo.erros());
    }

    /** Só acontece no PC de quem está adivinhando. */
    private void recebeuResultado(ForcaMessage m) {
        if (!modo.equals(ADIVINHANDO) || !m.getDeId().equals(advId)) {
            return;
        }
        esperandoResposta = false;
        mostrar(m.getMascara(), dicaAtual, m.getLetrasErradas(), m.getErros());
        if ("GANHOU".equals(m.getFim())) {
            log("Você acertou! A palavra era " + m.getPalavra() + ". Agora é sua vez de escolher uma.");
            modo = LIVRE;
        } else if ("PERDEU".equals(m.getFim())) {
            mascaraAtual = m.getPalavra();
            log("Você foi enforcado! A palavra era " + m.getPalavra() + ". Agora é sua vez de escolher uma.");
            modo = LIVRE;
        }
        if (modo.equals(LIVRE)) {
            selecionar(advId); // já deixa o adversário escolhido para a revanche
        }
    }

    // ------------------------------------------------------------------
    // Rede
    // ------------------------------------------------------------------

    private void enviar(ForcaMessage m) {
        enviarPara(m, advId, advNome);
    }

    private void enviarPara(ForcaMessage m, String id, String nome) {
        String ip = ipDe(id);
        envios.execute(() -> {
            boolean ok = false;
            try {
                InetAddress endereco = InetAddress.getByName(ip);
                ok = MessageDispatcher.send(cm, m, SendTarget.singlePeer(endereco), TransportMode.TCP) > 0;
            } catch (Exception ex) {
                ok = false;
            }
            if (!ok) {
                SwingUtilities.invokeLater(() -> falhouEnvio(m, nome));
            }
        });
    }

    private void falhouEnvio(ForcaMessage m, String nome) {
        log("Não consegui mandar a mensagem para " + nome + " por TCP. Ele ainda está na rede?");
        if (ForcaMessage.LETRA.equals(m.getTipo())) {
            // devolve a letra para poder tentar de novo
            letrasTentadas = letrasTentadas.replace(String.valueOf(m.getLetra()), "");
            esperandoResposta = false;
        }
        if (ForcaMessage.CONVITE.equals(m.getTipo())) {
            modo = LIVRE;
        }
        atualizarTela();
    }

    private String ipDe(String id) {
        for (UserPeerView v : cm.getPresenceService().listPeerViews()) {
            if (id != null && id.equals(v.getInstanceId())) {
                return v.getInetAddress().map(PeerIpv4::fromInetAddress).orElse("");
            }
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Tela
    // ------------------------------------------------------------------

    private void mostrar(String novaMascara, String novaDica, String novasErradas, int novosErros) {
        mascaraAtual = novaMascara;
        dicaAtual = novaDica == null ? "" : novaDica;
        erradasAtual = novasErradas == null ? "" : novasErradas;
        errosAtual = novosErros;
    }

    private void atualizarTela() {
        switch (modo) {
            case ESCOLHI -> status.setText(advNome + " está tentando adivinhar a sua palavra: " + jogo.palavra());
            case ADIVINHANDO -> status.setText(esperandoResposta
                    ? "Esperando " + advNome + " conferir a letra…"
                    : "Adivinhe a palavra de " + advNome + ". Clique numa letra.");
            default -> status.setText(mascaraAtual.isEmpty()
                    ? "Escolha alguém, digite uma palavra e clique em Desafiar."
                    : "Partida encerrada. Escolha uma palavra e desafie de novo!");
        }

        mascara.setText(mascaraAtual.isEmpty() ? " " : mascaraAtual);
        dica.setText(dicaAtual.isEmpty() ? " " : "Dica: " + dicaAtual);
        erradas.setText("Erros: " + errosAtual + " de " + ForcaJogo.MAX_ERROS
                + (erradasAtual.isEmpty() ? "" : "   Letras erradas: " + String.join(" ", erradasAtual.split(""))));
        desenho.setErros(errosAtual);

        boolean podeChutar = modo.equals(ADIVINHANDO) && !esperandoResposta;
        for (int i = 0; i < 26; i++) {
            char letra = (char) ('A' + i);
            botoesLetra[i].setEnabled(podeChutar && letrasTentadas.indexOf(letra) < 0);
        }

        boolean livre = modo.equals(LIVRE);
        pessoas.setEnabled(livre);
        campoPalavra.setEnabled(livre);
        campoDica.setEnabled(livre);
        desafiar.setEnabled(livre && pessoas.getItemCount() > 0);
        sair.setEnabled(!livre);
    }

    private void atualizarPessoas() {
        List<Pessoa> agora = new ArrayList<>();
        for (UserPeerView v : cm.getPresenceService().listPeerViews()) {
            if (v.isBroadcastTarget() || v.isSelf() || v.getInstanceId() == null) {
                continue;
            }
            agora.add(new Pessoa(v.getInstanceId(), v.getDisplayName()));
        }
        List<Pessoa> antes = new ArrayList<>();
        for (int i = 0; i < pessoas.getItemCount(); i++) {
            antes.add(pessoas.getItemAt(i));
        }
        if (!agora.equals(antes)) {
            Pessoa selecionada = (Pessoa) pessoas.getSelectedItem();
            pessoas.removeAllItems();
            for (Pessoa p : agora) {
                pessoas.addItem(p);
            }
            if (selecionada != null) {
                selecionar(selecionada.id());
            }
        }
        atualizarTela();
    }

    private void selecionar(String id) {
        for (int i = 0; i < pessoas.getItemCount(); i++) {
            if (pessoas.getItemAt(i).id().equals(id)) {
                pessoas.setSelectedIndex(i);
            }
        }
    }

    private void log(String texto) {
        historico.append(texto + "\n");
        historico.setCaretPosition(historico.getDocument().getLength());
    }
}
