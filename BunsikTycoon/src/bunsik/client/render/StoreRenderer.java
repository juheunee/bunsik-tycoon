package bunsik.client.render;

import bunsik.common.*;
import bunsik.common.Menu;
import java.awt.*;

public final class StoreRenderer {
    private StoreRenderer() { }
    public static void paint(Graphics2D g,StoreSnapshot state,int x,int y,boolean left) {
        Sprites.quality(g);
        g.setColor(new Color(56,37,25));g.fillRect(0,0,640,480);
        for(int ty=0;ty<480;ty+=36)for(int tx=0;tx<640;tx+=36){g.setColor((ty/36+tx/36)%2==0?new Color(255,246,222):new Color(244,221,176));g.fillRect(tx,ty,36,36);g.setColor(new Color(209,186,149));g.drawRect(tx,ty,36,36);}
        g.setColor(new Color(224,218,202));g.fillRect(12,92,92,380);
        for(int i=0;i<160;i++){int px=18+(i*37)%81,py=101+(i*53)%362;g.setColor(i%2==0?new Color(249,243,225):new Color(200,188,164));g.fillOval(px,py,2,2);}
        g.setColor(new Color(174,150,111));g.fillRect(100,92,3,380);
        Sprites.center(g,"줄 서는 곳",57,121,10,new Color(125,90,54));
        for(var p:StoreLayout.QUEUE)Sprites.rounded(g,p.x()-16,p.y()+25,32,8,7,new Color(255,207,70),new Color(204,160,49));
        Sprites.rounded(g,22,438,70,25,4,new Color(177,40,21),null);Sprites.center(g,"어서오세요",57,455,10,Color.WHITE);
        Sprites.kitchen(g);
        for(int i=0;i<state.tables().size();i++){var p=StoreLayout.TABLES.get(i);Sprites.table(g,p.x(),p.y(),i+1,state.tables().get(i));}
        for(int i=0;i<state.queue().size();i++){var p=StoreLayout.QUEUE.get(i);var c=state.queue().get(i);Sprites.customer(g,p.x(),p.y(),c.patience(),c.appearance());}
        Sprites.chef(g,x,y,left,state.holding());
        if(Math.abs(x-StoreLayout.KITCHEN.x())<85&&Math.abs(y-StoreLayout.KITCHEN.y())<150) {
            Sprites.rounded(g,x-45,y-81,90,19,14,new Color(74,51,27),null);Sprites.center(g,"SPACE 부엌 열기",x,y-67,10,Sprites.YELLOW);
        } else for(var p:StoreLayout.TABLES)if(Math.hypot(x-p.x(),y-p.y())<70){Sprites.rounded(g,x-45,y-81,90,19,14,new Color(74,51,27),null);Sprites.center(g,"SPACE 상호작용",x,y-67,10,Sprites.YELLOW);break;}
        Sprites.background(g,640,85);
        Sprites.rounded(g,8,7,89,32,4,Sprites.RED,new Color(161,39,26));Sprites.center(g,"분식왕",53,30,20,Sprites.YELLOW);
        Graphics2D awning=(Graphics2D)g.create();awning.translate(0,40);awning.clipRect(0,0,104,29);awning.scale(.33,.43);Sprites.awning(awning,320,60);awning.dispose();
        Sprites.rounded(g,129,8,347,54,5,new Color(255,220,77),new Color(137,91,40));
        Sprites.rounded(g,135,14,40,40,4,Sprites.RED,null);Sprites.center(g,"메뉴",155,41,15,Color.WHITE);
        int i=0;for(Menu menu:Menu.values()){int mx=207+i*52;Sprites.plate(g,menu,mx,26,23);Sprites.center(g,menu.label,mx,47,8,Sprites.INK);Sprites.center(g,menu.price+"G",mx,57,9,new Color(152,57,26));i++;}
        g.setPaint(new GradientPaint(0,5,Color.WHITE,0,65,new Color(162,178,185)));g.fillPolygon(new int[]{504,630,636,497},new int[]{6,6,60,60},4);g.setColor(new Color(121,140,149));g.drawPolygon(new int[]{504,630,636,497},new int[]{6,6,60,60},4);
        Sprites.rounded(g,540,21,56,22,3,Sprites.RED,null);Sprites.center(g,"주 방",568,37,13,Color.WHITE);
        g.setColor(Sprites.RED);g.fillRect(0,68,640,10);g.setColor(new Color(255,208,60));g.fillRect(0,65,640,3);
        g.setColor(new Color(125,137,139));g.setStroke(new BasicStroke(4));g.drawRect(1,1,637,477);
    }
}
