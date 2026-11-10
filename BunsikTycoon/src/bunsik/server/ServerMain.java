import javax.swing.SwingUtilities;

public final class ServerMain {

    private ServerMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerFrame().setVisible(true));
    }
}