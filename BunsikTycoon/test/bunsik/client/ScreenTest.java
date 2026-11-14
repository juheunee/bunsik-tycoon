package bunsik.client;

import bunsik.client.ui.*;
import bunsik.common.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** EDT 오프스크린 렌더링, 메뉴 드롭과 조리 잠금·결과 검증. */
public final class ScreenTest {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try {
                Files.createDirectories(Path.of("out/screens"));
                var sent=new ArrayList<Menu>();var closed=new int[1];
                GamePanel game=new GamePanel(m->{},()->{});
                var tables=List.of(new StoreSnapshot.TableView(TableState.WAITING_FOOD,Menu.TTEOKBOKKI,520,0,3),
                        new StoreSnapshot.TableView(TableState.EATING,Menu.GIMBAP,-1,0,8),
                        new StoreSnapshot.TableView(TableState.DIRTY,null,-1,41,0),
                        new StoreSnapshot.TableView(TableState.WAITING_ORDER,null,820,0,13));
                StoreSnapshot state=new StoreSnapshot(0,128000,214,7,6,84,238,253,false,Menu.TTEOKBOKKI,null,0,0,tables,
                        List.of(new StoreSnapshot.CustomerView(900,5),new StoreSnapshot.CustomerView(250,16)));
                game.setState(state);game.names("주흔","차민");game.notice("");render(game,"game");
                render(new TitlePanel(c->{},()->{}),"title");
                var kitchen=new KitchenOverlay(sent::add,()->closed[0]++,()->{});game.setKitchen(kitchen);game.doLayout();
                kitchen.open(StoreSnapshot.empty());render(game,"kitchen");
                mouse(kitchen,MouseEvent.MOUSE_PRESSED,300,190);mouse(kitchen,MouseEvent.MOUSE_DRAGGED,600,350);mouse(kitchen,MouseEvent.MOUSE_RELEASED,600,350);
                check(sent.equals(List.of(Menu.TTEOKBOKKI)),"drop must send COOK once");
                mouse(kitchen,MouseEvent.MOUSE_PRESSED,300,190);mouse(kitchen,MouseEvent.MOUSE_RELEASED,600,350);
                check(sent.size()==1,"pending cook must lock duplicate drop");
                kitchen.requestClose();check(kitchen.isVisible(),"pending cook must lock ESC");
                kitchen.rejected("다시 시도하세요");kitchen.requestClose();check(!kitchen.isVisible()&&closed[0]==1,"rejected cook must unlock");
                var cooking=new StoreSnapshot(0,128000,214,7,6,84,238,253,false,null,Menu.TTEOKBOKKI,1600,4000,tables,List.of());
                kitchen.open(cooking);render(game,"cooking");kitchen.requestClose();check(kitchen.isVisible(),"cooking must lock ESC");
                kitchen.update(state);render(game,"done");kitchen.requestClose();check(!kitchen.isVisible(),"completed cook must allow ESC");
                game.countdown(3);render(game,"countdown");game.countdown(0);game.opponent(state);render(game,"versus");
                var result=new ResultPanel(()->{},()->{});result.showResult(new Message(MessageType.GAME_OVER,-2,1,"주흔:84:21:742:25"));render(result,"result");
                try {result.showResult(new Message(MessageType.GAME_OVER,2,1,"주흔:84:21:742:25"));throw new AssertionError("invalid winner");}catch(IllegalArgumentException expected){}
                var lobby=new LobbyPanel(b->{},()->{});lobby.update(new Message(MessageType.LOBBY,"1:주흔:true","2:차민:false"),1);render(lobby,"lobby");
                System.out.println("ScreenTest: PASS (9 renders, drag/drop, pending/cooking lock, result validation)");
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static void mouse(JComponent c,int id,int x,int y){c.dispatchEvent(new MouseEvent(c,id,System.currentTimeMillis(),0,x,y,1,false));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void render(JComponent c,String name)throws Exception{
        c.setSize(1200,700);c.doLayout();BufferedImage image=new BufferedImage(1200,700,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();c.printAll(g);g.dispose();ImageIO.write(image,"png",Path.of("out/screens/"+name+".png").toFile());
    }
}
