package bunsik.client;

import bunsik.client.ui.GameFrame;
import bunsik.client.ui.GamePanel;
import bunsik.client.ui.ResultPanel;
import bunsik.client.ui.TitlePanel;
import bunsik.common.Message;
import bunsik.common.StoreSnapshot;
import bunsik.common.StoreLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Swing과 루프백 서버를 사용하는 시작·종료 순서 검사. 그래픽 환경에서 실행한다. */
public final class SessionScreenTest {
    public static void main(String[] args) throws Exception {
        try (var server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            GameFrame frame = onEdt(() -> new GameFrame(false));
            try {
                onEdt(() -> {
                    for (Component component : frame.getContentPane().getComponents()) {
                        if (component instanceof JTextField field && field.getText().matches("[0-9]+")) {
                            field.setText(Integer.toString(server.getLocalPort()));
                        }
                    }
                    click(frame, "혼자 하기 (1P)", 2);
                    return null;
                });
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    check(in.readLine().endsWith("|SOLO"), "solo HELLO");
                    write(out, "WELCOME|1");
                    Thread.sleep(200);
                    check(onEdt(() -> frame.getContentPane() instanceof TitlePanel), "WELCOME cannot start play");
                    write(out, "START|0|1|주흔|-", "COUNTDOWN|0");
                    waitFor(() -> frame.getContentPane() instanceof GamePanel);
                    GamePanel game = onEdt(() -> (GamePanel) frame.getContentPane());
                    space(game);
                    socket.setSoTimeout(300);
                    try {
                        throw new AssertionError("input before STATE: " + in.readLine());
                    } catch (SocketTimeoutException expected) {
                    }
                    socket.setSoTimeout(5000);

                    StoreSnapshot empty = StoreSnapshot.empty();
                    write(out, tick(empty, 15000).encode());
                    waitFor(() -> game.state().timeLeftMs() == 15000);
                    space(game);
                    check(in.readLine().startsWith("MOVE|"), "cook uses current position");
                    check("COOK|TTEOKBOKKI".equals(in.readLine()), "ready game accepts keyboard cook");
                    write(out, tick(empty, 0).encode());
                    waitFor(() -> game.state().timeLeftMs() == 0);
                    check(onEdt(() -> frame.getContentPane() == game), "zero STATE cannot show a result");
                    space(game);
                    write(out, "GAME_OVER|-2|1|주흔:84:21:742:25", "GAME_OVER");
                    check("LEAVE".equals(in.readLine()), "ending blocks input before LEAVE");
                    check(in.readLine() == null, "one LEAVE for duplicate terminal messages");
                    waitFor(() -> frame.getContentPane() instanceof ResultPanel);
                }
                onEdt(() -> {
                    click(frame, "다시 하기", 2);
                    check(frame.getContentPane() instanceof TitlePanel, "retry waits for fresh approval");
                    return null;
                });
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    check(in.readLine().endsWith("|SOLO"), "retry uses a new HELLO");
                    var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    write(out, "REJECT|테스트 접속 거절");
                }
                waitFor(() -> frame.getContentPane() instanceof TitlePanel
                        && button(frame, "혼자 하기 (1P)").isEnabled());
            } finally {
                onEdt(() -> { frame.dispose(); return null; });
            }
        }
        System.out.println("SessionScreenTest: PASS (START, STATE gate, ending, duplicate result, retry)");
    }

    private static Message tick(StoreSnapshot empty, long timeLeftMs) {
        return new StoreSnapshot(empty.slot(), timeLeftMs, 0, 0, 0, -1, StoreLayout.COOK_STATIONS.getFirst().position().x(), StoreLayout.COOK_STATIONS.getFirst().position().y(),
                false, null, null, 0, 0, empty.tables(), empty.queue()).toMessage();
    }

    private static void click(GameFrame frame, String label, int count) {
        JButton button = button(frame, label);
        for (int i = 0; i < count; i++) button.doClick(0);
    }

    private static JButton button(GameFrame frame, String label) {
        for (Component component : frame.getContentPane().getComponents()) {
            if (component instanceof JButton button && button.getText().equals(label)) {
                return button;
            }
        }
        throw new AssertionError("missing button: " + label);
    }

    private static void space(GamePanel game) throws Exception {
        onEdt(() -> {
            game.getActionMap().get("press-SPACE").actionPerformed(new ActionEvent(game, 0, "press"));
            game.getActionMap().get("release-SPACE").actionPerformed(new ActionEvent(game, 0, "release"));
            return null;
        });
    }

    private static void write(BufferedWriter out, String... lines) throws Exception {
        for (String line : lines) {
            out.write(line);
            out.newLine();
        }
        out.flush();
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (onEdt(condition)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("screen/state did not change");
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
