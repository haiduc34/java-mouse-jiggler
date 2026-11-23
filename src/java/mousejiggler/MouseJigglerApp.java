package mousejiggler;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Single-window Swing user interface for the mouse jiggler.
 */
public final class MouseJigglerApp {

    private static final Color BACKGROUND = new Color(0xd4d0c8);
    private static final Color TITLE_BAR = new Color(0xbdb9b2);
    private static final Color GREEN = new Color(0x2e8b57);
    private static final Color RED = new Color(0xc43c35);
    private static final Color DARK_GRAY = new Color(0x404040);
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("[+-]?(?:[0-9]*(?:[.,][0-9]*)?)?");
    private static final int MAX_INPUT_LENGTH = 6;

    private final JFrame frame;
    private final AtomicReference<mousejiggler.Settings> settings = new AtomicReference<>(Settings.defaults());
    private CursorController cursor;
    private JiggleEngine engine;

    private final JTextField moveIntervalField = createField("60000");
    private final JTextField returnIntervalField = createField("100");
    private final JTextField distanceField = createField("2");
    private final JButton movementButton = new ColoredButton();
    private final JButton returnButton = new ColoredButton();
    private final JButton minimizeButton = new JButton("Свернуть");
    private final JButton closeButton = new JButton("Закрыть");

    private boolean settingField;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to the default cross-platform look and feel.
            }
            new MouseJigglerApp().show();
        });
    }

    public MouseJigglerApp() {
        frame = new JFrame("Mouse Jiggler");
        frame.setUndecorated(true);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);
        root.add(createTitleBar(), BorderLayout.NORTH);
        root.add(createContent(), BorderLayout.CENTER);
        frame.setContentPane(root);

        hookField(moveIntervalField,
                Settings::moveIntervalMs,
                (s, v) -> new Settings(v, s.returnIntervalMs(), s.distance(), s.returnCursor()));
        hookField(returnIntervalField,
                Settings::returnIntervalMs,
                (s, v) -> new Settings(s.moveIntervalMs(), v, s.distance(), s.returnCursor()));
        hookField(distanceField,
                Settings::distance,
                (s, v) -> new Settings(s.moveIntervalMs(), s.returnIntervalMs(), v, s.returnCursor()));

        movementButton.addActionListener(e -> toggleMovement());
        returnButton.addActionListener(e -> toggleReturn());
        minimizeButton.addActionListener(e -> frame.setState(JFrame.ICONIFIED));
        closeButton.addActionListener(e -> close());

        updateMovementButton(false);
        updateReturnButton();
    }

    private void show() {
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ------------------------------------------------------------------ UI

    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(TITLE_BAR);
        titleBar.setPreferredSize(new Dimension(0, 24));
        titleBar.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel title = new JLabel("Mouse Jiggler", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        titleBar.add(title, BorderLayout.CENTER);

        MouseAdapter drag = new MouseAdapter() {
            private Point offset;

            @Override
            public void mousePressed(MouseEvent e) {
                Point onScreen = e.getLocationOnScreen();
                Point window = frame.getLocation();
                offset = new Point(onScreen.x - window.x, onScreen.y - window.y);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (offset == null) {
                    return;
                }
                Point onScreen = e.getLocationOnScreen();
                frame.setLocation(onScreen.x - offset.x, onScreen.y - offset.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                offset = null;
            }
        };
        titleBar.addMouseListener(drag);
        titleBar.addMouseMotionListener(drag);
        title.addMouseListener(drag);
        title.addMouseMotionListener(drag);
        return titleBar;
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));

        String[] labels = {
                "Интервал перемещения, мс",
                "Интервал возврата, мс",
                "Смещение курсора, пиксели",
        };
        JTextField[] fields = {moveIntervalField, returnIntervalField, distanceField};

        movementButton.setFont(movementButton.getFont().deriveFont(Font.BOLD));
        minimizeButton.setForeground(DARK_GRAY);
        closeButton.setForeground(DARK_GRAY);

        // Fix the button size so it does not depend on the caption length;
        // the longest caption is "Mouse Jiggler не действует".
        movementButton.setText("Mouse Jiggler не действует");
        Dimension buttonSize = movementButton.getPreferredSize();
        movementButton.setPreferredSize(buttonSize);
        returnButton.setPreferredSize(buttonSize);

        JPanel windowControls = new JPanel(new GridLayout(1, 2, 4, 0));
        windowControls.setOpaque(false);
        windowControls.add(minimizeButton);
        windowControls.add(closeButton);
        windowControls.setPreferredSize(buttonSize);

        for (int row = 0; row < 3; row++) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = row;
            gbc.insets = new Insets(2, 0, 2, 0);

            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets.right = 4;
            content.add(new JLabel(labels[row]), gbc);

            gbc.gridx = 1;
            gbc.insets.right = 6;
            content.add(fields[row], gbc);

            gbc.gridx = 2;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets.right = 0;
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            content.add(filler, gbc);

            gbc.gridx = 3;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.EAST;
            content.add(switch (row) {
                case 0 -> movementButton;
                case 1 -> returnButton;
                default -> windowControls;
            }, gbc);
        }
        return content;
    }

    private static JTextField createField(String initialValue) {
        JTextField field = new JTextField(initialValue);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        // Width fits exactly six digits, without empty space.
        FontMetrics metrics = field.getFontMetrics(field.getFont());
        Insets insets = field.getInsets();
        int width = metrics.stringWidth("999999") + insets.left + insets.right + 2;
        field.setPreferredSize(new Dimension(width, field.getPreferredSize().height));
        return field;
    }

    /**
     * Flat button that fills its background itself, so the green/red state
     * color is always visible — native look-and-feel themes may ignore
     * {@code setBackground} and paint the button with the system theme.
     */
    private static final class ColoredButton extends JButton {
        ColoredButton() {
            setContentAreaFilled(false);
            setFocusPainted(false);
            setForeground(DARK_GRAY);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.GRAY),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color color = getBackground();
            if (getModel().isPressed()) {
                color = color.darker();
            }
            g.setColor(color);
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    /** Limits input to six characters of a possibly unfinished signed decimal. */
    private static final class NumericDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String proposed = new StringBuilder(current)
                    .replace(offset, offset + length, text == null ? "" : text)
                    .toString();
            if (proposed.length() <= MAX_INPUT_LENGTH
                    && NUMBER_PATTERN.matcher(proposed).matches()) {
                super.replace(fb, offset, length, text, attrs);
            }
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            replace(fb, offset, length, "", null);
        }
    }

    // -------------------------------------------------------- input commits

    private void hookField(JTextField field,
                           Function<Settings, Integer> getter,
                           BiFunction<Settings, Integer, Settings> wither) {
        Timer commitTimer = new Timer(1000, e -> commitField(field, getter, wither));
        commitTimer.setRepeats(false);

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                schedule();
            }

            private void schedule() {
                if (!settingField) {
                    commitTimer.restart();
                }
            }
        });

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitTimer.stop();
                commitField(field, getter, wither);
            }
        });
    }

    private void commitField(JTextField field,
                             Function<Settings, Integer> getter,
                             BiFunction<Settings, Integer, Settings> wither) {
        Settings current = settings.get();
        int value = Settings.normalize(field.getText(), getter.apply(current));
        settingField = true;
        try {
            field.setText(String.valueOf(value));
        } finally {
            settingField = false;
        }
        if (value != getter.apply(current)) {
            settings.set(wither.apply(current, value));
        }
        wakeEngine();
    }

    // -------------------------------------------------------------- actions

    private void updateMovementButton(boolean running) {
        movementButton.setText(running ? "Mouse Jiggler действует" : "Mouse Jiggler не действует");
        movementButton.setBackground(running ? GREEN : RED);
    }

    private void updateReturnButton() {
        boolean enabled = settings.get().returnCursor();
        returnButton.setText(enabled ? "Возвращать курсор" : "Не возвращать курсор");
        returnButton.setBackground(enabled ? GREEN : RED);
        returnIntervalField.setEnabled(enabled);
    }

    private void toggleReturn() {
        Settings current = settings.get();
        settings.set(new Settings(current.moveIntervalMs(), current.returnIntervalMs(),
                current.distance(), !current.returnCursor()));
        updateReturnButton();
        wakeEngine();
    }

    private void toggleMovement() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
            updateMovementButton(false);
            return;
        }
        try {
            if (cursor == null) {
                cursor = new CursorController();
            }
            if (engine == null) {
                engine = new JiggleEngine(settings, cursor, this::onEngineError);
            }
            engine.start();
            updateMovementButton(true);
        } catch (Exception error) {
            updateMovementButton(false);
            showError("Ошибка запуска", error);
        }
    }

    private void onEngineError(Exception error) {
        SwingUtilities.invokeLater(() -> {
            updateMovementButton(false);
            showError("Ошибка перемещения курсора", error);
        });
    }

    private void showError(String title, Exception error) {
        JOptionPane.showMessageDialog(frame, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private void wakeEngine() {
        if (engine != null) {
            engine.wake();
        }
    }

    private void close() {
        if (engine != null) {
            engine.stop();
        }
        frame.dispose();
        System.exit(0);
    }
}
