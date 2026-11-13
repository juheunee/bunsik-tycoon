package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;

public final class LobbyPanel extends JPanel {
    private record Player(int id,String name,boolean ready) { }
    private List<Player> players=List.of();
    private int myId;
    private boolean ready;
    private final JButton readyButton,exit;
    public LobbyPanel(java.util.function.Consumer<Boolean> onReady,Runnable leave){
        setLayout(null);readyButton=UiKit.button("준비",Sprites.RED,()->onReady.accept(!ready));exit=UiKit.button("나가기",new Color(122,83,56),leave);add(readyButton);add(exit);
    }
    public void update(Message m,int id){
        var list=new java.util.ArrayList<Player>();
        for(int i=0;i<m.size();i++){
            if(m.get(i).equals("-")||m.get(i).isEmpty())continue;
            String[] p=m.get(i).split(":",-1);
            if(p.length!=3||(!p[2].equals("true")&&!p[2].equals("false")))throw new IllegalArgumentException("Invalid lobby");
            list.add(new Player(Integer.parseInt(p[0]),p[1],Boolean.parseBoolean(p[2])));
        }
        if(list.size()>2)throw new IllegalArgumentException("Lobby full");
        players=List.copyOf(list);myId=id;ready=list.stream().anyMatch(p->p.id()==id&&p.ready());readyButton.setText(ready?"준비 취소":"준비");repaint();
    }
    @Override public void doLayout(){UiKit.bounds(this,readyButton,410,480,220,56);UiKit.bounds(this,exit,646,480,144,56);}
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=UiKit.canvas(this,graphics);UiKit.backdrop(g);
        Sprites.center(g,"경쟁 모드 대기실",600,132,38,Sprites.RED);
        Sprites.center(g,"두 사람 모두 '준비'를 누르면 게임이 시작돼요.",600,165,16,new Color(131,83,47));
        for(int i=0;i<2;i++){
            int x=i==0?254:646;Player p=i<players.size()?players.get(i):null;
            Sprites.rounded(g,x+5,195,300,250,16,new Color(0,0,0,35),null);
            Sprites.rounded(g,x,190,300,250,16,UiKit.CREAM,p!=null&&p.id()==myId?Sprites.RED:new Color(173,159,137));
            Sprites.text(g,(i+1)+"P"+(p!=null&&p.id()==myId?" (나)":""),x+18,218,13,new Color(131,83,47));
            Graphics2D chef=(Graphics2D)g.create();chef.translate(x+150,322);chef.scale(1.65,1.65);Sprites.chef(chef,0,0,false,null);chef.dispose();
            Sprites.center(g,p==null?"입장 대기 중":p.name(),x+150,380,23,UiKit.INK);
            Sprites.rounded(g,x+90,392,120,32,30,p!=null&&p.ready()?new Color(74,155,39):new Color(202,191,172),null);
            Sprites.center(g,p!=null&&p.ready()?"준비 완료":"준비 중",x+150,414,15,Color.WHITE);
        }
        g.setColor(Sprites.YELLOW);g.fillOval(568,284,64,64);g.setColor(new Color(177,39,26));g.setStroke(new BasicStroke(5));g.drawOval(568,284,64,64);Sprites.center(g,"VS",600,327,27,new Color(177,39,26));g.dispose();
    }
}
