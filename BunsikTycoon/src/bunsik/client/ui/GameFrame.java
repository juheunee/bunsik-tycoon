package bunsik.client.ui;

import bunsik.client.NetworkClient;
import bunsik.common.*;
import bunsik.common.Menu;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public final class GameFrame extends JFrame {
    private final TitlePanel title;
    private final GamePanel game;
    private final ResultPanel result;
    private final LobbyPanel lobby;
    private NetworkClient network;
    private int slot,myId,session;
    private enum Phase { IDLE, CONNECTING, LOBBY, COUNTDOWN, PLAYING, ENDING, RESULT }

    private Phase phase = Phase.IDLE;
    private boolean preview, countdownFinished, hasState;
    private final Timer startTimeout = new Timer(10000, e -> showTitle("서버의 게임 시작 응답을 받지 못했습니다."));
    private final Timer readyTimeout = new Timer(10000, e -> showTitle("서버의 준비 상태 응답을 받지 못했습니다."));
    private int playerCount;
    private TitlePanel.Connection connection;
    public GameFrame(boolean preview) {
        super("도전! 분식왕");
        game=new GamePanel(this::send,()->showTitle("타이틀로 돌아왔습니다."));
        startTimeout.setRepeats(false);
        readyTimeout.setRepeats(false);
        title=new TitlePanel(this::connect,preview ? ()->preview("식당") : null);
        result=new ResultPanel(this::retry,()->showTitle("타이틀로 돌아왔습니다."));
        lobby=new LobbyPanel(ready -> {
            if (phase != Phase.LOBBY || network == null) return;
            readyTimeout.restart();
            network.send(new Message(MessageType.READY, ready));
        },()->showTitle("대기실에서 나왔습니다."));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1216,762);setMinimumSize(new Dimension(900,570));
        setLocationRelativeTo(null);setContentPane(title);
        if (preview) {
            JMenuBar bar=new JMenuBar();JMenu menu=new JMenu("화면 미리보기");
            for(String screen:new String[]{"시작","식당","카운트다운","부엌","조리 중","요리 완료","결과","대기실","2P 식당"}){
                JMenuItem item=new JMenuItem(screen);item.addActionListener(e->preview(screen));menu.add(item);
            }
            bar.add(menu);setJMenuBar(bar);
        }
        addWindowListener(new WindowAdapter(){
            @Override public void windowDeactivated(WindowEvent e){game.clearInput();}
            @Override public void windowClosed(WindowEvent e){disconnect();}
        });
        if(preview)preview("식당");
    }
    private void connect(TitlePanel.Connection settings) {
        if (phase != Phase.IDLE && phase != Phase.RESULT) return;
        disconnect();
        preview = false;
        phase = Phase.CONNECTING;
        setTitle("도전! 분식왕 · 게임 시작 대기");
        connection = settings;
        slot = 0;
        myId = -1;
        playerCount = 0;
        hasState = countdownFinished = false;
        game.countdown(0);
        updateInput();
        game.resetSessionInput();
        game.setState(StoreSnapshot.empty());
        game.opponent(null);
        game.setVersus(false);
        game.names(settings.nickname(), "상대");
        game.notice("");
        title.status("서버 연결 중…");
        show(title);
        int current = session;
        try {
            network = new NetworkClient(
                    m -> { if (session == current) onMessage(m); },
                    reason -> { if (session == current) showTitle(reason); });
            network.connect(settings.host(), settings.port(), AppConfig.load().connectTimeoutMs(),
                    settings.nickname(), settings.mode());
            startTimeout.restart();
        } catch (Exception e) {
            showTitle("설정 오류: " + e.getMessage());
        }
    }

    private void send(Message message) {
        if (!preview && network != null && phase == Phase.PLAYING) {
            network.send(message);
        }
    }

    private void updateInput() {
        game.setBlocked(phase != Phase.PLAYING);
    }

    private void onMessage(Message m) {
        if (phase == Phase.IDLE) return;
        try {
            switch (m.type()) {
                case WELCOME -> {
                    if (phase != Phase.CONNECTING) return;
                    myId = m.getInt(0);
                    if (myId < 1) throw new IllegalArgumentException("Invalid connection ID");
                    title.status("접속 확인 · 서버 응답을 기다립니다.");
                }
                case LOBBY -> {
                    if (!isVersus() || myId < 1
                            || (phase != Phase.CONNECTING && phase != Phase.LOBBY && phase != Phase.RESULT)) return;
                    lobby.update(m, myId);
                    if (!lobby.isAwaitingReady()) readyTimeout.stop();
                    if (phase == Phase.RESULT) {
                        result.setRetryEnabled(lobby.hasLocalPlayer());
                    } else if (phase == Phase.CONNECTING && lobby.hasLocalPlayer()) {
                        startTimeout.stop();
                        openLobby();
                    }
                }
                case START -> {
                    if (phase != (isVersus() ? Phase.LOBBY : Phase.CONNECTING) || myId < 1) return;
                    int nextSlot = m.getInt(0);
                    int count = m.getInt(1);
                    int expected = isVersus() ? 2 : 1;
                    if (count != expected || nextSlot < 0 || nextSlot >= count || m.size() != 4
                            || m.get(nextSlot + 2).isBlank() || (count == 2 && m.get(3 - nextSlot).isBlank())) {
                        throw new IllegalArgumentException("Invalid START");
                    }
                    slot = nextSlot;
                    playerCount = count;
                    readyTimeout.stop();
                    startTimeout.restart();
                    hasState = countdownFinished = false;
                    game.resetSessionInput();
                    game.setState(StoreSnapshot.empty());
                    game.opponent(null);
                    game.setVersus(count == 2);
                    phase = Phase.COUNTDOWN;
                    setTitle("도전! 분식왕");
                    game.names(m.get(slot + 2), count == 2 ? m.get(3 - slot) : "상대");
                    game.countdown(3);
                    game.notice("");
                    updateInput();
                    showGame();
                }
                case COUNTDOWN -> {
                    if (phase != Phase.COUNTDOWN) return;
                    int count = m.getInt(0);
                    if (count < 0 || count > 3) throw new IllegalArgumentException("Invalid countdown");
                    if (countdownFinished) return;
                    countdownFinished = count == 0;
                    game.countdown(count);
                    game.notice(countdownFinished && !hasState ? "서버 상태를 기다립니다." : "");
                    beginIfReady();
                }
                case STATE -> {
                    if (phase != Phase.COUNTDOWN && phase != Phase.PLAYING && phase != Phase.ENDING) return;
                    var state = StoreSnapshot.fromMessage(m);
                    if (state.slot() != slot) {
                        if (playerCount == 2) game.opponent(state);
                        return;
                    }
                    game.setState(state);
                    hasState = true;
                    beginIfReady();
                    if (phase == Phase.PLAYING && state.timeLeftMs() == 0) waitForResult();
                }
                case COOK_DONE -> {
                    Menu menu = Menu.valueOf(m.get(0));
                    if (phase == Phase.PLAYING) game.notice(menu.label + " 조리 완료! 조리대에서 집어 주세요.");
                }
                case TOAST -> {
                    if (phase != Phase.PLAYING) return;
                    game.serverNotice(m.get(0));
                }
                case GAME_OVER -> {
                    if (phase != Phase.PLAYING && phase != Phase.ENDING) return;
                    if (m.getInt(1) != playerCount) throw new IllegalArgumentException("Invalid result count");
                    result.showResult(m, slot);
                    phase = Phase.RESULT;
                    updateInput();
                    result.setRetryEnabled(!isVersus());
                    if (isVersus()) lobby.reset();
                    else disconnect();
                    show(result);
                }
                case REJECT -> { if (network != null) showTitle(m.get(0)); }
                default -> { }
            }
        } catch (IllegalArgumentException e) {
            showTitle("서버 메시지 형식 오류: " + e.getMessage());
        }
    }

    private void beginIfReady() {
        if (phase != Phase.COUNTDOWN || !countdownFinished || !hasState) return;
        startTimeout.stop();
        if (game.state().timeLeftMs() == 0) {
            waitForResult();
            return;
        }
        phase = Phase.PLAYING;
        game.notice("");
        updateInput();
    }

    private void waitForResult() {
        phase = Phase.ENDING;
        game.notice("서버의 영업 종료 결과를 기다립니다.");
        updateInput();
    }

    private boolean isVersus() {
        return connection != null && "VERSUS".equals(connection.mode());
    }

    private void retry() {
        if (preview) {
            preview("식당");
        } else if (phase == Phase.RESULT) {
            if (!isVersus()) connect(connection);
            else if (lobby.hasLocalPlayer()) openLobby();
        }
    }

    private void openLobby() {
        phase = Phase.LOBBY;
        setTitle("도전! 분식왕 · 경쟁 대기실");
        show(lobby);
    }

    private void show(JPanel panel){setContentPane(panel);revalidate();repaint();}
    private void showGame(){show(game);game.requestFocusInWindow();}
    private void showTitle(String reason) {
        disconnect();
        preview = false;
        phase = Phase.IDLE;
        setTitle("도전! 분식왕");
        updateInput();
        title.reset(reason);
        show(title);
    }

    private void disconnect() {
        session++;
        startTimeout.stop();
        readyTimeout.stop();
        lobby.reset();
        if (network != null) {
            network.leave();
            network = null;
        }
    }

    private void preview(String screen) {
        disconnect();phase=Phase.IDLE;preview=true;setTitle("도전! 분식왕 · 화면 미리보기");slot=0;game.names("주흔","차민");game.opponent(null);game.resetSessionInput();game.setVersus(screen.equals("2P 식당"));
        if(screen.equals("시작")){title.reset("화면 미리보기 · 실제 접속은 서버 실행 후 모드를 선택하세요.");show(title);return;}
        if(screen.equals("결과")){result.showResult(new Message(MessageType.GAME_OVER,-2,1,"주흔:84:21:742:25"),0);result.setRetryEnabled(true);show(result);return;}
        if(screen.equals("대기실")){lobby.update(new Message(MessageType.LOBBY,"1:주흔:true","2:차민:false"),1);show(lobby);return;}
        var tables=java.util.List.of(new StoreSnapshot.TableView(TableState.WAITING_FOOD,Menu.TTEOKBOKKI,520,0,3),
            new StoreSnapshot.TableView(TableState.EATING,Menu.GIMBAP,-1,0,8),
            new StoreSnapshot.TableView(TableState.DIRTY,null,-1,41,0),
            new StoreSnapshot.TableView(TableState.WAITING_ORDER,null,820,0,13));
        StoreSnapshot state=new StoreSnapshot(0,128000,214,7,6,84,238,253,false,Menu.TTEOKBOKKI,null,0,0,tables,
            java.util.List.of(new StoreSnapshot.CustomerView(900,5),new StoreSnapshot.CustomerView(250,16)));
        game.setState(state);showGame();game.countdown(0);updateInput();game.notice("화면 미리보기 · 상단 메뉴에서 조리 상태 선택");
        if(screen.equals("카운트다운")){game.setState(StoreSnapshot.empty());game.countdown(3);game.setBlocked(true);}
        if(screen.equals("2P 식당"))game.opponent(new StoreSnapshot(1,128000,180,6,5,79,430,265,false,null,null,0,0,tables,java.util.List.of(new StoreSnapshot.CustomerView(450,3))));
        if(screen.equals("부엌")||screen.equals("조리 중")||screen.equals("요리 완료")){
            var stations = switch (screen) {
                case "조리 중" -> java.util.Map.of(Menu.TTEOKBOKKI, 1600L, Menu.SUNDAE, 2800L);
                case "요리 완료" -> java.util.Map.of(Menu.TTEOKBOKKI, 0L, Menu.SUNDAE, 2800L);
                default -> java.util.Map.<Menu, Long>of();
            };
            game.setState(new StoreSnapshot(state.slot(), state.timeLeftMs(), state.gold(), state.servedCustomers(),
                    state.sales(), state.satisfaction(), state.chefX(), state.chefY(), state.facingLeft(),
                    java.util.List.of(new StoreSnapshot.Hand(Menu.TTEOKBOKKI, false), StoreSnapshot.Hand.DISH),
                    stations, state.tables(), state.queue()));
        }
    }
}
