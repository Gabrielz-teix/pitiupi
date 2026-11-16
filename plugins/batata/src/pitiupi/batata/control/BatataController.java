package pitiupi.batata.control;

import pitiupi.application.presence.UserPeerView;
import pitiupi.batata.net.AckMessage;
import pitiupi.batata.net.BatataMessage;
import pitiupi.batata.net.FimRodadaMessage;
import pitiupi.batata.net.Jogador;
import pitiupi.batata.net.PosseMessage;
import pitiupi.batata.net.RodadaInfo;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.presentation.shell.PeerIpv4;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Regras do jogo e do protocolo. Não desenha nada: avisa a tela pelo {@link BatataView}.
 *
 * <p>Tudo aqui roda na thread do Swing (os timers são {@link javax.swing.Timer} e o plugin
 * entrega as mensagens com {@code invokeLater}). Por isso não há {@code synchronized}:
 * só uma thread mexe no estado.</p>
 *
 * <h2>Protocolo</h2>
 * <ol>
 *   <li>Quem está com a batata manda {@link BatataMessage} (UDP unicast) para o próximo.</li>
 *   <li>Quem recebe responde {@link AckMessage} e avisa a sala com {@link PosseMessage} (multicast).</li>
 *   <li>Sem ACK em {@value #ACK_TIMEOUT_MS} ms, quem passou reenvia. Depois de
 *       {@value #MAX_TENTATIVAS} tentativas desiste e a batata volta para ele.</li>
 *   <li>O pavio viaja no pacote. Quando zera na mão de alguém, essa pessoa manda
 *       {@link FimRodadaMessage} (multicast, algumas cópias).</li>
 *   <li>Se quem está com a batata some da rede, o jogador vivo de menor id cria outra.</li>
 * </ol>
 */
public final class BatataController {

    static final int ACK_TIMEOUT_MS = 400;
    static final int MAX_TENTATIVAS = 6;
    private static final int PAVIO_MIN_MS = 12_000;
    private static final int PAVIO_MAX_MS = 30_000;
    private static final int PAVIO_RECRIADA_MIN_MS = 8_000;
    private static final int PAVIO_RECRIADA_MAX_MS = 15_000;
    private static final long PAUSA_ENTRE_RODADAS_MS = 4_000;
    private static final int COPIAS_DO_FIM = 3;
    private static final long REENVIO_FIM_MS = 2_000;
    private static final long REENVIO_FIM_DURANTE_MS = 20_000;
    private static final int VIGIA_MS = 250;
    /** Quantas checagens seguidas o dono precisa estar fora da lista para contar como perdido (~1 s). */
    private static final int CHECAGENS_DONO_SUMIDO = 4;

    private final ConnectionManager connectionManager;
    private final Transporte transporte;
    private final String meuId;
    private final Random random = new Random();
    private BatataView view = new BatataView() {
        @Override
        public void mostrar(Estado estado) {
        }

        @Override
        public void registrar(String linha) {
        }
    };

    // ---- partida
    private String partidaId;
    private int rodada;
    private List<Jogador> todos = new ArrayList<>();
    private List<Jogador> vivos = new ArrayList<>();
    private boolean rodadaAtiva;
    private boolean partidaAcabou;
    private long fimRodadaNanos;
    private String ultimoPerdedor;
    /** Último aviso de fim que conheço; é repetido por um tempo para quem não ouviu. */
    private FimRodadaMessage ultimoFim;
    private long ultimoReenvioFimNanos;

    // ---- onde está a batata (até onde eu sei)
    private int ultimoSeq;
    private String donoId;
    private String donoNome;
    private List<String> caminho = new ArrayList<>();
    private int checagensDonoSumido;

    // ---- quando a batata está comigo
    private boolean comigo;
    private long pavioFimNanos;
    private long recebiNanos;

    // ---- passe esperando ACK
    private BatataMessage pendente;
    private Jogador pendenteAlvo;
    private int tentativas;
    private long primeiroEnvioNanos;
    private long ultimoEnvioNanos;
    private long pavioNoEnvioMs;
    private int desistidoSeq = -1;
    private String desistidoId;

    private final Timer pavio;
    private final Timer retransmissao;
    private final Timer vigia;

    public BatataController(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.transporte = new Transporte(connectionManager);
        this.meuId = connectionManager.getHeartbeatManager().getInstanceId();

        pavio = new Timer(0, e -> explodir());
        pavio.setRepeats(false);
        retransmissao = new Timer(ACK_TIMEOUT_MS, e -> semAck());
        vigia = new Timer(VIGIA_MS, e -> vigiar());
        vigia.start();
    }

    public void setView(BatataView view) {
        this.view = view;
        atualizar();
    }

    public void setSimularPerda(boolean ligado) {
        transporte.setSimularPerda(ligado);
        registrar(ligado
                ? "Simulação de perda LIGADA: " + Math.round(Transporte.TAXA_PERDA * 100)
                + "% das batatas e ACKs enviados por este PC são jogados fora."
                : "Simulação de perda desligada.");
    }

    // =====================================================================
    // Ações da pessoa
    // =====================================================================

    public void novaPartida() {
        Map<String, Jogador> online = online();
        if (online.size() < 2) {
            registrar("Precisa de pelo menos 2 pessoas na lista \"Usuários nesta rede\" (todos na mesma porta).");
            return;
        }
        pararBatata();
        ultimoFim = null;
        partidaId = Long.toHexString(random.nextLong() & 0xffffffL);
        rodada = 0;
        todos = new ArrayList<>(online.values());
        vivos = new ArrayList<>(todos);
        partidaAcabou = false;
        registrar("Nova partida " + partidaId + " com " + nomes(todos) + ".");
        iniciarRodada();
    }

    public void passarPara(String alvoId) {
        if (!comigo || pendente != null || !rodadaAtiva || meuId.equals(alvoId)) {
            return;
        }
        Jogador alvo = buscar(vivos, alvoId);
        if (alvo == null) {
            registrar("Essa pessoa não está jogando esta rodada.");
            return;
        }
        Jogador atual = online().get(alvoId);
        if (atual == null) {
            registrar(alvo.getNome() + " saiu da rede. Escolha outra pessoa.");
            return;
        }
        long restante = pavioRestanteMs();
        if (restante <= 0) {
            explodir();
            return;
        }
        pavio.stop();
        comigo = false;
        pavioNoEnvioMs = restante;
        pendente = new BatataMessage(partidaId, rodada, meuId, meuNome(), vivos, todos,
                ultimoSeq + 1, alvoId, restante, caminho);
        pendenteAlvo = new Jogador(alvo.getId(), alvo.getNome(),
                atual.getIp().isBlank() ? alvo.getIp() : atual.getIp());
        tentativas = 0;
        primeiroEnvioNanos = System.nanoTime();
        enviarPendente();
        retransmissao.restart();
        atualizar();
    }

    // =====================================================================
    // Mensagens que chegam da rede (sempre na thread do Swing)
    // =====================================================================

    public void receber(Message message) {
        if (message instanceof BatataMessage b) {
            receberBatata(b);
        } else if (message instanceof AckMessage a) {
            receberAck(a);
        } else if (message instanceof PosseMessage p) {
            receberPosse(p);
        } else if (message instanceof FimRodadaMessage f) {
            receberFim(f);
        }
        atualizar();
    }

    private void receberBatata(BatataMessage b) {
        if (!meuId.equals(b.getParaId())) {
            return; // multicast de um passe entre outros dois no mesmo PC
        }
        // O ACK vai sempre, até para cópia repetida: se o primeiro ACK se perdeu,
        // é este que faz quem passou parar de reenviar.
        Jogador remetente = new Jogador(b.getDeId(), b.getDeNome(), ipDe(b.getDeId(), b.getVivos()));
        String como = transporte.enviarPara(
                new AckMessage(b.getPartidaId(), b.getRodada(), b.getSeq(), meuId, b.getDeId()),
                remetente, true);

        if (!adotar(b) || !rodadaAtiva) {
            return;
        }
        if (b.getSeq() <= ultimoSeq) {
            registrar("← batata #" + b.getSeq() + " de " + b.getDeNome()
                    + " de novo (retransmissão). Ignorada; só reenviei o ACK [" + como + "].");
            return;
        }
        registrar("← batata #" + b.getSeq() + " de " + b.getDeNome() + ". ACK [" + como + "].");
        caminho = new ArrayList<>(b.getCaminho());
        segurar(b.getSeq(), b.getPavioRestanteMs(), false);
    }

    private void receberAck(AckMessage a) {
        if (!meuId.equals(a.getParaId()) || pendente == null) {
            return;
        }
        if (a.getSeq() != pendente.getSeq() || a.getRodada() != rodada
                || !Objects.equals(a.getPartidaId(), partidaId)) {
            return;
        }
        long rtt = msDesde(ultimoEnvioNanos);
        String nota = tentativas > 1
                ? " depois de " + tentativas + " tentativas (RTT ambíguo: não dá para saber a qual envio esse ACK responde)"
                : "";
        registrar("✓ ACK de " + pendenteAlvo.getNome() + " em " + rtt + " ms" + nota + ".");
        concluirPasse();
    }

    private void receberPosse(PosseMessage p) {
        if (meuId.equals(p.getDeId()) || !adotar(p) || !rodadaAtiva || p.getSeq() < ultimoSeq) {
            return;
        }
        if (pendente != null && p.getSeq() >= pendente.getSeq() && p.getDeId().equals(pendente.getParaId())) {
            registrar("✓ posse de " + p.getDeNome() + " chegou antes do ACK (vale como confirmação).");
            concluirPasse();
        }
        if (comigo && p.getSeq() == desistidoSeq && p.getDeId().equals(desistidoId)) {
            registrar("Afinal a batata #" + p.getSeq() + " tinha chegado em " + p.getDeNome()
                    + "; só os ACKs se perderam. Descartei a minha para não ficar com duas.");
            comigo = false;
            pavio.stop();
        }
        if (comigo && p.getSeq() > ultimoSeq) {
            registrar("Batata DUPLICADA! " + p.getDeNome() + " tem a #" + p.getSeq()
                    + ", mais nova que a minha (#" + ultimoSeq + "). Descartei a minha.");
            comigo = false;
            pavio.stop();
        }
        if (pendente != null && p.getSeq() > pendente.getSeq()) {
            cancelarPendente();
        }
        boolean mudou = !p.getDeId().equals(donoId) || p.getSeq() != ultimoSeq;
        ultimoSeq = p.getSeq();
        donoId = p.getDeId();
        donoNome = p.getDeNome();
        caminho = new ArrayList<>(p.getCaminho());
        vivos = new ArrayList<>(p.getVivos());
        checagensDonoSumido = 0;
        if (p.isRecriada()) {
            registrar(p.getDeNome() + " criou uma batata nova (#" + p.getSeq() + ").");
        } else if (mudou) {
            registrar("• A batata está com " + p.getDeNome() + ".");
        }
    }

    private void receberFim(FimRodadaMessage f) {
        if (meuId.equals(f.getDeId())) {
            return;
        }
        // Fim de outra partida não interessa: pode ser cópia atrasada de um jogo que já acabou.
        if (!Objects.equals(f.getPartidaId(), partidaId)) {
            return;
        }
        boolean novo = f.getRodada() > rodada || (f.getRodada() == rodada && rodadaAtiva);
        if (!novo) {
            return; // cópia repetida do aviso
        }
        rodada = f.getRodada();
        ultimoFim = f;
        aplicarFim(f.getDeNome(), f.getVivos(), f.getCaminho());
    }

    // =====================================================================
    // Rodada
    // =====================================================================

    private void iniciarRodada() {
        Map<String, Jogador> online = online();
        vivos.removeIf(j -> !online.containsKey(j.getId()));
        if (vivos.size() < 2) {
            terminarPartida();
            return;
        }
        rodada++;
        comecarRodadaLocal();
        registrar("—— Rodada " + rodada + " — jogadores: " + nomes(vivos) + ". O pavio foi sorteado e é segredo.");
        segurar(1, sortear(PAVIO_MIN_MS, PAVIO_MAX_MS), false);
        Jogador primeiro = vivos.get(random.nextInt(vivos.size()));
        if (!primeiro.getId().equals(meuId)) {
            passarPara(primeiro.getId());
        }
    }

    private void comecarRodadaLocal() {
        pararBatata();
        rodadaAtiva = true;
        ultimoSeq = 0;
        donoId = null;
        donoNome = null;
        caminho = new ArrayList<>();
        ultimoPerdedor = null;
        checagensDonoSumido = 0;
        desistidoSeq = -1;
        desistidoId = null;
        ultimoFim = null;
    }

    /**
     * Uma mensagem de outra partida ou de uma rodada mais nova põe este PC nela.
     *
     * @return {@code true} se a mensagem é da rodada atual (depois de adotar)
     */
    private boolean adotar(RodadaInfo m) {
        if (!Objects.equals(m.getPartidaId(), partidaId)) {
            partidaId = m.getPartidaId();
            rodada = m.getRodada();
            todos = new ArrayList<>(m.getTodos());
            vivos = new ArrayList<>(m.getVivos());
            partidaAcabou = false;
            comecarRodadaLocal();
            registrar(m.getDeNome() + " está numa partida nova. Rodada " + rodada + ", jogadores: " + nomes(vivos) + ".");
            return true;
        }
        if (m.getRodada() > rodada) {
            rodada = m.getRodada();
            vivos = new ArrayList<>(m.getVivos());
            comecarRodadaLocal();
            registrar("—— Rodada " + rodada + " — jogadores: " + nomes(vivos) + ".");
            return true;
        }
        return m.getRodada() == rodada;
    }

    private void segurar(int seq, long pavioMs, boolean recriada) {
        cancelarPendente();
        comigo = true;
        donoId = meuId;
        donoNome = meuNome();
        ultimoSeq = seq;
        caminho.add(meuNome());
        recebiNanos = System.nanoTime();
        pavioFimNanos = recebiNanos + pavioMs * 1_000_000L;
        pavio.setInitialDelay((int) Math.max(1, pavioMs));
        pavio.restart();
        transporte.anunciar(new PosseMessage(partidaId, rodada, meuId, meuNome(), vivos, todos, seq, caminho, recriada));
        atualizar();
    }

    private void explodir() {
        if (!comigo || !rodadaAtiva) {
            return;
        }
        comigo = false;
        List<Jogador> depois = new ArrayList<>(vivos);
        depois.removeIf(j -> j.getId().equals(meuId));
        FimRodadaMessage fim = new FimRodadaMessage(partidaId, rodada, meuId, meuNome(), depois, todos, caminho);
        anunciarVariasVezes(fim, COPIAS_DO_FIM);
        ultimoFim = fim;
        aplicarFim(meuNome(), depois, caminho);
    }

    private void aplicarFim(String perdedor, List<Jogador> vivosDepois, List<String> caminhoFinal) {
        pararBatata();
        rodadaAtiva = false;
        vivos = new ArrayList<>(vivosDepois);
        caminho = new ArrayList<>(caminhoFinal);
        donoId = null;
        ultimoPerdedor = perdedor;
        fimRodadaNanos = System.nanoTime();
        registrar("BUM! A batata explodiu com " + perdedor + ". Caminho: " + String.join(" → ", caminho) + ".");
        if (vivos.size() <= 1) {
            terminarPartida();
        }
        atualizar();
    }

    private void terminarPartida() {
        pararBatata();
        rodadaAtiva = false;
        partidaAcabou = true;
        if (vivos.size() == 1) {
            registrar("FIM DE JOGO! " + vivos.get(0).getNome() + " venceu.");
        } else {
            registrar("Fim de jogo: não sobrou ninguém online para continuar.");
        }
        atualizar();
    }

    // =====================================================================
    // Confiabilidade: ACK, retransmissão, desistência
    // =====================================================================

    private void enviarPendente() {
        tentativas++;
        ultimoEnvioNanos = System.nanoTime();
        String como = transporte.enviarPara(pendente, pendenteAlvo, true);
        registrar("→ batata #" + pendente.getSeq() + " para " + pendenteAlvo.getNome()
                + " (tentativa " + tentativas + "/" + MAX_TENTATIVAS + ") [" + como + "]");
    }

    private void semAck() {
        if (pendente == null) {
            retransmissao.stop();
            return;
        }
        if (tentativas >= MAX_TENTATIVAS) {
            desistir();
            return;
        }
        registrar("  sem ACK em " + ACK_TIMEOUT_MS + " ms…");
        enviarPendente();
        atualizar();
    }

    private void desistir() {
        String nome = pendenteAlvo.getNome();
        long restante = pavioNoEnvioMs - msDesde(primeiroEnvioNanos);
        // Pode ser que a batata tenha chegado e só os ACKs se perderam. Guardamos o passe
        // abandonado e gastamos o número dele: o próximo passe sai com seq maior, então
        // se existirem duas batatas, a regra "seq maior vence" desempata.
        desistidoSeq = pendente.getSeq();
        desistidoId = pendente.getParaId();
        ultimoSeq = Math.max(ultimoSeq, desistidoSeq);
        cancelarPendente();
        registrar(nome + " não confirmou depois de " + MAX_TENTATIVAS + " tentativas. A batata voltou para você"
                + " (e o pavio continuou queimando).");
        comigo = true;
        donoId = meuId;
        donoNome = meuNome();
        pavioFimNanos = System.nanoTime() + Math.max(0, restante) * 1_000_000L;
        if (restante <= 0) {
            explodir();
            return;
        }
        pavio.setInitialDelay((int) restante);
        pavio.restart();
        atualizar();
    }

    private void concluirPasse() {
        retransmissao.stop();
        donoId = pendente.getParaId();
        donoNome = pendenteAlvo.getNome();
        ultimoSeq = Math.max(ultimoSeq, pendente.getSeq());
        caminho = new ArrayList<>(pendente.getCaminho());
        caminho.add(pendenteAlvo.getNome());
        pendente = null;
        pendenteAlvo = null;
        atualizar();
    }

    private void cancelarPendente() {
        retransmissao.stop();
        pendente = null;
        pendenteAlvo = null;
    }

    private void pararBatata() {
        pavio.stop();
        cancelarPendente();
        comigo = false;
    }

    // =====================================================================
    // Vigia: detecta batata perdida e começa a próxima rodada
    // =====================================================================

    private void vigiar() {
        if (rodadaAtiva) {
            verificarDono();
        } else {
            reenviarFim();
            if (partidaId != null && !partidaAcabou
                    && msDesde(fimRodadaNanos) >= PAUSA_ENTRE_RODADAS_MS
                    && meuId.equals(coordenador())) {
                iniciarRodada();
            }
        }
        atualizar();
    }

    /**
     * Repete o aviso de fim a cada {@value #REENVIO_FIM_MS} ms durante um tempo. Se alguém perdeu
     * todas as cópias (multicast também é UDP), continuaria achando que a rodada não acabou;
     * se esse alguém for o coordenador, ninguém começaria a próxima. Quem já ouviu ignora a cópia.
     */
    private void reenviarFim() {
        if (ultimoFim == null || msDesde(fimRodadaNanos) > REENVIO_FIM_DURANTE_MS
                || msDesde(ultimoReenvioFimNanos) < REENVIO_FIM_MS) {
            return;
        }
        ultimoReenvioFimNanos = System.nanoTime();
        transporte.anunciar(ultimoFim);
    }

    private void verificarDono() {
        if (comigo || pendente != null || donoId == null || online().containsKey(donoId)) {
            checagensDonoSumido = 0;
            return;
        }
        checagensDonoSumido++;
        if (checagensDonoSumido < CHECAGENS_DONO_SUMIDO) {
            return;
        }
        String perdidoId = donoId;
        vivos.removeIf(j -> j.getId().equals(perdidoId));
        if (checagensDonoSumido == CHECAGENS_DONO_SUMIDO) {
            registrar("A batata se perdeu: " + donoNome + " saiu da rede com ela.");
        }
        if (vivos.size() <= 1) {
            terminarPartida();
            return;
        }
        String eleito = coordenador();
        if (meuId.equals(eleito)) {
            registrar("Eleição: você tem o menor id entre os jogadores que restaram, então cria a batata nova.");
            segurar(ultimoSeq + 1, sortear(PAVIO_RECRIADA_MIN_MS, PAVIO_RECRIADA_MAX_MS), true);
        } else if (checagensDonoSumido == CHECAGENS_DONO_SUMIDO) {
            Jogador j = buscar(vivos, eleito);
            registrar("Esperando " + (j != null ? j.getNome() : "o eleito") + " (menor id) criar outra batata.");
        }
    }

    /** O jogador vivo e online de menor id. É ele quem recria a batata e começa a rodada seguinte. */
    private String coordenador() {
        Map<String, Jogador> online = online();
        return vivos.stream()
                .map(Jogador::getId)
                .filter(online::containsKey)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    // =====================================================================
    // Tela
    // =====================================================================

    private void atualizar() {
        Map<String, Jogador> online = online();
        BatataView.Fase fase;
        String titulo;
        String detalhe;
        boolean euVivo = buscar(vivos, meuId) != null;
        if (partidaId == null) {
            fase = BatataView.Fase.SEM_PARTIDA;
            titulo = "Nenhuma partida";
            detalhe = "Clique em \"Nova partida\". Quem estiver na lista da direita (mesma porta) entra no jogo.";
        } else if (partidaAcabou) {
            fase = BatataView.Fase.FIM_DE_JOGO;
            if (vivos.size() == 1) {
                titulo = vivos.get(0).getId().equals(meuId) ? "VOCÊ VENCEU!" : vivos.get(0).getNome() + " venceu!";
            } else {
                titulo = "Fim de jogo";
            }
            detalhe = "Clique em \"Nova partida\" para jogar de novo.";
        } else if (!rodadaAtiva) {
            fase = BatataView.Fase.EXPLODIU;
            titulo = ultimoPerdedor != null ? "BUM! Explodiu com " + ultimoPerdedor : "Esperando a próxima rodada";
            detalhe = "A próxima rodada começa em instantes.";
        } else if (pendente != null) {
            fase = BatataView.Fase.PASSANDO;
            titulo = "Passando para " + pendenteAlvo.getNome() + "…";
            detalhe = "Esperando o ACK (tentativa " + tentativas + " de " + MAX_TENTATIVAS + ").";
        } else if (comigo) {
            fase = BatataView.Fase.COMIGO;
            titulo = "A BATATA ESTÁ COM VOCÊ!";
            detalhe = "Passe logo: clique em alguém da lista.";
        } else {
            fase = BatataView.Fase.COM_OUTRO;
            titulo = donoNome != null ? "A batata está com " + donoNome : "Esperando a batata…";
            detalhe = euVivo ? "Fique esperto: ela pode vir para você." : "Você está fora desta rodada. Assista!";
        }

        List<BatataView.JogadorNaTela> jogadores = new ArrayList<>();
        List<Jogador> lista = todos.isEmpty() ? new ArrayList<>(online.values()) : todos;
        for (Jogador j : lista) {
            boolean comBatata = rodadaAtiva && j.getId().equals(donoId);
            jogadores.add(new BatataView.JogadorNaTela(
                    j.getId(), j.getNome(),
                    partidaId == null || buscar(vivos, j.getId()) != null,
                    online.containsKey(j.getId()),
                    j.getId().equals(meuId),
                    comBatata));
        }
        long segurando = comigo ? msDesde(recebiNanos) : 0;
        view.mostrar(new BatataView.Estado(
                fase, rodada, titulo, detalhe, List.copyOf(caminho), jogadores,
                comigo && pendente == null && rodadaAtiva, segurando));
    }

    private void registrar(String linha) {
        view.registrar(linha);
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    /** Pessoas na lista de presença do núcleo (heartbeat), por instanceId. */
    private Map<String, Jogador> online() {
        Map<String, Jogador> mapa = new LinkedHashMap<>();
        for (UserPeerView v : connectionManager.getPresenceService().listPeerViews()) {
            if (v.isBroadcastTarget() || v.getInstanceId() == null) {
                continue;
            }
            String ip = v.getInetAddress().map(PeerIpv4::fromInetAddress).orElse("");
            mapa.put(v.getInstanceId(), new Jogador(v.getInstanceId(), v.getDisplayName(), ip));
        }
        mapa.putIfAbsent(meuId, new Jogador(meuId, meuNome(), ""));
        return mapa;
    }

    private String ipDe(String id, List<Jogador> lista) {
        Jogador atual = online().get(id);
        if (atual != null && !atual.getIp().isBlank()) {
            return atual.getIp();
        }
        Jogador j = buscar(lista, id);
        return j != null ? j.getIp() : "";
    }

    private long pavioRestanteMs() {
        return (pavioFimNanos - System.nanoTime()) / 1_000_000L;
    }

    private void anunciarVariasVezes(Message message, int vezes) {
        transporte.anunciar(message);
        int[] enviadas = {1};
        Timer repetir = new Timer(150, null);
        repetir.addActionListener(e -> {
            transporte.anunciar(message);
            if (++enviadas[0] >= vezes) {
                repetir.stop();
            }
        });
        repetir.start();
    }

    private String meuNome() {
        return connectionManager.getUserName();
    }

    private long sortear(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static long msDesde(long nanos) {
        return (System.nanoTime() - nanos) / 1_000_000L;
    }

    private static Jogador buscar(List<Jogador> lista, String id) {
        for (Jogador j : lista) {
            if (j.getId().equals(id)) {
                return j;
            }
        }
        return null;
    }

    private static String nomes(List<Jogador> lista) {
        List<String> n = new ArrayList<>();
        for (Jogador j : lista) {
            n.add(j.getNome());
        }
        return String.join(", ", n);
    }
}
