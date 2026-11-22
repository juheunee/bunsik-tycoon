package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

public final class LobbyPanel extends JPanel {
    private record Player(int id,String name,boolean ready) { }
    private List<Player> players=List.of();
    private int myId = -1;
    private Boolean requestedReady;
    private final JButton readyButton,exit;
    public LobbyPanel(Consumer<Boolean> onReady,Runnable leave){
        setLayout(null);
        readyButton=UiKit.button("준비",Sprites.RED,()-> {
            requestedReady = !localPlayer().ready();
            refresh();
            onReady.accept(requestedReady);
        });
        exit=UiKit.button("나가기",new Color(122,83,56),leave);add(readyButton);add(exit);
        refresh();
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
        players=List.copyOf(list);
        myId=id;
        Player local = localPlayer();
        if (local == null || (requestedReady != null && requestedReady == local.ready())) {
            requestedReady = null;
        }
        refresh();
    }
    public void reset() {
        players = List.of();
        myId = -1;
        requestedReady = null;
        refresh();
    }
    public boolean hasLocalPlayer() { return localPlayer() != null; }
    public boolean isAwaitingReady() { return requestedReady != null; }
    private Player localPlayer() {
        return players.stream().filter(player -> player.id() == myId).findFirst().orElse(null);
    }
    private boolean allReady() {
        return players.size() == 2 && players.stream().allMatch(Player::ready);
    }
    private void refresh() {
        Player local = localPlayer();
        readyButton.setText(local != null && local.ready() ? "준비 취소" : "준비");
        readyButton.setEnabled(local != null && requestedReady == null && !allReady());
        repaint();
    }
    @Override public void doLayout(){UiKit.bounds(this,readyButton,410,480,220,56);UiKit.bounds(this,exit,646,480,144,56);}
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=UiKit.canvas(this,graphics);UiKit.backdrop(g);
        Sprites.center(g,"경쟁 모드 대기실",600,132,38,Sprites.RED);
        String instruction = players.size() < 2 ? "상대 참가자를 기다리는 중"
                : allReady() ? "서버의 시작 신호를 기다리는 중" : "두 사람이 준비하면 서버가 게임을 시작해요.";
        if (requestedReady != null) instruction = "서버의 준비 상태를 기다리는 중";
        Sprites.center(g,players.size()+"/2명 · "+instruction,600,165,16,new Color(131,83,47));
        for(int i=0;i<2;i++){
            int x=i==0?254:646;Player p=i<players.size()?players.get(i):null;
            Sprites.rounded(g,x+5,195,300,250,16,new Color(0,0,0,35),null);
            Sprites.rounded(g,x,190,300,250,16,UiKit.CREAM,p!=null&&p.id()==myId?Sprites.RED:new Color(173,159,137));
            if (p != null) Sprites.text(g,p.id()==myId?"나":"상대",x+18,218,13,new Color(131,83,47));
            Graphics2D chef=(Graphics2D)g.create();chef.translate(x+150,322);chef.scale(1.65,1.65);Sprites.chef(chef,0,0,false,null);chef.dispose();
            Sprites.center(g,p==null?"입장 대기 중":p.name(),x+150,380,23,UiKit.INK);
            Sprites.rounded(g,x+90,392,120,32,30,p!=null&&p.ready()?new Color(74,155,39):new Color(202,191,172),null);
            Sprites.center(g,p==null?"빈 자리":p.ready()?"준비 완료":"준비 중",x+150,414,15,Color.WHITE);
        }
        g.setColor(Sprites.YELLOW);g.fillOval(568,284,64,64);g.setColor(new Color(177,39,26));g.setStroke(new BasicStroke(5));g.drawOval(568,284,64,64);Sprites.center(g,"VS",600,327,27,new Color(177,39,26));g.dispose();
    }
}
