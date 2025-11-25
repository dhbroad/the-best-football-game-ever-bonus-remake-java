import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

// Removed "ActionListener" from the implements list
public class TheBestFootballGame extends JPanel implements KeyListener {

    // --- Constants ---
    private static final int TILE_SIZE = 40; // Size of one grid square in pixels
    private static final int GRID_W = 22;    // 22 units long (0=Endzone, 1-20=Field, 21=Endzone)
    private static final int GRID_H = 7;     // 7 units high
    private static final int PANEL_W = GRID_W * TILE_SIZE;
    private static final int PANEL_H = GRID_H * TILE_SIZE;
    
    // --- Game Settings ---
    private static final int TURN_DELAY = 500; // Defenders move every 0.5 seconds (500ms)
    
    // --- State ---
    private boolean isRunning = false;
    private boolean isGameOver = false;
    private int score = 0;
    private int level = 1;
    
    // Timers
    private Timer defenderTimer; // Handles the 0.5s tick for defenders
    private long gameStartTime;  // To handle the 0.5s initial delay
    
    // Grid & Entities
    private Player player;
    private ArrayList<Defender> defenders;
    private ArrayList<Referee> referees;
    
    // Assets
    private BufferedImage imgPlayer, imgDefender, imgReferee, imgKnockedDefender;
    
    public TheBestFootballGame() {
        setPreferredSize(new Dimension(PANEL_W, PANEL_H));
        setBackground(new Color(34, 139, 34)); // Fallback green
        setFocusable(true);
        addKeyListener(this);
        
        // Generate Assets Programmatically
        generateAssets();
        
        // Defender Timer: Ticks every 0.5s using a Lambda expression instead of the class instance
        defenderTimer = new Timer(TURN_DELAY, e -> tickDefenders());
        
        initGame();
    }
    
    // --- Asset Generation (Procedural Sprites) ---
    private void generateAssets() {
        // Player: Blue Helmet/Jersey
        imgPlayer = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imgPlayer.createGraphics();
        g.setColor(Color.BLUE);
        g.fillOval(5, 5, TILE_SIZE-10, TILE_SIZE-10);
        g.setColor(Color.WHITE); // Helmet stripe
        g.fillRect(18, 5, 4, TILE_SIZE-10); 
        g.dispose();

        // Defender: Red Blocky Jersey
        imgDefender = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        g = imgDefender.createGraphics();
        g.setColor(new Color(200, 0, 0)); // Dark Red
        g.fillRect(5, 5, TILE_SIZE-10, TILE_SIZE-10);
        g.setColor(Color.BLACK); // Pads
        g.fillRect(4, 10, TILE_SIZE-8, 6);
        g.dispose();

        // Knocked Defender: Flattened/Darker
        imgKnockedDefender = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        g = imgKnockedDefender.createGraphics();
        g.setColor(new Color(100, 0, 0)); // Darker Red
        g.fillRect(5, 15, TILE_SIZE-10, 10); // Squashed
        g.dispose();

        // Referee: Black/White Stripes
        imgReferee = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        g = imgReferee.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(5, 5, TILE_SIZE-10, TILE_SIZE-10);
        g.setColor(Color.BLACK);
        for(int i=5; i<35; i+=6) {
            g.fillRect(i, 5, 3, TILE_SIZE-10); // Stripes
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
        
        // Reset Player (Start at Left Endzone/Field boundary)
        player = new Player(1, 3); // x=1, y=3 (Center-Left)
        
        // Spawn Defenders & Refs based on Level
        defenders = new ArrayList<>();
        referees = new ArrayList<>();
        
        // Difficulty scaling
        int defenderCount = 5 + (level * 3);
        int refereeCount = Math.max(1, defenderCount / 15);
        
        Random rand = new Random();
        
        // Spawn Defenders
        for (int i = 0; i < defenderCount; i++) {
            int dx, dy;
            do {
                dx = 5 + rand.nextInt(GRID_W - 6); // Spawn deeper in field
                dy = rand.nextInt(GRID_H);
            } while (isOccupied(dx, dy)); // Don't overlap spawn
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

    // Helper: Check if a coordinate is occupied by any LIVING entity (Defender or Ref)
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
    
    // --- Defender AI (The Tick) ---
    private void tickDefenders() {
        if (!isRunning || isGameOver) return;
        
        // Note #3: Start moving after 0.5s delay from game start
        if (System.currentTimeMillis() - gameStartTime < 500) return;

        Random rand = new Random();

        // Move Defenders
        for (Defender d : defenders) {
            if (d.isKnockedDown) continue; // Knocked defenders don't move

            // Simple AI: Move towards player or random
            int dx = 0; 
            int dy = 0;

            // 60% chance to track player
            if (rand.nextDouble() < 0.6) {
                if (player.x < d.x) dx = -1;
                else if (player.x > d.x) dx = 1;
                
                if (player.y < d.y) dy = -1;
                else if (player.y > d.y) dy = 1;
                
                // Choose only one axis to move (Cardinal)
                if (dx != 0 && dy != 0) {
                    if (rand.nextBoolean()) dx = 0; else dy = 0;
                }
            } else if (rand.nextDouble() < 0.5) {
                // Random move
                if (rand.nextBoolean()) dx = rand.nextBoolean() ? 1 : -1;
                else dy = rand.nextBoolean() ? 1 : -1;
            }
            // Else stay (dx=0, dy=0)

            int targetX = d.x + dx;
            int targetY = d.y + dy;

            // Check Bounds
            if (targetX < 0 || targetX >= GRID_W || targetY < 0 || targetY >= GRID_H) continue;

            // Check Collision with other mobs (Note #2: Cannot overlap)
            if (!isOccupied(targetX, targetY)) {
                d.x = targetX;
                d.y = targetY;
            }
            
            // Note #5: If defender moves into player space -> TACKLED
            if (d.x == player.x && d.y == player.y) {
                gameOver();
                return;
            }
        }
        
        // Move Referees (Same logic, but they don't tackle)
        for (Referee r : referees) {
             // Random patrol logic
            int rx = 0, ry = 0;
            if (rand.nextDouble() < 0.7) { // Move
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

    // --- Player Input Handling ---
    @Override
    public void keyPressed(KeyEvent e) {
        if (!isRunning) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                if (isGameOver) initGame(); // Restart
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

    private void movePlayer(int dx, int dy) {
        int targetX = player.x + dx;
        int targetY = player.y + dy;

        // Bounds Check
        if (targetX < 0 || targetX >= GRID_W || targetY < 0 || targetY >= GRID_H) return;

        // Check collisions
        // 1. Check for Referee (Immovable Object)
        for (Referee r : referees) {
            if (r.x == targetX && r.y == targetY) return; // Blocked
        }

        // 2. Check for Defender
        Defender defenderAtTarget = null;
        for (Defender d : defenders) {
            if (!d.isKnockedDown && d.x == targetX && d.y == targetY) {
                defenderAtTarget = d;
                break;
            }
        }

        if (defenderAtTarget != null) {
            // Note #4: Knock down logic
            // Check grid BEHIND the defender
            int behindX = targetX + dx;
            int behindY = targetY + dy;
            
            boolean canKnockDown = true;
            
            // Is behind out of bounds?
            if (behindX < 0 || behindX >= GRID_W || behindY < 0 || behindY >= GRID_H) {
                canKnockDown = false; 
            } 
            // Is there another entity behind?
            else if (isOccupied(behindX, behindY)) {
                canKnockDown = false;
            }

            if (canKnockDown) {
                // KNOCKDOWN!
                defenderAtTarget.isKnockedDown = true;
                // Player moves ON TOP of defender
                player.x = targetX;
                player.y = targetY;
            } else {
                // Blocked (Cannot knock down because someone is behind)
                return;
            }
        } else {
            // Empty space (or space with already knocked defender), just move
            player.x = targetX;
            player.y = targetY;
        }

        // Check Win Condition (Right Endzone)
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
        g.setColor(new Color(34, 139, 34)); // Classic Green
        g.fillRect(TILE_SIZE, 0, PANEL_W - (2 * TILE_SIZE), PANEL_H);

        // Draw Endzones
        g.setColor(new Color(200, 200, 200)); // Greyish/White for endzones
        g.fillRect(0, 0, TILE_SIZE, PANEL_H); // Left
        g.fillRect(PANEL_W - TILE_SIZE, 0, TILE_SIZE, PANEL_H); // Right
        
        // Draw Grid Lines
        g.setColor(new Color(0, 0, 0, 50));
        for (int i=0; i<=GRID_W; i++) g.drawLine(i*TILE_SIZE, 0, i*TILE_SIZE, PANEL_H);
        for (int i=0; i<=GRID_H; i++) g.drawLine(0, i*TILE_SIZE, PANEL_W, i*TILE_SIZE);

        if (player == null) return; // Not initialized

        // Draw Knocked Defenders (Layer: Bottom)
        for (Defender d : defenders) {
            if (d.isKnockedDown) {
                g.drawImage(imgKnockedDefender, d.x * TILE_SIZE, d.y * TILE_SIZE, null);
            }
        }

        // Draw Player (Layer: Middle)
        g.drawImage(imgPlayer, player.x * TILE_SIZE, player.y * TILE_SIZE, null);

        // Draw Active Defenders & Referees (Layer: Top)
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

    // --- Entity Classes ---
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

    // Unused Interface Methods
    public void keyReleased(KeyEvent e) {}
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("The Best Football Game - Grid Remake");
        TheBestFootballGame game = new TheBestFootballGame();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}