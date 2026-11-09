package chess;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

public final class Gui {
    private static final Color BACKGROUND = new Color(31, 34, 40);
    private static final Color PANEL = new Color(42, 46, 54);
    private static final Color TEXT = new Color(239, 241, 245);
    private static final Color MUTED = new Color(171, 177, 188);

    private final JFrame frame = new JFrame("Chess");
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
    private final JSpinner depth = new JSpinner(new SpinnerNumberModel(64, 1, 64, 1));
    private final JCheckBox useBook = new JCheckBox("Book", true);
    private final JLabel whiteClock = new JLabel("3:00", SwingConstants.CENTER);
    private final JLabel blackClock = new JLabel("3:00", SwingConstants.CENTER);
    private final Board board = new Board();
    private final OpeningBook openingBook = new OpeningBook();
    private final Random random = new Random();
    private final Timer clockTimer;

    private Game game = new Game();
    private Clock clock = new Clock();
    private Engine whiteEngine;
    private Engine blackEngine;
    private boolean engineThinking;
    private String forcedResult;
    private long turnStarted;
    private int generation;

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Chess");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The cross-platform look and feel remains usable.
            }
            new Gui().show();
        });
    }

    private Gui() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(920, 760));
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setLayout(new BorderLayout(0, 0));

        JPanel header = new JPanel(new BorderLayout(12, 12));
        header.setBackground(PANEL);
        header.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        JLabel title = new JLabel("CHESS");
        title.setForeground(TEXT);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
        header.add(title, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        controls.setOpaque(false);
        JLabel depthLabel = new JLabel("Max depth");
        depthLabel.setForeground(MUTED);
        controls.add(mode);
        controls.add(depthLabel);
        controls.add(depth);
        useBook.setOpaque(false);
        useBook.setForeground(TEXT);
        controls.add(useBook);
        JButton newGame = new JButton("New Game");
        newGame.addActionListener(event -> startGame());
        controls.add(newGame);
        JButton match = new JButton("Run Match");
        match.addActionListener(event -> runMatch(match));
        controls.add(match);
        JButton train = new JButton("Train 10");
        train.addActionListener(event -> runTraining(train));
        controls.add(train);
        mode.addActionListener(event -> startGame());
        header.add(controls, BorderLayout.EAST);

        status.setForeground(TEXT);
        status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        status.setBorder(BorderFactory.createEmptyBorder(10, 8, 0, 8));

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(BACKGROUND);
        north.add(header, BorderLayout.NORTH);
        north.add(status, BorderLayout.SOUTH);
        frame.add(north, BorderLayout.NORTH);

        JPanel boardHolder = new JPanel(new BorderLayout(0, 8));
        boardHolder.setBackground(BACKGROUND);
        boardHolder.setBorder(BorderFactory.createEmptyBorder(12, 22, 10, 22));
        styleClock(blackClock);
        styleClock(whiteClock);
        boardHolder.add(blackClock, BorderLayout.NORTH);
        boardHolder.add(board, BorderLayout.CENTER);
        boardHolder.add(whiteClock, BorderLayout.SOUTH);
        frame.add(boardHolder, BorderLayout.CENTER);

        JLabel help = new JLabel("Drag a piece or click it, then click a highlighted square.", SwingConstants.CENTER);
        help.setForeground(MUTED);
        help.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        help.setBorder(BorderFactory.createEmptyBorder(4, 12, 15, 12));
        frame.add(help, BorderLayout.SOUTH);

        clockTimer = new Timer(100, event -> tickClock());
        clockTimer.start();
    }

    private void show() {
        frame.pack();
        frame.setSize(980, 920);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        startGame();
    }

    private void startGame() {
        generation++;
        engineThinking = false;
        forcedResult = null;
        game = new Game();
        clock = new Clock();
        int maximumDepth = (Integer) depth.getValue();
        Eval eval = Eval.loadOrDefault(Eval.userModel());
        Mode selected = (Mode) mode.getSelectedItem();
        whiteEngine = selected != null && selected.control(chess.Color.WHITE) == Control.ENGINE
                ? new Engine("White engine", maximumDepth, 128, eval) : null;
        blackEngine = selected != null && selected.control(chess.Color.BLACK) == Control.ENGINE
                ? new Engine("Black engine", maximumDepth, 128, eval) : null;
        turnStarted = System.nanoTime();
        board.reset();
        updateStatus();
        runEngineIfNeeded();
    }

    private void playHuman(Move move) {
        if (!humanTurn() || engineThinking || finished()) {
            return;
        }
        chess.Color mover = game.position().sideToMove();
        long elapsed = elapsedSinceTurnStart();
        if (!clock.completeMove(mover, elapsed)) {
            declareTimeout(mover);
            return;
        }
        game.play(move);
        turnStarted = System.nanoTime();
        board.lastMove = move;
        board.resetSelection();
        updateStatus();
        runEngineIfNeeded();
    }

    private boolean humanTurn() {
        Mode selected = (Mode) mode.getSelectedItem();
        if (selected == null) {
            return false;
        }
        return selected.control(game.position().sideToMove()) == Control.HUMAN;
    }

    private void runEngineIfNeeded() {
        Result result = game.result();
        Mode selected = (Mode) mode.getSelectedItem();
        if (result.finished() || forcedResult != null || selected == null
                || selected.control(game.position().sideToMove()) != Control.ENGINE) {
            return;
        }

        engineThinking = true;
        board.resetSelection();
        updateStatus();
        int runGeneration = generation;
        Position position = game.position();
        List<Move> legal = game.legalMoves();
        chess.Color mover = position.sideToMove();
        Engine engine = mover == chess.Color.WHITE ? whiteEngine : blackEngine;
        long remaining = clock.remaining(mover);
        Move bookMove = useBook.isSelected() ? openingBook.choose(position, legal, random) : null;
        long searchStarted = System.nanoTime();

        new SwingWorker<EngineMove, Void>() {
            @Override
            protected EngineMove doInBackground() {
                if (bookMove != null) return new EngineMove(bookMove, true, null, 0);
                SearchResult searched = engine.search(position, legal,
                        SearchLimits.timed(remaining, clock.incrementMillis()));
                long elapsed = Math.max(1, (System.nanoTime() - searchStarted + 999_999) / 1_000_000);
                return new EngineMove(searched.move(), false, searched.stats(), elapsed);
            }

            @Override
            protected void done() {
                if (runGeneration != generation) {
                    return;
                }
                try {
                    EngineMove engineMove = get();
                    if (forcedResult != null) return;
                    if (!engineMove.book() && !clock.completeMove(mover, engineMove.elapsedMillis())) {
                        declareTimeout(mover);
                        return;
                    }
                    game.play(engineMove.move());
                    turnStarted = System.nanoTime();
                    board.lastMove = engineMove.move();
                    engineThinking = false;
                    board.repaint();
                    updateStatus();
                    if (!game.result().finished()) {
                        Timer pause = new Timer(350, event -> runEngineIfNeeded());
                        pause.setRepeats(false);
                        pause.start();
                    }
                } catch (Exception exception) {
                    engineThinking = false;
                    status.setText("Engine error: " + exception.getMessage());
                }
            }
        }.execute();
    }

    private void updateStatus() {
        Result result = game.result();
        if (forcedResult != null) {
            status.setText(forcedResult);
        } else if (result.finished()) {
            status.setText(result.description());
        } else if (engineThinking) {
            status.setText(colorName(game.position().sideToMove()) + " engine is thinking…");
        } else {
            String check = Moves.inCheck(game.position(), game.position().sideToMove()) ? " — Check!" : "";
            status.setText(colorName(game.position().sideToMove()) + " to move" + check);
        }
        board.repaint();
        updateClocks();
    }

    private void tickClock() {
        if (!frame.isVisible() || finished()) {
            updateClocks();
            return;
        }
        chess.Color mover = game.position().sideToMove();
        if (elapsedSinceTurnStart() >= clock.remaining(mover)) {
            declareTimeout(mover);
        } else {
            updateClocks();
        }
    }

    private void updateClocks() {
        long white = clock.remaining(chess.Color.WHITE);
        long black = clock.remaining(chess.Color.BLACK);
        if (!finished()) {
            long elapsed = elapsedSinceTurnStart();
            if (game.position().sideToMove() == chess.Color.WHITE) white = Math.max(0, white - elapsed);
            else black = Math.max(0, black - elapsed);
        }
        whiteClock.setText("White   " + Clock.display(white));
        blackClock.setText("Black   " + Clock.display(black));
    }

    private long elapsedSinceTurnStart() {
        return Math.max(0, (System.nanoTime() - turnStarted + 999_999) / 1_000_000);
    }

    private boolean finished() {
        return forcedResult != null || game.result().finished();
    }

    private void declareTimeout(chess.Color loser) {
        clock.setRemaining(loser, 0);
        engineThinking = false;
        forcedResult = colorName(loser.opposite()) + " wins on time.";
        board.resetSelection();
        updateStatus();
    }

    private static void styleClock(JLabel label) {
        label.setOpaque(true);
        label.setBackground(PANEL);
        label.setForeground(TEXT);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        label.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
    }

    private void runMatch(JButton button) {
        button.setEnabled(false);
        status.setText("Running a paired 3+1 engine match in the background…");
        Path output = Path.of(System.getProperty("user.home"), "Documents", "Chess Matches",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        new SwingWorker<Match.Summary, Void>() {
            @Override protected Match.Summary doInBackground() throws Exception {
                Eval eval = Eval.loadOrDefault(Eval.userModel());
                return new Match(new Match.Config(1, (Integer) depth.getValue(),
                        Clock.DEFAULT_INITIAL_MILLIS, Clock.DEFAULT_INCREMENT_MILLIS, output),
                        eval, eval, 128, 0, true).run();
            }

            @Override protected void done() {
                button.setEnabled(true);
                try {
                    Match.Summary summary = get();
                    JOptionPane.showMessageDialog(frame, summary.display(), "Match complete",
                            JOptionPane.INFORMATION_MESSAGE);
                    updateStatus();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(frame, "Match failed: " + exception.getMessage(),
                            "Match error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void runTraining(JButton button) {
        button.setEnabled(false);
        Path output = Eval.userModel().getParent();
        status.setText("Training 10 generations at depth 3 with a 5+0.1 clock…");
        new SwingWorker<Trainer.Summary, Void>() {
            @Override protected Trainer.Summary doInBackground() throws Exception {
                return new Trainer(Trainer.Config.fast(10, Trainer.DEFAULT_PAIRS, output)).run();
            }

            @Override protected void done() {
                button.setEnabled(true);
                try {
                    Trainer.Summary summary = get();
                    JOptionPane.showMessageDialog(frame, summary.display()
                                    + "\nNew engine games will use the latest promoted weights.",
                            "Training complete", JOptionPane.INFORMATION_MESSAGE);
                    updateStatus();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(frame, "Training failed: " + exception.getMessage(),
                            "Training error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private record EngineMove(Move move, boolean book, SearchStats stats, long elapsedMillis) {
    }

    private Move choosePromotion(List<Move> candidates) {
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        String[] choices = {"Queen", "Rook", "Bishop", "Knight"};
        int selected = JOptionPane.showOptionDialog(frame, "Choose a promotion piece", "Promotion",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        PieceType type = switch (selected) {
            case 1 -> PieceType.ROOK;
            case 2 -> PieceType.BISHOP;
            case 3 -> PieceType.KNIGHT;
            default -> PieceType.QUEEN;
        };
        return candidates.stream().filter(move -> move.promotion() == type).findFirst().orElse(candidates.getFirst());
    }

    private static String colorName(chess.Color color) {
        return color == chess.Color.WHITE ? "White" : "Black";
    }

    private enum Control {
        HUMAN, ENGINE
    }

    private enum Mode {
        TWO_PLAYERS("Two Players", Control.HUMAN, Control.HUMAN),
        WHITE_ENGINE("Play White vs Engine", Control.HUMAN, Control.ENGINE),
        BLACK_ENGINE("Play Black vs Engine", Control.ENGINE, Control.HUMAN),
        ENGINES("Watch Engine vs Engine", Control.ENGINE, Control.ENGINE);

        private final String label;
        private final Control white;
        private final Control black;

        Mode(String label, Control white, Control black) {
            this.label = label;
            this.white = white;
            this.black = black;
        }

        Control control(chess.Color color) {
            return color == chess.Color.WHITE ? white : black;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final class Board extends JPanel {
        private static final Color LIGHT = new Color(238, 216, 181);
        private static final Color DARK = new Color(181, 136, 99);
        private static final Color SELECTED = new Color(246, 246, 105, 180);
        private static final Color LAST = new Color(205, 210, 80, 125);
        private static final Color MOVE = new Color(34, 70, 45, 145);

        private Square selected;
        private List<Move> selectedMoves = List.of();
        private Square dragFrom;
        private Piece draggedPiece;
        private Point dragPoint;
        private Move lastMove;

        Board() {
            setOpaque(true);
            setBackground(BACKGROUND);
            setPreferredSize(new Dimension(720, 720));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    press(event);
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (draggedPiece != null) {
                        dragPoint = event.getPoint();
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    release(event);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void reset() {
            lastMove = null;
            resetSelection();
        }

        void resetSelection() {
            selected = null;
            selectedMoves = List.of();
            dragFrom = null;
            draggedPiece = null;
            dragPoint = null;
            repaint();
        }

        private void press(MouseEvent event) {
            if (!humanTurn() || engineThinking || finished()) {
                return;
            }
            Square square = squareAt(event.getPoint());
            if (square == null) {
                return;
            }

            if (selected != null && selectedMoves.stream().anyMatch(move -> move.to().equals(square))) {
                dragFrom = selected;
                draggedPiece = game.position().pieceAt(selected);
                dragPoint = event.getPoint();
                repaint();
                return;
            }

            Piece piece = game.position().pieceAt(square);
            if (piece != null && piece.color() == game.position().sideToMove()) {
                selected = square;
                selectedMoves = game.legalMoves().stream().filter(move -> move.from().equals(square)).toList();
                dragFrom = square;
                draggedPiece = piece;
                dragPoint = event.getPoint();
            } else {
                resetSelection();
            }
            repaint();
        }

        private void release(MouseEvent event) {
            if (draggedPiece == null || dragFrom == null) {
                return;
            }
            Square target = squareAt(event.getPoint());
            if (target != null && !target.equals(dragFrom)) {
                List<Move> candidates = selectedMoves.stream()
                        .filter(move -> move.from().equals(dragFrom) && move.to().equals(target))
                        .toList();
                if (!candidates.isEmpty()) {
                    draggedPiece = null;
                    dragPoint = null;
                    playHuman(choosePromotion(candidates));
                    return;
                }
            }
            draggedPiece = null;
            dragFrom = null;
            dragPoint = null;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Metrics metrics = metrics();

            for (int rank = 0; rank < 8; rank++) {
                for (int file = 0; file < 8; file++) {
                    int x = metrics.x + file * metrics.cell;
                    int y = metrics.y + (7 - rank) * metrics.cell;
                    g.setColor((file + rank) % 2 == 1 ? LIGHT : DARK);
                    g.fillRect(x, y, metrics.cell, metrics.cell);

                    Square square = new Square(file, rank);
                    if (lastMove != null && (lastMove.from().equals(square) || lastMove.to().equals(square))) {
                        g.setColor(LAST);
                        g.fillRect(x, y, metrics.cell, metrics.cell);
                    }
                    if (square.equals(selected)) {
                        g.setColor(SELECTED);
                        g.fillRect(x, y, metrics.cell, metrics.cell);
                    }
                }
            }

            drawMoveHints(g, metrics);
            drawCoordinates(g, metrics);
            drawPieces(g, metrics);
            g.setColor(new Color(12, 14, 17));
            g.setStroke(new BasicStroke(2));
            g.drawRect(metrics.x, metrics.y, metrics.size, metrics.size);
            g.dispose();
        }

        private void drawMoveHints(Graphics2D g, Metrics metrics) {
            for (Move move : selectedMoves) {
                Square target = move.to();
                int centerX = metrics.x + target.file() * metrics.cell + metrics.cell / 2;
                int centerY = metrics.y + (7 - target.rank()) * metrics.cell + metrics.cell / 2;
                g.setColor(MOVE);
                if (game.position().pieceAt(target) == null && !target.equals(game.position().enPassant())) {
                    int diameter = Math.max(12, metrics.cell / 4);
                    g.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
                } else {
                    int inset = Math.max(5, metrics.cell / 14);
                    g.setStroke(new BasicStroke(Math.max(4, metrics.cell / 13f)));
                    g.drawOval(metrics.x + target.file() * metrics.cell + inset,
                            metrics.y + (7 - target.rank()) * metrics.cell + inset,
                            metrics.cell - 2 * inset, metrics.cell - 2 * inset);
                }
            }
        }

        private void drawCoordinates(Graphics2D g, Metrics metrics) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, metrics.cell / 8)));
            for (int file = 0; file < 8; file++) {
                g.setColor(file % 2 == 0 ? DARK.darker() : LIGHT.brighter());
                g.drawString(String.valueOf((char) ('a' + file)),
                        metrics.x + file * metrics.cell + metrics.cell - metrics.cell / 7,
                        metrics.y + metrics.size - 5);
            }
            for (int rank = 0; rank < 8; rank++) {
                g.setColor(rank % 2 == 0 ? LIGHT.brighter() : DARK.darker());
                g.drawString(String.valueOf(rank + 1), metrics.x + 4,
                        metrics.y + (7 - rank) * metrics.cell + metrics.cell / 7);
            }
        }

        private void drawPieces(Graphics2D g, Metrics metrics) {
            Font pieceFont = new Font(Font.SERIF, Font.PLAIN, Math.max(36, (int) (metrics.cell * 0.79)));
            g.setFont(pieceFont);
            FontMetrics fontMetrics = g.getFontMetrics();
            for (int rank = 0; rank < 8; rank++) {
                for (int file = 0; file < 8; file++) {
                    Square square = new Square(file, rank);
                    Piece piece = game.position().pieceAt(square);
                    if (piece == null || (draggedPiece != null && square.equals(dragFrom))) {
                        continue;
                    }
                    int centerX = metrics.x + file * metrics.cell + metrics.cell / 2;
                    int centerY = metrics.y + (7 - rank) * metrics.cell + metrics.cell / 2;
                    drawPiece(g, fontMetrics, piece, centerX, centerY, metrics.cell);
                }
            }
            if (draggedPiece != null && dragPoint != null) {
                drawPiece(g, fontMetrics, draggedPiece, dragPoint.x, dragPoint.y, metrics.cell);
            }
        }

        private void drawPiece(Graphics2D g, FontMetrics metrics, Piece piece,
                               int centerX, int centerY, int cell) {
            String symbol = symbol(piece);
            int width = metrics.stringWidth(symbol);
            int x = centerX - width / 2;
            int y = centerY + (metrics.getAscent() - metrics.getDescent()) / 2;
            if (piece.color() == chess.Color.WHITE) {
                g.setColor(new Color(35, 38, 43, 175));
                g.drawString(symbol, x + Math.max(1, cell / 45), y + Math.max(1, cell / 45));
                g.setColor(new Color(250, 247, 237));
            } else {
                g.setColor(new Color(247, 239, 220, 150));
                g.drawString(symbol, x + Math.max(1, cell / 55), y + Math.max(1, cell / 55));
                g.setColor(new Color(31, 33, 37));
            }
            g.drawString(symbol, x, y);
        }

        private Square squareAt(Point point) {
            Metrics metrics = metrics();
            if (point.x < metrics.x || point.y < metrics.y
                    || point.x >= metrics.x + metrics.size || point.y >= metrics.y + metrics.size) {
                return null;
            }
            int file = (point.x - metrics.x) / metrics.cell;
            int rank = 7 - (point.y - metrics.y) / metrics.cell;
            return new Square(file, rank);
        }

        private Metrics metrics() {
            int size = Math.min(getWidth(), getHeight());
            int cell = Math.max(1, size / 8);
            size = cell * 8;
            return new Metrics((getWidth() - size) / 2, (getHeight() - size) / 2, size, cell);
        }

        private String symbol(Piece piece) {
            return switch (piece.type()) {
                case KING -> piece.color() == chess.Color.WHITE ? "♔" : "♚";
                case QUEEN -> piece.color() == chess.Color.WHITE ? "♕" : "♛";
                case ROOK -> piece.color() == chess.Color.WHITE ? "♖" : "♜";
                case BISHOP -> piece.color() == chess.Color.WHITE ? "♗" : "♝";
                case KNIGHT -> piece.color() == chess.Color.WHITE ? "♘" : "♞";
                case PAWN -> piece.color() == chess.Color.WHITE ? "♙" : "♟";
            };
        }

        private record Metrics(int x, int y, int size, int cell) {
        }
    }
}
