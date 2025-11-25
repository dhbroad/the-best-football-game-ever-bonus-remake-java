import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class TheBestFootballGame extends JPanel implements KeyListener {

    // --- Grid & Dimensions ---
    private static final int TILE_SIZE = 48; // Adjusted for visibility
    private static final int VIEW_W = 14;    // Visible grid width
    private static final int VIEW_H = 7;     // Visible grid height
    private static final int FIELD_W = 42;   // Total field length
    
    private static final int SCOREBOARD_W = (int)(2.5 * TILE_SIZE);
    private static final int WINDOW_W = (VIEW_W * TILE_SIZE) + SCOREBOARD_W;
    private static final int WINDOW_H = VIEW_H * TILE_SIZE;

    // --- Game Constants ---
    private static final int TURN_DELAY = 500; // Defenders move every 0.5s
    private static final int START_ATTEMPTS = 4;
    private static final int GAME_DURATION = 60; // Seconds

    // --- State ---
    private boolean isRunning = false;
    private boolean isGameOver = false;
    private boolean isTouchdownAnim = false;
    private boolean inPreGame = true;
    
    private int score = 0;
    private int attempts = START_ATTEMPTS;
    private int timeRemaining = GAME_DURATION;
    private int touchdowns = 0;
    
    // Camera
    private int cameraX = 0; // The grid index of the left-most visible column

    // Timers
    private Timer defenderTimer;
    private Timer gameClock;
    private Timer animationResetTimer; // For alternating feet logic

    // Entities
    private Player player;
    private ArrayList<Defender> defenders;
    private ArrayList<Referee> referees;

    // --- Assets ---
    private BufferedImage imgPlayerRunLeft, imgPlayerStandLeft;
    private BufferedImage imgPlayerUpRightFoot, imgPlayerDownRightFoot;
    private BufferedImage imgDefenderRight, imgDefenderKnocked;
    private BufferedImage imgRefRight;
    private BufferedImage imgEndzoneRight, imgEndzoneLeft; // Left is flipped Right
    private BufferedImage imgTouchdown, imgScoreboard;
    
    // Sounds
    private Clip clipCheer, clipSeal, clipWhistle, clipStep, clipThud;

    public TheBestFootballGame() {
        setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));
        setBackground(new Color(34, 139, 34)); // Grass Green
        setFocusable(true);
        addKeyListener(this);

        loadAssets();
        loadSounds();

        // Logic Timers
        defenderTimer = new Timer(TURN_DELAY, e -> tickDefenders());
        gameClock = new Timer(1000, e -> {
            if (isRunning && timeRemaining > 0) {
                timeRemaining--;
                if (timeRemaining <= 0) gameOver("TIME'S UP!");
            }
        });

        // Reset animation state to "standing" after a delay if needed
        animationResetTimer = new Timer(200, e -> {
            // Optional: Reset to standing frame if idle?
            // Keeping simple for now based on specific sprite logic
        });

        initGameSession();
        // Start pre-game sequence
        preGameSequence();
    }

    // --- Asset Loading ---
    private void loadAssets() {
        try {
            // Load uploaded assets
            imgPlayerRunLeft = loadImage("TBFGE - Player Running Left.png");
            imgPlayerStandLeft = loadImage("TBFGE - Player Standing Left.png");
            imgPlayerUpRightFoot = loadImage("TBFGE - Player Running Up - Right Foot Down.png");
            imgPlayerDownRightFoot = loadImage("TBFGE - Player Running Down - Right Foot Down.png");
            
            imgDefenderRight = loadImage("TBFGE - Defender Facing Right.png");
            imgDefenderKnocked = loadImage("TBFGE - Defender Knocked Down.png");
            imgRefRight = loadImage("TBFGE - Referee Facing Right.png");
            
            imgEndzoneRight = loadImage("TBFGE - Endzone Right.png");
            // Generate Left Endzone by flipping Right
            imgEndzoneLeft = flipImageHorizontally(imgEndzoneRight);
            
            imgTouchdown = loadImage("TBFGE - Touch Down.png");
            imgScoreboard = loadImage("TBFGE - Scoreboard Start.png");
            
        } catch (Exception e) {
            System.out.println("Error loading images: " + e.getMessage());
            System.out.println("Make sure image files are in the same folder as the Java file.");
        }
    }
    
    private BufferedImage loadImage(String name) {
        try {
            return ImageIO.read(new File(name));
        } catch (IOException e) {
            return createPlaceholder(name);
        }
    }
    
    private BufferedImage createPlaceholder(String name) {
        // Fallback if file missing
        BufferedImage img = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0,0,TILE_SIZE, TILE_SIZE);
        g.setColor(Color.BLACK);
        g.drawString("?", 10, 20);
        g.dispose();
        return img;
    }
    
    private BufferedImage flipImageHorizontally(BufferedImage src) {
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-src.getWidth(null), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(src, null);
    }

    private void loadSounds() {
        clipCheer = loadClip("cheer.wav");
        clipSeal = loadClip("seal.wav");
        clipWhistle = loadClip("whistle.wav");
        clipStep = loadClip("step.wav");
        clipThud = loadClip("thud.wav");
    }

    private Clip loadClip(String filename) {
        try {
            File f = new File(filename);
            if(!f.exists()) return null;
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(f);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            return clip;
        } catch (Exception e) {
            return null; 
        }
    }

    private void playSound(Clip clip) {
        if (clip == null) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    // --- Game Logic ---

    private void initGameSession() {
        score = 0;
        attempts = START_ATTEMPTS;
        touchdowns = 0;
        resetField();
    }

    private void resetField() {
        isRunning = false;
        isGameOver = false;
        isTouchdownAnim = false;
        timeRemaining = GAME_DURATION;
        
        // Camera Setup: Show Right Side (Field End)
        // Field width 42. View 14. Max CameraX = 42 - 14 = 28.
        cameraX = FIELD_W - VIEW_W; 

        // Player Setup: Start in Right Endzone Innermost (Index 40)
        // Endzone is 40, 41. Innermost is 40.
        player = new Player(40, 3); 
        
        spawnDefendersAndRefs();
        
        repaint();
    }
    
    private void preGameSequence() {
        inPreGame = true;
        new Thread(() -> {
            try {
                playSound(clipCheer);
                Thread.sleep(1000);
                playSound(clipSeal);
                Thread.sleep(600);
                playSound(clipSeal);
                Thread.sleep(1000);
                playSound(clipWhistle);
                startGame();
            } catch (InterruptedException e) {}
        }).start();
    }
    
    private void startGame() {
        inPreGame = false;
        isRunning = true;
        gameClock.start();
        defenderTimer.start();
        repaint();
    }

    private void spawnDefendersAndRefs() {
        defenders = new ArrayList<>();
        referees = new ArrayList<>();
        
        // Density Logic
        int defenderCount = Math.min(20, 7 + touchdowns);
        int refereeCount = Math.max(1, defenderCount / 5);
        
        Random rand = new Random();
        
        // Spawn Defenders (Between x=5 and x=38 to avoid instant death spawn)
        for (int i = 0; i < defenderCount; i++) {
            int dx, dy;
            do {
                dx = 5 + rand.nextInt(33); 
                dy = rand.nextInt(VIEW_H);
            } while (isOccupied(dx, dy)); 
            defenders.add(new Defender(dx, dy));
        }
        
        // Spawn Refs
        for (int i = 0; i < refereeCount; i++) {
            int rx, ry;
            do {
                rx = 5 + rand.nextInt(33);
                ry = rand.nextInt(VIEW_H);
            } while (isOccupied(rx, ry));
            referees.add(new Referee(rx, ry));
        }
    }

    private boolean isOccupied(int x, int y) {
        if (player != null && player.x == x && player.y == y) return true;
        for (Defender d : defenders) if (d.x == x && d.y == y) return true;
        for (Referee r : referees) if (r.x == x && r.y == y) return true;
        return false;
    }

    // --- Update Loop ---
    private void tickDefenders() {
        if (!isRunning || isGameOver) return;
        Random rand = new Random();

        for (Defender d : defenders) {
            if (d.isKnockedDown) continue;

            // Random movement (Cardinal or Stay)
            // slightly higher chance to stay? (Prompt #1)
            if (rand.nextDouble() < 0.6) continue; // 60% Stay

            int dx = 0, dy = 0;
            if (rand.nextBoolean()) dx = rand.nextBoolean() ? 1 : -1;
            else dy = rand.nextBoolean() ? 1 : -1;

            // Keep Facing Logic (Prompt #7 images)
            // Only face Left/Right. If moving up/down, keep last facing.
            if (dx > 0) d.facingRight = true;
            if (dx < 0) d.facingRight = false;

            int tx = d.x + dx;
            int ty = d.y + dy;

            if (tx < 0 || tx >= FIELD_W || ty < 0 || ty >= VIEW_H) continue;

            if (!isOccupied(tx, ty)) {
                d.x = tx;
                d.y = ty;
            }
            
            // Check Tackle (Prompt #2: Defender moves into standing player)
            if (d.x == player.x && d.y == player.y) {
                playerTackled();
                return;
            }
        }
        
        // Referees move
        for (Referee r : referees) {
            if (rand.nextDouble() < 0.7) continue; 
            int rx = 0, ry = 0;
            if (rand.nextBoolean()) rx = rand.nextBoolean() ? 1 : -1;
            else ry = rand.nextBoolean() ? 1 : -1;
            
            if (rx > 0) r.facingRight = true;
            if (rx < 0) r.facingRight = false;
            
            int tx = r.x + rx;
            int ty = r.y + ry;
            
            if (tx >= 0 && tx < FIELD_W && ty >= 0 && ty >= VIEW_H && !isOccupied(tx, ty)) {
                r.x = tx; r.y = ty;
            }
        }
        repaint();
    }

    // --- Input ---
    @Override
    public void keyPressed(KeyEvent e) {
        if (inPreGame) return;
        if (!isRunning && isGameOver) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                if (attempts > 0) resetField(); 
                else initGameSession();
                
                // Reset pause
                Timer t = new Timer(1000, ex -> {
                     playSound(clipWhistle);
                     startGame();
                });
                t.setRepeats(false);
                t.start();
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
        // Update Sprite State
        if (dx < 0) { player.facingLeft = true; player.state = Player.State.RUN_SIDE; }
        if (dx > 0) { player.facingLeft = false; player.state = Player.State.RUN_SIDE; }
        if (dy < 0) { 
            player.state = Player.State.RUN_UP; 
            player.stepLeftFoot = !player.stepLeftFoot; // Alternate
        }
        if (dy > 0) { 
            player.state = Player.State.RUN_DOWN; 
            player.stepLeftFoot = !player.stepLeftFoot; // Alternate
        }

        int tx = player.x + dx;
        int ty = player.y + dy;

        if (tx < 0 || tx >= FIELD_W || ty < 0 || ty >= VIEW_H) return;

        // Ref Collision
        for (Referee r : referees) if (r.x == tx && r.y == ty) return;

        // Defender Interaction
        Defender targetDef = null;
        for (Defender d : defenders) {
            if (!d.isKnockedDown && d.x == tx && d.y == ty) {
                targetDef = d; break;
            }
        }

        if (targetDef != null) {
            // Check Knockdown (Must have space behind)
            int bx = tx + dx;
            int by = ty + dy;
            boolean canKnock = true;
            if (bx < 0 || bx >= FIELD_W || by < 0 || by >= VIEW_H) canKnock = false;
            else if (isOccupied(bx, by)) canKnock = false;

            if (canKnock) {
                targetDef.isKnockedDown = true;
                score++; // 1 pt for knockdown
                playSound(clipThud);
                // Move player
                player.x = tx; player.y = ty;
            } else {
                // Blocked -> Game Over? Or just blocked?
                // Video implies you just can't move there. 
                // If you run INTO them without knockdown, usually in these games you get tackled.
                // But user said "Player can knock over... if running into them... as long as not another behind".
                // Implies if there IS one behind, you just fail to move or get tackled. Let's assume tackle if failed?
                // Actually safer to just block movement to avoid frustration unless specific instruction.
                return; 
            }
        } else {
            player.x = tx; player.y = ty;
        }
        
        playSound(clipStep);
        updateCamera();

        // Check Touchdown (Left Endzone indices 0 and 1. Innermost is 1)
        if (player.x <= 1) {
            scoreTouchdown();
        }
        repaint();
    }
    
    // --- Camera Logic (Prompt #4) ---
    private void updateCamera() {
        // Direction is Right to Left.
        // Visible range: [cameraX, cameraX + 13]
        // Player Screen X = player.x - cameraX
        
        // "Stationary until player has reached 5th grid spot"
        // 5th spot from RIGHT of screen (indices 0..13) is index 9.
        // If player screen X moves < 9, camera moves left.
        
        int playerScreenX = player.x - cameraX;
        
        // The "Lock" point is screen index 9.
        // If player is at screen index 8 (6th spot from right), we shift camera left.
        if (playerScreenX < 9) {
            if (cameraX > 0) {
                cameraX--;
            }
        }
        // If player moves right and goes off edge?
        if (playerScreenX > 12 && cameraX < FIELD_W - VIEW_W) {
            cameraX++;
        }
    }

    private void playerTackled() {
        playSound(clipThud);
        attempts--;
        if (attempts <= 0) {
            gameOver("GAME OVER");
        } else {
            gameOver("TACKLED!");
        }
    }

    private void scoreTouchdown() {
        playSound(clipCheer);
        score += 7;
        touchdowns++;
        attempts = START_ATTEMPTS; // Reset attempts on TD? Usually yes in these arcade games.
        isTouchdownAnim = true;
        isRunning = false;
        gameClock.stop();
        defenderTimer.stop();
        repaint();
        
        // Delay then reset
        Timer t = new Timer(3000, e -> {
             resetField();
             // Pause before whistle
             Timer t2 = new Timer(1000, e2 -> {
                 playSound(clipWhistle);
                 startGame();
             });
             t2.setRepeats(false);
             t2.start();
        });
        t.setRepeats(false);
        t.start();
    }

    private void gameOver(String msg) {
        isRunning = false;
        isGameOver = true;
        gameClock.stop();
        defenderTimer.stop();
        repaint();
    }

    // --- Rendering ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // 1. Draw Field
        drawField(g);
        
        // 2. Draw Entities
        drawEntities(g);
        
        // 3. Draw Scoreboard (Right Side)
        drawScoreboard(g);
        
        // 4. Overlays
        if (isTouchdownAnim) {
            g.drawImage(imgTouchdown, (WINDOW_W - SCOREBOARD_W)/2 - imgTouchdown.getWidth()/2, WINDOW_H/2 - imgTouchdown.getHeight()/2, null);
        }
        
        if (isGameOver) {
            g.setColor(new Color(0,0,0,180));
            g.fillRect(0, 0, WINDOW_W - SCOREBOARD_W, WINDOW_H);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            String msg = attempts <= 0 ? "GAME OVER" : "TACKLED!";
            int w = g.getFontMetrics().stringWidth(msg);
            g.drawString(msg, (WINDOW_W - SCOREBOARD_W)/2 - w/2, WINDOW_H/2);
            
            g.setFont(new Font("Arial", Font.BOLD, 20));
            String sub = "Press SPACE";
            w = g.getFontMetrics().stringWidth(sub);
            g.drawString(sub, (WINDOW_W - SCOREBOARD_W)/2 - w/2, WINDOW_H/2 + 50);
        }
    }

    private void drawField(Graphics g) {
        // Visible range: cameraX to cameraX + VIEW_W
        for (int i = 0; i < VIEW_W; i++) {
            int gridX = cameraX + i;
            int drawX = i * TILE_SIZE;
            
            // Draw Turf
            g.setColor(new Color(34, 139, 34));
            g.fillRect(drawX, 0, TILE_SIZE, WINDOW_H);
            
            // Draw Yard Lines (Every unit, White)
            g.setColor(new Color(255, 255, 255, 100));
            g.drawLine(drawX, 0, drawX, WINDOW_H);
            
            // Draw Endzones
            if (gridX <= 1) { // Left Endzone
                if (imgEndzoneLeft != null) {
                    // Endzone image is likely 2 units wide or we tile it
                    // Draw slice based on gridX
                    if(gridX == 0) g.drawImage(imgEndzoneLeft, drawX, 0, TILE_SIZE, WINDOW_H, 0, 0, imgEndzoneLeft.getWidth()/2, imgEndzoneLeft.getHeight(), null);
                    if(gridX == 1) g.drawImage(imgEndzoneLeft, drawX, 0, TILE_SIZE, WINDOW_H, imgEndzoneLeft.getWidth()/2, 0, imgEndzoneLeft.getWidth(), imgEndzoneLeft.getHeight(), null);
                } else {
                    g.setColor(Color.BLUE);
                    g.fillRect(drawX, 0, TILE_SIZE, WINDOW_H);
                }
            }
            if (gridX >= 40) { // Right Endzone
                if (imgEndzoneRight != null) {
                    if(gridX == 40) g.drawImage(imgEndzoneRight, drawX, 0, TILE_SIZE, WINDOW_H, 0, 0, imgEndzoneRight.getWidth()/2, imgEndzoneRight.getHeight(), null);
                    if(gridX == 41) g.drawImage(imgEndzoneRight, drawX, 0, TILE_SIZE, WINDOW_H, imgEndzoneRight.getWidth()/2, 0, imgEndzoneRight.getWidth(), imgEndzoneRight.getHeight(), null);
                } else {
                    g.setColor(Color.RED);
                    g.fillRect(drawX, 0, TILE_SIZE, WINDOW_H);
                }
            }
        }
    }

    private void drawEntities(Graphics g) {
        // Offset x by -cameraX
        int offX = -cameraX * TILE_SIZE;

        // Knocked Defenders first (bottom layer)
        for (Defender d : defenders) {
            if (d.isKnockedDown) {
                 drawSprite(g, imgDefenderKnocked, d.x, d.y, offX, false);
            }
        }

        // Player
        BufferedImage sprite = imgPlayerStandLeft;
        boolean flip = !player.facingLeft; // Images are Left by default
        
        if (player.state == Player.State.RUN_SIDE) sprite = imgPlayerRunLeft;
        else if (player.state == Player.State.RUN_UP) {
            sprite = player.stepLeftFoot ? flipImageHorizontally(imgPlayerUpRightFoot) : imgPlayerUpRightFoot;
            flip = false; // Already handled flip
        }
        else if (player.state == Player.State.RUN_DOWN) {
            sprite = player.stepLeftFoot ? flipImageHorizontally(imgPlayerDownRightFoot) : imgPlayerDownRightFoot;
            flip = false;
        }
        else {
            // Standing
            sprite = imgPlayerStandLeft;
        }
        
        drawSprite(g, sprite, player.x, player.y, offX, flip);

        // Active Defenders
        for (Defender d : defenders) {
            if (!d.isKnockedDown) {
                // Logic: Defenders face Left or Right.
                // Image provided: "Defender Facing Right".
                // So if facingRight=true, use image. If false, flip.
                drawSprite(g, imgDefenderRight, d.x, d.y, offX, !d.facingRight);
            }
        }

        // Refs
        for (Referee r : referees) {
            drawSprite(g, imgRefRight, r.x, r.y, offX, !r.facingRight);
        }
    }
    
    private void drawSprite(Graphics g, BufferedImage img, int gridX, int gridY, int offsetX, boolean flipHorizontal) {
        // Check visibility
        if (gridX < cameraX || gridX >= cameraX + VIEW_W) return;
        
        int x = (gridX * TILE_SIZE) + offsetX;
        int y = gridY * TILE_SIZE;
        
        if (flipHorizontal) {
            g.drawImage(img, x + TILE_SIZE, y, -TILE_SIZE, TILE_SIZE, null);
        } else {
            g.drawImage(img, x, y, TILE_SIZE, TILE_SIZE, null);
        }
    }

    private void drawScoreboard(Graphics g) {
        int x = VIEW_W * TILE_SIZE;
        // Draw BG
        g.setColor(Color.BLACK);
        g.fillRect(x, 0, SCOREBOARD_W, WINDOW_H);
        
        if (imgScoreboard != null) {
            // Draw the template background
            g.drawImage(imgScoreboard, x, 0, SCOREBOARD_W, WINDOW_H, null);
        }
        
        // Draw Text Values
        g.setColor(Color.WHITE);
        g.setFont(new Font("Impact", Font.PLAIN, 20)); // Arcade style font
        
        // Coordinates are approximate based on the "Scoreboard Start.png" layout
        // Time Left
        drawCenteredText(g, String.valueOf(timeRemaining), x + SCOREBOARD_W/2, 80);
        // Score
        drawCenteredText(g, String.valueOf(score), x + SCOREBOARD_W/2, 150);
        // Yards To Go (Target is 0. Player is at player.x. 1 unit = 5 yards)
        int yards = Math.max(0, player.x * 5);
        drawCenteredText(g, String.valueOf(yards), x + SCOREBOARD_W/2, 225);
        // Attempts
        drawCenteredText(g, String.valueOf(attempts), x + SCOREBOARD_W/2, 300);
    }
    
    private void drawCenteredText(Graphics g, String text, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text);
        g.drawString(text, x - w/2, y);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("The Best Football Game");
            TheBestFootballGame game = new TheBestFootballGame();
            frame.add(game);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    // --- Inner Classes ---
    public void keyReleased(KeyEvent e) {
        // Reset to standing if key released? 
        // Usually in grid movement, state resets when movement finishes.
        // Keeping state until next move for visual clarity or adding an idle timer.
        // Using animationResetTimer (simple approach)
    }
    public void keyTyped(KeyEvent e) {}

    class Player {
        int x, y;
        boolean facingLeft = true;
        boolean stepLeftFoot = false; // For up/down anim
        State state = State.STAND;
        
        enum State { STAND, RUN_SIDE, RUN_UP, RUN_DOWN }

        Player(int x, int y) { this.x = x; this.y = y; }
    }

    class Defender {
        int x, y;
        boolean isKnockedDown = false;
        boolean facingRight = false; // Default to facing player (Left)
        Defender(int x, int y) { this.x = x; this.y = y; }
    }

    class Referee {
        int x, y;
        boolean facingRight = true;
        Referee(int x, int y) { this.x = x; this.y = y; }
    }
}