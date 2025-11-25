import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class TheBestFootballGame extends JPanel implements ActionListener, KeyListener {

    // Game Constants
    private static final int WIDTH = 800;
    private static final int HEIGHT = 400;
    private static final int PLAYER_SIZE = 20;
    private static final int DEFENDER_SIZE = 20;
    private static final int GAME_DURATION = 60; // Seconds

    // Game State
    private Timer timer;
    private boolean isRunning = false;
    private boolean isGameOver = false;
    private int score = 0;
    private int timeRemaining;
    private int level = 1;
    private Color fieldColor;

    // Entities
    private Rectangle player;
    private ArrayList<Rectangle> defenders;
    private int playerSpeed = 5;
    private int defenderSpeed = 3;

    // Inputs
    private boolean up, down, left, right;

    public TheBestFootballGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        timer = new Timer(16, this); // ~60 FPS
        initGame();
    }

    private void initGame() {
        score = 0;
        level = 1;
        timeRemaining = GAME_DURATION * 60; // frames
        isRunning = false;
        isGameOver = false;
        fieldColor = new Color(34, 139, 34); // Classic Green
        resetField();
    }

    private void resetField() {
        // Start player on the left
        player = new Rectangle(50, HEIGHT / 2 - PLAYER_SIZE / 2, PLAYER_SIZE, PLAYER_SIZE);
        
        // Spawn defenders based on level
        defenders = new ArrayList<>();
        Random rand = new Random();
        int defenderCount = 3 + (level * 2); // Increase difficulty

        for (int i = 0; i < defenderCount; i++) {
            // Defenders spawn on the right half of the field
            int dx = WIDTH/2 + rand.nextInt(WIDTH/2 - 50);
            int dy = rand.nextInt(HEIGHT - DEFENDER_SIZE);
            defenders.add(new Rectangle(dx, dy, DEFENDER_SIZE, DEFENDER_SIZE));
        }
        
        // Change field color per level (reference to the "shades of blue" memory, mostly green/blue variants)
        if (level > 1) {
             int r = Math.max(0, 34 - (level * 5));
             int g = Math.max(50, 139 - (level * 10));
             int b = Math.min(255, 34 + (level * 20));
             fieldColor = new Color(r, g, b);
        } else {
            fieldColor = new Color(34, 139, 34);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isRunning && !isGameOver) {
            update();
        }
        repaint();
    }

    private void update() {
        // 1. Timer
        timeRemaining--;
        if (timeRemaining <= 0) {
            isGameOver = true;
            return;
        }

        // 2. Player Movement
        if (up && player.y > 0) player.y -= playerSpeed;
        if (down && player.y < HEIGHT - PLAYER_SIZE) player.y += playerSpeed;
        if (left && player.x > 0) player.x -= playerSpeed;
        if (right && player.x < WIDTH - PLAYER_SIZE) player.x += playerSpeed;

        // 3. Defender Logic (Simple AI: Patrol + Chase)
        for (Rectangle defender : defenders) {
            // Move towards the left (kickoff team running down field)
            defender.x -= defenderSpeed;

            // Slight tracking of player Y
            if (defender.y < player.y && Math.random() > 0.8) defender.y++;
            if (defender.y > player.y && Math.random() > 0.8) defender.y--;

            // Loop defenders to right if they go off screen (simulate continuous field or waves)
            // Alternatively, for this specific game, they might just clear out. 
            // We will wrap them to keep difficulty high.
            if (defender.x < 0) {
                defender.x = WIDTH + new Random().nextInt(200);
                defender.y = new Random().nextInt(HEIGHT - DEFENDER_SIZE);
            }

            // 4. Collision Detection (Tackle)
            if (player.intersects(defender)) {
                // Tackled! Reset to start of level (Touchback)
                resetField(); 
                // Optional: Penalty time?
                // timeRemaining -= 60; 
                break; // Stop processing this frame
            }
        }

        // 5. Scoring (Touchdown)
        if (player.x >= WIDTH - PLAYER_SIZE - 10) {
            score++;
            level++;
            // Bonus time for scoring?
            // timeRemaining += 100; 
            resetField();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw Field
        g.setColor(fieldColor);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Draw Endzones
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, HEIGHT); // Left Endzone
        g.fillRect(WIDTH - 40, 0, 40, HEIGHT); // Right Endzone (Goal)
        
        // Yard lines
        for(int i=1; i<10; i++) {
            g.drawLine(40 + (i * (WIDTH-80)/10), 0, 40 + (i * (WIDTH-80)/10), HEIGHT);
        }

        if (!isRunning) {
            drawMenu(g);
            return;
        }

        // Draw Player
        g.setColor(Color.BLUE);
        g.fillOval(player.x, player.y, PLAYER_SIZE, PLAYER_SIZE);
        g.setColor(Color.WHITE);
        g.drawOval(player.x, player.y, PLAYER_SIZE, PLAYER_SIZE); // Outline

        // Draw Defenders
        g.setColor(Color.RED);
        for (Rectangle defender : defenders) {
            g.fillRect(defender.x, defender.y, DEFENDER_SIZE, DEFENDER_SIZE);
        }

        // Draw UI
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("SCORE: " + score, 20, 30);
        g.drawString("TIME: " + (timeRemaining / 60), WIDTH - 120, 30);
        g.drawString("LEVEL: " + level, WIDTH / 2 - 40, 30);

        if (isGameOver) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            String msg = "GAME OVER";
            String scoreMsg = "Final Score: " + score;
            String restartMsg = "Press SPACE to Restart";
            g.drawString(msg, WIDTH / 2 - g.getFontMetrics().stringWidth(msg) / 2, HEIGHT / 2 - 40);
            g.drawString(scoreMsg, WIDTH / 2 - g.getFontMetrics().stringWidth(scoreMsg) / 2, HEIGHT / 2 + 10);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString(restartMsg, WIDTH / 2 - g.getFontMetrics().stringWidth(restartMsg) / 2, HEIGHT / 2 + 50);
        }
    }

    private void drawMenu(Graphics g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 36));
        String title = "THE BEST FOOTBALL GAME";
        g.drawString(title, WIDTH / 2 - g.getFontMetrics().stringWidth(title) / 2, HEIGHT / 3);

        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String instr1 = "Use ARROW KEYS to run.";
        String instr2 = "Avoid RED defenders.";
        String instr3 = "Reach the RIGHT ENDZONE to score.";
        String start = "Press SPACE to Start";

        g.drawString(instr1, WIDTH / 2 - g.getFontMetrics().stringWidth(instr1) / 2, HEIGHT / 2);
        g.drawString(instr2, WIDTH / 2 - g.getFontMetrics().stringWidth(instr2) / 2, HEIGHT / 2 + 30);
        g.drawString(instr3, WIDTH / 2 - g.getFontMetrics().stringWidth(instr3) / 2, HEIGHT / 2 + 60);
        
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(start, WIDTH / 2 - g.getFontMetrics().stringWidth(start) / 2, HEIGHT / 2 + 120);
    }

    // Input Handling
    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_SPACE) {
            if (!isRunning || isGameOver) {
                initGame();
                isRunning = true;
                timer.start();
            }
        }
        if (key == KeyEvent.VK_UP) up = true;
        if (key == KeyEvent.VK_DOWN) down = true;
        if (key == KeyEvent.VK_LEFT) left = true;
        if (key == KeyEvent.VK_RIGHT) right = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_UP) up = false;
        if (key == KeyEvent.VK_DOWN) down = false;
        if (key == KeyEvent.VK_LEFT) left = false;
        if (key == KeyEvent.VK_RIGHT) right = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("The Best Football Game (Reproduction)");
        TheBestFootballGame game = new TheBestFootballGame();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}