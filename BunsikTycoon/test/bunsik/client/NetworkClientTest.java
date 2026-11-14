package bunsik.client;

import bunsik.common.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** 실제 소켓으로 새 클라이언트의 UTF-8·명령 전송·EDT 전달·EOF를 검증한다. */
public final class NetworkClientTest {
    public static void main(String[] args)throws Exception {
        try(ServerSocket server=new ServerSocket(0)){
            var received=new LinkedBlockingQueue<Message>();var callbacks=new LinkedBlockingQueue<Message>();
            CountDownLatch disconnected=new CountDownLatch(1);
            CompletableFuture<Void> peer=CompletableFuture.runAsync(()->{
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(5000);
                    var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    var out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
                    received.add(Message.parse(in.readLine()));
                    for(Message m:new Message[]{new Message(MessageType.WELCOME,1),new Message(MessageType.START,0,1,"주흔",""),
                        new Message(MessageType.COUNTDOWN,0),StoreSnapshot.empty().toMessage(),new Message(MessageType.OPEN_KITCHEN)}){
                        out.write(m.encode());out.newLine();out.flush();
                    }
                    for(int i=0;i<3;i++)received.add(Message.parse(in.readLine()));
                    out.write(new Message(MessageType.GAME_OVER,-2,1,"주흔:84:21:742:25").encode());out.newLine();out.flush();
                }catch(Exception e){throw new CompletionException(e);}
            });
            try(NetworkClient client=new NetworkClient(m->{
                if(!SwingUtilities.isEventDispatchThread())throw new AssertionError("receiver outside EDT");callbacks.add(m);
            },reason->{if(!SwingUtilities.isEventDispatchThread())throw new AssertionError("disconnect outside EDT");disconnected.countDown();})){
                client.connect("127.0.0.1",server.getLocalPort(),3000,"주흔","SOLO");
                check(take(received).encode().equals("HELLO|주흔|SOLO"));
                MessageType[] order={MessageType.WELCOME,MessageType.START,MessageType.COUNTDOWN,MessageType.STATE,MessageType.OPEN_KITCHEN};
                for(MessageType type:order)check(take(callbacks).type()==type);
                client.send(new Message(MessageType.MOVE,230,210,true));
                client.send(new Message(MessageType.INTERACT,230,210));
                client.send(new Message(MessageType.COOK,Menu.GIMBAP));
                check(take(received).encode().equals("MOVE|230|210|true"));
                check(take(received).encode().equals("INTERACT|230|210"));
                check(take(received).encode().equals("COOK|GIMBAP"));
                check(take(callbacks).type()==MessageType.GAME_OVER);
                check(disconnected.await(5,TimeUnit.SECONDS));peer.get(5,TimeUnit.SECONDS);
            }
        }
        System.out.println("NetworkClientTest: PASS (UTF-8, command order, EDT, EOF)");
    }
    private static Message take(BlockingQueue<Message> queue)throws Exception{
        Message m=queue.poll(5,TimeUnit.SECONDS);if(m==null)throw new AssertionError("timed out");return m;
    }
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
}
