package pitiupi.pong.control;

import pitiupi.application.presence.UserPeerView;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.pong.net.ConviteMessage;
import pitiupi.pong.net.EntradaMessage;
import pitiupi.pong.net.EstadoMessage;
import pitiupi.pong.net.PongMsg;
import pitiupi.pong.net.RespostaMessage;
import pitiupi.pong.net.SairMessage;
import pitiupi.presentation.shell.PeerIpv4;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Convite, laço do jogo, envio em tempo real e medidas. Não desenha nada.
 *
 * <p>Tudo roda na thread do Swing: o laço é um {@link javax.swing.Timer} de ~60 quadros por
 * segundo e o plugin entrega as mensagens com {@code invokeLater}.</p>
 *
 * <h2>Quem faz o quê</h2>
 * <ul>
 *   <li><b>Host</b> (quem desafiou): roda a {@link Fisica} e manda {@link EstadoMessage}.</li>
 *   <li><b>Convidado</b>: manda só a própria raquete ({@link EntradaMessage}) e desenha o estado
 *       que chega, interpolado. A raquete dele é desenhada na hora, sem esperar a rede.</li>
 * </ul>
 */
public final class PongController {

    private enum Papel { NENHUM, HOST, CONVIDADO }

    private enum Fase { LIVRE, CONVIDANDO, CONVITE_RECEBIDO, EM_JOGO, FIM }

    private static final int TICK_MS = 10;
    private static final long TIMEOUT_MS = 5_000;
    private static final int CONVITE_INTERVALO_MS = 500;
    private static final int CONVITE_TENTATIVAS = 24;
    private static final long CONTAGEM_MS = 3_000;
    private static final long PAUSA_PONTO_MS = 1_200;
    private static final long ATRASO_INTERPOLACAO_MS = 100;
    private static final long ENVIO_APOS_FIM_MS = 3_000;
    private static final double VELOCIDADE_TECLADO = 520;

    private final ConnectionManager connectionManager;
    private final Rede rede;
    private final String meuId;
    private final Random random = new Random();
    private PongView view;

    private Papel papel = Papel.NENHUM;
    private Fase fase = Fase.LIVRE;
    private String partidaId;
    private String advId;
    private String advNome;
    private String advIp;

    // host
    private Fisica fisica = new Fisica();
    private int faseJogo = EstadoMessage.CONTAGEM;
    private long faseAteNanos;
    private int saque;
    private int proximoSaque = 1;
    private double raqueteAdversario = Fisica.ALTURA / 2;

    // convidado
    private EstadoMessage ultimoEstado;
    private ConviteMessage convitePendente;
    private RespostaMessage respostaDada;

    // os dois
    private double minhaRaquete = Fisica.ALTURA / 2;
    private int teclado;
    private int envioHz = 30;
    private boolean interpolar = true;
    private boolean mostrarCrua;
    private int seqEnvio;
    private long ultimoEnvioNanos;
    private boolean temEco;
    private long ecoValor;
    private long ecoChegouMs;
    private long ultimoRecebidoMs;
    private long fimNanos;
    private long ultimoTickNanos = System.nanoTime();
    private long ultimoStatusNanos;
    private int convitesEnviados;

    private final Estatisticas estatisticas = new Estatisticas();
    private final Interpolador interpolador = new Interpolador();
    private final Timer laco;
    private final Timer conviteTimer;

    public PongController(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.rede = new Rede(connectionManager);
        this.meuId = connectionManager.getHeartbeatManager().getInstanceId();
        this.view = new PongView() {
            @Override
            public void quadro(Quadro quadro) {
            }

            @Override
            public void status(Status status) {
            }

            @Override
            public void registrar(String linha) {
            }
        };
        laco = new Timer(TICK_MS, e -> tick());
        laco.start();
        conviteTimer = new Timer(CONVITE_INTERVALO_MS, e -> repetirConvite());
    }

    public void setView(PongView view) {
        this.view = view;
        atualizarStatus();
    }

    // =====================================================================
    // Ações da pessoa
    // =====================================================================

    public void desafiar(String id) {
        if (fase != Fase.LIVRE && fase != Fase.FIM) {
            return;
        }
        PongView.Adversario alvo = null;
        for (PongView.Adversario a : online()) {
            if (a.id().equals(id)) {
                alvo = a;
            }
        }
        if (alvo == null) {
            registrar("Essa pessoa não está mais na rede.");
            return;
        }
        papel = Papel.NENHUM;
        partidaId = Long.toHexString(random.nextLong() & 0xffffffL);
        advId = alvo.id();
        advNome = alvo.nome();
        advIp = ipDe(advId);
        fase = Fase.CONVIDANDO;
        convitesEnviados = 0;
        registrar("Desafio enviado para " + advNome + " (" + advIp + "). Esperando resposta…");
        enviarConvite();
        conviteTimer.restart();
        atualizarStatus();
    }

    public void aceitar() {
        if (fase != Fase.CONVITE_RECEBIDO || convitePendente == null) {
            return;
        }
        ConviteMessage c = convitePendente;
        convitePendente = null;
        iniciarSessao(c.getPartidaId(), c.getDeId(), c.getDeNome(), Papel.CONVIDADO);
        respostaDada = new RespostaMessage(partidaId, meuId, advId, true, meuNome(), null);
        rede.enviarSemSimulacao(respostaDada, advIp);
        registrar("Você aceitou. " + advNome + " é o host: a bola é calculada no PC dele, e você vê o que ele manda.");
    }

    public void recusar() {
        if (fase != Fase.CONVITE_RECEBIDO || convitePendente == null) {
            return;
        }
        ConviteMessage c = convitePendente;
        convitePendente = null;
        rede.enviarSemSimulacao(new RespostaMessage(c.getPartidaId(), meuId, c.getDeId(), false, meuNome(), "recusou"),
                ipDe(c.getDeId()));
        fase = Fase.LIVRE;
        registrar("Você recusou o desafio de " + c.getDeNome() + ".");
        atualizarStatus();
    }

    /** Botão "Sair": cancela o convite ou sai da partida. */
    public void sair() {
        if (fase == Fase.CONVIDANDO) {
            conviteTimer.stop();
            fase = Fase.LIVRE;
            registrar("Desafio cancelado.");
            atualizarStatus();
            return;
        }
        if (papel == Papel.NENHUM) {
            return;
        }
        if (advId != null) {
            SairMessage s = new SairMessage(partidaId, meuId, advId);
            for (int i = 0; i < 3; i++) {
                rede.enviarSemSimulacao(s, advIp);
            }
        }
        encerrar("Você saiu da partida.");
    }

    public void setMinhaRaquete(double y) {
        minhaRaquete = Fisica.limitarRaquete(y);
    }

    /** −1 sobe, +1 desce, 0 parado. */
    public void setTeclado(int direcao) {
        teclado = direcao;
    }

    public void setEnvioHz(int hz) {
        envioHz = Math.max(1, hz);
        registrar("Agora este PC envia " + envioHz + " pacotes por segundo.");
    }

    public void setInterpolar(boolean ligado) {
        interpolar = ligado;
        registrar(ligado
                ? "Interpolação ligada: desenha " + ATRASO_INTERPOLACAO_MS + " ms no passado, deslizando entre dois pacotes."
                : "Interpolação desligada: desenha o último pacote que chegou, do jeito que chegou.");
    }

    public void setMostrarCrua(boolean ligado) {
        mostrarCrua = ligado;
    }

    public void configurarRede(int perdaPct, int atrasoMs, int jitterMs) {
        rede.configurar(perdaPct, atrasoMs, jitterMs);
        if (perdaPct == 0 && atrasoMs == 0 && jitterMs == 0) {
            registrar("Rede simulada desligada: os pacotes deste PC saem como estão.");
        } else {
            registrar("Rede simulada neste PC: perde " + perdaPct + "%, atraso " + atrasoMs
                    + " ms + até " + jitterMs + " ms de jitter.");
        }
    }

    // =====================================================================
    // Mensagens que chegam (thread do Swing)
    // =====================================================================

    public void receber(Message message) {
        if (!(message instanceof PongMsg p) || !meuId.equals(p.getParaId())) {
            return;
        }
        if (p instanceof EstadoMessage e) {
            receberEstado(e);
        } else if (p instanceof EntradaMessage en) {
            receberEntrada(en);
        } else if (p instanceof ConviteMessage c) {
            receberConvite(c);
        } else if (p instanceof RespostaMessage r) {
            receberResposta(r);
        } else if (p instanceof SairMessage s) {
            receberSair(s);
        }
    }

    private void receberConvite(ConviteMessage c) {
        if (papel == Papel.CONVIDADO && c.getPartidaId().equals(partidaId)) {
            // O host não recebeu minha resposta e mandou o convite de novo: repito a resposta.
            if (respostaDada != null) {
                rede.enviarSemSimulacao(respostaDada, advIp);
            }
            return;
        }
        if (fase == Fase.CONVITE_RECEBIDO && convitePendente != null
                && convitePendente.getPartidaId().equals(c.getPartidaId())) {
            return; // mesma pergunta repetida, ainda aguardando a pessoa clicar
        }
        if (fase == Fase.LIVRE || fase == Fase.FIM) {
            papel = Papel.NENHUM;
            convitePendente = c;
            fase = Fase.CONVITE_RECEBIDO;
            registrar(c.getDeNome() + " te desafiou para uma partida!");
            atualizarStatus();
            return;
        }
        rede.enviarSemSimulacao(new RespostaMessage(c.getPartidaId(), meuId, c.getDeId(), false, meuNome(), "ocupado"),
                ipDe(c.getDeId()));
    }

    private void receberResposta(RespostaMessage r) {
        if (fase != Fase.CONVIDANDO || !r.getPartidaId().equals(partidaId) || !r.getDeId().equals(advId)) {
            return;
        }
        conviteTimer.stop();
        if (r.isAceito()) {
            iniciarSessao(partidaId, advId, advNome, Papel.HOST);
            registrar(advNome + " aceitou! Você é o host: a física roda neste PC.");
        } else {
            fase = Fase.LIVRE;
            registrar(advNome + ("ocupado".equals(r.getMotivo()) ? " está ocupado em outra partida." : " recusou o desafio."));
            atualizarStatus();
        }
    }

    private void receberSair(SairMessage s) {
        if (papel != Papel.NENHUM && s.getPartidaId().equals(partidaId) && s.getDeId().equals(advId)) {
            encerrar(advNome + " saiu da partida.");
        }
    }

    private void receberEstado(EstadoMessage e) {
        if (papel != Papel.CONVIDADO || !e.getPartidaId().equals(partidaId) || !e.getDeId().equals(advId)) {
            return;
        }
        if (!aceitarTempoReal(e.getSeq(), e.getTempo(), e.getEco(), e.getSegurado())) {
            return;
        }
        ultimoEstado = e;
        interpolador.adicionar(System.nanoTime(),
                new double[]{e.getBolaX(), e.getBolaY(), e.getRaqueteHost()}, e.getSaque());
        if (e.getFase() == EstadoMessage.FIM && fase == Fase.EM_JOGO) {
            boolean venci = e.getPlacarConvidado() > e.getPlacarHost();
            terminar(venci ? "Você venceu " : advNome + " venceu ", e.getPlacarHost(), e.getPlacarConvidado());
        }
    }

    private void receberEntrada(EntradaMessage en) {
        if (papel != Papel.HOST || !en.getPartidaId().equals(partidaId) || !en.getDeId().equals(advId)) {
            return;
        }
        if (!aceitarTempoReal(en.getSeq(), en.getTempo(), en.getEco(), en.getSegurado())) {
            return;
        }
        raqueteAdversario = Fisica.limitarRaquete(en.getRaquete());
        interpolador.adicionar(System.nanoTime(), new double[]{raqueteAdversario}, 0);
    }

    /** Sequência, RTT e eco: comum aos dois tipos de pacote de tempo real. */
    private boolean aceitarTempoReal(int seq, long tempoRemoto, long eco, long segurado) {
        long agora = agoraMs();
        ultimoRecebidoMs = agora;
        if (!estatisticas.receber(seq, tempoRemoto, agora)) {
            return false; // chegou depois de um mais novo: descartado
        }
        if (eco >= 0) {
            estatisticas.amostraRtt(agora - eco - segurado);
        }
        temEco = true;
        ecoValor = tempoRemoto;
        ecoChegouMs = agora;
        return true;
    }

    // =====================================================================
    // Laço
    // =====================================================================

    private void tick() {
        long agora = System.nanoTime();
        double dt = Math.max(0, Math.min(0.05, (agora - ultimoTickNanos) / 1e9));
        ultimoTickNanos = agora;
        if (teclado != 0) {
            minhaRaquete = Fisica.limitarRaquete(minhaRaquete + teclado * VELOCIDADE_TECLADO * dt);
        }

        if (papel == Papel.HOST && fase == Fase.EM_JOGO) {
            passoHost(dt, agora);
        }
        if (papel != Papel.NENHUM) {
            if (fase == Fase.EM_JOGO && agoraMs() - ultimoRecebidoMs > TIMEOUT_MS) {
                encerrar("Conexão perdida: nada chegou de " + advNome + " há " + TIMEOUT_MS / 1000 + " s.");
            } else if (deveEnviar(agora)) {
                enviarTempoReal();
                // Agenda o próximo a partir do horário "ideal", não de agora: o timer do Windows
                // tem passo de ~16 ms e, sem isso, o atraso de cada tique se acumula e a taxa cai.
                long intervalo = 1_000_000_000L / envioHz;
                ultimoEnvioNanos += intervalo;
                if (agora - ultimoEnvioNanos > intervalo) {
                    ultimoEnvioNanos = agora;
                }
            }
        }
        estatisticas.tick();
        view.quadro(montarQuadro(agora));
        if (agora - ultimoStatusNanos > 1_000_000_000L) {
            if (advId != null) {
                String ip = ipDe(advId);
                if (!ip.isBlank()) {
                    advIp = ip;
                }
            }
            atualizarStatus();
        }
    }

    private void passoHost(double dt, long agora) {
        fisica.raqueteHost = minhaRaquete;
        fisica.raqueteConvidado = raqueteAdversario;
        if (faseJogo == EstadoMessage.CONTAGEM || faseJogo == EstadoMessage.PONTO) {
            if (agora >= faseAteNanos) {
                fisica.sacar(proximoSaque);
                saque++;
                faseJogo = EstadoMessage.JOGANDO;
            }
            return;
        }
        if (faseJogo != EstadoMessage.JOGANDO) {
            return;
        }
        Fisica.Evento ev = fisica.passo(dt);
        if (ev == Fisica.Evento.PONTO_HOST || ev == Fisica.Evento.PONTO_CONVIDADO) {
            saque++;
            boolean doHost = ev == Fisica.Evento.PONTO_HOST;
            registrar("Ponto de " + (doHost ? "você" : advNome) + ". Placar: "
                    + fisica.placarHost + " × " + fisica.placarConvidado);
            if (fisica.placarHost >= Fisica.PONTOS_PARA_VENCER || fisica.placarConvidado >= Fisica.PONTOS_PARA_VENCER) {
                faseJogo = EstadoMessage.FIM;
                terminar(doHost ? "Você venceu " : advNome + " venceu ", fisica.placarHost, fisica.placarConvidado);
            } else {
                faseJogo = EstadoMessage.PONTO;
                faseAteNanos = agora + PAUSA_PONTO_MS * 1_000_000L;
                proximoSaque = doHost ? 1 : -1; // saca em direção a quem levou o ponto
            }
        }
    }

    private boolean deveEnviar(long agora) {
        boolean ativo = fase == Fase.EM_JOGO
                || (fase == Fase.FIM && (agora - fimNanos) / 1_000_000L < ENVIO_APOS_FIM_MS);
        return ativo && agora - ultimoEnvioNanos >= 1_000_000_000L / envioHz;
    }

    private void enviarTempoReal() {
        long t = agoraMs();
        long eco = temEco ? ecoValor : -1;
        long segurado = temEco ? t - ecoChegouMs : 0;
        Message m;
        if (papel == Papel.HOST) {
            int contagem = faseJogo == EstadoMessage.CONTAGEM
                    ? (int) Math.ceil((faseAteNanos - System.nanoTime()) / 1e9) : 0;
            m = new EstadoMessage(partidaId, meuId, advId, seqEnvio++, t, eco, segurado,
                    fisica.bolaX, fisica.bolaY, fisica.raqueteHost, fisica.raqueteConvidado,
                    fisica.placarHost, fisica.placarConvidado, saque, faseJogo, Math.max(0, contagem));
        } else {
            m = new EntradaMessage(partidaId, meuId, advId, seqEnvio++, t, eco, segurado, minhaRaquete);
        }
        rede.enviar(m, advIp);
    }

    // =====================================================================
    // Sessão
    // =====================================================================

    private void iniciarSessao(String partida, String adversarioId, String adversarioNome, Papel novoPapel) {
        partidaId = partida;
        advId = adversarioId;
        advNome = adversarioNome;
        advIp = ipDe(adversarioId);
        papel = novoPapel;
        fase = Fase.EM_JOGO;
        estatisticas.reiniciar();
        interpolador.limpar();
        seqEnvio = 0;
        temEco = false;
        ultimoRecebidoMs = agoraMs();
        ultimoEstado = null;
        minhaRaquete = Fisica.ALTURA / 2;
        raqueteAdversario = Fisica.ALTURA / 2;
        if (papel == Papel.HOST) {
            fisica = new Fisica();
            faseJogo = EstadoMessage.CONTAGEM;
            faseAteNanos = System.nanoTime() + CONTAGEM_MS * 1_000_000L;
            saque = 0;
            proximoSaque = random.nextBoolean() ? 1 : -1;
        }
        atualizarStatus();
    }

    private void terminar(String quem, int placarHost, int placarConvidado) {
        fase = Fase.FIM;
        fimNanos = System.nanoTime();
        registrar("FIM: " + quem + placarHost + " × " + placarConvidado + ". Desafie de novo para outra partida.");
        atualizarStatus();
    }

    private void encerrar(String motivo) {
        conviteTimer.stop();
        papel = Papel.NENHUM;
        fase = Fase.LIVRE;
        respostaDada = null;
        convitePendente = null;
        ultimoEstado = null;
        interpolador.limpar();
        registrar(motivo);
        atualizarStatus();
    }

    private void enviarConvite() {
        convitesEnviados++;
        rede.enviarSemSimulacao(new ConviteMessage(partidaId, meuId, advId, meuNome()), advIp);
    }

    private void repetirConvite() {
        if (fase != Fase.CONVIDANDO) {
            conviteTimer.stop();
            return;
        }
        if (convitesEnviados >= CONVITE_TENTATIVAS) {
            conviteTimer.stop();
            fase = Fase.LIVRE;
            registrar(advNome + " não respondeu ao desafio.");
            atualizarStatus();
            return;
        }
        enviarConvite();
    }

    // =====================================================================
    // Tela
    // =====================================================================

    private PongView.Quadro montarQuadro(long agora) {
        List<String> medidas = papel == Papel.NENHUM ? List.of() : medidas();
        double centro = Fisica.ALTURA / 2;
        if (papel == Papel.NENHUM) {
            String aviso = switch (fase) {
                case CONVIDANDO -> "Esperando " + advNome + " responder…";
                case CONVITE_RECEBIDO -> (convitePendente != null ? convitePendente.getDeNome() : "Alguém") + " te desafiou!";
                default -> "Escolha alguém e clique em Desafiar";
            };
            return new PongView.Quadro(Fisica.LARGURA / 2, centro, false, centro, centro, 0, 0, "", "",
                    true, aviso, false, 0, 0, medidas);
        }
        long noPassado = agora - ATRASO_INTERPOLACAO_MS * 1_000_000L;
        if (papel == Papel.HOST) {
            double direita = raqueteAdversario;
            if (interpolar && !interpolador.vazio()) {
                direita = interpolador.em(noPassado)[0];
            }
            String aviso = switch (faseJogo) {
                case EstadoMessage.CONTAGEM -> String.valueOf((int) Math.ceil((faseAteNanos - agora) / 1e9));
                case EstadoMessage.PONTO -> "Ponto!";
                case EstadoMessage.FIM -> fisica.placarHost > fisica.placarConvidado ? "Você venceu!" : advNome + " venceu!";
                default -> null;
            };
            return new PongView.Quadro(fisica.bolaX, fisica.bolaY, faseJogo == EstadoMessage.JOGANDO,
                    minhaRaquete, direita, fisica.placarHost, fisica.placarConvidado,
                    meuNome() + " (você)", advNome, true, aviso, false, 0, 0, medidas);
        }
        // convidado
        if (ultimoEstado == null) {
            return new PongView.Quadro(Fisica.LARGURA / 2, centro, false, centro, minhaRaquete, 0, 0,
                    advNome, meuNome() + " (você)", false, "Esperando o primeiro pacote de " + advNome + "…",
                    false, 0, 0, medidas);
        }
        double[] v = interpolar ? interpolador.em(noPassado) : interpolador.ultimo();
        double[] crua = interpolador.ultimo();
        EstadoMessage e = ultimoEstado;
        String aviso = switch (e.getFase()) {
            case EstadoMessage.CONTAGEM -> e.getContagem() > 0 ? String.valueOf(e.getContagem()) : null;
            case EstadoMessage.PONTO -> "Ponto!";
            case EstadoMessage.FIM -> e.getPlacarConvidado() > e.getPlacarHost() ? "Você venceu!" : advNome + " venceu!";
            default -> null;
        };
        return new PongView.Quadro(v[0], v[1], e.getFase() == EstadoMessage.JOGANDO,
                v[2], minhaRaquete, e.getPlacarHost(), e.getPlacarConvidado(),
                advNome, meuNome() + " (você)", false, aviso,
                mostrarCrua && e.getFase() == EstadoMessage.JOGANDO, crua[0], crua[1], medidas);
    }

    private List<String> medidas() {
        Estatisticas s = estatisticas;
        List<String> linhas = new ArrayList<>();
        linhas.add(papel == Papel.HOST
                ? "Você é o HOST: a física roda aqui"
                : "Você é o CONVIDADO: desenha o que o host manda");
        linhas.add(String.format("RTT %s ms   SRTT %s ms   jitter %.1f ms",
                ms(s.rtt()), ms(s.srtt()), s.jitter()));
        linhas.add(String.format("recebendo %.0f pacotes/s   perda %.1f%%   fora de ordem %d",
                s.pacotesPorSegundo(), s.perdaPct(), s.foraDeOrdem()));
        linhas.add(String.format("enviando %.0f/s (meta %d) · %d bytes/pacote · %.1f kbit/s",
                rede.pacotesPorSegundo(), envioHz, rede.bytesUltimoPacote(), rede.kbpsEnvio()));
        linhas.add(interpolar ? "interpolação: ligada (" + ATRASO_INTERPOLACAO_MS + " ms)" : "interpolação: desligada");
        return linhas;
    }

    private void atualizarStatus() {
        ultimoStatusNanos = System.nanoTime();
        String texto = switch (fase) {
            case LIVRE -> "Escolha um adversário e clique em Desafiar.";
            case CONVIDANDO -> "Desafio enviado para " + advNome + "…";
            case CONVITE_RECEBIDO -> (convitePendente != null ? convitePendente.getDeNome() : "Alguém") + " te desafiou!";
            case EM_JOGO -> "Jogando contra " + advNome + ". Mova o mouse sobre o campo (ou use W/S, ↑/↓).";
            case FIM -> "Partida encerrada. Desafie de novo ou escolha outra pessoa.";
        };
        view.status(new PongView.Status(
                texto,
                fase == Fase.LIVRE || fase == Fase.FIM,
                fase == Fase.EM_JOGO || fase == Fase.CONVIDANDO || fase == Fase.FIM,
                fase == Fase.CONVITE_RECEBIDO && convitePendente != null ? convitePendente.getDeNome() : null,
                online()));
    }

    private void registrar(String linha) {
        view.registrar(linha);
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private List<PongView.Adversario> online() {
        List<PongView.Adversario> lista = new ArrayList<>();
        for (UserPeerView v : connectionManager.getPresenceService().listPeerViews()) {
            if (v.isBroadcastTarget() || v.isSelf() || v.getInstanceId() == null || meuId.equals(v.getInstanceId())) {
                continue;
            }
            lista.add(new PongView.Adversario(v.getInstanceId(), v.getDisplayName()));
        }
        return lista;
    }

    private String ipDe(String id) {
        for (UserPeerView v : connectionManager.getPresenceService().listPeerViews()) {
            if (id != null && id.equals(v.getInstanceId())) {
                return v.getInetAddress().map(PeerIpv4::fromInetAddress).orElse("");
            }
        }
        return advIp != null && id != null && id.equals(advId) ? advIp : "";
    }

    private String meuNome() {
        return connectionManager.getUserName();
    }

    private static long agoraMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private static String ms(double v) {
        return v < 0 ? "—" : String.format("%.0f", v);
    }
}
