package bunsik.client.render;

import bunsik.common.*;
import bunsik.common.Menu;
import java.awt.*;
import java.awt.geom.*;

/** docs/screenshots 참고. 외부 이미지 없이 직접 그리는 임시 그래픽. */
public final class Sprites {
    private Sprites() { }
    public static final Color RED=new Color(217,54,37), INK=new Color(65,39,24), YELLOW=new Color(255,208,62);
    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    public static void text(Graphics2D g,String value,int x,int y,int size,Color color) {
        g.setFont(new Font("맑은 고딕",Font.BOLD,size)); g.setColor(color); g.drawString(value,x,y);
    }
    public static void center(Graphics2D g,String value,int x,int y,int size,Color color) {
        g.setFont(new Font("맑은 고딕",Font.BOLD,size)); g.setColor(color);
        g.drawString(value,x-g.getFontMetrics().stringWidth(value)/2,y);
    }
    public static void rounded(Graphics2D g,int x,int y,int w,int h,int radius,Color fill,Color stroke) {
        g.setColor(fill); g.fillRoundRect(x,y,w,h,radius,radius);
        if(stroke!=null) { g.setColor(stroke); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x,y,w,h,radius,radius); }
    }
    public static void plate(Graphics2D source,Menu menu,int x,int y,int diameter) {
        Graphics2D g=(Graphics2D)source.create(); g.translate(x-diameter/2.0,y-diameter/2.0); g.scale(diameter/100.0,diameter/100.0);
        g.setColor(new Color(0,0,0,35)); g.fillOval(3,8,98,92);
        g.setColor(new Color(83,157,84)); g.fillOval(0,0,98,92);
        g.setColor(new Color(129,198,125)); g.fillOval(4,4,90,84);
        g.setColor(new Color(244,251,238)); g.fillOval(13,12,72,65);
        if(menu!=null) switch(menu) {
            case TTEOKBOKKI -> {
                g.setColor(new Color(217,48,30)); g.fillOval(17,16,64,57);
                for(int i=0;i<9;i++) {
                    double a=i*2.4; int px=43+(int)(Math.cos(a)*21),py=40+(int)(Math.sin(a)*17);
                    Graphics2D food=(Graphics2D)g.create(); food.rotate(a,px+9,py+3);
                    rounded(food,px,py,23,7,6,new Color(255,231,188),new Color(239,163,119)); food.dispose();
                }
                g.setColor(new Color(79,153,73)); g.fillRect(31,29,7,4);g.fillRect(62,55,7,4);
            }
            case GIMBAP -> {
                for(int i=0;i<7;i++) { double a=i*Math.PI/3; int px=i==6?49:49+(int)(Math.cos(a)*23),py=i==6?44:44+(int)(Math.sin(a)*19);
                    g.setColor(new Color(29,58,35));g.fillOval(px-10,py-9,20,18);g.setColor(Color.WHITE);g.fillOval(px-8,py-7,16,14);
                    g.setColor(YELLOW);g.fillOval(px-4,py-4,6,5);g.setColor(RED);g.fillOval(px+2,py,4,4);g.setColor(new Color(61,146,64));g.fillOval(px-5,py+2,4,4);
                }
            }
            case SUNDAE -> { for(int i=0;i<8;i++) { double a=i*2.4;int px=43+(int)(Math.cos(a)*22),py=36+(int)(Math.sin(a)*20);
                g.setColor(new Color(62,28,39));g.fillOval(px-8,py-5,18,13);g.setColor(new Color(137,83,101));g.drawOval(px-4,py-2,6,5); }
            }
            case TWIGIM -> { for(int i=0;i<5;i++) { Graphics2D food=(Graphics2D)g.create();food.rotate(i*.6,50,45);
                rounded(food,25+i*4,22+i*7,32,11,7,new Color(228,162,49),new Color(205,142,31));
                food.setColor(new Color(255,220,126));for(int j=0;j<4;j++)food.fillOval(29+i*4+j*6,25+i*7,2,2);food.dispose(); }
            }
            case EOMUK -> { for(int i=0;i<3;i++) {g.setColor(new Color(145,102,47));g.setStroke(new BasicStroke(2));g.drawLine(27+i*16,70,62+i*12,15);
                for(int j=0;j<3;j++)rounded(g,23+i*16+j*6,49-j*10,18,11,3,new Color(242,207,139),new Color(207,166,95));}
            }
        }
        g.dispose();
    }
    public static void chef(Graphics2D source,int x,int y,boolean left,Menu holding) {
        Graphics2D g=(Graphics2D)source.create();g.translate(x,y);g.scale(.78,.78);
        g.setColor(new Color(0,0,0,45));g.fillOval(-23,22,46,10);
        g.setColor(new Color(55,42,35));g.fillRoundRect(-15,9,10,18,5,5);g.fillRoundRect(6,9,10,18,5,5);
        rounded(g,-25,-27,50,42,20,Color.WHITE,null);
        g.setColor(RED);g.fillRoundRect(-17,-24,34,38,7,7);
        g.setColor(YELLOW);g.fillRoundRect(-9,-7,18,12,4,4);
        g.setColor(new Color(255,210,166));g.fillOval(-23,-71,46,47);g.fillOval(-28,-11,12,12);g.fillOval(17,-11,12,12);
        g.setColor(RED);g.fillArc(-25,-77,50,44,0,180);g.fillOval(left?18:-32,-61,13,10);
        g.setColor(Color.WHITE);for(int i=0;i<5;i++)g.fillOval(-17+i*8,-65-(i%2)*7,4,4);
        g.setColor(INK);g.fillOval(-11,-49,5,6);g.fillOval(7,-49,5,6);g.drawArc(-8,-45,17,14,180,180);
        if(holding!=null)plate(g,holding,0,-12,43);g.dispose();
    }
    public static void customer(Graphics2D g,int x,int y,int patience,int appearance) {
        g.setColor(new Color(0,0,0,40));g.fillOval(x-16,y+19,32,7);
        g.setColor(new Color(60,45,35));g.fillRoundRect(x-10,y+8,7,16,4,4);g.fillRoundRect(x+4,y+8,7,16,4,4);
        g.setColor(Color.getHSBColor((appearance%20)/20f,.55f,.72f));g.fillRoundRect(x-17,y-17,34,32,12,12);
        g.setColor(new Color(255,208,160));g.fillOval(x-13,y-44,27,29);
        g.setColor(new Color(54,38,29));g.fillArc(x-14,y-48,28,24,0,180);
        if(appearance%3==0)g.fillOval(x-5,y-54,12,10);
        g.fillOval(x-7,y-34,3,4);g.fillOval(x+5,y-34,3,4);
        if(patience<0||patience>=600)g.drawArc(x-5,y-32,11,10,180,180);
        else if(patience>=300)g.drawLine(x-4,y-25,x+5,y-25);
        else {g.drawArc(x-5,y-26,11,8,0,180);text(g,"♯",x+10,y-42,14,RED);}
        if(patience>=0) {
            rounded(g,x-20,y-62,40,6,4,new Color(94,72,49),null);
            rounded(g,x-18,y-61,36*patience/1000,4,3,patience<300?RED:patience<600?new Color(226,173,62):new Color(100,183,92),null);
        }
    }
    public static void bubble(Graphics2D g,String value,int x,int y) {
        g.setFont(new Font("맑은 고딕",Font.BOLD,11));int w=g.getFontMetrics().stringWidth(value)+14;
        rounded(g,x,y-23,w,23,6,Color.WHITE,INK);
        g.setColor(Color.WHITE);g.fillPolygon(new int[]{x+6,x+12,x+6},new int[]{y-1,y-1,y+6},3);
        text(g,value,x+7,y-7,11,RED);
    }
    public static void table(Graphics2D g,int x,int y,int number,StoreSnapshot.TableView table) {
        g.setColor(new Color(134,26,19));g.fillRect(x-9,y-38,3,16);g.fillRect(x+5,y-38,3,16);
        g.setColor(RED);g.fillOval(x-13,y-39,26,11);
        if(table.state()!=TableState.EMPTY&&table.state()!=TableState.DIRTY) {
            customer(g,x,y-35,table.patience(),table.appearance());
            if(table.state()==TableState.WAITING_ORDER)bubble(g,"주문!",x+22,y-83);
            if(table.state()==TableState.WAITING_FOOD&&table.menu()!=null) {rounded(g,x+23,y-95,34,29,5,Color.WHITE,INK);plate(g,table.menu(),x+40,y-80,27);}
            if(table.state()==TableState.EATING)text(g,"냠냠",x+14,y-91,10,INK);
        }
        g.setColor(new Color(0,0,0,32));g.fillOval(x-49,y-14,98,46);
        g.setPaint(new GradientPaint(x,y-23,new Color(232,240,242),x,y+23,new Color(162,180,187)));
        g.fillOval(x-46,y-23,92,44);g.setColor(new Color(116,136,144));g.setStroke(new BasicStroke(2));g.drawOval(x-46,y-23,92,44);
        if(table.state()==TableState.EATING)plate(g,table.menu(),x,y-3,32);
        if(table.state()==TableState.DIRTY) {plate(g,null,x-10,y-3,32);text(g,"+"+table.gold(),x+6,y-14,12,new Color(146,93,20));g.setColor(YELLOW);g.fillOval(x+14,y-3,17,10);g.setColor(new Color(176,119,14));g.drawOval(x+14,y-3,17,10);}
        g.setColor(RED);g.fillOval(x+32,y+4,16,16);center(g,""+number,x+40,y+16,11,Color.WHITE);
        g.setColor(new Color(134,26,19));g.fillRect(x-9,y+32,3,16);g.fillRect(x+5,y+32,3,16);
        g.setColor(RED);g.fillOval(x-13,y+29,26,11);
    }
    public static void awning(Graphics2D g,int width,int height) {
        for(int x=0;x<width;x+=142) {g.setColor(RED);g.fillRoundRect(x,-height,71,height*2,60,60);g.setColor(Color.WHITE);g.fillRoundRect(x+71,-height,71,height*2,60,60);}
        g.setColor(new Color(0,0,0,20));g.fillRect(0,0,width,4);
    }
    public static void background(Graphics2D g,int width,int height) {
        g.setColor(new Color(255,249,237));g.fillRect(0,0,width,height);
        g.setColor(new Color(244,231,210));g.setStroke(new BasicStroke(1));
        for(int y=0;y<height;y+=28){g.drawLine(0,y,width,y);for(int x=(y/28%2)*28;x<width;x+=56)g.drawLine(x,y,x,y+28);}
    }
    public static void kitchen(Graphics2D g) {
        rounded(g,491,92,143,380,5,new Color(186,198,203),new Color(122,140,148));
        for(int y=96;y<470;y+=24){g.setColor(new Color(153,169,175));g.drawLine(493,y,632,y);}
        rounded(g,494,96,31,371,5,new Color(211,220,223),new Color(132,150,158));
        for(int i=0;i<3;i++) {
            rounded(g,539,102+i*73,91,56,5,new Color(123,143,151),null);
            g.setColor(i==0?RED:i==1?new Color(172,131,64):new Color(222,176,57));g.fillRoundRect(544,107+i*73,81,44,4,4);
            if(i==0) {for(int j=0;j<8;j++){Graphics2D food=(Graphics2D)g.create();food.rotate(j*.7,581,130);rounded(food,552+j*5,118+(j%3)*8,23,6,5,new Color(255,224,186),null);food.dispose();}}
            else for(int j=0;j<12;j++) {g.setColor(i==1?new Color(238,203,129):new Color(208,143,33));g.fillRoundRect(550+(j%6)*12,112+i*73+(j/6)*20,10,11,3,3);}
        }
        g.setColor(new Color(218,228,230));g.fillOval(555,318,57,57);g.setColor(new Color(125,144,152));g.setStroke(new BasicStroke(2));g.drawOval(555,318,57,57);g.fillOval(578,341,13,7);
        rounded(g,534,389,96,68,5,new Color(202,157,99),null);rounded(g,541,400,50,15,15,new Color(29,47,26),null);
        plate(g,Menu.GIMBAP,557,431,20);plate(g,Menu.GIMBAP,579,431,20);plate(g,Menu.GIMBAP,601,431,20);
        for(int i=0;i<3;i++){g.setColor(new Color(255,255,255,110));g.drawArc(565+i*19,80-i*3,10,24,80,140);}
    }
}
