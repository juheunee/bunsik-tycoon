package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.*;
import bunsik.common.Menu;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.function.Consumer;
import javax.swing.*;

public final class KitchenOverlay extends JComponent {
    private final Consumer<Menu> cook;
    private final Runnable close,discard;
    private StoreSnapshot state=StoreSnapshot.empty();
    private Menu dragging,requested;
    private Point pointer;
    private String warning="";
    private static final Menu[] MENUS={Menu.TTEOKBOKKI,Menu.GIMBAP,Menu.SUNDAE,Menu.EOMUK,Menu.TWIGIM};
    private static final Rectangle[] CARDS={new Rectangle(236,145,136,124),new Rectangle(828,145,136,124),
        new Rectangle(236,494,136,124),new Rectangle(532,494,136,124),new Rectangle(828,494,136,124)};
    public KitchenOverlay(Consumer<Menu> cook,Runnable close,Runnable discard) {
        this.cook=cook;this.close=close;this.discard=discard;setOpaque(false);setVisible(false);
        MouseAdapter mouse=new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Point p=logical(e);
                if(new Rectangle(860,620,110,26).contains(p)){requestClose();return;}
                if(state.cooking()!=null||state.holding()!=null||requested!=null)return;
                for(int i=0;i<CARDS.length;i++)if(CARDS[i].contains(p)){dragging=MENUS[i];pointer=p;break;}repaint();
            }
            @Override public void mouseDragged(MouseEvent e){pointer=logical(e);repaint();}
            @Override public void mouseReleased(MouseEvent e) {
                if(dragging==null)return;Point p=logical(e);Menu choice=dragging;dragging=null;
                if(new Ellipse2D.Double(470,216,260,260).contains(p)&&state.cooking()==null&&state.holding()==null&&requested==null){
                    requested=choice;warning="서버의 조리 시작 응답을 기다립니다.";cook.accept(choice);
                } repaint();
            }
        };
        addMouseListener(mouse);addMouseMotionListener(mouse);
    }
    private Point logical(MouseEvent e){return new Point(e.getX()*1200/Math.max(1,getWidth()),e.getY()*700/Math.max(1,getHeight()));}
    public void open(StoreSnapshot next){state=next;requested=null;warning="";dragging=null;setVisible(true);repaint();}
    public void update(StoreSnapshot next){state=next;if(next.cooking()!=null||next.holding()!=null){requested=null;warning="";}repaint();}
    public void rejected(String text){requested=null;warning=text;repaint();}
    public void requestClose(){
        if(state.cooking()!=null||requested!=null){warning="조리 중에는 부엌을 닫을 수 없습니다.";repaint();return;}
        dragging=null;setVisible(false);close.run();
    }
    public void discard(){if(isVisible()&&state.holding()!=null&&state.cooking()==null){discard.run();}}
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=UiKit.canvas(this,graphics);
        g.setColor(new Color(0,0,0,145));g.fillRect(0,92,1200,578);
        g.setPaint(new GradientPaint(0,118,new Color(244,247,248),0,646,new Color(205,215,220)));
        g.fillRoundRect(208,118,784,528,24,24);g.setColor(new Color(121,141,150));g.setStroke(new BasicStroke(4));g.drawRoundRect(208,118,784,528,24,24);
        for(int y=134;y<635;y+=8){g.setColor(new Color(255,255,255,65));g.drawLine(229,y,972,y);}
        Sprites.rounded(g,437,142,326,37,35,Sprites.RED,null);Sprites.center(g,"부엌 · 메뉴를 접시로 끌어다 놓으세요",600,167,16,Color.WHITE);
        boolean busy=state.cooking()!=null||state.holding()!=null||requested!=null;
        for(int i=0;i<CARDS.length;i++){
            var r=CARDS[i];Menu menu=MENUS[i];boolean enabled=!busy&&dragging!=menu;
            Graphics2D card=(Graphics2D)g.create();if(!enabled)card.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,.48f));
            Sprites.rounded(card,r.x+3,r.y+5,r.width,r.height,10,new Color(0,0,0,35),null);
            Sprites.rounded(card,r.x,r.y,r.width,r.height,10,Color.WHITE,enabled?Sprites.RED:new Color(161,148,127));
            Sprites.plate(card,menu,r.x+r.width/2,r.y+48,65);Sprites.center(card,menu.label,r.x+r.width/2,r.y+99,18,UiKit.INK);
            Sprites.center(card,String.format("%.1f초 · %dG",menu.cookTimeMs/1000.0,menu.price),r.x+r.width/2,r.y+116,12,new Color(131,89,56));card.dispose();
        }
        Menu shown=state.holding()!=null?state.holding():state.cooking();
        Sprites.plate(g,shown,600,351,250);
        if(shown==null)Sprites.center(g,"여기에 놓기",600,351,17,new Color(164,150,130));
        if(state.cooking()!=null){
            double progress=state.cookTotalMs()==0?0:1-state.cookRemainMs()/(double)state.cookTotalMs();
            g.setStroke(new BasicStroke(9,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(179,188,192));
            g.drawOval(465,216,270,270);g.setColor(UiKit.ORANGE);g.draw(new Arc2D.Double(465,216,270,270,90,-360*progress,Arc2D.OPEN));
            Sprites.center(g,String.format("%s 조리 중… %.1f초",state.cooking().label,state.cookRemainMs()/1000.0),600,199,17,UiKit.INK);
        } else if(state.holding()!=null){
            Sprites.center(g,state.holding().label+"를 들고 손님에게 가져다 주세요",600,199,16,UiKit.INK);
            Sprites.rounded(g,485,316,230,60,14,Sprites.YELLOW,Sprites.RED);Sprites.center(g,"요리 완료!",600,358,32,Sprites.RED);
        }
        Sprites.text(g,"ESC 닫기",901,636,12,new Color(131,83,47));
        if(state.holding()!=null)Sprites.center(g,"SPACE · 들고 있는 음식 버리기",600,462,14,new Color(131,83,47));
        if(!warning.isEmpty())Sprites.center(g,warning,600,625,13,Sprites.RED);
        if(dragging!=null&&pointer!=null)Sprites.plate(g,dragging,pointer.x,pointer.y,76);
        g.dispose();
    }
}
