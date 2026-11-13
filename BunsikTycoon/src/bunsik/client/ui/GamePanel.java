package bunsik.client.ui;

import bunsik.common.*;
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
    private boolean left,blocked=true,hideOpponent;
    private String notice="서버 시작 대기 중",nickname="사장님",otherName="상대";
    private int countdown=-1;
    private final JButton exit;
    private KitchenOverlay kitchen;
    private Runnable overlayDiscard=()->{};
    public void setOverlayDiscard(Runnable action){overlayDiscard=action;}
    public void interact(){
        if(kitchen!=null&&kitchen.isVisible()){overlayDiscard.run();return;}
        if(!blocked)sender.accept(new Message(MessageType.INTERACT,(int)x,(int)y));
    }
    public GamePanel(Consumer<Message> sender,Runnable onExit) {
        this.sender=sender;setLayout(null);setPreferredSize(new Dimension(1200,700));UiKit.style(this);setFocusable(true);
        exit=UiKit.button("나가기",new Color(103,69,47),onExit);add(exit);
        for(String key:new String[]{"UP","DOWN","LEFT","RIGHT","W","A","S","D"}) {
            bind(key,false,()->{if(!blocked)pressed.add(key);});bind(key,true,()->pressed.remove(key));
        }
        bind("SPACE",false,()->{if(!blocked)sender.accept(new Message(MessageType.INTERACT,(int)x,(int)y));});
        bind("H",false,()->{hideOpponent=!hideOpponent;repaint();});
        addFocusListener(new FocusAdapter(){@Override public void focusLost(FocusEvent e){clearInput();}});
        timer=new Timer(GameConfig.INPUT_TICK_MS,e->tick());
    }
    public void setKitchen(KitchenOverlay value) {kitchen=value;add(value);setComponentZOrder(value,0);}
    public void bind(String key,boolean release,Runnable action) {
        String id=(release?"release-":"press-")+key;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke((release?"released ":"pressed ")+key),id);
        getActionMap().put(id,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});
    }
    private void tick() {
        if(blocked)return;
        int dx=(has("RIGHT","D")?1:0)-(has("LEFT","A")?1:0),dy=(has("DOWN","S")?1:0)-(has("UP","W")?1:0);
        if(dx==0&&dy==0)return;
        double step=GameConfig.PLAYER_SPEED*GameConfig.INPUT_TICK_MS/1000.0/Math.hypot(dx,dy);
        x=StoreLayout.clampX((int)Math.round(x+dx*step));y=StoreLayout.clampY((int)Math.round(y+dy*step));
        if(dx!=0)left=dx<0;sender.accept(new Message(MessageType.MOVE,(int)x,(int)y,left));repaint();
    }
    private boolean has(String a,String b){return pressed.contains(a)||pressed.contains(b);}
    public void setState(StoreSnapshot next){state=next;x=next.chefX();y=next.chefY();left=next.facingLeft();if(kitchen!=null)kitchen.update(next);repaint();}
    public StoreSnapshot state(){return state;}
    public void names(String mine,String other){nickname=mine;otherName=other;repaint();}
    public void opponent(StoreSnapshot next){opponent=next;repaint();}
    public void countdown(int value){countdown=value;setBlocked(value!=0);repaint();}
    public void setBlocked(boolean value){blocked=value;clearInput();}
    public void clearInput(){pressed.clear();}
    public void notice(String text){notice=text;repaint();}
    @Override public void doLayout(){UiKit.bounds(this,exit,1060,64,120,26);if(kitchen!=null)kitchen.setBounds(0,0,getWidth(),getHeight());}
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
        if(opponent!=null&&!hideOpponent) {
            Sprites.text(g,"상대 식당 · "+otherName+"   만족도 "+opponent.satisfaction()+"% · 판매 "+opponent.sales()+" · 골드 "+opponent.gold(),615,82,13,UiKit.CREAM);
            drawStore(g,state,10,194,580,376,(int)x,(int)y,left);
            drawStore(g,opponent,610,194,580,376,opponent.chefX(),opponent.chefY(),opponent.facingLeft());
            g.setColor(new Color(19,12,8));g.fillRect(598,92,4,578);
        } else drawStore(g,state,165,98,870,566,(int)x,(int)y,left);
        Sprites.center(g,"방향키/WASD 이동   SPACE 주문받기 · 서빙 · 치우기 · 부엌   마우스 메뉴를 접시로 끌기   ESC 부엌 닫기"+(opponent==null?"":"   H 상대 식당 숨기기"),600,690,12,UiKit.CREAM);
        if(!notice.isEmpty()){Sprites.rounded(g,360,98,480,26,20,new Color(54,34,18,230),null);Sprites.center(g,notice,600,116,12,Sprites.YELLOW);}
        if(countdown>0){g.setColor(new Color(0,0,0,145));g.fillRect(0,92,1200,578);Sprites.center(g,""+countdown,600,422,135,Sprites.RED);Sprites.center(g,""+countdown,600,416,125,Sprites.YELLOW);Sprites.center(g,"손님이 곧 들어와요! 방향키로 움직이고 스페이스로 일해요",600,478,20,Color.WHITE);}
        g.dispose();
    }
    private static void drawStore(Graphics2D g,StoreSnapshot state,int bx,int by,int w,int h,int x,int y,boolean left){
        Graphics2D room=(Graphics2D)g.create();room.translate(bx,by);room.scale(w/640.0,h/480.0);room.clipRect(0,0,640,480);StoreRenderer.paint(room,state,x,y,left);room.dispose();
    }
}
