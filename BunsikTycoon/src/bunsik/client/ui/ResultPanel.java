package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.Message;
import bunsik.common.MessageType;
import java.awt.*;
import java.util.List;
import javax.swing.*;

public final class ResultPanel extends JPanel {
    public record Score(String nickname,int satisfaction,int sales,int gold,int customers) { }
    private List<Score> scores=List.of();
    private int winner=-2;
    private final JButton retry,home;
    public ResultPanel(Runnable onRetry,Runnable onHome){
        setLayout(null);retry=UiKit.button("다시 하기",Sprites.RED,onRetry);home=UiKit.button("처음 화면으로",new Color(122,83,56),onHome);add(retry);add(home);
    }
    public void showResult(Message message){
        if(message.type()!=MessageType.GAME_OVER)throw new IllegalArgumentException("Expected GAME_OVER");
        int count=message.getInt(1),win=message.getInt(0);
        if(count<1||count>2||message.size()!=count+2||win < -2||win>=count)throw new IllegalArgumentException("Invalid result");
        var list=new java.util.ArrayList<Score>();
        for(int i=0;i<count;i++){
            String[] fields=message.get(i+2).split(":",-1);if(fields.length!=5)throw new IllegalArgumentException("Invalid score");
            int satisfaction=Integer.parseInt(fields[1]),sales=Integer.parseInt(fields[2]),gold=Integer.parseInt(fields[3]),customers=Integer.parseInt(fields[4]);
            if(satisfaction < -1||satisfaction>100||sales<0||gold<0||customers<0)throw new IllegalArgumentException("Invalid score values");
            list.add(new Score(fields[0],satisfaction,sales,gold,customers));
        }
        scores=List.copyOf(list);winner=win;retry.setText(count==1?"다시 하기":"대기실로");repaint();
    }
    @Override public void doLayout(){UiKit.bounds(this,retry,410,470,180,56);UiKit.bounds(this,home,610,470,180,56);}
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=UiKit.canvas(this,graphics);UiKit.backdrop(g);
        Sprites.center(g,"영업 종료!",600,141,44,Color.WHITE);Sprites.center(g,"영업 종료!",600,137,42,Sprites.RED);
        String caption=winner==-2?"오늘 영업도 수고하셨습니다!":winner==-1?"무승부! 두 사장님 모두 수고하셨어요":"승리! "+scores.get(winner).nickname()+" 사장님의 분식집이 이겼어요";
        Sprites.center(g,caption,600,187,20,UiKit.INK);
        Sprites.rounded(g,285,220,640,172,14,new Color(0,0,0,35),null);
        Sprites.rounded(g,280,214,640,172,14,UiKit.CREAM,new Color(173,159,137));
        g.setColor(Sprites.RED);g.fillRoundRect(282,216,636,42,12,12);g.fillRect(282,237,636,21);
        String[] headers={"사장님","만족도","판매","골드","손님"};int[] xs={406,562,664,760,856};
        for(int i=0;i<headers.length;i++)Sprites.center(g,headers[i],xs[i],243,16,Color.WHITE);
        for(int row=0;row<scores.size();row++){
            if((winner>=0&&winner==row)||scores.size()==1){g.setColor(new Color(255,222,135));g.fillRect(283,258+row*64,634,64);}
            Score score=scores.get(row);String[] cells={score.nickname(),score.satisfaction()<0?"-":score.satisfaction()+"%",""+score.sales(),""+score.gold(),""+score.customers()};
            for(int i=0;i<cells.length;i++)Sprites.center(g,cells[i],xs[i],299+row*64,20,i==1?new Color(181,39,26):UiKit.INK);
        }
        Sprites.center(g,"승리 기준: 만족도가 높은 쪽 → 같으면 판매 수",600,449,13,new Color(144,103,73));g.dispose();
    }
}
