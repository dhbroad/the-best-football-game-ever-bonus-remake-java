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
    private static final int VIEW_H = 7;
    // 2 EndzoneL + 40 Field + 2 EndzoneR = 44 total grid units
    private static final int GRID_W = 44; 
    private static final int FIELD_START_X = 2; // Index where the green field starts (Goal Line)
    private static final int FIELD_END_X = GRID_W - 2; // Index where the green field ends (42)
    
    private static final int SCOREBOARD_W = (int)(2.5 * TILE_SIZE);
    private static final int WINDOW_W = (VIEW_W * TILE_SIZE) + SCOREBOARD_W;
    private static final int WINDOW_H = VIEW_H * TILE_SIZE;

    // --- Game Constants ---
    private static final int TURN_DELAY = 500; 
    private static final int START_ATTEMPTS = 4;
    private static final int GAME_DURATION = 60; 
    // Requested grass color: RGB 3, 214, 73 (#03D649)
    private static final Color FIELD_COLOR = new Color(3, 214, 73); 

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
    private BufferedImage imgGrassTexture; // Now holds the repeating pattern
    
    // --- Sounds ---
    private Clip clipCheer, clipSeal, clipWhistle, clipStep, clipThud;

    public TheBestFootballGame() {
        setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));
        setBackground(FIELD_COLOR);
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
    
    private BufferedImage flipImageHorizontally(BufferedImage src) {
        if (src == null) return null;
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-src.getWidth(null), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(src, null);
    }

    private void generateGrassTexture() {
        // Create a texture pattern for one TILE_SIZE * TILE_SIZE block
        BufferedImage tileTexture = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tileTexture.createGraphics();
        g.setColor(FIELD_COLOR);
        g.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
        
        // --- FIX APPLIED HERE: Clamp RGB values to prevent IllegalArgumentException ---
        int darkR = Math.max(0, FIELD_COLOR.getRed() - 5);
        int darkG = Math.max(0, FIELD_COLOR.getGreen() - 5);
        int darkB = Math.max(0, FIELD_COLOR.getBlue() - 5);
        
        Color darkerGreen = new Color(darkR, darkG, darkB, 80); 
        // --------------------------------------------------------------------------
        
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
        imgGrassTexture = new BufferedImage(VIEW_W * TILE_SIZE, WINDOW_H, BufferedImage.TYPE_INT_ARGB);
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

    // --- Game Sequences ---

    private void initGameSession() {
        score = 0;
        attempts = START_ATTEMPTS;
        touchdowns = 0;
        gameState = GameState.MENU;
    }

    private void prepareField() {
        // Player starts at index 42 (FIELD_END_X), the first green tile next to the right EZ.
        player = new Player(FIELD_END_X, 3); 
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
        
        int defenderCount = Math.min(30, 12 + touchdowns * 2);
        Random rand = new Random();
        
        for (int i = 0; i < defenderCount; i++) {
            int dx, dy;
            do {
                dx = FIELD_START_X + 2 + rand.nextInt(FIELD_END_X - FIELD_START_X - 4); 
                dy = rand.nextInt(VIEW_H);
            } while (isOccupied(dx, dy)); 
            defenders.add(new Defender(dx, dy));
        }
        
        int refereeCount = Math.max(1, defenderCount / 5);
        for (int i = 0; i < refereeCount; i++) {
            int rx, ry;
            do {
                rx = FIELD_START_X + rand.nextInt(FIELD_END_X - FIELD_START_X);
                ry = rand.nextInt(VIEW_H);
            } while (isOccupied(rx, ry));
            referees.add(new Referee(rx, ry));
        }
    }

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

        for (Defender d : defenders) {
            if (d.isKnockedDown) continue;

            int dx = 0, dy = 0;

            if (Math.abs(d.x - player.x) + Math.abs(d.y - player.y) == 1) {
                if (rand.nextDouble() < 0.8) {
                    dx = player.x - d.x;
                    dy = player.y - d.y;
                }
            }

            if (dx == 0 && dy == 0) {
                if (rand.nextDouble() < 0.6) continue;
                if (rand.nextBoolean()) dx = rand.nextBoolean() ? 1 : -1;
                else dy = rand.nextBoolean() ? 1 : -1;
            }

            if (dx > 0) d.facingRight = true; else if (dx < 0) d.facingRight = false;

            int tx = d.x + dx;
            int ty = d.y + dy;

            if (tx < 0 || tx >= GRID_W || ty < 0 || ty >= VIEW_H) continue;

            if (!isOccupied(tx, ty)) {
                d.x = tx;
                d.y = ty;
            }
            
            if (d.x == player.x && d.y == player.y) {
                playerTackled();
                return;
            }
        }
        
        // Referee movement
        Random refRand = new Random();
        for (Referee r : referees) {
            if (refRand.nextDouble() < 0.7) continue; 
            int rx = (refRand.nextBoolean()) ? (refRand.nextBoolean() ? 1 : -1) : 0;
            int ry = (rx == 0) ? (refRand.nextBoolean() ? 1 : -1) : 0;
            if (rx > 0) r.facingRight = true; else if (rx < 0) r.facingRight = false;
            int tx = r.x + rx; int ty = r.y + ry;
            if (tx >= 0 && tx < GRID_W && ty >= 0 && ty >= VIEW_H && !isOccupied(tx, ty)) {
                r.x = tx; r.y = ty;
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
    public void mouseClicked(MouseEvent e) {
        if (gameState == GameState.MENU) startFirstGameSequence();
    }
    public void mousePressed(MouseEvent e) {} public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {} public void mouseExited(MouseEvent e) {}
    public void keyReleased(KeyEvent e) {} public void keyTyped(KeyEvent e) {}

    // --- Player Movement & Collision ---
    private void movePlayer(int dx, int dy) {
        // Sprite Logic
        if (dx != 0) { player.facingLeft = (dx < 0); player.state = (player.state == Player.State.RUN_SIDE) ? Player.State.STAND : Player.State.RUN_SIDE; }
        if (dy != 0) { player.state = (dy < 0) ? Player.State.RUN_UP : Player.State.RUN_DOWN; player.stepLeftFoot = !player.stepLeftFoot; }

        int tx = player.x + dx;
        int ty = player.y + dy;

        if (tx < 0 || tx >= GRID_W || ty < 0 || ty >= VIEW_H) return;

        for (Referee r : referees) if (r.x == tx && r.y == ty) return;

        Defender targetDef = null;
        for (Defender d : defenders) {
            if (!d.isKnockedDown && d.x == tx && d.y == ty) {
                targetDef = d; break;
            }
        }

        if (targetDef != null) {
            int bx = tx + dx;
            int by = ty + dy;
            
            boolean blockerBehind = false;
            for (Defender d : defenders) {
                 if (!d.isKnockedDown && d.x == bx && d.y == by) {
                     blockerBehind = true;
                     break;
                 }
            }

            if (blockerBehind) {
                playerTackled();
                return;
            } else if (bx < 0 || bx >= GRID_W || by < 0 || by >= VIEW_H) {
                 return;
            } else {
                targetDef.isKnockedDown = true;
                score++; 
                playSound(clipThud);
                player.x = tx; player.y = ty;
            }
        } else {
            player.x = tx; player.y = ty;
        }
        
        playSound(clipStep);
        updateCamera();

        // Touchdown condition: Reached index 1 (Goal Line)
        if (player.x < FIELD_START_X) scoreTouchdown();
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
        
        drawField(g);
        drawEntities(g);
        drawSidelines(g);
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
        // Draw Grass Texture (Tiled background)
        if (imgGrassTexture != null) {
            g.drawImage(imgGrassTexture, 0, 0, null);
        }
        
        // Midfield Logo (Starts at grid index 20, 4 wide)
        int logoGridX = 20; 
        int logoDrawX = (logoGridX - cameraX) * TILE_SIZE;
        // Vertically centered: (VIEW_H * TILE_SIZE / 2) - (logo height / 2)
        // Logo is 4 tiles high (4 * TILE_SIZE)
        int logoDrawY = (VIEW_H * TILE_SIZE / 2) - (4 * TILE_SIZE / 2);
        
        if (imgMidfieldLogo != null) {
             // Ensure logo is drawn only if visible
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
                g.drawLine(drawX, 0, drawX, WINDOW_H);
            }
            
            // Endzones (Left: 0-1, Right: 42-43)
            if (gridX < FIELD_START_X) { // Left Endzone (Indices 0, 1)
                drawEndzoneSlice(g, imgEndzoneLeft, gridX, drawX, Color.BLUE);
            }
            if (gridX >= FIELD_END_X) { // Right Endzone (Indices 42, 43)
                drawEndzoneSlice(g, imgEndzoneRight, gridX - FIELD_END_X, drawX, Color.RED);
            }
        }
    }
    
    private void drawEndzoneSlice(Graphics g, BufferedImage img, int sliceIndex, int drawX, Color fallback) {
        if (img == null) { 
            g.setColor(fallback); g.fillRect(drawX, 0, TILE_SIZE, WINDOW_H);
            return;
        }

        // The Endzone image is assumed to be 2 tiles wide.
        // Each tile takes up half of the full image width.
        int srcWidthPerTile = img.getWidth() / 2; // This is the width of one tile in the source image
        int srcX1 = sliceIndex * srcWidthPerTile; // Start X for the specific slice
        int srcX2 = srcX1 + srcWidthPerTile;      // End X for the specific slice

        g.drawImage(img, 
            drawX, 0, drawX + TILE_SIZE, WINDOW_H, // Destination rectangle (1 tile width)
            srcX1, 0, srcX2, img.getHeight(),      // Source rectangle (1 tile width slice)
            null);
    }

    private void drawSidelines(Graphics g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, VIEW_W * TILE_SIZE, 3);
        g.fillRect(0, WINDOW_H - 3, VIEW_W * TILE_SIZE, 3);
    }

    private void drawEntities(Graphics g) {
        int offX = -cameraX * TILE_SIZE;
        for (Defender d : defenders) if (d.isKnockedDown) drawSprite(g, imgDefenderKnocked, d.x, d.y, offX, false);

        if (gameState == GameState.TACKLED && imgTackle != null) {
            drawSprite(g, imgTackle, player.x, player.y, offX, false);
        } else {
            BufferedImage sprite = imgPlayerStandLeft;
            boolean flip = !player.facingLeft; 
            if (player.state == Player.State.RUN_SIDE) sprite = imgPlayerRunLeft;
            else if (player.state == Player.State.RUN_UP) { sprite = player.stepLeftFoot ? flipImageHorizontally(imgPlayerUpRightFoot) : imgPlayerUpRightFoot; flip = false; }
            else if (player.state == Player.State.RUN_DOWN) { sprite = player.stepLeftFoot ? flipImageHorizontally(imgPlayerDownRightFoot) : imgPlayerDownRightFoot; flip = false; }
            drawSprite(g, sprite, player.x, player.y, offX, flip);
        }

        for (Defender d : defenders) if (!d.isKnockedDown) drawSprite(g, imgDefenderRight, d.x, d.y, offX, !d.facingRight);
        for (Referee r : referees) drawSprite(g, imgRefRight, r.x, r.y, offX, !r.facingRight);
    }
    
    private void drawSprite(Graphics g, BufferedImage img, int gridX, int gridY, int offsetX, boolean flipHorizontal) {
        if(img == null || gridX < cameraX || gridX >= cameraX + VIEW_W) return;
        int x = (gridX * TILE_SIZE) + offsetX; int y = gridY * TILE_SIZE;
        if (flipHorizontal) g.drawImage(img, x + TILE_SIZE, y, -TILE_SIZE, TILE_SIZE, null);
        else g.drawImage(img, x, y, TILE_SIZE, TILE_SIZE, null);
    }

    private void drawTouchdownAnim(Graphics g) {
        if (imgTouchdown == null) return;
        
        int w = imgTouchdown.getWidth() / 2;
        int h = imgTouchdown.getHeight() / 2;
        
        // Left edge of sprite must be 1 pixel from right edge of left endzone (grid index 2).
        int drawX = (FIELD_START_X * TILE_SIZE) + 1; 
        int drawY = WINDOW_H/2 - h/2;
        
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
        double yards = Math.max(0, (player.x - FIELD_START_X) * 2.5);
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
        g.setColor(new Color(0,0,0,180)); g.fillRect(0, 0, WINDOW_W - SCOREBOARD_W, WINDOW_H);
        g.setColor(Color.WHITE); g.setFont(new Font("Arial", Font.BOLD, 40));
        String msg = attempts <= 0 ? "GAME OVER" : "TIME'S UP!";
        g.drawString(msg, (WINDOW_W - SCOREBOARD_W)/2 - g.getFontMetrics().stringWidth(msg)/2, WINDOW_H/2);
        g.setFont(new Font("Arial", Font.BOLD, 20)); String sub = "Press SPACE to Restart";
        g.drawString(sub, (WINDOW_W - SCOREBOARD_W)/2 - g.getFontMetrics().stringWidth(sub)/2, WINDOW_H/2 + 50);
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