import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

// Main Class
public class TheBestFootballGame extends JPanel implements KeyListener {

    // --- Constants ---
    private static final int TILE_SIZE = 40; 
    private static final int GRID_W = 22;    
    private static final int GRID_H = 7;     
    private static final int PANEL_W = GRID_W * TILE_SIZE;
    private static final int PANEL_H = GRID_H * TILE_SIZE;
    private static final int TURN_DELAY = 500; 
    
    // --- State ---
    private boolean isRunning = false;
    private boolean isGameOver = false;
    private int score = 0;
    private int level = 1;
    
    // Timers
    private Timer defenderTimer; 
    private long gameStartTime;  
    
    // Entities
    private Player player;
    private ArrayList<Defender> defenders;
    private ArrayList<Referee> referees;
    
    // Assets
    private BufferedImage imgPlayer, imgDefender, imgReferee, imgKnockedDefender;
    
    // --- Constructor ---
    public TheBestFootballGame() {
        setPreferredSize(new Dimension(PANEL_W, PANEL_H));
        setBackground(new Color(34, 139, 34)); 
        setFocusable(true);
        addKeyListener(this); // connects the keyboard
        
        generateAssets();
        
        // Timer for defender movement (0.5s tick)
        defenderTimer = new Timer(TURN_DELAY, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tickDefenders();
            }
        });
        
        initGame();
    }
    
    // --- Asset Generation ---
    private void generateAssets() {
        // Player (Blue)
        imgPlayer = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imgPlayer.createGraphics();
        g.setColor(Color.BLUE);
        g.fillOval(5, 5, TILE_SIZE-10, TILE_SIZE-10);
        g.setColor(Color.WHITE); 
        g.fillRect(18, 5, 4, TILE_SIZE-10); 
        g.dispose();

        // Defender (Red Block)
        imgDefender = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        g = imgDefender.createGraphics();
        g.setColor(new Color(200, 0, 0)); 
        g.fillRect(5, 5, TILE_SIZE-10, TILE_SIZE-10);
        g.setColor(Color.BLACK); 
        g.fillRect(4, 10, TILE_SIZE-8, 6);
        g.dispose();

        // Knocked Defender (Squashed)
        imgKnockedDefender = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        g = imgKnockedDefender.createGraphics();
        g.setColor(new Color(100, 0, 0)); 
        g.fillRect(5, 15, TILE_SIZE-10, 10); 
        g.dispose();

        // Referee (Stripes)
        imgReferee = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        g = imgReferee.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(5, 5, TILE_SIZE-10, TILE_SIZE-10);
        g.setColor(Color.BLACK);
        for(int i=5; i<35; i+=6) {
            g.fillRect(i, 5, 3, TILE_SIZE-10); 
        }
        g.dispose();
    }

    // --- Game Logic ---
    private void initGame() {
        score = 0;
        level = 1;
        startLevel();
    }
    
    private void startLevel() {
        isRunning = true;
        isGameOver = false;
        
        // Reset Player to left side
        player = new Player(1, 3); 
        
        defenders = new ArrayList<>();
        referees = new ArrayList<>();
        
        int defenderCount = 5 + (level * 3);
        int refereeCount = Math.max(1, defenderCount / 15);
        
        Random rand = new Random();
        
        // Spawn Defenders
        for (int i = 0; i < defenderCount; i++) {
            int dx, dy;
            do {
                dx = 5 + rand.nextInt(GRID_W - 6); 
                dy = rand.nextInt(GRID_H);
            } while (isOccupied(dx, dy)); 
            defenders.add(new Defender(dx, dy));
        }
        
        // Spawn Referees
        for (int i = 0; i < refereeCount; i++) {
            int rx, ry;
            do {
                rx = 5 + rand.nextInt(GRID_W - 6);
                ry = rand.nextInt(GRID_H);
            } while (isOccupied(rx, ry));
            referees.add(new Referee(rx, ry));
        }
        
        gameStartTime = System.currentTimeMillis();
        defenderTimer.restart();
        repaint();
    }

    private boolean isOccupied(int x, int y) {
        if (player != null && player.x == x && player.y == y) return true;
        for (Defender d : defenders) {
            if (!d.isKnockedDown && d.x == x && d.y == y) return true;
        }
        for (Referee r : referees) {
            if (r.x == x && r.y == y) return true;
        }
        return false;
    }
    
    // --- The "Tick" (Defender Movement) ---
    private void tickDefenders() {
        if (!isRunning || isGameOver) return;
        
        // Wait 0.5s at start of level
        if (System.currentTimeMillis() - gameStartTime < 500) return;

        Random rand = new Random();

        // Move Defenders
        for (Defender d : defenders) {
            if (d.isKnockedDown) continue; 

            int dx = 0; 
            int dy = 0;

            // AI Logic
            if (rand.nextDouble() < 0.6) {
                if (player.x < d.x) dx = -1;
                else if (player.x > d.x) dx = 1;
                
                if (player.y < d.y) dy = -1;
                else if (player.y > d.y) dy = 1;
                
                if (dx != 0 && dy != 0) {
                    if (rand.nextBoolean()) dx = 0; else dy = 0;
                }
            } else if (rand.nextDouble() < 0.5) {
                if (rand.nextBoolean()) dx = rand.nextBoolean() ? 1 : -1;
                else dy = rand.nextBoolean() ? 1 : -1;
            }

            int targetX = d.x + dx;
            int targetY = d.y + dy;

            if (targetX < 0 || targetX >= GRID_W || targetY < 0 || targetY >= GRID_H) continue;

            if (!isOccupied(targetX, targetY)) {
                d.x = targetX;
                d.y = targetY;
            }
            
            // Check Tackle
            if (d.x == player.x && d.y == player.y) {
                gameOver();
                return;
            }
        }
        
        // Move Referees
        for (Referee r : referees) {
            int rx = 0, ry = 0;
            if (rand.nextDouble() < 0.7) { 
                if (rand.nextBoolean()) rx = rand.nextBoolean() ? 1 : -1;
                else ry = rand.nextBoolean() ? 1 : -1;
            }
            
            int tx = r.x + rx;
            int ty = r.y + ry;
            
            if (tx >= 0 && tx < GRID_W && ty >= 0 && ty < GRID_H) {
                 if (!isOccupied(tx, ty)) {
                     r.x = tx;
                     r.y = ty;
                 }
            }
        }
        repaint();
    }

    // --- Keyboard Input (KeyListener Implementation) ---
    @Override
    public void keyPressed(KeyEvent e) {
        if (!isRunning) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                if (isGameOver) initGame(); 
                else startLevel();
            }
            return;
        }

        int dx = 0;
        int dy = 0;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP: dy = -1; break;
            case KeyEvent.VK_DOWN: dy = 1; break;
            case KeyEvent.VK_LEFT: dx = -1; break;
            case KeyEvent.VK_RIGHT: dx = 1; break;
        }

        if (dx != 0 || dy != 0) {
            movePlayer(dx, dy);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) { 
        // Mandatory method for KeyListener, can be empty
    }

    @Override
    public void keyTyped(KeyEvent e) { 
        // Mandatory method for KeyListener, can be empty
    }

    // --- Player Movement Logic ---
    private void movePlayer(int dx, int dy) {
        int targetX = player.x + dx;
        int targetY = player.y + dy;

        if (targetX < 0 || targetX >= GRID_W || targetY < 0 || targetY >= GRID_H) return;

        // 1. Check Referee
        for (Referee r : referees) {
            if (r.x == targetX && r.y == targetY) return; 
        }

        // 2. Check Defender
        Defender defenderAtTarget = null;
        for (Defender d : defenders) {
            if (!d.isKnockedDown && d.x == targetX && d.y == targetY) {
                defenderAtTarget = d;
                break;
            }
        }

        if (defenderAtTarget != null) {
            // Knockdown Logic
            int behindX = targetX + dx;
            int behindY = targetY + dy;
            
            boolean canKnockDown = true;
            
            if (behindX < 0 || behindX >= GRID_W || behindY < 0 || behindY >= GRID_H) {
                canKnockDown = false; 
            } else if (isOccupied(behindX, behindY)) {
                canKnockDown = false;
            }

            if (canKnockDown) {
                defenderAtTarget.isKnockedDown = true;
                player.x = targetX;
                player.y = targetY;
            } else {
                return; // Blocked
            }
        } else {
            player.x = targetX;
            player.y = targetY;
        }

        // Score
        if (player.x == GRID_W - 1) {
            score++;
            level++;
            startLevel();
        }

        repaint();
    }

    private void gameOver() {
        isRunning = false;
        isGameOver = true;
        defenderTimer.stop();
        repaint();
    }

    // --- Rendering ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw Turf
        g.setColor(new Color(34, 139, 34)); 
        g.fillRect(TILE_SIZE, 0, PANEL_W - (2 * TILE_SIZE), PANEL_H);

        // Draw Endzones
        g.setColor(new Color(200, 200, 200)); 
        g.fillRect(0, 0, TILE_SIZE, PANEL_H); 
        g.fillRect(PANEL_W - TILE_SIZE, 0, TILE_SIZE, PANEL_H); 
        
        // Draw Grid Lines
        g.setColor(new Color(0, 0, 0, 50));
        for (int i=0; i<=GRID_W; i++) g.drawLine(i*TILE_SIZE, 0, i*TILE_SIZE, PANEL_H);
        for (int i=0; i<=GRID_H; i++) g.drawLine(0, i*TILE_SIZE, PANEL_W, i*TILE_SIZE);

        if (player == null) return;

        // Draw Knocked Defenders
        for (Defender d : defenders) {
            if (d.isKnockedDown) {
                g.drawImage(imgKnockedDefender, d.x * TILE_SIZE, d.y * TILE_SIZE, null);
            }
        }

        // Draw Player
        g.drawImage(imgPlayer, player.x * TILE_SIZE, player.y * TILE_SIZE, null);

        // Draw Defenders & Refs
        for (Defender d : defenders) {
            if (!d.isKnockedDown) {
                g.drawImage(imgDefender, d.x * TILE_SIZE, d.y * TILE_SIZE, null);
            }
        }
        for (Referee r : referees) {
            g.drawImage(imgReferee, r.x * TILE_SIZE, r.y * TILE_SIZE, null);
        }
        
        // UI
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("Score: " + score, 20, 30);
        g.drawString("Level: " + level, 150, 30);
        
        if (isGameOver) {
            g.setColor(new Color(0,0,0,180));
            g.fillRect(0, 0, PANEL_W, PANEL_H);
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 50));
            String msg = "GAME OVER";
            g.drawString(msg, PANEL_W/2 - g.getFontMetrics().stringWidth(msg)/2, PANEL_H/2);
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            String sub = "Press SPACE to Restart";
            g.drawString(sub, PANEL_W/2 - g.getFontMetrics().stringWidth(sub)/2, PANEL_H/2 + 50);
        }
    }

    // --- Main Method ---
    public static void main(String[] args) {
        JFrame frame = new JFrame("The Best Football Game - Grid Remake");
        TheBestFootballGame game = new TheBestFootballGame();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // --- Inner Classes (Entities) ---
    // These MUST be inside the TheBestFootballGame class brackets
    class Player {
        int x, y;
        Player(int x, int y) { this.x = x; this.y = y; }
    }
    
    class Defender {
        int x, y;
        boolean isKnockedDown = false;
        Defender(int x, int y) { this.x = x; this.y = y; }
    }
    
    class Referee {
        int x, y;
        Referee(int x, int y) { this.x = x; this.y = y; }
    }

} // End of class TheBestFootballGame