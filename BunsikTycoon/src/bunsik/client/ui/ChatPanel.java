package bunsik.client.ui;

import bunsik.common.ChatMessages;
import bunsik.common.Message;
import java.awt.BorderLayout;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.*;

/** One conversation shared by the versus lobby and game on the same connection. */
public final class ChatPanel extends JPanel {
    private final JTextArea history = new JTextArea();
    private final JTextField input = new JTextField();
    private final Consumer<Message> sender;
    private final Runnable afterGameInput;
    private final Runnable beforeGameInput;
    private boolean editing, gameMode, enterArmed, composing;

    public ChatPanel(Consumer<Message> sender, Runnable beforeGameInput, Runnable afterGameInput) {
        this.sender = sender;
        this.beforeGameInput = beforeGameInput;
        this.afterGameInput = afterGameInput;
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("채팅 · Enter"));
        history.setEditable(false);
        history.setLineWrap(true);
        history.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(history);
        add(scroll, BorderLayout.CENTER);
        add(input, BorderLayout.SOUTH);
        input.addActionListener(e -> submit());
        input.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (gameMode && !editing) {
                    beforeGameInput.run();
                    editing = true;
                    enterArmed = true;
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (gameMode && editing) editing = false;
            }
        });
        input.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) enterArmed = true;
            }
        });
        input.addInputMethodListener(new InputMethodListener() {
            @Override public void inputMethodTextChanged(InputMethodEvent e) {
                composing = e.getText() != null && e.getCommittedCharacterCount() < e.getText().getEndIndex();
            }
            @Override public void caretPositionChanged(InputMethodEvent e) { }
        });
        input.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "chat-cancel");
        input.getActionMap().put("chat-cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { cancel(); }
        });
    }

    public void setGameMode(boolean value) {
        gameMode = value;
        editing = !value;
        enterArmed = !value;
        input.setText("");
        input.setEnabled(true);
    }

    public boolean isEditing() { return editing; }
    public JTextField input() { return input; }
    public String history() { return history.getText(); }

    public void beginGameInput() {
        if (editing || !gameMode) return;
        editing = true;
        enterArmed = false; // The key that opened chat must be released before it can send.
        input.requestFocusInWindow();
    }

    public void enterReleased() { enterArmed = true; }

    private void submit() {
        if (!editing || composing || (gameMode && !enterArmed)) return;
        try {
            Message request = ChatMessages.request(input.getText());
            sender.accept(request);
            input.setText("");
            if (gameMode) finish();
        } catch (IllegalArgumentException ignored) {
            // Keep invalid text available for editing. The protocol rejects blank/overlong text.
        }
    }

    public void cancel() {
        input.setText("");
        if (gameMode && editing) finish();
    }

    private void finish() {
        editing = false;
        enterArmed = false;
        afterGameInput.run();
    }

    public void append(Message message) {
        JScrollBar bar = ((JScrollPane) history.getParent().getParent()).getVerticalScrollBar();
        boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 8;
        history.append(ChatMessages.display(message) + "\n");
        if (atBottom) SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
    }

    public void clear() {
        history.setText("");
        input.setText("");
        editing = !gameMode;
    }
}
