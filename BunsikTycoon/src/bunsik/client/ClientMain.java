package bunsik.client;

import bunsik.client.ui.GameFrame;
import javax.swing.SwingUtilities;

public final class ClientMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameFrame(args.length > 0 && "--preview".equals(args[0])).setVisible(true));
    }
}
