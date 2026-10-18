import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*
 * Number Guessing Game (Java + Swing, dark theme)
 *
 * Structure:
 *   Difficulty        -> the three levels: range + allowed attempts
 *   GameLogic              -> pure game logic, no printing, no UI (unchanged)
 *   ScoreRecord/ScoreBoard -> one finished game / the saved score history
 *   RoundedButton     -> custom painted button with hover effect
 *   RoundedPanel      -> custom painted card with rounded corners
 *   AttemptsBar       -> custom progress bar for attempts left
 *   GameWindow        -> the window, only handles display and user input
 *   Number_guessing_game_optimized  -> holds main(), just launches the window
 */

// ---------------------------------------------------------------
// Difficulty levels: label, max number, allowed attempts
// ---------------------------------------------------------------
enum Difficulty {
    EASY("Easy (1 to 50)", 50, 10),
    MEDIUM("Medium (1 to 100)", 100, 8),
    HARD("Hard (1 to 500)", 500, 12);

    final String label;
    final int maxRange;
    final int maxAttempts;

    Difficulty(String label, int maxRange, int maxAttempts) {
        this.label = label;
        this.maxRange = maxRange;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public String toString() {
        return label;
    }
}

// ---------------------------------------------------------------
// Game logic only. It never prints anything.
// ---------------------------------------------------------------
class GameLogic {
    enum Result { TOO_LOW, TOO_HIGH, CORRECT }
    enum Temperature { HOT, WARM, COLD }

    private final int maxRange;
    private final int maxAttempts;
    private final int secretNumber;
    private int numberOfGuesses = 0;

    GameLogic(int maxRange, int maxAttempts) {
        this.maxRange = maxRange;
        this.maxAttempts = maxAttempts;
        // nextInt(max) gives 0..max-1, so +1 makes it 1..max
        this.secretNumber = new Random().nextInt(maxRange) + 1;
    }

    Result guess(int guessedNumber) {
        numberOfGuesses++;
        if (guessedNumber < secretNumber) return Result.TOO_LOW;
        if (guessedNumber > secretNumber) return Result.TOO_HIGH;
        return Result.CORRECT;
    }

    // Limits scale with the range: 5% = HOT, 15% = WARM, else COLD.
    Temperature temperatureOf(int guessedNumber) {
        int distance = Math.abs(guessedNumber - secretNumber);
        int hotLimit  = Math.max(1, maxRange * 5 / 100);
        int warmLimit = Math.max(2, maxRange * 15 / 100);
        if (distance <= hotLimit)  return Temperature.HOT;
        if (distance <= warmLimit) return Temperature.WARM;
        return Temperature.COLD;
    }

    boolean isOutOfAttempts() {
        return numberOfGuesses >= maxAttempts;
    }

    int getMaxRange()        { return maxRange; }
    int getMaxAttempts()     { return maxAttempts; }
    int getSecretNumber()    { return secretNumber; }
    int getNumberOfGuesses() { return numberOfGuesses; }
    int getAttemptsLeft()    { return maxAttempts - numberOfGuesses; }
}

// ---------------------------------------------------------------
// Score history: one finished game, and the saved list of them
// ---------------------------------------------------------------
class ScoreRecord {
    final String time;
    final Difficulty difficulty;
    final boolean won;
    final int guesses;

    ScoreRecord(String time, Difficulty difficulty, boolean won, int guesses) {
        this.time = time;
        this.difficulty = difficulty;
        this.won = won;
        this.guesses = guesses;
    }

    // One line of the save file, e.g.  2026-10-18 14:32,MEDIUM,WIN,5
    String toCsv() {
        return time + "," + difficulty.name() + "," + (won ? "WIN" : "LOSS") + "," + guesses;
    }

    // Returns null if the line is damaged, so one bad line never breaks loading
    static ScoreRecord fromCsv(String line) {
        String[] p = line.split(",");
        if (p.length != 4) return null;
        try {
            return new ScoreRecord(p[0].trim(), Difficulty.valueOf(p[1].trim()),
                    p[2].trim().equals("WIN"), Integer.parseInt(p[3].trim()));
        } catch (IllegalArgumentException ex) { // bad difficulty name or bad number
            return null;
        }
    }
}

class ScoreBoard {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Saved in the user's home folder so it works no matter where the game is run from
    private final Path file = Paths.get(System.getProperty("user.home"), "number_guesser_scores.csv");
    private final List<ScoreRecord> records = new ArrayList<>();

    ScoreBoard() {
        load();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                ScoreRecord r = ScoreRecord.fromCsv(line);
                if (r != null) records.add(r);
            }
        } catch (IOException ex) {
            System.err.println("Could not read scores: " + ex.getMessage());
        }
    }

    void add(Difficulty difficulty, boolean won, int guesses) {
        ScoreRecord r = new ScoreRecord(LocalDateTime.now().format(FORMAT), difficulty, won, guesses);
        records.add(r);
        try {
            // APPEND: add one line to the end, no need to rewrite the whole file
            Files.write(file, (r.toCsv() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            System.err.println("Could not save score: " + ex.getMessage());
        }
    }

    void clear() {
        records.clear();
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            System.err.println("Could not delete scores: " + ex.getMessage());
        }
    }

    List<ScoreRecord> all() { return new ArrayList<>(records); }
    Path getFile()          { return file; }
    int played()            { return records.size(); }

    int won() {
        int n = 0;
        for (ScoreRecord r : records) if (r.won) n++;
        return n;
    }

    // Fewest guesses in a win on this difficulty, or -1 if never won
    int bestFor(Difficulty d) {
        int best = -1;
        for (ScoreRecord r : records) {
            if (r.won && r.difficulty == d && (best == -1 || r.guesses < best)) best = r.guesses;
        }
        return best;
    }
}

// ---------------------------------------------------------------
// Custom UI pieces
// ---------------------------------------------------------------

// A button with rounded corners and a hover effect.
class RoundedButton extends JButton {
    private Color fill;
    private boolean hover = false;

    RoundedButton(String text, Color fill) {
        super(text);
        this.fill = fill;
        setContentAreaFilled(false); // we paint the background ourselves
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setForeground(Color.WHITE);
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        });
    }

    void setFill(Color fill) {
        this.fill = fill;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color c;
        if (!isEnabled())   c = new Color(51, 65, 85);
        else if (hover)     c = fill.brighter();
        else                c = fill;
        g2.setColor(c);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
        g2.dispose();
        super.paintComponent(g); // draws the text on top
    }
}

// A panel with rounded corners (used as a "card").
class RoundedPanel extends JPanel {
    private final Color fill;

    RoundedPanel(Color fill) {
        this.fill = fill;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
        g2.dispose();
        super.paintComponent(g);
    }
}

// Progress bar for attempts left: green -> orange -> red as it drains.
class AttemptsBar extends JComponent {
    private int left = 1;
    private int max = 1;

    AttemptsBar() {
        setPreferredSize(new Dimension(360, 10));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
    }

    void setValues(int left, int max) {
        this.left = left;
        this.max = max;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        g2.setColor(new Color(51, 65, 85)); // track
        g2.fillRoundRect(0, 0, w, h, h, h);

        double ratio = (double) left / max;
        Color c;
        if (ratio > 0.5)       c = new Color(74, 222, 128);
        else if (ratio > 0.25) c = new Color(251, 191, 36);
        else                   c = new Color(248, 113, 113);
        g2.setColor(c);
        g2.fillRoundRect(0, 0, (int) (w * ratio), h, h, h);
        g2.dispose();
    }
}

// ---------------------------------------------------------------
// The window
// ---------------------------------------------------------------
class GameWindow extends JFrame {
    // Palette
    private static final Color BG      = new Color(15, 23, 42);
    private static final Color CARD    = new Color(30, 41, 59);
    private static final Color CHIP    = new Color(51, 65, 85);
    private static final Color ACCENT  = new Color(13, 148, 136);
    private static final Color TEXT    = new Color(226, 232, 240);
    private static final Color MUTED   = new Color(148, 163, 184);
    private static final Color BLUE    = new Color(96, 165, 250);
    private static final Color RED     = new Color(248, 113, 113);
    private static final Color GREEN   = new Color(74, 222, 128);
    private static final Color ORANGE  = new Color(251, 191, 36);
    private static final Color HOT_C   = new Color(251, 146, 60);
    private static final Color WARM_C  = new Color(250, 204, 21);
    private static final Color COLD_C  = new Color(125, 211, 252);

    private GameLogic game;
    private Difficulty current = Difficulty.MEDIUM;

    private final RoundedButton[] diffButtons = new RoundedButton[Difficulty.values().length];
    private final JLabel rangeLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel bigNumberLabel = new JLabel("?", SwingConstants.CENTER);
    private final JLabel hintLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel tempLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JTextField inputField = new JTextField(6);
    private final RoundedButton guessButton = new RoundedButton("Guess", ACCENT);
    private final RoundedButton newGameButton = new RoundedButton("New Game", CHIP);
    private final JLabel countLabel = new JLabel("", SwingConstants.CENTER);
    private final AttemptsBar attemptsBar = new AttemptsBar();
    private final JLabel bestLabel = new JLabel(" ", SwingConstants.CENTER);
    private final RoundedButton scoresButton = new RoundedButton("Scores", CHIP);
    private final ScoreBoard scoreBoard = new ScoreBoard();
    private final JTextPane historyPane = new JTextPane();

    GameWindow() {
        super("Number Guesser");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildLayout();
        wireEvents();
        startNewGame();
        pack();
        Dimension s = getSize();
        setSize(Math.max(s.width, 460), s.height);
        setMinimumSize(getSize());
        setLocationRelativeTo(null); // centre on screen
    }

    private JLabel styled(JLabel label, int size, int style, Color color) {
        label.setFont(new Font(Font.SANS_SERIF, style, size));
        label.setForeground(color);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private void buildLayout() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        // Title
        JLabel title = styled(new JLabel("NUMBER GUESSER", SwingConstants.CENTER),
                22, Font.BOLD, ACCENT);

        // Difficulty toggle buttons
        JPanel diffRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        diffRow.setOpaque(false);
        Difficulty[] levels = Difficulty.values();
        for (int i = 0; i < levels.length; i++) {
            final Difficulty level = levels[i];
            String name = level.name().charAt(0) + level.name().substring(1).toLowerCase();
            diffButtons[i] = new RoundedButton(name, CHIP);
            diffButtons[i].addActionListener(e -> selectDifficulty(level));
            diffRow.add(diffButtons[i]);
        }

        // Main card: range, big number, hints
        RoundedPanel card = new RoundedPanel(CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        styled(rangeLabel, 14, Font.PLAIN, MUTED);
        styled(bigNumberLabel, 64, Font.BOLD, TEXT);
        styled(hintLabel, 18, Font.BOLD, TEXT);
        styled(tempLabel, 14, Font.BOLD, TEXT);
        card.add(rangeLabel);
        card.add(bigNumberLabel);
        card.add(hintLabel);
        card.add(Box.createVerticalStrut(2));
        card.add(tempLabel);

        // Input row
        inputField.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        inputField.setHorizontalAlignment(JTextField.CENTER);
        inputField.setBackground(CARD);
        inputField.setForeground(TEXT);
        inputField.setCaretColor(TEXT);
        inputField.setDisabledTextColor(MUTED);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CHIP, 2, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        inputRow.setOpaque(false);
        inputRow.add(inputField);
        inputRow.add(guessButton);

        // Attempts
        styled(countLabel, 13, Font.PLAIN, MUTED);
        styled(bestLabel, 13, Font.BOLD, ACCENT);

        // History
        JLabel historyTitle = styled(new JLabel("This game", SwingConstants.LEFT),
                13, Font.BOLD, MUTED);
        historyTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        historyPane.setEditable(false);
        historyPane.setBackground(CARD);
        historyPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        historyPane.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JScrollPane historyScroll = new JScrollPane(historyPane);
        historyScroll.setBorder(new LineBorder(CHIP, 1, true));
        historyScroll.getViewport().setBackground(CARD);
        historyScroll.setPreferredSize(new Dimension(380, 140));

        // Bottom row
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottomRow.setOpaque(false);
        bottomRow.add(newGameButton);
        bottomRow.add(Box.createHorizontalStrut(10));
        bottomRow.add(scoresButton);

        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        diffRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        inputRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        attemptsBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        historyScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        root.add(title);
        root.add(Box.createVerticalStrut(14));
        root.add(diffRow);
        root.add(Box.createVerticalStrut(14));
        root.add(card);
        root.add(Box.createVerticalStrut(14));
        root.add(inputRow);
        root.add(Box.createVerticalStrut(14));
        root.add(countLabel);
        root.add(Box.createVerticalStrut(6));
        root.add(attemptsBar);
        root.add(Box.createVerticalStrut(8));
        root.add(bestLabel);
        root.add(Box.createVerticalStrut(14));
        root.add(historyTitle);
        root.add(Box.createVerticalStrut(4));
        root.add(historyScroll);
        root.add(Box.createVerticalStrut(14));
        root.add(bottomRow);

        setContentPane(root);
    }

    private void wireEvents() {
        guessButton.addActionListener(e -> handleGuess());
        inputField.addActionListener(e -> handleGuess()); // Enter key
        newGameButton.addActionListener(e -> startNewGame());
        scoresButton.addActionListener(e -> showScores());
    }

    private void selectDifficulty(Difficulty level) {
        current = level;
        startNewGame();
    }

    private void refreshDifficultyButtons() {
        Difficulty[] levels = Difficulty.values();
        for (int i = 0; i < levels.length; i++) {
            diffButtons[i].setFill(levels[i] == current ? ACCENT : CHIP);
        }
    }

    private void startNewGame() {
        game = new GameLogic(current.maxRange, current.maxAttempts);
        refreshDifficultyButtons();
        rangeLabel.setText("Guess a number between 1 and " + game.getMaxRange());
        bigNumberLabel.setText("?");
        bigNumberLabel.setForeground(TEXT);
        setHint(" ", TEXT);
        setTemperature(" ", TEXT);
        updateAttempts();
        updateBestLabel();
        historyPane.setText("");
        inputField.setText("");
        inputField.setEnabled(true);
        guessButton.setEnabled(true);
        inputField.requestFocusInWindow();
    }

    private void handleGuess() {
        if (!guessButton.isEnabled()) return; // game already over

        String text = inputField.getText().trim();

        // 1) Must be a whole number
        int guessed;
        try {
            guessed = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            setHint("Please enter a whole number", ORANGE);
            setTemperature(" ", TEXT);
            focusAndSelectInput();
            return;
        }

        // 2) Must be inside the range. Invalid input does NOT use up an attempt.
        if (guessed < 1 || guessed > game.getMaxRange()) {
            setHint("Enter a number from 1 to " + game.getMaxRange(), ORANGE);
            setTemperature(" ", TEXT);
            focusAndSelectInput();
            return;
        }

        // 3) Valid guess: ask the game logic what happened
        GameLogic.Result result = game.guess(guessed);
        int count = game.getNumberOfGuesses();
        updateAttempts();

        if (result == GameLogic.Result.CORRECT) {
            bigNumberLabel.setText(String.valueOf(game.getSecretNumber()));
            bigNumberLabel.setForeground(GREEN);
            setHint("You got it in " + count + (count == 1 ? " guess!" : " guesses!"), GREEN);
            setTemperature(" ", TEXT);
            addHistory("#" + count + "  " + guessed + "  ->  correct!", GREEN);
            recordScore(true, count);
            endGame();
            return;
        }

        // Wrong guess: higher/lower hint
        String direction;
        Color directionColor;
        if (result == GameLogic.Result.TOO_LOW) {
            setHint("Higher your guess", BLUE);
            direction = "too low";
            directionColor = BLUE;
        } else {
            setHint("Lower your guess", RED);
            direction = "too high";
            directionColor = RED;
        }

        // Wrong guess: hot/warm/cold hint
        GameLogic.Temperature temp = game.temperatureOf(guessed);
        switch (temp) {
            case HOT:
                setTemperature("Hot - very close!", HOT_C);
                break;
            case WARM:
                setTemperature("Warm - getting closer", WARM_C);
                break;
            default:
                setTemperature("Cold - far away", COLD_C);
                break;
        }
        addHistory("#" + count + "  " + guessed + "  ->  " + direction
                + " (" + temp.name().toLowerCase() + ")", directionColor);

        // Lose condition
        if (game.isOutOfAttempts()) {
            bigNumberLabel.setText(String.valueOf(game.getSecretNumber()));
            bigNumberLabel.setForeground(RED);
            setHint("Out of guesses!", RED);
            setTemperature("The number was " + game.getSecretNumber(), MUTED);
            recordScore(false, count);
            endGame();
            return;
        }

        focusAndSelectInput();
    }

    // Stop accepting guesses until a new game starts
    private void endGame() {
        inputField.setEnabled(false);
        guessButton.setEnabled(false);
        newGameButton.requestFocusInWindow();
    }

    private void updateAttempts() {
        countLabel.setText("Guesses: " + game.getNumberOfGuesses() + " / "
                + game.getMaxAttempts() + "   (" + game.getAttemptsLeft() + " left)");
        attemptsBar.setValues(game.getAttemptsLeft(), game.getMaxAttempts());
    }

    // Adds one coloured line at the end of a text pane
    private static void appendLine(JTextPane pane, String line, Color color) {
        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, color);
        try {
            doc.insertString(doc.getLength(), line + "\n", attr);
        } catch (BadLocationException ex) {
            // cannot happen: we always insert at the end
        }
    }

    private void addHistory(String line, Color color) {
        appendLine(historyPane, line, color);
        historyPane.setCaretPosition(historyPane.getDocument().getLength()); // scroll to newest
    }

    private static String nice(Difficulty d) {
        return d.name().charAt(0) + d.name().substring(1).toLowerCase();
    }

    private void recordScore(boolean won, int guesses) {
        scoreBoard.add(current, won, guesses);
        updateBestLabel();
    }

    private void updateBestLabel() {
        int best = scoreBoard.bestFor(current);
        if (best == -1) {
            bestLabel.setText("Best (" + nice(current) + "): no win yet");
        } else {
            bestLabel.setText("Best (" + nice(current) + "): " + best + (best == 1 ? " guess" : " guesses"));
        }
    }

    // Pop-up window with the saved score history
    private void showScores() {
        JDialog dialog = new JDialog(this, "Score History", true);
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        int played = scoreBoard.played();
        int won = scoreBoard.won();
        String summary = played == 0
                ? "No games played yet"
                : "Played " + played + "   |   Won " + won + "   |   Win rate " + (won * 100 / played) + "%";
        JLabel summaryLabel = styled(new JLabel(summary, SwingConstants.CENTER), 16, Font.BOLD, TEXT);
        summaryLabel.setToolTipText("Saved in " + scoreBoard.getFile());

        StringBuilder bests = new StringBuilder("Best:   ");
        Difficulty[] levels = Difficulty.values();
        for (int i = 0; i < levels.length; i++) {
            int b = scoreBoard.bestFor(levels[i]);
            bests.append(nice(levels[i])).append(" ").append(b == -1 ? "-" : String.valueOf(b));
            if (i < levels.length - 1) bests.append("   |   ");
        }
        JLabel bestsLabel = styled(new JLabel(bests.toString(), SwingConstants.CENTER), 13, Font.PLAIN, ACCENT);

        // Newest game first, up to 50 games
        JTextPane list = new JTextPane();
        list.setEditable(false);
        list.setBackground(CARD);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        list.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        List<ScoreRecord> records = scoreBoard.all();
        int shown = 0;
        for (int i = records.size() - 1; i >= 0 && shown < 50; i--, shown++) {
            ScoreRecord r = records.get(i);
            String line = String.format("%s  %-7s %-4s %3d guesses",
                    r.time, nice(r.difficulty), r.won ? "WIN" : "LOSS", r.guesses);
            appendLine(list, line, r.won ? GREEN : RED);
        }
        if (records.isEmpty()) appendLine(list, "Win or lose a game and it shows up here.", MUTED);
        list.setCaretPosition(0); // start at the top (newest)

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(new LineBorder(CHIP, 1, true));
        scroll.getViewport().setBackground(CARD);
        scroll.setPreferredSize(new Dimension(440, 240));
        scroll.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Clear needs two clicks so one slip doesn't wipe the history
        final boolean[] armed = {false};
        RoundedButton clearButton = new RoundedButton("Clear history", CHIP);
        clearButton.setEnabled(played > 0);
        clearButton.addActionListener(e -> {
            if (!armed[0]) {
                armed[0] = true;
                clearButton.setText("Sure? Click again");
                clearButton.setFill(RED);
            } else {
                scoreBoard.clear();
                updateBestLabel();
                dialog.dispose();
            }
        });
        RoundedButton closeButton = new RoundedButton("Close", ACCENT);
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.add(clearButton);
        buttons.add(closeButton);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

        root.add(summaryLabel);
        root.add(Box.createVerticalStrut(6));
        root.add(bestsLabel);
        root.add(Box.createVerticalStrut(12));
        root.add(scroll);
        root.add(Box.createVerticalStrut(12));
        root.add(buttons);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void setHint(String message, Color color) {
        hintLabel.setText(message);
        hintLabel.setForeground(color);
    }

    private void setTemperature(String message, Color color) {
        tempLabel.setText(message);
        tempLabel.setForeground(color);
    }

    private void focusAndSelectInput() {
        inputField.requestFocusInWindow();
        inputField.selectAll();
    }
}

// ---------------------------------------------------------------
// Entry point. The public class name must match the file name.
// ---------------------------------------------------------------
public class Number_guessing_game_optimized {
    public static void main(String[] args) {
        // Dark scrollbar colours (Metal look and feel respects these keys)
        UIManager.put("ScrollBar.thumb", new Color(71, 85, 105));
        UIManager.put("ScrollBar.thumbShadow", new Color(71, 85, 105));
        UIManager.put("ScrollBar.thumbHighlight", new Color(71, 85, 105));
        UIManager.put("ScrollBar.thumbDarkShadow", new Color(71, 85, 105));
        UIManager.put("ScrollBar.track", new Color(30, 41, 59));

        // Swing UI should be created on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> new GameWindow().setVisible(true));
    }
}