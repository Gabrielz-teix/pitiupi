package pitiupi.batata.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.application.peeractions.PeerTarget;
import pitiupi.batata.GUI.BatataPanel;
import pitiupi.batata.control.BatataController;
import pitiupi.batata.net.AckMessage;
import pitiupi.batata.net.BatataMessage;
import pitiupi.batata.net.FimRodadaMessage;
import pitiupi.batata.net.PosseMessage;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Batata Quente: um token (a batata) passa de PC em PC; quem estiver com ela quando o
 * pavio acabar sai da partida. Liga o controller e a tela ao núcleo.
 */
public class BatataPlugin extends Plugin {

    private BatataController controller;
    private PluginWorkspace workspace;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.workspace = workspace;
        this.controller = new BatataController(connectionManager);
        BatataPanel panel = new BatataPanel(controller);
        controller.setView(panel);
        bindShellWorkspaceContent(panel);
        installPeerActions(workspace.peerActions());
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "batata.abrir",
                "Abrir Batata Quente",
                PeerActionTarget.ALL_PEERS,
                target -> workspace.openOrFocusTab(getIdentifier(), getDisplayName())
        ));
        registrar.register(PeerAction.of(
                "batata.passar",
                "Passar a batata para esta pessoa",
                PeerActionTarget.SINGLE_PEER,
                target -> {
                    workspace.openOrFocusTab(getIdentifier(), getDisplayName());
                    if (target instanceof PeerTarget.Single single) {
                        controller.passarPara(single.peer().getInstanceId());
                    }
                }
        ));
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(BatataMessage.class, AckMessage.class, PosseMessage.class, FimRodadaMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        if (controller != null) {
            SwingUtilities.invokeLater(() -> controller.receber(message));
        }
    }

    @Override
    public String getName() {
        return "Batata Quente";
    }

    @Override
    public String getAuthor() {
        return "Carlos Gabriel Teixeira";
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getIdentifier() {
        return "Pitiupi.BatataQuente";
    }
}
