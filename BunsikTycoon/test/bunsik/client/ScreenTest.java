package bunsik.client;

import bunsik.client.ui.*;
import bunsik.common.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** EDT 오프스크린 렌더링, 키보드 상호작용과 조리 잠금·결과 검증. */
public final class ScreenTest {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try {
                Files.createDirectories(Path.of("out/screens"));
                var actions=new ArrayList<Message>();
                GamePanel game=new GamePanel(actions::add,()->{});
                check(bunsik.client.render.Sprites.image("/missing-test-image.png").isEmpty(),"missing image must allow fallback");
                var tables=List.of(new StoreSnapshot.TableView(TableState.WAITING_FOOD,Menu.TTEOKBOKKI,520,0,3),
                        new StoreSnapshot.TableView(TableState.EATING,Menu.GIMBAP,-1,0,8),
                        new StoreSnapshot.TableView(TableState.DIRTY,null,-1,41,0),
                        new StoreSnapshot.TableView(TableState.WAITING_ORDER,null,820,0,13));
                StoreSnapshot state=new StoreSnapshot(0,128000,214,7,6,84,238,253,false,Menu.TTEOKBOKKI,null,0,0,tables,
                        List.of(new StoreSnapshot.CustomerView(900,5),new StoreSnapshot.CustomerView(250,16)));
                game.setState(state);game.names("주흔","차민");game.notice("");render(game,"game");
                BufferedImage populated=ImageIO.read(Path.of("out/screens/game.png").toFile());
                game.setState(StoreSnapshot.empty());render(game,"empty");
                BufferedImage empty=ImageIO.read(Path.of("out/screens/empty.png").toFile());
                boolean changed=false;
                for(int y=0;y<700&&!changed;y++)for(int x=0;x<1200;x++)if(populated.getRGB(x,y)!=empty.getRGB(x,y)){changed=true;break;}
                check(changed,"different snapshots must change rendering");
                game.setState(state);
                render(new TitlePanel(c->{},()->{}),"title");
                var station=StoreLayout.COOK_STATIONS.getFirst();
                var second=StoreLayout.COOK_STATIONS.get(1);
                var idle=at(state,station.position(),null,null);
                game.setState(idle);
                space(game);
                check(actions.isEmpty(),"blocked game cannot cook");
                game.setBlocked(false);
                game.setState(at(state,new StoreLayout.Position(480,120),null,null));
                space(game);
                check(actions.isEmpty(),"out of reach must send nothing");
                game.setState(idle);
                render(game,"kitchen");
                press(game,"press-SPACE");
                press(game,"press-SPACE");
                press(game,"release-SPACE");
                space(game);
                check(actions.size()==2 && actions.getFirst().type()==MessageType.MOVE
                        && actions.getLast().encode().equals("COOK|"+station.menu()),"one positioned cook");
                game.setState(idle);
                space(game);
                check(actions.size()==2,"unchanged snapshot preserves the lock");
                game.serverNotice("서버가 조리 요청을 거절했습니다.");
                game.setState(idle);
                space(game);
                check(actions.size()==4,"feedback plus state permits a retry");
                game.setState(at(state,second.position(),null,station.menu()));
                render(game,"cooking");
                space(game);
                check(actions.size()==4,"B3 single cook blocks the other station");
                game.setState(at(state,second.position(),station.menu(),null));
                render(game,"done");
                space(game);
                check(actions.size()==4,"B3-held food blocks cook and station cannot discard");

                game.setState(at(state,StoreLayout.DISCARD,station.menu(),null));
                space(game);
                space(game);
                check(actions.size()==5 && actions.getLast().type()==MessageType.INTERACT,"one discard");
                game.setState(at(state,StoreLayout.DISCARD,null,null));
                space(game);
                check(actions.size()==5,"cannot discard with empty hands");
                game.setState(at(state,second.position(),null,null));
                space(game);
                check(actions.size()==7 && actions.getLast().encode().equals("COOK|"+second.menu()),"next station after discard");
                game.setState(at(state,StoreLayout.TABLES.getFirst(),second.menu(),null));
                space(game);
                space(game);
                check(actions.size()==8 && actions.getLast().type()==MessageType.INTERACT,"serve mismatch goes to server once");
                game.serverNotice("주문 메뉴가 달라요");
                game.setState(at(state,StoreLayout.TABLES.getFirst(),second.menu(),null));
                check(game.state().holding()==second.menu(),"rejection preserves held food");
                game.setBlocked(true);
                space(game);
                check(actions.size()==8,"ending blocks interactions");
                game.countdown(3);render(game,"countdown");game.countdown(0);game.opponent(state);render(game,"versus");
                var result=new ResultPanel(()->{},()->{});result.showResult(new Message(MessageType.GAME_OVER,-2,1,"주흔:84:21:742:25"));render(result,"result");
                try {result.showResult(new Message(MessageType.GAME_OVER,2,1,"주흔:84:21:742:25"));throw new AssertionError("invalid winner");}catch(IllegalArgumentException expected){}
                var lobby=new LobbyPanel(b->{},()->{});lobby.update(new Message(MessageType.LOBBY,"1:주흔:true","2:차민:false"),1);render(lobby,"lobby");
                System.out.println("ScreenTest: PASS (10 renders, keyboard targets, cook/serve/discard locks, result validation)");
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static void press(GamePanel game,String action) {
        game.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(game,0,action));
    }
    private static void space(GamePanel game) {
        press(game,"press-SPACE");
        press(game,"release-SPACE");
    }
    private static StoreSnapshot at(StoreSnapshot state,StoreLayout.Position point,Menu holding,Menu cooking) {
        return new StoreSnapshot(0,state.timeLeftMs(),state.gold(),state.servedCustomers(),state.sales(),
                state.satisfaction(),point.x(),point.y(),false,holding,cooking,
                cooking==null?0:1600,cooking==null?0:cooking.cookTimeMs,state.tables(),state.queue());
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void render(JComponent c,String name)throws Exception{
        c.setSize(1200,700);c.doLayout();BufferedImage image=new BufferedImage(1200,700,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();c.printAll(g);g.dispose();ImageIO.write(image,"png",Path.of("out/screens/"+name+".png").toFile());
    }
}
