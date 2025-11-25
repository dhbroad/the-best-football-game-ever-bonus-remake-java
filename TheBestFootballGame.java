import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

public class TheBestFootballGame extends JPanel implements KeyListener {

    // --- Constants ---
    private static final int TILE_SIZE = 30; // Slightly smaller to fit 42 units on screen
    private static final int GRID_W = 42;    // 0=Endzone, 1-40=Field, 41=Endzone
    private static final int GRID_H = 7;     
    private static final int PANEL_W = GRID_W * TILE_SIZE;
    private static final int PANEL_H = GRID_H * TILE_SIZE;
    private static final int TURN_DELAY = 500; // 0.5 seconds
    
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
    private BufferedImage imgGrass, imgEndzoneLeft, imgEndzoneRight;
    
    public TheBestFootballGame() {
        // Adjust window size to fit the wide field
        setPreferredSize(new Dimension(PANEL_W, PANEL_H));
        setFocusable(true);
        addKeyListener(this);
        
        // Generate detailed assets
        generateAssets();
        
        // Defender Timer
        defenderTimer = new Timer(TURN_DELAY, e -> tickDefenders());
        
        initGame();
    }
    
    // --- Procedural Asset Generation (Matches Video Style) ---
    private void generateAssets() {
        // 1. Textured Grass (Noise)
        imgGrass = new BufferedImage(PANEL_W, PANEL_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gBg = imgGrass.createGraphics();
        gBg.setColor(new Color(34, 139, 34)); // Base Green
        gBg.fillRect(0, 0, PANEL_W, PANEL_H);
        Random r = new Random();
        for(int i=0; i<PANEL_W * PANEL_H / 2; i++) {
            int noise = r.nextInt(20);
            gBg.setColor(new Color(34 + noise, 139 + noise, 34 + noise)); // Slight variation
            gBg.fillRect(r.nextInt(PANEL_W), r.nextInt(PANEL_H), 2, 2);
        }
        gBg.dispose();

        // 2. Endzones (Checkered Pattern)
        imgEndzoneLeft = createEndzoneTexture(Color.BLUE);
        imgEndzoneRight = createEndzoneTexture(Color.RED);

        // 3. Player (Blue Jersey, Gold Helmet, White Pants)
        imgPlayer = createHumanSprite(new Color(0, 0, 200), new Color(255, 215, 0));

        // 4. Defender (Red Jersey, Red Helmet, White Pants)
        imgDefender = createHumanSprite(new Color(200, 0, 0), new Color(200, 0, 0));

        // 5. Referee (Black/White Stripes)
        imgReferee = createRefereeSprite();

        // 6. Knocked Defender (Flattened)
        imgKnockedDefender = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imgKnockedDefender.createGraphics();
        g.setColor(new Color(150, 0, 0));
        g.fillOval(5, 10, TILE_SIZE-10, 10); // Squashed blob
        g.dispose();
    }

    private BufferedImage createEndzoneTexture(Color c) {
        BufferedImage img = new BufferedImage(TILE_SIZE, PANEL_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, TILE_SIZE, PANEL_H);
        g.setColor(new Color(255,255,255, 50)); // Semi-transparent checks
        for(int y=0; y<PANEL_H; y+=10) {
            if((y/10)%2==0) g.fillRect(0, y, TILE_SIZE/2, 10);
            else g.fillRect(TILE_SIZE/2, y, TILE_SIZE/2, 10);
        }
        g.dispose();
        return img;
    }

    private BufferedImage createHumanSprite(Color jersey, Color helmet) {
        BufferedImage img = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Pants (White)
        g.setColor(Color.WHITE);
        g.fillRect(10, 20, 4, 8); // Left leg
        g.fillRect(16, 20, 4, 8); // Right leg
        // Jersey (Body)
        g.setColor(jersey);
        g.fillRect(6, 10, 18, 10); // Torso
        g.fillOval(4, 10, 6, 6);   // Left Shoulder
        g.fillOval(20, 10, 6, 6);  // Right Shoulder
        // Helmet (Head)
        g.setColor(helmet);
        g.fillOval(8, 2, 14, 12);
        g.dispose();
        return img;
    }

    private BufferedImage createRefereeSprite() {
        BufferedImage img = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Pants (Black)
        g.setColor(Color.BLACK);
        g.fillRect(10, 20, 4, 8);
        g.fillRect(16, 20, 4, 8);
        // Shirt (White)
        g.setColor(Color.WHITE);
        g.fillRect(6, 10, 18, 10);
        // Stripes (Black)
        g.setColor(Color.BLACK);
        g.fillRect(9, 10, 2, 10);
        g.fillRect(14, 10, 2, 10);
        g.fillRect(19, 10, 2, 10);
        // Head
        g.setColor(new Color(255, 220, 177)); // Skin tone
        g.fillOval(10, 4, 10, 10);
        g.setColor(Color.WHITE); // Hat
        g.fillRect(9, 2, 12, 4);
        g.dispose();
        return img;
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
        
        // Reset Player (Left Field Boundary)
        player = new Player(1, 3); 
        
        defenders = new ArrayList<>();
        referees = new ArrayList<>();
        
        // Difficulty Calculation
        int defenderCount = 5 + (level * 2);
        int refereeCount = Math.max(1, defenderCount / 15);
        
        Random rand = new Random();
        
        // Spawn Defenders (Randomly on field 5-40)
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
    
    // --- AI Turn ---
    private void tickDefenders() {
        if (!isRunning || isGameOver) return;
        if (System.currentTimeMillis() - gameStartTime < 500) return;

        Random rand = new Random();

        for (Defender d : defenders) {
            if (d.isKnockedDown) continue; 

            // 50% Chance to stay still
            if (rand.nextDouble() < 0.5) continue;

            // Random Cardinal Move
            int dx = 0, dy = 0;
            if (rand.nextBoolean()) dx = rand.nextBoolean() ? 1 : -1;
            else dy = rand.nextBoolean() ? 1 : -1;

            int targetX = d.x + dx;
            int targetY = d.y + dy;

            // Bounds Check
            if (targetX < 0 || targetX >= GRID_W || targetY < 0 || targetY >= GRID_H) continue;

            // Collision Check
            if (!isOccupied(targetX, targetY)) {
                d.x = targetX;
                d.y = targetY;
            }
            
            // TACKLE CHECK: If defender moves into player's square
            if (d.x == player.x && d.y == player.y) {
                gameOver();
                return;
            }
        }
        
        // Move Referees (Lower chance to move)
        for (Referee r : referees) {
            if (rand.nextDouble() > 0.3) continue; // 70% stay
            
            int rx = 0, ry = 0;
            if (rand.nextBoolean()) rx = rand.nextBoolean() ? 1 : -1;
            else ry = rand.nextBoolean() ? 1 : -1;
            
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

    // --- Player Input ---
    @Override
    public void keyPressed(KeyEvent e) {
        if (!isRunning) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                if (isGameOver) initGame(); 
                else startLevel();
            }
            return;
        }

        int dx = 0, dy = 0;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP: dy = -1; break;
            case KeyEvent.VK_DOWN: dy = 1; break;
            case KeyEvent.VK_LEFT: dx = -1; break;
            case KeyEvent.VK_RIGHT: dx = 1; break;
        }

        if (dx != 0 || dy != 0) movePlayer(dx, dy);
    }

    private void movePlayer(int dx, int dy) {
        int targetX = player.x + dx;
        int targetY = player.y + dy;

        if (targetX < 0 || targetX >= GRID_W || targetY < 0 || targetY >= GRID_H) return;

        // 1. Check Referee
        for (Referee r : referees) {
            if (r.x == targetX && r.y == targetY) return; 
        }

        // 2. Check Defender (Collision or Knockdown)
        Defender defenderAtTarget = null;
        for (Defender d : defenders) {
            if (!d.isKnockedDown && d.x == targetX && d.y == targetY) {
                defenderAtTarget = d;
                break;
            }
        }

        if (defenderAtTarget != null) {
            // Knockdown Rule: Must have space behind
            int behindX = targetX + dx;
            int behindY = targetY + dy;
            
            boolean canKnockDown = true;
            if (behindX < 0 || behindX >= GRID_W || behindY < 0 || behindY >= GRID_H) canKnockDown = false; 
            else if (isOccupied(behindX, behindY)) canKnockDown = false;

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

        // Scoring (Right Endzone is index 41)
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

        // Draw Textured Grass
        g.drawImage(imgGrass, 0, 0, null);

        // Draw Endzones
        g.drawImage(imgEndzoneLeft, 0, 0, null);
        g.drawImage(imgEndzoneRight, PANEL_W - TILE_SIZE, 0, null);
        
        // Draw Field Lines (White yard lines every 5 units)
        g.setColor(new Color(255, 255, 255, 150));
        for (int i = 1; i < GRID_W - 1; i++) {
            if (i % 5 == 0) { // Every 5 yards
                g.fillRect(i * TILE_SIZE - 1, 0, 3, PANEL_H);
            }
        }

        if (player == null) return;

        // Draw Entities (Layered)
        // 1. Knocked Defenders
        for (Defender d : defenders) {
            if (d.isKnockedDown) g.drawImage(imgKnockedDefender, d.x * TILE_SIZE, d.y * TILE_SIZE, null);
        }

        // 2. Player
        g.drawImage(imgPlayer, player.x * TILE_SIZE, player.y * TILE_SIZE, null);

        // 3. Active Defenders & Refs
        for (Defender d : defenders) {
            if (!d.isKnockedDown) g.drawImage(imgDefender, d.x * TILE_SIZE, d.y * TILE_SIZE, null);
        }
        for (Referee r : referees) {
            g.drawImage(imgReferee, r.x * TILE_SIZE, r.y * TILE_SIZE, null);
        }
        
        // UI
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("SCORE: " + score, PANEL_W/2 - 40, 25);
        g.drawString("LEVEL: " + level, PANEL_W/2 - 40, PANEL_H - 10);
        
        if (isGameOver) {
            g.setColor(new Color(0,0,0,180));
            g.fillRect(0, 0, PANEL_W, PANEL_H);
            g.setColor(Color.RED);
            g.setFont(new Font("SansSerif", Font.BOLD, 60));
            String msg = "GAME OVER";
            g.drawString(msg, PANEL_W/2 - g.getFontMetrics().stringWidth(msg)/2, PANEL_H/2);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 25));
            String sub = "Press SPACE to Restart";
            g.drawString(sub, PANEL_W/2 - g.getFontMetrics().stringWidth(sub)/2, PANEL_H/2 + 50);
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("The Best Football Game");
        TheBestFootballGame game = new TheBestFootballGame();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // Interfaces
    public void keyReleased(KeyEvent e) {}
    public void keyTyped(KeyEvent e) {}

    // Entity Classes
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
}