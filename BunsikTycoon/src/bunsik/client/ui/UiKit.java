package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.Menu;
import java.awt.*;
import javax.swing.*;

public final class UiKit {
    private UiKit() { }
    public static final Color CREAM=new Color(255,246,228), INK=Sprites.INK, ORANGE=new Color(249,140,26);
    public static void style(JComponent c) { c.setFont(new Font("맑은 고딕",Font.PLAIN,15));c.setForeground(INK);c.setBackground(CREAM); }
    public static Graphics2D canvas(JComponent c,Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();Sprites.quality(g);g.scale(c.getWidth()/1200.0,c.getHeight()/700.0);return g;
    }
    public static void backdrop(Graphics2D g) {
        Sprites.background(g,1200,700);Sprites.awning(g,1200,74);
        g.setColor(new Color(226,232,234));g.fillRect(0,608,1200,26);
        g.setColor(new Color(169,184,192));g.fillRect(0,634,1200,66);
        int i=0;for(Menu menu:Menu.values())Sprites.plate(g,menu,200+(i++)*200,619,82);
    }
    public static JButton button(String text,Color color,Runnable action) {
        JButton b=new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create();Sprites.quality(g);
                Sprites.rounded(g,1,5,getWidth()-3,getHeight()-7,40,color.darker(),null);
                Sprites.rounded(g,1,1,getWidth()-3,getHeight()-8,40,isEnabled()?(getModel().isRollover()?color.brighter():color):new Color(180,166,143),color.darker());
                Sprites.center(g,getText(),getWidth()/2,getHeight()/2+5,Math.max(12,getHeight()/3),Color.WHITE);g.dispose();
            }
        };
        b.setContentAreaFilled(false);b.setBorderPainted(false);b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.addActionListener(e->action.run());return b;
    }
    public static void bounds(JComponent parent,JComponent child,int x,int y,int w,int h) {
        child.setBounds(x*parent.getWidth()/1200,y*parent.getHeight()/700,w*parent.getWidth()/1200,h*parent.getHeight()/700);
    }
}
