package bunsik.client.ui;

import bunsik.common.Message;
import bunsik.common.MessageType;
import java.awt.event.ActionEvent;
import javax.swing.JButton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

/** Swing action boundaries; real keyboard and Korean IME still need manual checks. */
public final class GameChatInputTest {
    public static void main(String[] args) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            List<Message> sent = new ArrayList<>();
            GamePanel game = new GamePanel(sent::add, () -> { });
            ChatPanel chat = new ChatPanel(sent::add, game::clearInput, () -> { });
            game.setVersus(true);
            game.setBlocked(false);
            game.setChatPanel(chat);
            chat.setGameMode(true);
            action(game, "press-W");
            game.openChat();
            check(chat.isEditing(), "Enter opens editor");
            action(game, "press-W");
            action(game, "press-SPACE");
            action(game, "press-H");
            action(game, "press-ESCAPE");
            check(sent.isEmpty(), "game actions suppressed while editing");
            check(game.getComponents().length == 4, "chat stays attached to game");
            chat.input().setText("  안녕|친구  ");
            chat.input().postActionEvent();
            check(sent.isEmpty(), "opening Enter cannot immediately send");
            chat.enterReleased();
            chat.input().postActionEvent();
            check(sent.size() == 1 && sent.getFirst().type() == MessageType.CHAT
                    && sent.getFirst().get(0).equals("안녕 친구"), "one normalized send");
            check(!chat.isEditing(), "game input resumes");
            game.openChat();
            chat.enterReleased();
            chat.input().setText("   ");
            chat.input().postActionEvent();
            check(sent.size() == 1 && chat.isEditing(), "blank message stays in editor");
            chat.cancel();
            check(!chat.isEditing() && sent.size() == 1, "Escape cancels without send");
            chat.append(new Message(MessageType.CHAT, 1, "Alpha", "기록 유지"));
            JButton toggle = null;
            for (var child : game.getComponents()) {
                if (child instanceof JButton button && button.getText().startsWith("채팅 접기")) toggle = button;
            }
            check(toggle != null, "game has collapse control");
            toggle.doClick();
            check(!chat.isVisible() && chat.history().contains("기록 유지"), "collapse keeps history");
            game.openChat();
            check(chat.isVisible() && chat.isEditing(), "Enter reopens collapsed chat");
            System.out.println("GameChatInputTest: PASS (open, guard, send, blank, cancel)");
            return null;
        });
        SwingUtilities.invokeAndWait(task);
        task.get();
    }

    private static void action(GamePanel game, String id) {
        game.getActionMap().get(id).actionPerformed(new ActionEvent(game, 0, id));
    }
    private static void check(boolean valid, String reason) {
        if (!valid) throw new AssertionError(reason);
    }
}
