import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class TheBestFootballGame extends JPanel implements KeyListener {

    // --- Grid & Dimensions ---
    private static final int TILE_SIZE = 48; 
    private static final int VIEW_W = 14;    
    private static final int VIEW_H = 7;     
    private static final int FIELD_W = 42;   
    
    private static final int SCOREBOARD_W = (int)(2.5 * TILE_SIZE);
    private static final int WINDOW_W = (VIEW_W * TILE_SIZE) + SCOREBOARD_W;
    private static final int WINDOW_H = VIEW_H * TILE_SIZE;

    // --- Game Constants ---
    private static final int TURN_DELAY = 500; 
    private static final int START_ATTEMPTS = 4;
    private static final int GAME_DURATION = 60; 

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
    private int cameraX = 0; 

    // Timers
    private Timer defenderTimer;
    private Timer gameClock;

    // Entities
    private Player player;
    private ArrayList<Defender> defenders;
    private ArrayList<Referee> referees;

    // --- Assets ---
    private BufferedImage imgPlayerRunLeft, imgPlayerStandLeft;
    private BufferedImage imgPlayerUpRightFoot, imgPlayerDownRightFoot;
    private BufferedImage imgDefenderRight, imgDefenderKnocked;
    private BufferedImage imgRefRight;
    private BufferedImage imgEndzoneRight, imgEndzoneLeft; 
    private BufferedImage imgTouchdown, imgScoreboard;
    
    // Sounds
    private Clip clipCheer, clipSeal, clipWhistle, clipStep, clipThud;

    public TheBestFootballGame() {
        setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));
        setBackground(new Color(34, 139, 34)); 
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

        initGameSession();
        preGameSequence();
    }

    // --- Asset Loading ---
    private void loadAssets() {
        try {
            imgPlayerRunLeft = loadImage("TBFGE - Player Running Left.png");
            imgPlayerStandLeft = loadImage("TBFGE - Player Standing Left.png");
            imgPlayerUpRightFoot = loadImage("TBFGE - Player Running Up - Right Foot Down.png");
            imgPlayerDownRightFoot = loadImage("TBFGE - Player Running Down - Right Foot Down.png");
            
            imgDefenderRight = loadImage("TBFGE - Defender Facing Right.png");
            imgDefenderKnocked = loadImage("TBFGE - Defender Knocked Down.png");
            imgRefRight = loadImage("TBFGE - Referee Facing Right.png");
            
            imgEndzoneRight = loadImage("TBFGE - Endzone Right.png");
            imgEndzoneLeft = flipImageHorizontally(imgEndzoneRight);
            
            imgTouchdown = loadImage("TBFGE - Touch Down.png");
            imgScoreboard = loadImage("TBFGE - Scoreboard Start.png");
            
        } catch (Exception e) {
            System.out.println("Error loading images: " + e.getMessage());
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
        if (src == null) return null;
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
        cameraX = FIELD_W - VIEW_W; 

        // Player Setup: Start in Right Endzone Innermost (Index 40)
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
        
        // Spawn Defenders (Between x=5 and x=38)
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

            // 60% Chance to Stay
            if (rand.nextDouble() < 0.6) continue; 

            int dx = 0, dy = 0;
            if (rand.nextBoolean()) dx = rand.nextBoolean() ? 1 : -1;
            else dy = rand.nextBoolean() ? 1 : -1;

            if (dx > 0) d.facingRight = true;
            if (dx < 0) d.facingRight = false;

            int tx = d.x + dx;
            int ty = d.y + dy;

            if (tx < 0 || tx >= FIELD_W || ty < 0 || ty >= VIEW_H) continue;

            if (!isOccupied(tx, ty)) {
                d.x = tx;
                d.y = ty;
            }
            
            // Tackle Check
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
        // --- Sprite Logic ---
        if (dx != 0) {
            player.facingLeft = (dx < 0);
            // Toggle between Run and Stand on side steps
            if (player.state == Player.State.RUN_SIDE) {
                player.state = Player.State.STAND;
            } else {
                player.state = Player.State.RUN_SIDE;
            }
        }
        
        if (dy < 0) { 
            player.state = Player.State.RUN_UP; 
            player.stepLeftFoot = !player.stepLeftFoot; // Toggle foot
        }
        if (dy > 0) { 
            player.state = Player.State.RUN_DOWN; 
            player.stepLeftFoot = !player.stepLeftFoot; // Toggle foot
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
            // Knockdown Logic
            int bx = tx + dx;
            int by = ty + dy;
            boolean canKnock = true;
            if (bx < 0 || bx >= FIELD_W || by < 0 || by >= VIEW_H) canKnock = false;
            else if (isOccupied(bx, by)) canKnock = false;

            if (canKnock) {
                targetDef.isKnockedDown = true;
                score++; 
                playSound(clipThud);
                player.x = tx; player.y = ty;
            } else {
                return; // Blocked
            }
        } else {
            player.x = tx; player.y = ty;
        }
        
        playSound(clipStep);
        updateCamera();

        if (player.x <= 1) {
            scoreTouchdown();
        }
        repaint();
    }
    
    // --- Camera Logic ---
    private void updateCamera() {
        // Direction is Right to Left.
        int playerScreenX = player.x - cameraX;
        
        // The "Lock" point is screen index 9.
        if (playerScreenX < 9) {
            if (cameraX > 0) {
                cameraX--;
            }
        }
        // If player moves right and goes off edge
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
        attempts = START_ATTEMPTS;
        isTouchdownAnim = true;
        isRunning = false;
        gameClock.stop();
        defenderTimer.stop();
        repaint();
        
        Timer t = new Timer(3000, e -> {
             resetField();
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
        
        // 1. Field
        drawField(g);
        
        // 2. Entities
        drawEntities(g);
        
        // 3. Scoreboard
        drawScoreboard(g);
        
        // 4. Overlays
        if (isTouchdownAnim) {
            if(imgTouchdown != null)
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
        for (int i = 0; i < VIEW_W; i++) {
            int gridX = cameraX + i;
            int drawX = i * TILE_SIZE;
            
            // Turf
            g.setColor(new Color(34, 139, 34));
            g.fillRect(drawX, 0, TILE_SIZE, WINDOW_H);
            
            // Lines
            g.setColor(new Color(255, 255, 255, 100));
            g.drawLine(drawX, 0, drawX, WINDOW_H);
            
            // Endzones
            if (gridX <= 1) { // Left
                if (imgEndzoneLeft != null) {
                    int srcX1 = (gridX == 0) ? 0 : imgEndzoneLeft.getWidth()/2;
                    int srcX2 = (gridX == 0) ? imgEndzoneLeft.getWidth()/2 : imgEndzoneLeft.getWidth();
                    g.drawImage(imgEndzoneLeft, drawX, 0, drawX + TILE_SIZE, WINDOW_H, 
                                srcX1, 0, srcX2, imgEndzoneLeft.getHeight(), null);
                } else {
                    g.setColor(Color.BLUE);
                    g.fillRect(drawX, 0, TILE_SIZE, WINDOW_H);
                }
            }
            if (gridX >= 40) { // Right
                if (imgEndzoneRight != null) {
                    int srcX1 = (gridX == 40) ? 0 : imgEndzoneRight.getWidth()/2;
                    int srcX2 = (gridX == 40) ? imgEndzoneRight.getWidth()/2 : imgEndzoneRight.getWidth();
                    g.drawImage(imgEndzoneRight, drawX, 0, drawX + TILE_SIZE, WINDOW_H, 
                                srcX1, 0, srcX2, imgEndzoneRight.getHeight(), null);
                } else {
                    g.setColor(Color.RED);
                    g.fillRect(drawX, 0, TILE_SIZE, WINDOW_H);
                }
            }
        }
    }

    private void drawEntities(Graphics g) {
        int offX = -cameraX * TILE_SIZE;

        // 1. Knocked Defenders
        for (Defender d : defenders) {
            if (d.isKnockedDown) {
                 drawSprite(g, imgDefenderKnocked, d.x, d.y, offX, false);
            }
        }

        // 2. Player
        BufferedImage sprite = imgPlayerStandLeft;
        boolean flip = !player.facingLeft; 
        
        if (player.state == Player.State.RUN_SIDE) sprite = imgPlayerRunLeft;
        else if (player.state == Player.State.RUN_UP) {
            sprite = player.stepLeftFoot ? flipImageHorizontally(imgPlayerUpRightFoot) : imgPlayerUpRightFoot;
            flip = false; 
        }
        else if (player.state == Player.State.RUN_DOWN) {
            sprite = player.stepLeftFoot ? flipImageHorizontally(imgPlayerDownRightFoot) : imgPlayerDownRightFoot;
            flip = false;
        }
        else {
            sprite = imgPlayerStandLeft;
        }
        drawSprite(g, sprite, player.x, player.y, offX, flip);

        // 3. Active Defenders
        for (Defender d : defenders) {
            if (!d.isKnockedDown) {
                drawSprite(g, imgDefenderRight, d.x, d.y, offX, !d.facingRight);
            }
        }

        // 4. Refs
        for (Referee r : referees) {
            drawSprite(g, imgRefRight, r.x, r.y, offX, !r.facingRight);
        }
    }
    
    private void drawSprite(Graphics g, BufferedImage img, int gridX, int gridY, int offsetX, boolean flipHorizontal) {
        if(img == null) return;
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
        g.setColor(Color.BLACK);
        g.fillRect(x, 0, SCOREBOARD_W, WINDOW_H);
        
        if (imgScoreboard != null) {
            g.drawImage(imgScoreboard, x, 0, SCOREBOARD_W, WINDOW_H, null);
        }
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Impact", Font.PLAIN, 20));
        
        drawCenteredText(g, String.valueOf(timeRemaining), x + SCOREBOARD_W/2, 80);
        drawCenteredText(g, String.valueOf(score), x + SCOREBOARD_W/2, 150);
        
        int yards = Math.max(0, player.x * 5);
        drawCenteredText(g, String.valueOf(yards), x + SCOREBOARD_W/2, 225);
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
    public void keyReleased(KeyEvent e) {}
    public void keyTyped(KeyEvent e) {}

    class Player {
        int x, y;
        boolean facingLeft = true;
        boolean stepLeftFoot = false; 
        State state = State.RUN_SIDE; // Start running
        
        enum State { STAND, RUN_SIDE, RUN_UP, RUN_DOWN }

        Player(int x, int y) { this.x = x; this.y = y; }
    }

    class Defender {
        int x, y;
        boolean isKnockedDown = false;
        boolean facingRight = false; 
        Defender(int x, int y) { this.x = x; this.y = y; }
    }

    class Referee {
        int x, y;
        boolean facingRight = true;
        Referee(int x, int y) { this.x = x; this.y = y; }
    }
}