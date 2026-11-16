package pitiupi.forca.plugin;

import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.control.ConnectionManager;
import pitiupi.forca.GUI.ForcaPanel;
import pitiupi.forca.net.ForcaMessage;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Plugin do jogo da forca para duas pessoas.
 */
public class ForcaPlugin extends Plugin {

    private ForcaPanel painel;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        // quando chega um convite, a aba abre sozinha
        painel = new ForcaPanel(connectionManager, () -> workspace.openOrFocusTab(getIdentifier(), getDisplayName()));
        bindShellWorkspaceContent(painel);
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(ForcaMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        if (message instanceof ForcaMessage m && painel != null) {
            SwingUtilities.invokeLater(() -> painel.receber(m));
        }
    }

    @Override
    public String getName() {
        return "Forca";
    }

    @Override
    public String getDisplayName() {
        return "Jogo da Forca";
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
        return "Pitiupi.Forca";
    }
}
