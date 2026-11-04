package bunsik.client;

import bunsik.client.ui.GameFrame;
import bunsik.common.AppConfig;

import javax.swing.SwingUtilities;

/**
 * 클라이언트 실행 시작점. 사용법: {@code java -cp out bunsik.client.ClientMain}
 */
public final class ClientMain {

    private ClientMain() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        AppConfig cfg = AppConfig.load();
        // Swing 화면은 반드시 화면 스레드(EDT)에서 만든다.
        SwingUtilities.invokeLater(() -> new GameFrame(cfg).setVisible(true));
    }
}
