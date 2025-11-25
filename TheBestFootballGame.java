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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;

public class TheBestFootballGame extends JPanel implements KeyListener, MouseListener {

    // --- Grid & Dimensions ---
    private static final int TILE_SIZE = 48; 
    private static final int VIEW_W = 14;    
    private static final int VIEW_H = 7;     // Playable rows
    private static final int BORDER_H = 1;   // Sideline rows (top and bottom)
    
    // 2 EndzoneL + 40 Field + 2 EndzoneR = 44 total grid units
    private static final int GRID_W = 44; 
    private static final int FIELD_START_X = 2; // Index where the green field starts (Goal Line)
    private static final int FIELD_END_X = GRID_W - 2; // Index where the green field ends (42)
    
    private static final int SCOREBOARD_W = (int)(2.5 * TILE_SIZE);
    private static final int WINDOW_W = (VIEW_W * TILE_SIZE) + SCOREBOARD_W;
    // Total height = 7 Playable rows + 2 Border rows (9 total)
    private static final int WINDOW_H = (VIEW_H + 2 * BORDER_H) * TILE_SIZE;

    // --- Game Constants ---
    private static final int TURN_DELAY = 500; 
    private static final int START_ATTEMPTS = 4;
    private static final int GAME_DURATION = 60; 
    
    private static final Color FIELD_COLOR = new Color(3, 214, 73); 
    private static final Color SIDELINE_COLOR = new Color(255, 255, 255); // White for sidelines

    // --- State Management ---
    private enum GameState { MENU, READY, PLAYING, TOUCHDOWN, TACKLED, GAMEOVER }
    private GameState gameState = GameState.MENU;

    // --- Stats ---
    private int score = 0;
    private int attempts = START_ATTEMPTS;
    private int timeRemaining = GAME_DURATION;
    private int touchdowns = 0;
    private DecimalFormat df = new DecimalFormat("#.#");
    
    // --- Camera ---
    private int cameraX = 0; 

    // --- Timers ---
    private Timer defenderTimer;
    private Timer gameClock;
    private Timer tdBlinkTimer;
    
    // Animation State
    private boolean showTDSprite = false;
    private int tdBlinkCount = 0;

    // --- Entities ---
    private Player player;
    private ArrayList<Defender> defenders;
    private ArrayList<Referee> referees;

    // --- Assets ---
    private BufferedImage imgPlayerRunLeft, imgPlayerStandLeft;
    private BufferedImage imgPlayerUpRightFoot, imgPlayerDownRightFoot;
    private BufferedImage imgDefenderRight, imgDefenderKnocked;
    private BufferedImage imgTackle, imgMidfieldLogo;
    private BufferedImage imgRefRight;
    private BufferedImage imgEndzoneRight, imgEndzoneLeft; 
    private BufferedImage imgTouchdown, imgScoreboard;
    private BufferedImage imgGrassTexture; 
    
    // --- Sounds ---
    private Clip clipCheer, clipSeal, clipWhistle, clipStep, clipThud;

    public TheBestFootballGame() {
        setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));
        // Background color of the entire panel (including new margins/sidelines)
        setBackground(SIDELINE_COLOR); 
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);

        loadAssets();
        generateGrassTexture();
        loadSounds();

        defenderTimer = new Timer(TURN_DELAY, e -> tickDefenders());
        
        gameClock = new Timer(1000, e -> {
            if (gameState == GameState.PLAYING && timeRemaining > 0) {
                timeRemaining--;
                if (timeRemaining <= 0) gameOver("TIME'S UP!");
            }
            repaint();
        });

        // Touchdown Blink Timer (0.625s ON/OFF cycle)
        tdBlinkTimer = new Timer(625, e -> {
            tdBlinkCount++;
            showTDSprite = (tdBlinkCount % 2 != 0); 

            if (tdBlinkCount >= 8) { 
                tdBlinkTimer.stop();
                resetPlaySequence();
            }
            repaint();
        });

        initGameSession(); 
    }

    // --- Asset Loading & Generation ---
    
    private BufferedImage flipImageHorizontally(BufferedImage src) {
        if (src == null) return null;
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-src.getWidth(null), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(src, null);
    }

    private void generateGrassTexture() {
        BufferedImage tileTexture = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tileTexture.createGraphics();
        g.setColor(FIELD_COLOR);
        g.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
        
        // Clamp RGB values to prevent IllegalArgumentException (Fixed in previous step)
        int darkR = Math.max(0, FIELD_COLOR.getRed() - 5);
        int darkG = Math.max(0, FIELD_COLOR.getGreen() - 5);
        int darkB = Math.max(0, FIELD_COLOR.getBlue() - 5);
        
        Color darkerGreen = new Color(darkR, darkG, darkB, 80); 
        Random r = new Random();
        
        // Apply subtle noise/smudges for texture
        for(int x = 0; x < TILE_SIZE; x+=2) { 
            for(int y = 0; y < TILE_SIZE; y+=2) {
                if(r.nextDouble() < 0.2) { 
                    g.setColor(darkerGreen);
                    int smudgeSize = r.nextInt(3) + 1; 
                    g.fillRect(x, y, smudgeSize, smudgeSize);
                }
            }
        }
        g.dispose();

        // Create the full texture image by tiling the pattern
        imgGrassTexture = new BufferedImage(VIEW_W * TILE_SIZE, WINDOW_H - 2 * BORDER_H * TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D fullG = imgGrassTexture.createGraphics();
        
        for (int x = 0; x < VIEW_W; x++) {
            for (int y = 0; y < VIEW_H; y++) {
                fullG.drawImage(tileTexture, x * TILE_SIZE, y * TILE_SIZE, null);
            }
        }
        fullG.dispose();
    }
    
    private BufferedImage loadImage(String name) {
        try { return ImageIO.read(new File(name)); } 
        catch (IOException e) { return createPlaceholder(name); }
    }
    
    private BufferedImage createPlaceholder(String name) {
        BufferedImage img = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.MAGENTA); g.fillRect(0,0,TILE_SIZE, TILE_SIZE);
        g.dispose(); return img;
    }

    private void loadAssets() {
        try {
            imgPlayerRunLeft = loadImage("TBFGE - Player Running Left.png");
            imgPlayerStandLeft = loadImage("TBFGE - Player Standing Left.png");
            imgPlayerUpRightFoot = loadImage("TBFGE - Player Running Up - Right Foot Down.png");
            imgPlayerDownRightFoot = loadImage("TBFGE - Player Running Down - Right Foot Down.png");
            imgDefenderRight = loadImage("TBFGE - Defender Facing Right.png");
            imgDefenderKnocked = loadImage("TBFGE - Defender Knocked Down.png");
            imgTackle = loadImage("TBFGE - Defender Tackling Player.png");
            imgRefRight = loadImage("TBFGE - Referee Facing Right.png");
            imgEndzoneRight = loadImage("TBFGE - Endzone Right.png");
            imgEndzoneLeft = flipImageHorizontally(imgEndzoneRight);
            imgTouchdown = loadImage("TBFGE - Touch Down.png");
            imgScoreboard = loadImage("TBFGE - Scoreboard Start.png");
            imgMidfieldLogo = loadImage("TBFGE - Walrus Midfield Logo.png");
        } catch (Exception e) {
            System.out.println("Error loading images: " + e.getMessage());
        }
    }
    // ... (Sound methods loadClip, playSound) ... 
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
        } catch (Exception e) { return null; }
    }

    private void playSound(Clip clip) {
        if (clip == null) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }
    // ------------------------------------------

    // --- Game Sequences ---

    private void initGameSession() {
        score = 0;
        attempts = START_ATTEMPTS;
        touchdowns = 0;
        timeRemaining = GAME_DURATION; // Fix #3: Resetting time
        gameState = GameState.MENU;
    }

    private void prepareField() {
        // Player starts at index 42 (FIELD_END_X), the first green tile next to the right EZ.
        // Player starts in the middle playable row (3)
        player = new Player(FIELD_END_X, VIEW_H / 2); 
        // Camera starts locked to the right side of the field
        cameraX = GRID_W - VIEW_W; 
        
        spawnDefendersAndRefs();
        
        gameClock.stop();
        defenderTimer.stop();
        tdBlinkTimer.stop();
        
        repaint();
    }

    private void startFirstGameSequence() {
        prepareField();
        gameState = GameState.READY;
        playSound(clipCheer);
        Timer sealDelay = new Timer(1000, e -> playSound(clipSeal));
        sealDelay.setRepeats(false); sealDelay.start();
        startPlayAfterDelay(3000);
    }
    
    private void resetPlaySequence() {
        prepareField();
        gameState = GameState.READY;
        startPlayAfterDelay(3000);
    }
    
    private void startPlayAfterDelay(int delay) {
        Timer startTimer = new Timer(delay, e -> {
             playSound(clipWhistle);
             gameState = GameState.PLAYING;
             gameClock.start();
             defenderTimer.start();
             repaint();
        });
        startTimer.setRepeats(false); startTimer.start();
    }

    private void spawnDefendersAndRefs() {
        defenders = new ArrayList<>();
        referees = new ArrayList<>();
        
        // Calculate Defender Count based on scaling (Change #4)
        double fieldRatio = (double)GRID_W / VIEW_W; 
        int defendersPerView = Math.min(20, 10 + touchdowns * 2); 
        int totalDefenders = (int) Math.round(defendersPerView * fieldRatio);
        
        // Minimum spawning boundary (avoids endzones and immediate 1-tile borders)
        int minSpawnX = FIELD_START_X + 1; 
        int maxSpawnX = FIELD_END_X - 1; 
        
        Random rand = new Random();
        
        for (int i = 0; i < totalDefenders; i++) {
            int dx, dy;
            do {
                dx = minSpawnX + rand.nextInt(maxSpawnX - minSpawnX); 
                dy = rand.nextInt(VIEW_H);
            } while (isOccupied(dx, dy)); 
            defenders.add(new Defender(dx, dy));
        }
        
        // Calculate Referee Count (Change #4: 5 times less)
        int totalReferees = Math.max(1, totalDefenders / 5);
        for (int i = 0; i < totalReferees; i++) {
            int rx, ry;
            do {
                rx = minSpawnX + rand.nextInt(maxSpawnX - minSpawnX); 
                ry = rand.nextInt(VIEW_H);
            } while (isOccupied(rx, ry));
            referees.add(new Referee(rx, ry));
        }
    }

    // Checks for occupation within the playable grid (0 to GRID_W-1, 0 to VIEW_H-1)
    private boolean isOccupied(int x, int y) {
        if (player != null && player.x == x && player.y == y) return true;
        for (Defender d : defenders) if (!d.isKnockedDown && d.x == x && d.y == y) return true;
        for (Referee r : referees) if (r.x == x && r.y == y) return true;
        return false;
    }

    // --- Update Loop (AI) ---
    private void tickDefenders() {
        if (gameState != GameState.PLAYING) return;
        Random rand = new Random();

        // ------------------ DEFENDER MOVEMENT ------------------
        for (Defender d : defenders) {
            if (d.isKnockedDown) continue;

            int dx = 0, dy = 0;

            // Priority 1: Move towards adjacent player tile
            if (Math.abs(d.x - player.x) + Math.abs(d.y - player.y) == 1) {
                if (rand.nextDouble() < 0.8) {
                    dx = player.x - d.x;
                    dy = player.y - d.y;
                }
            }

            // Priority 2: Random movement
            if (dx == 0 && dy == 0) {
                if (rand.nextDouble() < 0.6) continue;
                if (rand.nextBoolean()) dx = rand.nextBoolean() ? 1 : -1;
                else dy = rand.nextBoolean() ? 1 : -1;
            }

            if (dx > 0) d.facingRight = true; else if (dx < 0) d.facingRight = false;

            int tx = d.x + dx;
            int ty = d.y + dy;

            // Check boundaries (Change #2: prevent moving into outer endzone columns or sidelines)
            if (tx <= 0 || tx >= GRID_W - 1 || ty < 0 || ty >= VIEW_H) continue;

            // Check for tackle on target move position (Change #6)
            if (tx == player.x && ty == player.y) {
                playerTackled();
                return;
            }

            // Move if the target square is not occupied by another entity
            if (!isOccupied(tx, ty)) {
                d.x = tx;
                d.y = ty;
            }
        }
        
        // ------------------ REFEREE MOVEMENT (Change #1) ------------------
        Random refRand = new Random();
        for (Referee r : referees) {
            if (refRand.nextDouble() < 0.7) continue; 
            
            int rx = (refRand.nextBoolean()) ? (refRand.nextBoolean() ? 1 : -1) : 0;
            int ry = (rx == 0) ? (refRand.nextBoolean() ? 1 : -1) : 0;
            
            if (rx > 0) r.facingRight = true; else if (rx < 0) r.facingRight = false;
            
            int tx = r.x + rx; int ty = r.y + ry;
            
            // Check boundaries (Change #2: prevent moving into outer endzone columns or sidelines)
            if (tx <= 0 || tx >= GRID_W - 1 || ty < 0 || ty >= VIEW_H) continue;
            
            // Move only if the target square is not occupied by another referee
            boolean refOccupied = false;
            for(Referee otherR : referees) {
                if(otherR != r && otherR.x == tx && otherR.y == ty) {
                    refOccupied = true;
                    break;
                }
            }
            if (!refOccupied) {
                r.x = tx; 
                r.y = ty;
            }
        }
        repaint();
    }

    // --- Input Handling ---
    @Override
    public void keyPressed(KeyEvent e) {
        if (gameState == GameState.GAMEOVER && e.getKeyCode() == KeyEvent.VK_SPACE) {
            initGameSession(); startFirstGameSequence(); return;
        }
        // Player should only move during PLAYING state (Change #5)
        if (gameState != GameState.PLAYING) return;

        int dx = 0, dy = 0;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP: dy = -1; break;
            case KeyEvent.VK_DOWN: dy = 1; break;
            case KeyEvent.VK_LEFT: dx = -1; break;
            case KeyEvent.VK_RIGHT: dx = 1; break;
        }
        if (dx != 0 || dy != 0) movePlayer(dx, dy);
    }

    @Override
    public void keyReleased(KeyEvent e) { /* Do nothing - enforces single-key press movement */ } 
    @Override
    public void keyTyped(KeyEvent e) { /* Do nothing - enforces single-key press movement */ }
    
    public void mouseClicked(MouseEvent e) { if (gameState == GameState.MENU) startFirstGameSequence(); }
    public void mousePressed(MouseEvent e) {} public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {} public void mouseExited(MouseEvent e) {}

    // --- Player Movement & Collision ---
    private void movePlayer(int dx, int dy) {
        // Sprite Logic
        if (dx != 0) { player.facingLeft = (dx < 0); player.state = (player.state == Player.State.RUN_SIDE) ? Player.State.STAND : Player.State.RUN_SIDE; }
        if (dy != 0) { player.state = (dy < 0) ? Player.State.RUN_UP : Player.State.RUN_DOWN; player.stepLeftFoot = !player.stepLeftFoot; }

        int tx = player.x + dx;
        int ty = player.y + dy;

        // Check boundaries (Change #2: prevent moving into outer endzone columns or sidelines)
        // Playable X range: 1 to GRID_W - 2 (42)
        // Playable Y range: 0 to VIEW_H - 1 (6)
        if (tx <= 0 || tx >= GRID_W - 1 || ty < 0 || ty >= VIEW_H) return;

        // Player cannot enter Referee space
        for (Referee r : referees) if (r.x == tx && r.y == ty) return;

        Defender targetDef = null;
        for (Defender d : defenders) {
            if (!d.isKnockedDown && d.x == tx && d.y == ty) {
                targetDef = d; break;
            }
        }

        if (targetDef != null) {
            // Player is trying to move into a defender's space (Collision/Push/Tackle check)
            int bx = tx + dx;
            int by = ty + dy;
            
            boolean blockerBehind = false;
            // Check if there is another unknocked defender in the target push-back square
            for (Defender d : defenders) {
                 // Check if the blocker is behind the target defender AND the blocker is not knocked down
                 if (d != targetDef && !d.isKnockedDown && d.x == bx && d.y == by) {
                     blockerBehind = true;
                     break;
                 }
            }

            // Check if push-back position is out of bounds (0 or 43, or outside 0-6 Y range)
            boolean pushOutOfBounds = (bx <= 0 || bx >= GRID_W - 1 || by < 0 || by >= VIEW_H);

            if (blockerBehind || pushOutOfBounds) {
                playerTackled(); // Tackle if blocked or pushed out of bounds
                return;
            } else {
                // Successful push: Knock down target defender and move into their spot
                targetDef.isKnockedDown = true;
                score++; 
                playSound(clipThud);
                player.x = tx; player.y = ty;
            }
        } else {
            // Clear movement
            player.x = tx; player.y = ty;
        }
        
        playSound(clipStep);
        updateCamera();

        // Touchdown condition: Reached index 1 (Goal Line, the second column)
        if (player.x == FIELD_START_X - 1) scoreTouchdown();
        repaint();
    }
    
    private void updateCamera() {
        int playerScreenX = player.x - cameraX;
        if (playerScreenX < 9 && cameraX > 0) cameraX--;
        if (playerScreenX > 11 && cameraX < GRID_W - VIEW_W) cameraX++;
    }

    private void playerTackled() {
        playSound(clipThud);
        gameState = GameState.TACKLED;
        defenderTimer.stop(); gameClock.stop();
        repaint();

        Timer pauseTimer = new Timer(5000, e -> {
            attempts--;
            if (attempts <= 0) gameOver("GAME OVER");
            else resetPlaySequence();
        });
        pauseTimer.setRepeats(false); pauseTimer.start();
    }

    private void scoreTouchdown() {
        playSound(clipCheer);
        gameState = GameState.TOUCHDOWN;
        score += 7;
        touchdowns++;
        attempts = START_ATTEMPTS;
        defenderTimer.stop(); gameClock.stop();
        
        tdBlinkCount = 0;
        showTDSprite = true;
        tdBlinkTimer.start();
        
        repaint();
    }

    private void gameOver(String msg) {
        gameState = GameState.GAMEOVER;
        gameClock.stop(); defenderTimer.stop();
        repaint();
    }

    // --- Rendering ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (gameState == GameState.MENU) { drawStartScreen(g); return; }
        
        // Draw the main field area (offset by the top border height)
        drawField(g);
        drawEntities(g);
        // Removed drawSidelines(g) as the panel background now acts as the thick sideline (Change #7)
        drawScoreboard(g);
        
        if (gameState == GameState.TOUCHDOWN && showTDSprite) drawTouchdownAnim(g);
        if (gameState == GameState.GAMEOVER) drawGameOver(g);
    }

    private void drawStartScreen(Graphics g) {
        // Darker start screen background
        g.setColor(new Color(120, 200, 120)); 
        g.fillRect(0, 0, WINDOW_W, WINDOW_H);
        
        // Darker green for the circle
        g.setColor(new Color(2, 180, 60)); 
        int circleSize = 180;
        g.fillOval(WINDOW_W/2 - circleSize/2, WINDOW_H/2 - circleSize/2, circleSize, circleSize);
        
        g.setColor(Color.WHITE); g.setFont(new Font("Arial", Font.BOLD, 18));
        String msg = "Click here to start!";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, WINDOW_W/2 - fm.stringWidth(msg)/2, WINDOW_H/2 + 5);
    }

    private void drawField(Graphics g) {
        // Draw the background color of the field within the bounds of the sidelines
        g.setColor(FIELD_COLOR);
        g.fillRect(0, BORDER_H * TILE_SIZE, VIEW_W * TILE_SIZE, VIEW_H * TILE_SIZE);

        // Draw Grass Texture (Tiled background) - Offset by the top border
        if (imgGrassTexture != null) {
            g.drawImage(imgGrassTexture, 0, BORDER_H * TILE_SIZE, null);
        }
        
        // Midfield Logo (Starts at grid index 20, 4 wide)
        int logoGridX = 20; 
        int logoDrawX = (logoGridX - cameraX) * TILE_SIZE;
        // Vertically centered within the VIEW_H area. Offset by BORDER_H * TILE_SIZE
        int logoDrawY = (VIEW_H * TILE_SIZE / 2) - (4 * TILE_SIZE / 2) + BORDER_H * TILE_SIZE;
        
        if (imgMidfieldLogo != null) {
             if (logoDrawX + 4 * TILE_SIZE > 0 && logoDrawX < VIEW_W * TILE_SIZE) {
                g.drawImage(imgMidfieldLogo, logoDrawX, logoDrawY, 4 * TILE_SIZE, 4 * TILE_SIZE, null);
             }
        }

        for (int i = 0; i < VIEW_W; i++) {
            int gridX = cameraX + i;
            int drawX = i * TILE_SIZE;
            
            // Yard lines for the green field area (Indices 2 through 41)
            if (gridX >= FIELD_START_X && gridX <= FIELD_END_X - 1) {
                g.setColor(new Color(255, 255, 255, 100));
                g.drawLine(drawX, BORDER_H * TILE_SIZE, drawX, WINDOW_H - BORDER_H * TILE_SIZE);
            }
            
            // Endzones (Left: 0-1, Right: 42-43) - Offset by the top border
            if (gridX < FIELD_START_X) { // Left Endzone (Indices 0, 1)
                drawEndzoneSlice(g, imgEndzoneLeft, gridX, drawX, Color.BLUE);
            }
            if (gridX >= FIELD_END_X) { // Right Endzone (Indices 42, 43)
                drawEndzoneSlice(g, imgEndzoneRight, gridX - FIELD_END_X, drawX, Color.RED);
            }
        }
    }
    
    private void drawEndzoneSlice(Graphics g, BufferedImage img, int sliceIndex, int drawX, Color fallback) {
        int drawY = BORDER_H * TILE_SIZE;
        int sliceHeight = VIEW_H * TILE_SIZE;

        if (img == null) { 
            g.setColor(fallback); g.fillRect(drawX, drawY, TILE_SIZE, sliceHeight);
            return;
        }

        int srcWidthPerTile = img.getWidth() / 2; 
        int srcX1 = sliceIndex * srcWidthPerTile; 
        int srcX2 = srcX1 + srcWidthPerTile;      

        g.drawImage(img, 
            drawX, drawY, drawX + TILE_SIZE, drawY + sliceHeight, // Destination rect (1 tile width, 7 tiles high)
            srcX1, 0, srcX2, img.getHeight(),                      // Source rect (1 tile width slice)
            null);
    }


    private void drawEntities(Graphics g) {
        int offX = -cameraX * TILE_SIZE;
        // Offset Y position by the top border height
        int offY = BORDER_H * TILE_SIZE; 
        
        for (Defender d : defenders) if (d.isKnockedDown) drawSprite(g, imgDefenderKnocked, d.x, d.y, offX, offY, false);

        if (gameState == GameState.TACKLED && imgTackle != null) {
            drawSprite(g, imgTackle, player.x, player.y, offX, offY, false);
        } else {
            BufferedImage sprite = imgPlayerStandLeft;
            boolean flip = !player.facingLeft; 
            if (player.state == Player.State.RUN_SIDE) sprite = imgPlayerRunLeft;
            else if (player.state == Player.State.RUN_UP) { sprite = player.stepLeftFoot ? flipImageHorizontally(imgPlayerUpRightFoot) : imgPlayerUpRightFoot; flip = false; }
            else if (player.state == Player.State.RUN_DOWN) { sprite = player.stepLeftFoot ? flipImageHorizontally(imgPlayerDownRightFoot) : imgPlayerDownRightFoot; flip = false; }
            drawSprite(g, sprite, player.x, player.y, offX, offY, flip);
        }

        for (Defender d : defenders) if (!d.isKnockedDown) drawSprite(g, imgDefenderRight, d.x, d.y, offX, offY, !d.facingRight);
        for (Referee r : referees) drawSprite(g, imgRefRight, r.x, r.y, offX, offY, !r.facingRight);
    }
    
    private void drawSprite(Graphics g, BufferedImage img, int gridX, int gridY, int offsetX, int offsetY, boolean flipHorizontal) {
        if(img == null || gridX < cameraX || gridX >= cameraX + VIEW_W) return;
        int x = (gridX * TILE_SIZE) + offsetX; 
        int y = (gridY * TILE_SIZE) + offsetY; // Added vertical offset
        if (flipHorizontal) g.drawImage(img, x + TILE_SIZE, y, -TILE_SIZE, TILE_SIZE, null);
        else g.drawImage(img, x, y, TILE_SIZE, TILE_SIZE, null);
    }

    private void drawTouchdownAnim(Graphics g) {
        if (imgTouchdown == null) return;
        
        int w = imgTouchdown.getWidth() / 2;
        int h = imgTouchdown.getHeight() / 2;
        
        // Left edge of sprite must be 1 pixel from right edge of left endzone (grid index 2).
        int drawX = (FIELD_START_X * TILE_SIZE) + 1; 
        // Centered vertically within the playable area, plus top border offset
        int drawY = (WINDOW_H - 2 * BORDER_H * TILE_SIZE)/2 - h/2 + BORDER_H * TILE_SIZE;
        
        g.drawImage(imgTouchdown, drawX, drawY, w, h, null);
    }

    private void drawScoreboard(Graphics g) {
        int x = VIEW_W * TILE_SIZE;
        g.setColor(Color.BLACK); g.fillRect(x, 0, SCOREBOARD_W, WINDOW_H);
        if (imgScoreboard != null) g.drawImage(imgScoreboard, x, 0, SCOREBOARD_W, WINDOW_H, null);
        
        g.setColor(Color.BLACK); g.setFont(new Font("Impact", Font.PLAIN, 20));
        
        // Time Left - Slightly Down (75)
        drawCenteredText(g, String.valueOf(timeRemaining), x + SCOREBOARD_W/2, 75);
        
        // Score - Shifted UP 2 pixels (138)
        drawCenteredText(g, String.valueOf(score), x + SCOREBOARD_W/2, 138);
        
        // Yards to go: Distance from goal line (FIELD_START_X = 2) * 2.5
        double yards = Math.max(0, (player.x - (FIELD_START_X - 1)) * 2.5);
        // Up slightly more (220)
        drawCenteredText(g, df.format(yards), x + SCOREBOARD_W/2, 220);
        
        // Attempts Left - Down slightly (305)
        drawCenteredText(g, String.valueOf(attempts), x + SCOREBOARD_W/2, 305);
    }
    
    private void drawCenteredText(Graphics g, String text, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x - fm.stringWidth(text)/2, y);
    }
    
    private void drawGameOver(Graphics g) {
        // Draw over the field area, not the scoreboard
        g.setColor(new Color(0,0,0,180)); g.fillRect(0, 0, VIEW_W * TILE_SIZE, WINDOW_H);
        g.setColor(Color.WHITE); g.setFont(new Font("Arial", Font.BOLD, 40));
        String msg = attempts <= 0 ? "GAME OVER" : "TIME'S UP!";
        g.drawString(msg, (VIEW_W * TILE_SIZE)/2 - g.getFontMetrics().stringWidth(msg)/2, WINDOW_H/2);
        g.setFont(new Font("Arial", Font.BOLD, 20)); String sub = "Press SPACE to Restart";
        g.drawString(sub, (VIEW_W * TILE_SIZE)/2 - g.getFontMetrics().stringWidth(sub)/2, WINDOW_H/2 + 50);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("The Best Football Game");
            TheBestFootballGame game = new TheBestFootballGame();
            frame.add(game); frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false); frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    class Player {
        int x, y; boolean facingLeft = true, stepLeftFoot = false; State state = State.RUN_SIDE;
        enum State { STAND, RUN_SIDE, RUN_UP, RUN_DOWN }
        Player(int x, int y) { this.x = x; this.y = y; }
    }
    class Defender {
        int x, y; boolean isKnockedDown = false, facingRight = false;
        Defender(int x, int y) { this.x = x; this.y = y; }
    }
    class Referee {
        int x, y; boolean facingRight = true;
        Referee(int x, int y) { this.x = x; this.y = y; }
    }
}