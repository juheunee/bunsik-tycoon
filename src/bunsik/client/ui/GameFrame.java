package bunsik.client.ui;

import bunsik.client.NetworkClient;
import bunsik.common.AppConfig;
import bunsik.common.Menu;
import bunsik.common.Message;
import bunsik.common.MessageType;
import bunsik.common.StoreSnapshot;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 클라이언트 창 하나. 화면 네 장(시작 → 대기실 → 게임 → 결과)을 카드처럼 넘기고,
 * 서버에서 온 메시지를 알맞은 화면에 전달하는 "교통정리" 역할을 한다.
 */
public final class GameFrame extends JFrame implements NetworkClient.Listener {

    static final String TITLE = "title";
    static final String LOBBY = "lobby";
    static final String GAME = "game";
    static final String RESULT = "result";

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final TitlePanel titlePanel;
    private final LobbyPanel lobbyPanel;
    private final GamePanel gamePanel;
    private final ResultPanel resultPanel;

    private NetworkClient net;
    private int myClientId = -1;

    public GameFrame(AppConfig cfg) {
        super("도전! 분식왕");
        titlePanel = new TitlePanel(this, cfg);
        lobbyPanel = new LobbyPanel(this);
        gamePanel = new GamePanel(this);
        resultPanel = new ResultPanel(this);

        root.add(titlePanel, TITLE);
        root.add(lobbyPanel, LOBBY);
        root.add(gamePanel, GAME);
        root.add(resultPanel, RESULT);
        root.setPreferredSize(new Dimension(1200, 700));
        setContentPane(root);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 창을 닫을 때 서버에 인사하고 소켓을 닫아야 서버에 유령 접속자가 남지 않는다.
                if (net != null) {
                    net.close();
                }
                dispose();
                System.exit(0);
            }
        });
    }

    void show(String card) {
        cards.show(root, card);
        if (GAME.equals(card)) {
            gamePanel.requestFocusInWindow();
        }
    }

    // ------------------------------------------------------------------
    // 시작 화면에서 호출
    // ------------------------------------------------------------------

    /** 접속은 시간이 걸릴 수 있어 별도 스레드에서 하고, 결과만 화면 스레드로 돌려받는다. */
    void connect(String nickname, String host, int port, boolean versus) {
        titlePanel.setBusy(true, "서버에 접속하는 중...");
        Thread t = new Thread(() -> {
            NetworkClient client = new NetworkClient(this);
            try {
                client.connect(host, port);
                SwingUtilities.invokeLater(() -> {
                    net = client;
                    lobbyPanel.reset();
                    client.send(MessageType.HELLO, nickname, versus ? "VERSUS" : "SOLO");
                    if (versus) {
                        show(LOBBY);
                    }
                    titlePanel.setBusy(false, " ");
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    titlePanel.setBusy(false, " ");
                    showError("서버(" + host + ":" + port + ")에 연결할 수 없어요.\n"
                            + "서버가 켜져 있는지, IP와 포트가 맞는지, 같은 네트워크인지 확인해 주세요.");
                });
            }
        }, "connector");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------
    // 대기실·결과 화면에서 호출
    // ------------------------------------------------------------------

    void sendReady(boolean ready) {
        if (net != null) {
            net.send(MessageType.READY, ready);
        }
    }

    void backToLobby() {
        show(LOBBY);
    }

    void leaveToTitle() {
        if (net != null) {
            net.close();
            net = null;
        }
        gamePanel.stop();
        titlePanel.setBusy(false, " ");
        show(TITLE);
    }

    NetworkClient network() {
        return net;
    }

    int myClientId() {
        return myClientId;
    }

    private void showError(String text) {
        JOptionPane.showMessageDialog(this, text, "도전! 분식왕", JOptionPane.WARNING_MESSAGE);
    }

    // ------------------------------------------------------------------
    // 서버 메시지 처리 (NetworkClient가 화면 스레드에서 호출)
    // ------------------------------------------------------------------

    @Override
    public void onMessage(Message m) {
        try {
            switch (m.type()) {
                case WELCOME -> myClientId = m.getInt(0);
                case REJECT -> {
                    String reason = m.size() > 0 ? m.get(0) : "입장할 수 없어요.";
                    leaveToTitle();
                    showError(reason);
                }
                case LOBBY -> lobbyPanel.update(parseLobby(m), myClientId);
                case START -> {
                    String[] nicks = {m.get(2), m.get(3)};
                    gamePanel.prepare(net, m.getInt(0), m.getInt(1), nicks);
                    show(GAME);
                }
                case COUNTDOWN -> gamePanel.setCountdown(m.getInt(0));
                case STATE -> gamePanel.applySnapshot(StoreSnapshot.fromMessage(m));
                case OPEN_KITCHEN -> gamePanel.openKitchen();
                case COOK_DONE -> gamePanel.onCookDone(Menu.fromCode(m.get(0)));
                case TOAST -> gamePanel.toast(m.get(0));
                case GAME_OVER -> {
                    gamePanel.stop();
                    resultPanel.showResult(m, gamePanel.mySlot());
                    show(RESULT);
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[클라이언트] 메시지 처리 실패: " + m + " (" + e.getMessage() + ")");
        }
    }

    private static List<LobbyPanel.Entry> parseLobby(Message m) {
        List<LobbyPanel.Entry> list = new ArrayList<>();
        for (int i = 0; i < m.size(); i++) {
            String[] f = m.get(i).split(":");
            if (f.length >= 3) {
                list.add(new LobbyPanel.Entry(Integer.parseInt(f[0]), f[1], Boolean.parseBoolean(f[2])));
            }
        }
        return list;
    }

    @Override
    public void onDisconnected() {
        // MVP 수준의 끊김 처리: 안내 후 시작 화면으로. (재접속·상대 알림 등은 README "앞으로 할 일")
        if (net == null) {
            return;
        }
        net = null;
        gamePanel.stop();
        titlePanel.setBusy(false, " ");
        show(TITLE);
        showError("서버와의 연결이 끊어졌어요.");
    }
}
