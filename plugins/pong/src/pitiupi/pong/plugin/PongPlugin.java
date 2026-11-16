package pitiupi.pong.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.application.peeractions.PeerTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;
import pitiupi.pong.GUI.PongPanel;
import pitiupi.pong.control.PongController;
import pitiupi.pong.net.ConviteMessage;
import pitiupi.pong.net.EntradaMessage;
import pitiupi.pong.net.EstadoMessage;
import pitiupi.pong.net.RespostaMessage;
import pitiupi.pong.net.SairMessage;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Pong em rede para duas pessoas. Liga o controller e a tela ao núcleo.
 */
public class PongPlugin extends Plugin {

    private PongController controller;
    private PluginWorkspace workspace;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.workspace = workspace;
        this.controller = new PongController(connectionManager);
        PongPanel panel = new PongPanel(controller);
        controller.setView(panel);
        bindShellWorkspaceContent(panel);
        installPeerActions(workspace.peerActions());
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "pong.desafiar",
                "Desafiar para Pong",
                PeerActionTarget.SINGLE_PEER,
                target -> {
                    workspace.openOrFocusTab(getIdentifier(), getDisplayName());
                    if (target instanceof PeerTarget.Single single) {
                        controller.desafiar(single.peer().getInstanceId());
                    }
                }
        ));
    }

    @Override
    public void onShellTabClosed() {
        if (controller != null) {
            controller.sair();
        }
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(EstadoMessage.class, EntradaMessage.class, ConviteMessage.class,
                RespostaMessage.class, SairMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        if (controller != null) {
            SwingUtilities.invokeLater(() -> controller.receber(message));
        }
    }

    @Override
    public String getName() {
        return "Pong";
    }

    @Override
    public String getDisplayName() {
        return "Pong em rede";
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
        return "Pitiupi.Pong";
    }
}
