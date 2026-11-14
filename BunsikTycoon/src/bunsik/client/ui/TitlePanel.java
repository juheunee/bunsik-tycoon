package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.AppConfig;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;

public final class TitlePanel extends JPanel {
    public record Connection(String nickname,String host,int port,String mode) { }
    private final JTextField nickname=new JTextField("주흔"),host=new JTextField("127.0.0.1"),port=new JTextField("9999");
    private final JButton solo,versus,demo;
    private String status="";
    public TitlePanel(Consumer<Connection> onStart,Runnable preview) {
        setLayout(null);UiKit.style(this);
        try { var config=AppConfig.load();host.setText(config.serverHost());port.setText(""+config.serverPort()); }
        catch(Exception e) {status="설정 확인 필요: "+e.getMessage();}
        for(JTextField field:new JTextField[]{nickname,host,port}) {UiKit.style(field);field.setBackground(Color.WHITE);field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(173,159,137),2),BorderFactory.createEmptyBorder(3,10,3,10)));add(field);}
        solo=UiKit.button("혼자 하기 (1P)",UiKit.ORANGE,()->start("SOLO",onStart));
        versus=UiKit.button("경쟁 하기 (2P)",Sprites.RED,()->start("VERSUS",onStart));
        demo=UiKit.button("화면 미리보기",new Color(122,83,56),preview);
        add(solo);add(versus);add(demo);
    }
    private void start(String mode,Consumer<Connection> callback) {
        String name=nickname.getText().trim();
        try {
            int value=Integer.parseInt(port.getText().trim());
            if(name.isEmpty()||name.length()>20||name.matches(".*[|:,\\r\\n].*")||host.getText().isBlank()||value<1||value>65535)throw new IllegalArgumentException();
            solo.setEnabled(false);versus.setEnabled(false);status="서버 연결 중…";repaint();
            callback.accept(new Connection(name,host.getText().trim(),value,mode));
        } catch(IllegalArgumentException e) {status="닉네임 1~20자, 서버 IP와 포트(1~65535)를 확인하세요.";repaint();}
    }
    public void reset(String text) {solo.setEnabled(true);versus.setEnabled(true);status=text;repaint();}
    @Override public void doLayout() {
        UiKit.bounds(this,nickname,490,301,300,39);UiKit.bounds(this,host,490,353,300,39);UiKit.bounds(this,port,490,405,300,39);
        UiKit.bounds(this,solo,410,468,181,54);UiKit.bounds(this,versus,608,468,181,54);UiKit.bounds(this,demo,500,536,200,34);
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);Graphics2D g=UiKit.canvas(this,graphics);UiKit.backdrop(g);
        Sprites.rounded(g,248,100,716,128,16,new Color(0,0,0,45),null);
        Sprites.rounded(g,244,96,712,120,16,Sprites.YELLOW,new Color(168,41,27));
        Sprites.center(g,"도전! 분식왕",600,169,62,Color.WHITE);Sprites.center(g,"도전! 분식왕",600,165,60,Sprites.RED);
        Sprites.center(g,"주문 받고 · 요리하고 · 서빙하고 · 치우고! 오늘 영업도 힘내 볼까요?",600,202,16,UiKit.INK);
        Sprites.rounded(g,375,276,460,300,15,new Color(0,0,0,32),null);
        Sprites.rounded(g,370,270,460,300,15,UiKit.CREAM,new Color(173,159,137));
        Sprites.text(g,"닉네임",410,327,16,UiKit.INK);Sprites.text(g,"서버 IP",410,379,16,UiKit.INK);Sprites.text(g,"포트",410,431,16,UiKit.INK);
        Sprites.center(g,status,600,593,13,new Color(140,78,49));g.dispose();
    }
}
