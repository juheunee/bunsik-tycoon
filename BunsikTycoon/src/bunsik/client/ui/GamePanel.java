package bunsik.client.ui;

import bunsik.common.*;
import bunsik.common.Menu;
import bunsik.client.render.StoreRenderer;
import bunsik.client.render.Sprites;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.*;

public final class GamePanel extends JPanel {
    private StoreSnapshot state=StoreSnapshot.empty(),opponent;
    private final Set<String> pressed=new HashSet<>();
    private final Consumer<Message> sender;
    private final Timer timer;
    private double x=state.chefX(),y=state.chefY();
    private boolean left,blocked=true,versus,hideOpponent,hDown,spaceDown,noticeBeforeState;
    private String notice="서버 시작 대기 중",nickname="사장님",otherName="상대";
    private int countdown=-1;
    private boolean paused;
    private final JButton exit, opponentButton, chatToggle;
    private ChatPanel chat;
    private boolean chatCollapsed;
    private record Interaction(Menu station, int table, String hint, boolean available, boolean cook) {
        Interaction(int table, String hint, boolean available) {
            this(null, table, hint, available, false);
        }
    }
    private Interaction pending;
    private boolean choosingDiscard;

    private void interact() {
        // 버릴 손 선택 중 SPACE: 취소
        if (choosingDiscard) {
            choosingDiscard=false;
            repaint();
            return;
        }
        if (blocked || pending!=null) return;
        Interaction target=target();
        if (target==null || !target.available()) return;
        pending=target;
        noticeBeforeState=false;
        if (target.cook()) {
            // COOK carries only the menu. Queue the current position on the same writer first.
            sender.accept(new Message(MessageType.MOVE,(int)x,(int)y,left));
            sender.accept(new Message(MessageType.COOK,target.station()));
        } else {
            sender.accept(new Message(MessageType.INTERACT,(int)x,(int)y));
         // 양손 모두 음식이면 서버 안내(TOAST)와 함께 버릴 손 선택 시작
            if (StoreLayout.nearKitchenTarget(StoreLayout.DISCARD,(int)x,(int)y)
                    && state.hands().stream().allMatch(hand->hand.menu()!=null)) {
                choosingDiscard=true;
                pressed.clear();
            }
        }
    }
    
    // 방향키 왼쪽/A: 왼손, 오른쪽/D: 오른손
    private void chooseDiscard(String key) {
        int hand=key.equals("LEFT")||key.equals("A")?0:key.equals("RIGHT")||key.equals("D")?1:-1;
        if (hand<0) return;
        choosingDiscard=false;
        pending=new Interaction(-1,"",true);
        noticeBeforeState=false;
        sender.accept(new Message(MessageType.DISCARD,hand));
        repaint();
    }

    private Interaction target() {
        int currentX=(int)x, currentY=(int)y;
        if (StoreLayout.nearKitchenTarget(StoreLayout.DISH_BIN, currentX, currentY)) {
            boolean hasDish = state.hands().stream().anyMatch(StoreSnapshot.Hand::dish);
            return new Interaction(-1, hasDish ? "SPACE 그릇 내려놓기" : "내려놓을 그릇 없음", hasDish);
        }
        boolean hasFood = state.hands().stream().anyMatch(hand -> hand.menu() != null);
        if (StoreLayout.nearKitchenTarget(StoreLayout.DISCARD, currentX, currentY)) {
            return new Interaction(-1, hasFood ? "SPACE 음식 버리기" : "폐기할 음식 없음", hasFood);
        }
        var station=StoreLayout.nearestStation(currentX,currentY);
        if (station!=null) {
            Menu menu=station.menu();
            Long remaining = state.stations().get(menu);
            if (remaining == null) return new Interaction(menu, -1, "SPACE " + menu.label + " 조리", true, true);
            if (remaining > 0) return new Interaction(menu, -1, menu.label + " 조리 중", false, false);
            boolean freeHand = state.hasFreeHand();
            return new Interaction(menu, -1, freeHand ? "SPACE 음식 집기" : "한 손을 비워 주세요", freeHand, false);
        }
        int nearest=-1;
        double best=StoreLayout.TABLE_REACH;
        for (int i=0;i<StoreLayout.TABLES.size();i++) {
            double distance=StoreLayout.distance(StoreLayout.TABLES.get(i),currentX,currentY);
            if (distance<best) {
                nearest=i;
                best=distance;
            }
        }
        if (nearest<0) return null;
        var table=state.tables().get(nearest);
        return switch (table.state()) {
            case WAITING_ORDER -> new Interaction(nearest,"SPACE 주문 받기",true);
            case WAITING_FOOD -> {
                boolean matchingFood = table.menu() != null
                        && state.hands().stream().anyMatch(hand -> hand.menu() == table.menu());
                yield new Interaction(nearest, matchingFood ? "SPACE 서빙하기"
                        : hasFood ? "주문 메뉴가 달라요" : "음식 준비 필요", hasFood);
            }
            case DIRTY -> new Interaction(nearest,
                    state.hasFreeHand() ? "SPACE 그릇 집기" : "한 손을 비워 주세요", state.hasFreeHand());
            case EATING -> new Interaction(nearest,"식사 중",false);
            case EMPTY -> null;
        };
    }

    public GamePanel(Consumer<Message> sender,Runnable onExit) {
        this.sender=sender;setLayout(null);setPreferredSize(new Dimension(1200,700));UiKit.style(this);setFocusable(true);
        exit=UiKit.button("나가기",new Color(103,69,47),onExit);add(exit);exit.setFocusable(false);
        opponentButton=UiKit.button("상대 식당 숨기기",new Color(103,69,47),()-> {
            toggleOpponent();
            requestFocusInWindow();
        });
        opponentButton.setFocusable(false);
        opponentButton.setVisible(false);
        add(opponentButton);
        chatToggle=UiKit.button("채팅 접기 ▼",new Color(103,69,47),()->setChatCollapsed(!chatCollapsed));
        chatToggle.setFocusable(false);
        chatToggle.setVisible(false);
        add(chatToggle);
        bind("ESCAPE",false,onExit);
        for(String key:new String[]{"UP","DOWN","LEFT","RIGHT","W","A","S","D"}) {
            bind(key,false,()->{if(choosingDiscard){chooseDiscard(key);return;}if(!blocked)pressed.add(key);});bind(key,true,()->pressed.remove(key));
        }
        bind("SPACE",false,()-> {
            if (!spaceDown) {
                spaceDown=true;
                interact();
            }
        });
        bind("SPACE",true,()->spaceDown=false);
        bind("H",false,()-> {
            if (hDown) return;
            hDown = true;
            toggleOpponent();
        });
        bind("H",true,()->hDown=false);
        addFocusListener(new FocusAdapter(){@Override public void focusLost(FocusEvent e){clearInput();}});
        timer=new Timer(GameConfig.INPUT_TICK_MS,e->tick());
    }
    public void bind(String key,boolean release,Runnable action) {
        String id=(release?"release-":"press-")+key;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke((release?"released ":"pressed ")+key),id);
        getActionMap().put(id,new AbstractAction(){public void actionPerformed(ActionEvent e){
            if (chat != null && chat.isEditing()) return;
            action.run();
        }});
    }
    public void setChatPanel(ChatPanel next) {
        if (chat == next) return;
        if (chat != null) remove(chat);
        chat = next;
        chatCollapsed = false;
        if (chat != null) add(chat);
        chatToggle.setVisible(chat != null);
        chatToggle.setText("채팅 접기 ▼");
        revalidate(); repaint();
    }
    private void setChatCollapsed(boolean collapsed) {
        if (chat == null) return;
        if (collapsed && chat.isEditing()) chat.cancel();
        chatCollapsed = collapsed;
        chat.setVisible(!collapsed);
        chatToggle.setText(collapsed ? "채팅 펼치기 ▲" : "채팅 접기 ▼");
        revalidate(); repaint();
        requestFocusInWindow();
    }
    public void openChat() {
        if (chat == null) return;
        if (chatCollapsed) setChatCollapsed(false);
        clearInput();
        chat.beginGameInput();
    }
    private void tick() {
        if(blocked||choosingDiscard)return;
        int dx=(has("RIGHT","D")?1:0)-(has("LEFT","A")?1:0),dy=(has("DOWN","S")?1:0)-(has("UP","W")?1:0);
        if(dx==0&&dy==0)return;
        double step=GameConfig.PLAYER_SPEED*GameConfig.INPUT_TICK_MS/1000.0/Math.hypot(dx,dy);
        x=StoreLayout.clampX((int)Math.round(x+dx*step));y=StoreLayout.clampY((int)Math.round(y+dy*step));
        if(dx!=0)left=dx<0;sender.accept(new Message(MessageType.MOVE,(int)x,(int)y,left));repaint();
    }
    private boolean has(String a,String b){return pressed.contains(a)||pressed.contains(b);}
    public void setState(StoreSnapshot next) {
        if (pending != null && (noticeBeforeState || !next.hands().equals(state.hands())
                || (pending.station() != null && !state.stations().containsKey(pending.station())
                        && next.stations().containsKey(pending.station()))
                || tableChanged(next))) clearPending();
        state=next;
        if (pressed.isEmpty() || blocked) {
            x=next.chefX();
            y=next.chefY();
            left=next.facingLeft();
        }
        repaint();
    }
    private boolean tableChanged(StoreSnapshot next) {
        int index=pending.table();
        return index>=0 && (next.tables().get(index).state()!=state.tables().get(index).state()
                || next.tables().get(index).menu()!=state.tables().get(index).menu());
    }
    private void clearPending(){pending=null;noticeBeforeState=false;}
    public void serverNotice(String text){if(pending!=null)noticeBeforeState=true;notice(text);}
    public StoreSnapshot state(){return state;}
    public void names(String mine,String other){nickname=mine;otherName=other;repaint();}
    public void opponent(StoreSnapshot next){opponent=next;repaint();}
    StoreSnapshot opponentState(){return opponent;}
    public void setVersus(boolean value) {
        versus = value;
        opponentButton.setVisible(value);
        revalidate();
        repaint();
    }
    private void toggleOpponent() {
        if (!versus) return;
        hideOpponent = !hideOpponent;
        refreshOpponentButton();
    }
    private void refreshOpponentButton() {
        opponentButton.setText(hideOpponent ? "상대 식당 보기" : "상대 식당 숨기기");
        repaint();
    }
    public void setPaused(boolean value){paused=value;repaint();}
    public void countdown(int value){countdown=value;repaint();}
    public void setBlocked(boolean value){blocked=value;pressed.clear();if(value){clearPending();choosingDiscard=false;}}
    public void resetSessionInput(){clearInput();clearPending();hideOpponent=false;refreshOpponentButton();}
    public void clearInput(){pressed.clear();spaceDown=false;hDown=false;}
    public void notice(String text){notice=text;repaint();}
    @Override public void doLayout(){
        if (versus) UiKit.bounds(this,exit,924,16,120,30);
        else UiKit.bounds(this,exit,1060,64,120,26);
        UiKit.bounds(this,opponentButton,740,16,170,30);
        UiKit.bounds(this,chatToggle,1060,chatCollapsed?630:473,130,28);
        if (chat != null) UiKit.bounds(this,chat,870,505,320,155);
    }
    @Override public void addNotify(){super.addNotify();timer.start();}
    @Override public void removeNotify(){timer.stop();clearInput();super.removeNotify();}
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);Graphics2D g=UiKit.canvas(this,graphics);
        g.setColor(new Color(44,28,18));g.fillRect(0,0,1200,700);
        for(int i=0;i<1200;i+=48){g.setColor(new Color(51,32,20));g.drawLine(i,92,i,670);}
        g.setColor(Sprites.RED);g.fillRect(0,0,1200,60);
        Sprites.text(g,"도전! 분식왕",28,40,25,new Color(137,34,19));Sprites.text(g,"도전! 분식왕",27,37,24,Sprites.YELLOW);
        String[] labels={"골드","다녀간 손님","판매 메뉴","만족도"};
        String[] values={""+state.gold(),""+state.servedCustomers(),""+state.sales(),state.satisfaction()<0?"-":state.satisfaction()+"%"};
        int[] widths={62,80,70,70};int pos=195;
        for(int i=0;i<4;i++){Sprites.rounded(g,pos,10,widths[i],44,7,UiKit.CREAM,null);Sprites.center(g,labels[i],pos+widths[i]/2,25,11,new Color(133,87,52));Sprites.center(g,values[i],pos+widths[i]/2,49,22,new Color(180,39,26));pos+=widths[i]+9;}
        Sprites.rounded(g,1058,9,128,44,7,new Color(57,26,12),null);Sprites.text(g,"남은 시간",1068,25,10,UiKit.CREAM);
        long seconds=(state.timeLeftMs()+999)/1000;Sprites.center(g,String.format("%d:%02d",seconds/60,seconds%60),1122,45,24,Sprites.YELLOW);
        g.setColor(new Color(63,38,22));g.fillRect(0,60,1200,32);
        Sprites.text(g,"내 식당 · "+nickname,14,82,14,Sprites.YELLOW);
        boolean split = versus && !hideOpponent;
        String hands = "왼손 · " + state.hands().get(0).label() + "   오른손 · " + state.hands().get(1).label();
        if (split) Sprites.text(g,hands,14,138,14,UiKit.CREAM);
        else Sprites.text(g,hands,versus ? 420 : 260,82,14,UiKit.CREAM);
        Interaction target=blocked?null:target();
        String hint=choosingDiscard?"← "+state.hands().get(0).label()+", → "+state.hands().get(1).label()
                :pending!=null?"서버 응답 대기":target==null?"":target.hint();
        if (split) {
            Sprites.text(g,"상대 식당 · "+otherName,615,82,14,Sprites.YELLOW);
            drawVersusStore(g,state,10,150,580,510,(int)x,(int)y,left,hint);
            if (opponent == null) {
                Sprites.center(g,"상대 식당 상태를 기다리는 중",900,400,18,UiKit.CREAM);
            } else {
                String satisfaction = opponent.satisfaction()<0 ? "-" : opponent.satisfaction()+"%";
                Sprites.text(g,"골드 "+opponent.gold()+" · 손님 "+opponent.servedCustomers()+" · 판매 "
                        +opponent.sales()+" · 만족도 "+satisfaction,615,138,13,UiKit.CREAM);
                drawVersusStore(g,opponent,610,150,580,510,opponent.chefX(),opponent.chefY(),opponent.facingLeft(),"");
            }
            g.setColor(new Color(19,12,8));g.fillRect(598,92,4,578);
        } else if (versus) {
            drawVersusStore(g,state,10,98,1180,566,(int)x,(int)y,left,hint);
        } else drawStore(g,state,165,98,870,566,(int)x,(int)y,left,hint);
        Sprites.center(g,"방향키/WASD 이동   SPACE 조리 · 픽업 · 주문 · 서빙 · 청소   ESC 나가기"
                +(versus?"   H 상대 식당 표시 전환":""),600,690,12,UiKit.CREAM);
        // 서버 PAUSE 상태 표시 (서버 안내 문구는 정지 화면 위에 보이도록 먼저 그림)
        if(paused){g.setColor(new Color(0,0,0,120));g.fillRect(0,92,1200,578);Sprites.center(g,versus?"잠시 멈춤":"일시 정지 (P: 계속)",600,400,40,Color.WHITE);}
        if(!notice.isEmpty()){Sprites.rounded(g,360,98,480,26,20,new Color(54,34,18,230),null);Sprites.center(g,notice,600,116,12,Sprites.YELLOW);}
        if(countdown>0){g.setColor(new Color(0,0,0,145));g.fillRect(0,92,1200,578);Sprites.center(g,""+countdown,600,422,135,Sprites.RED);Sprites.center(g,""+countdown,600,416,125,Sprites.YELLOW);Sprites.center(g,"손님이 곧 들어와요! 방향키로 움직이고 스페이스로 일해요",600,478,20,Color.WHITE);}
        g.dispose();
    }
    private void drawVersusStore(Graphics2D g,StoreSnapshot snapshot,int bx,int by,int w,int h,
                                 int chefX,int chefY,boolean facingLeft,String hint) {
        double canvasX = getWidth()/1200.0;
        double canvasY = getHeight()/700.0;
        double scale = Math.min(w*canvasX/StoreLayout.WIDTH,h*canvasY/StoreLayout.HEIGHT);
        double width = scale*StoreLayout.WIDTH/canvasX;
        double height = scale*StoreLayout.HEIGHT/canvasY;
        drawStore(g,snapshot,bx+(w-width)/2,by+(h-height)/2,width,height,chefX,chefY,facingLeft,hint);
    }
    private static void drawStore(Graphics2D g,StoreSnapshot state,double bx,double by,double w,double h,
                                  int x,int y,boolean left,String hint){
        Graphics2D room=(Graphics2D)g.create();
        room.translate(bx,by);
        room.scale(w/StoreLayout.WIDTH,h/StoreLayout.HEIGHT);
        room.clipRect(0,0,StoreLayout.WIDTH,StoreLayout.HEIGHT);
        StoreRenderer.paint(room,state,x,y,left,g2->KitchenOverlay.paint(g2,state),hint);
        room.dispose();
    }
}
