package bunsik.client.ui;

import bunsik.common.GameConfig;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot;
import bunsik.server.GameServer;
import bunsik.server.ServerFrame;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class VersusScreenTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--real")) {
            realServer();
        } else {
            scriptedServer(0);
            scriptedServer(1);
            System.out.println("VersusScreenTest: PASS (both slots, ready, start gate, H/button, result, retained socket)");
        }
    }

    private static void scriptedServer(int slot) throws Exception {
        GameFrame frame = onEdt(() -> {
            GameFrame client = new GameFrame(false);
            client.addNotify();
            return client;
        });
        try (var server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            join(frame, server.getLocalPort(), "Alpha");
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                var out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                check("HELLO|Alpha|VERSUS".equals(in.readLine()), "expected versus HELLO");
                out.println("LOBBY|27:Alpha:false");
                out.println("WELCOME|27");
                check(onEdt(() -> frame.getContentPane() instanceof TitlePanel), "lobby requires WELCOME first");
                out.println("LOBBY|27:Alpha:false");
                waitFor(() -> frame.getContentPane() instanceof LobbyPanel);
                ChatPanel chat = onEdt(() -> chat(frame));
                onEdt(() -> { chat.input().setText("대기실 인사"); chat.input().postActionEvent(); return null; });
                check("CHAT|대기실 인사".equals(in.readLine()), "lobby chat uses game socket");
                out.println("CHAT|27|Alpha|대기실 인사");
                waitFor(() -> chat.history().contains("대기실 인사"));
                onEdt(() -> {
                    JButton ready = button(frame, "준비");
                    ready.doClick(0);
                    check(!ready.isEnabled() && ready.getText().equals("준비"), "ready waits for server");
                    ready.doClick(0);
                    return null;
                });
                check("READY|true".equals(in.readLine()), "ready request");
                out.println("LOBBY|42:Beta:false|27:Alpha:false");
                expectSilence(socket, in);
                check(onEdt(() -> !button(frame, "준비").isEnabled()), "other lobby update cannot acknowledge ready");
                out.println("LOBBY|42:Beta:false|27:Alpha:true");
                waitFor(() -> enabled(frame, "준비 취소"));
                click(frame, "준비 취소");
                check("READY|false".equals(in.readLine()), "cancel request");
                out.println("LOBBY|42:Beta:true|27:Alpha:false");
                waitFor(() -> enabled(frame, "준비"));
                click(frame, "준비");
                check("READY|true".equals(in.readLine()), "second ready");
                out.println("LOBBY|42:Beta:true|27:Alpha:true");
                waitFor(() -> frame.getContentPane() instanceof LobbyPanel && !enabled(frame, "준비")
                        && !enabled(frame, "준비 취소"));
                check(onEdt(() -> frame.getContentPane() instanceof LobbyPanel), "both ready cannot start without START");

                String names = slot == 0 ? "Alpha|Beta" : "Beta|Alpha";
                out.println("START|" + slot + "|2|" + names);
                waitFor(() -> frame.getContentPane() instanceof GamePanel);
                GamePanel game = onEdt(() -> (GamePanel) frame.getContentPane());
                check(onEdt(() -> game.opponentState() == null), "opponent starts empty");
                out.println(snapshot(1 - slot, 9000, 30));
                if (slot == 0) out.println("COUNTDOWN|0");
                waitFor(() -> game.opponentState() != null);
                key(game, "SPACE");
                expectSilence(socket, in);

                out.println(snapshot(slot, 9000, 71));
                waitFor(() -> game.state().gold() == 71);
                if (slot == 1) {
                    key(game, "SPACE");
                    expectSilence(socket, in);
                    out.println("COUNTDOWN|0");
                    out.println(snapshot(0, 9000, 32));
                    waitFor(() -> game.opponentState().gold() == 32);
                }
                onEdt(() -> {
                    game.openChat();
                    chat.enterReleased();
                    chat.input().setText("경기 인사");
                    chat.input().postActionEvent();
                    return null;
                });
                check("CHAT|경기 인사".equals(in.readLine()), "playing chat uses game socket");
                out.println("CHAT|27|Alpha|경기 인사");
                waitFor(() -> chat.history().contains("경기 인사"));
                onEdt(() -> {
                    action(game, "press-H");
                    action(game, "press-H");
                    check(button(frame, "상대 식당 보기").isVisible(), "held H toggles once");
                    action(game, "release-H");
                    return null;
                });
                out.println(snapshot(1 - slot, 8000, 31));
                waitFor(() -> game.opponentState().gold() == 31);
                check(onEdt(() -> game.state().slot() == slot && game.state().gold() == 71
                        && game.opponentState().slot() == 1 - slot), "snapshots remain separate");
                click(frame, "상대 식당 보기");
                check(onEdt(() -> !button(frame, "상대 식당 숨기기").isFocusable()
                        && game.opponentState().gold() == 31), "show newest opponent without taking keyboard focus");
                onEdt(() -> { action(game, "press-RIGHT"); return null; });
                check(in.readLine().startsWith("MOVE|"), "movement works after display toggle");
                onEdt(() -> { action(game, "release-RIGHT"); return null; });
                key(game, "SPACE");
                String command = in.readLine();
                check(command.startsWith("MOVE|"), "cook sends location first");
                do {
                    command = in.readLine();
                } while (command != null && command.startsWith("MOVE|"));
                check("COOK|TTEOKBOKKI".equals(command), "keyboard cook uses local coordinates");

                out.println(snapshot(slot, 0, 71));
                waitFor(() -> game.state().timeLeftMs() == 0);
                key(game, "SPACE");
                expectSilence(socket, in);
                out.println("GAME_OVER|0|2|" + scores(slot));
                waitFor(() -> frame.getContentPane() instanceof ResultPanel);
                ResultPanel result = onEdt(() -> (ResultPanel) frame.getContentPane());
                check(onEdt(() -> result.caption().equals(slot == 0 ? "승리" : "패배")), "result uses local slot");
                check(onEdt(() -> !button(frame, "대기실로").isEnabled()), "wait for lobby reset");
                out.println("GAME_OVER|-1|2|" + scores(slot));
                out.println("LOBBY|27:Alpha:false|42:Beta:false");
                waitFor(() -> button(frame, "대기실로").isEnabled());
                check(onEdt(() -> !result.caption().equals("무승부")), "duplicate result was ignored");
                click(frame, "대기실로");
                click(frame, "준비");
                check("READY|true".equals(in.readLine()), "rematch uses same socket");
                out.println("REJECT|테스트 거절");
                check("LEAVE".equals(in.readLine()), "rejected session closes normally");
                waitFor(() -> frame.getContentPane() instanceof TitlePanel);
            }
        } finally {
            onEdt(() -> { frame.dispose(); return null; });
        }
    }

    private static String scores(int slot) {
        return slot == 0 ? "Alpha:80:2:100:5|Beta:70:1:50:4" : "Beta:80:2:100:5|Alpha:70:1:50:4";
    }

    private static void realServer() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        ServerFrame serverFrame = onEdt(ServerFrame::new);
        GameServer server = new GameServer(serverFrame, port);
        GameFrame alpha = onEdt(() -> new GameFrame(false));
        GameFrame beta = onEdt(() -> new GameFrame(false));
        try {
            server.start();
            join(alpha, port, "Alpha");
            waitFor(() -> alpha.getContentPane() instanceof LobbyPanel);
            join(beta, port, "Beta");
            waitFor(() -> beta.getContentPane() instanceof LobbyPanel);
            click(alpha, "준비");
            waitFor(() -> enabled(alpha, "준비 취소"));
            click(alpha, "준비 취소");
            waitFor(() -> enabled(alpha, "준비"));
            readyBoth(alpha, beta);
            GamePanel first = onEdt(() -> (GamePanel) alpha.getContentPane());
            GamePanel second = onEdt(() -> (GamePanel) beta.getContentPane());
            waitFor(() -> first.opponentState() != null && second.opponentState() != null
                    && first.state().timeLeftMs() < GameConfig.GAME_DURATION_MS);
            check(onEdt(() -> first.state().slot() == 0 && first.opponentState().slot() == 1
                    && second.state().slot() == 1 && second.opponentState().slot() == 0), "real server slots");
            System.out.println("VersusScreenTest: real server started the three-minute round");
            waitFor(() -> alpha.getContentPane() instanceof ResultPanel
                    && beta.getContentPane() instanceof ResultPanel, GameConfig.GAME_DURATION_MS + 8000L);
            check(onEdt(() -> ((ResultPanel) alpha.getContentPane()).caption().equals("무승부")
                    && ((ResultPanel) beta.getContentPane()).caption().equals("무승부")), "real server tie");
            waitFor(() -> button(alpha, "대기실로").isEnabled() && button(beta, "대기실로").isEnabled());
            click(alpha, "대기실로");
            click(beta, "대기실로");
            readyBoth(alpha, beta);
            waitFor(() -> first.opponentState() != null && second.opponentState() != null
                    && first.state().timeLeftMs() < GameConfig.GAME_DURATION_MS);
            System.out.println("VersusScreenTest: PASS (real server, two clients, timed result and same-socket rematch)");
        } finally {
            onEdt(() -> { alpha.dispose(); beta.dispose(); serverFrame.dispose(); return null; });
            server.shutdown();
        }
    }

    private static void readyBoth(GameFrame alpha, GameFrame beta) throws Exception {
        click(alpha, "준비");
        click(beta, "준비");
        waitFor(() -> alpha.getContentPane() instanceof GamePanel && beta.getContentPane() instanceof GamePanel);
    }

    private static String snapshot(int slot, long timeLeftMs, int gold) {
        StoreSnapshot empty = StoreSnapshot.empty();
        var position = StoreLayout.COOK_STATIONS.getFirst().position();
        return new StoreSnapshot(slot, timeLeftMs, gold, 0, 0, -1, position.x(), position.y(),
                false, null, null, 0, 0, empty.tables(), empty.queue()).toMessage().encode();
    }

    private static void expectSilence(Socket socket, BufferedReader in) throws Exception {
        socket.setSoTimeout(250);
        try {
            throw new AssertionError("unexpected command: " + in.readLine());
        } catch (SocketTimeoutException expected) {
            // The connection remains open, with no commands in this phase.
        } finally {
            socket.setSoTimeout(5000);
        }
    }

    private static void join(GameFrame frame, int port, String nickname) throws Exception {
        onEdt(() -> {
            int i = 0;
            for (Component component : frame.getContentPane().getComponents()) {
                if (component instanceof JTextField field) {
                    field.setText(new String[]{nickname, "127.0.0.1", Integer.toString(port)}[i++]);
                }
            }
            button(frame, "경쟁 하기 (2P)").doClick(0);
            return null;
        });
    }

    private static void key(GamePanel game, String key) throws Exception {
        onEdt(() -> {
            action(game, "press-" + key);
            action(game, "release-" + key);
            return null;
        });
    }

    private static void action(GamePanel game, String id) {
        game.getActionMap().get(id).actionPerformed(new ActionEvent(game, 0, id));
    }

    private static JButton button(GameFrame frame, String label) {
        JButton button = findButton(frame, label);
        if (button == null) throw new AssertionError("missing button: " + label);
        return button;
    }

    private static ChatPanel chat(GameFrame frame) {
        for (Component component : frame.getContentPane().getComponents()) {
            if (component instanceof ChatPanel panel) return panel;
        }
        throw new AssertionError("missing chat panel");
    }

    private static boolean enabled(GameFrame frame, String label) {
        JButton button = findButton(frame, label);
        return button != null && button.isEnabled();
    }

    private static JButton findButton(GameFrame frame, String label) {
        for (Component component : frame.getContentPane().getComponents()) {
            if (component instanceof JButton button && button.getText().equals(label)) return button;
        }
        return null;
    }

    private static void click(GameFrame frame, String label) throws Exception {
        onEdt(() -> { button(frame, label).doClick(0); return null; });
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        waitFor(condition, 5000);
    }

    private static void waitFor(Callable<Boolean> condition, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
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
