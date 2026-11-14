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
    private final KitchenOverlay kitchen;
    private final ResultPanel result;
    private final LobbyPanel lobby;
    private NetworkClient network;
    private int slot,myId,session;
    private boolean preview;
    private TitlePanel.Connection connection;
    private Timer previewCook;
    public GameFrame(boolean preview) {
        super("도전! 분식왕");
        game=new GamePanel(this::send,()->showTitle("서버를 먼저 실행해 주세요."));
        kitchen=new KitchenOverlay(menu->send(new Message(MessageType.COOK,menu)),()->{game.setBlocked(false);game.requestFocusInWindow();},()->send(new Message(MessageType.INTERACT,game.state().chefX(),game.state().chefY())));
        game.setKitchen(kitchen);
        game.bind("ESCAPE",false,()->{if(kitchen.isVisible())kitchen.requestClose();});
        game.bind("SPACE",false,game::interact);
        game.setOverlayDiscard(kitchen::discard);
        title=new TitlePanel(this::connect,()->preview("식당"));
        result=new ResultPanel(()->{if(this.preview)preview("식당");else if(connection!=null)connect(connection);},()->showTitle("서버를 먼저 실행해 주세요."));
        lobby=new LobbyPanel(ready->send(new Message(MessageType.READY,ready)),()->showTitle("대기실에서 나왔습니다."));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1216,762);setMinimumSize(new Dimension(900,570));
        setLocationRelativeTo(null);setContentPane(title);
        JMenuBar bar=new JMenuBar();JMenu menu=new JMenu("화면 미리보기");
        for(String screen:new String[]{"시작","식당","카운트다운","부엌","조리 중","요리 완료","결과","대기실","2P 식당"}){
            JMenuItem item=new JMenuItem(screen);item.addActionListener(e->preview(screen));menu.add(item);
        }
        bar.add(menu);JLabel label=new JLabel("  미리보기는 서버 영업·점수 계산 없이 화면만 확인합니다.");bar.add(label);setJMenuBar(bar);
        addWindowListener(new WindowAdapter(){
            @Override public void windowDeactivated(WindowEvent e){game.clearInput();}
            @Override public void windowClosed(WindowEvent e){disconnect();}
        });
        if(preview)preview("식당");
    }
    private void connect(TitlePanel.Connection settings) {
        disconnect();preview=false;connection=settings;slot=0;myId=-1;
        kitchen.setVisible(false);game.countdown(0);game.setBlocked(true);
        game.setState(StoreSnapshot.empty());game.opponent(null);game.names(settings.nickname(),"상대");
        int current=session;
        try {
            network=new NetworkClient(m->{if(session==current)onMessage(m);},reason->{if(session==current)showTitle(reason);});
            network.connect(settings.host(),settings.port(),AppConfig.load().connectTimeoutMs(),settings.nickname(),settings.mode());
        }catch(Exception e){showTitle("설정 오류: "+e.getMessage());}
    }
    private void send(Message message) {
        if(preview){previewAction(message);return;}
        if(network!=null)network.send(message);
    }
    private void onMessage(Message m) {
        try {
            switch(m.type()){
                case WELCOME -> {myId=m.getInt(0);showGame();game.setBlocked(true);game.notice("접속 완료 · 서버의 START를 기다립니다.");}
                case LOBBY -> {kitchen.setVisible(false);game.setBlocked(true);lobby.update(m,myId);show(lobby);}
                case START -> {slot=m.getInt(0);int count=m.getInt(1);if(slot<0||slot>=count||count<1||count>2)throw new IllegalArgumentException("Invalid START");game.names(m.get(slot+2),count==2?m.get((1-slot)+2):"상대");showGame();game.countdown(3);game.notice("");}
                case COUNTDOWN -> {int count=m.getInt(0);if(count<0||count>3)throw new IllegalArgumentException("Invalid countdown");game.countdown(count);game.notice("");}
                case STATE -> {var state=StoreSnapshot.fromMessage(m);if(state.slot()==slot)game.setState(state);else game.opponent(state);}
                case OPEN_KITCHEN -> {game.setBlocked(true);kitchen.open(game.state());}
                case COOK_DONE -> {Menu.valueOf(m.get(0));game.notice("요리 완료! 손님에게 서빙해 주세요.");}
                case TOAST -> {game.notice(m.get(0));kitchen.rejected(m.get(0));}
                case GAME_OVER -> {result.showResult(m);kitchen.setVisible(false);game.setBlocked(true);show(result);}
                case REJECT -> showTitle(m.get(0));
                default -> { }
            }
        }catch(IllegalArgumentException e){game.notice("서버 메시지 형식 오류: "+e.getMessage());}
    }
    private void show(JPanel panel){setContentPane(panel);revalidate();repaint();}
    private void showGame(){show(game);game.requestFocusInWindow();}
    private void showTitle(String reason){disconnect();preview=false;kitchen.setVisible(false);game.setBlocked(true);title.reset(reason);show(title);}
    private void disconnect(){session++;if(previewCook!=null){previewCook.stop();previewCook=null;}if(network!=null){network.close();network=null;}}
    private void preview(String screen) {
        disconnect();preview=true;slot=0;game.names("주흔","차민");game.opponent(null);kitchen.setVisible(false);
        if(screen.equals("시작")){title.reset("화면 미리보기 · 실제 접속은 서버 실행 후 모드를 선택하세요.");show(title);return;}
        if(screen.equals("결과")){result.showResult(new Message(MessageType.GAME_OVER,-2,1,"주흔:84:21:742:25"));show(result);return;}
        if(screen.equals("대기실")){lobby.update(new Message(MessageType.LOBBY,"1:주흔:true","2:차민:false"),1);show(lobby);return;}
        var tables=java.util.List.of(new StoreSnapshot.TableView(TableState.WAITING_FOOD,Menu.TTEOKBOKKI,520,0,3),
            new StoreSnapshot.TableView(TableState.EATING,Menu.GIMBAP,-1,0,8),
            new StoreSnapshot.TableView(TableState.DIRTY,null,-1,41,0),
            new StoreSnapshot.TableView(TableState.WAITING_ORDER,null,820,0,13));
        StoreSnapshot state=new StoreSnapshot(0,128000,214,7,6,84,238,253,false,Menu.TTEOKBOKKI,null,0,0,tables,
            java.util.List.of(new StoreSnapshot.CustomerView(900,5),new StoreSnapshot.CustomerView(250,16)));
        game.setState(state);showGame();game.countdown(0);game.notice("화면 미리보기 · SPACE 부엌 · 상단 메뉴에서 다른 화면 선택");
        if(screen.equals("카운트다운")){game.setState(StoreSnapshot.empty());game.countdown(3);}
        if(screen.equals("2P 식당"))game.opponent(new StoreSnapshot(1,128000,180,6,5,79,430,265,false,null,null,0,0,tables,java.util.List.of(new StoreSnapshot.CustomerView(450,3))));
        if(screen.equals("부엌")||screen.equals("조리 중")||screen.equals("요리 완료")){
            StoreSnapshot next=copy(state,screen.equals("요리 완료")?Menu.TTEOKBOKKI:null,screen.equals("조리 중")?Menu.TTEOKBOKKI:null,screen.equals("조리 중")?1600:0);
            game.setState(next);game.setBlocked(true);kitchen.open(next);
        }
    }
    private static StoreSnapshot copy(StoreSnapshot s,Menu holding,Menu cooking,long remaining){
        return new StoreSnapshot(s.slot(),s.timeLeftMs(),s.gold(),s.servedCustomers(),s.sales(),s.satisfaction(),
            s.chefX(),s.chefY(),s.facingLeft(),holding,cooking,remaining,cooking==null?0:cooking.cookTimeMs,s.tables(),s.queue());
    }
    private void previewAction(Message m){
        if(m.type()==MessageType.INTERACT){
            if(kitchen.isVisible()&&game.state().holding()!=null){game.setState(copy(game.state(),null,null,0));kitchen.rejected("음식을 버렸습니다. (미리보기)");}
            else if(!kitchen.isVisible()){game.setState(copy(game.state(),null,null,0));game.setBlocked(true);kitchen.open(game.state());}
        } else if(m.type()==MessageType.COOK) {
            Menu menu=Menu.valueOf(m.get(0));long start=System.nanoTime();
            previewCook=new Timer(40,e->{
                long remain=Math.max(0,menu.cookTimeMs-(System.nanoTime()-start)/1000000);
                game.setState(copy(game.state(),remain==0?menu:null,remain==0?null:menu,remain));
                if(remain==0){previewCook.stop();previewCook=null;}
            });
            game.setState(copy(game.state(),null,menu,menu.cookTimeMs));previewCook.start();
        }
    }
}
