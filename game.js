// The Best Football Game - JavaScript/Canvas Version
// Converted from Java Swing version

class TheBestFootballGame {
    // Grid & Dimensions
    static TILE_SIZE = 48;
    static VIEW_W = 14;
    static VIEW_H = 7;
    
    static GRID_W = 44;
    static FIELD_START_X = 2;
    static FIELD_END_X = 42; // GRID_W - 2
    
    static SIDELINE_H = 3;
    static SCOREBOARD_W = Math.floor(2.5 * TheBestFootballGame.TILE_SIZE);
    static WINDOW_W = (TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE) + TheBestFootballGame.SCOREBOARD_W;
    static WINDOW_H = (TheBestFootballGame.VIEW_H * TheBestFootballGame.TILE_SIZE) + (TheBestFootballGame.SIDELINE_H * 2);
    
    // Game Constants
    static TURN_DELAY = 500;
    static START_ATTEMPTS = 4;
    static GAME_DURATION = 60;
    static FIRST_DOWN_DISTANCE = 10;
    
    static FIELD_COLOR = '#03D649';
    static SIDELINE_COLOR = '#FFFFFF';
    
    // Game States
    static GameState = {
        MENU: 'MENU',
        READY: 'READY',
        PLAYING: 'PLAYING',
        TOUCHDOWN: 'TOUCHDOWN',
        TACKLED: 'TACKLED',
        GAMEOVER: 'GAMEOVER'
    };
    
    constructor(canvas) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        
        // Set canvas size
        canvas.width = TheBestFootballGame.WINDOW_W;
        canvas.height = TheBestFootballGame.WINDOW_H;
        
        // State
        this.gameState = TheBestFootballGame.GameState.MENU;
        this.keyIsPressed = false;
        this.keysPressed = new Set();
        
        // Stats
        this.score = 0;
        this.attempts = TheBestFootballGame.START_ATTEMPTS;
        this.timeRemaining = TheBestFootballGame.GAME_DURATION;
        this.touchdowns = 0;
        
        // First Down
        this.firstDownMarkerX = 0;
        this.yardsToGo = 0;
        this.attemptsRemaining = TheBestFootballGame.START_ATTEMPTS;
        
        // Camera
        this.cameraX = 0;
        
        // Timers
        this.defenderTimerId = null;
        this.gameClockId = null;
        this.tdBlinkTimerId = null;
        
        // Animation
        this.showTDSprite = false;
        this.tdBlinkCount = 0;
        
        // Tackle
        this.tackleSource = null;
        
        // Entities
        this.player = null;
        this.defenders = [];
        this.referees = [];
        
        // Assets
        this.images = {};
        this.sounds = {};
        this.grassTexture = null;
        
        // Load assets
        this.loadAssets();
        
        // Event listeners
        this.setupEventListeners();
        
        // Start rendering
        this.render();
    }
    
    setupEventListeners() {
        // Keyboard
        window.addEventListener('keydown', (e) => this.handleKeyDown(e));
        window.addEventListener('keyup', (e) => this.handleKeyUp(e));
        
        // Mouse
        this.canvas.addEventListener('click', (e) => this.handleClick(e));
    }
    
    loadAssets() {
        const imageFiles = [
            'TBFGE - Player Running Left.png',
            'TBFGE - Player Standing Left.png',
            'TBFGE - Player Running Up - Right Foot Down.png',
            'TBFGE - Player Running Down - Right Foot Down.png',
            'TBFGE - Defender Facing Right.png',
            'TBFGE - Defender Knocked Down.png',
            'TBFGE - Defender Tackling.png',
            'TBFGE - Player Tackled.png',
            'TBFGE - Tackle Flash.png',
            'TBFGE - Referee Facing Right.png',
            'TBFGE - Endzone Right.png',
            'TBFGE - Touch Down.png',
            'TBFGE - Scoreboard Start.png',
            'TBFGE - Walrus Midfield Logo.png',
            'TBFGE - First Down Marker.png'
        ];
        
        let loadedCount = 0;
        const totalAssets = imageFiles.length;
        
        imageFiles.forEach(filename => {
            const img = new Image();
            img.onload = () => {
                loadedCount++;
                if (loadedCount === totalAssets) {
                    this.generateGrassTexture();
                    this.initGameSession();
                }
            };
            img.onerror = () => {
                console.warn(`Failed to load: ${filename}`);
                loadedCount++;
                if (loadedCount === totalAssets) {
                    this.generateGrassTexture();
                    this.initGameSession();
                }
            };
            img.src = filename;
            this.images[filename] = img;
        });
        
        // Load sounds
        const soundFiles = {
            'cheer': 'cheer.wav',
            'seal': 'seal.wav',
            'whistle': 'whistle.wav',
            'step': 'step.wav',
            'thud': 'thud.wav'
        };
        
        for (let [key, filename] of Object.entries(soundFiles)) {
            const audio = new Audio(filename);
            audio.preload = 'auto';
            this.sounds[key] = audio;
        }
    }
    
    generateGrassTexture() {
        const tileCanvas = document.createElement('canvas');
        tileCanvas.width = TheBestFootballGame.TILE_SIZE;
        tileCanvas.height = TheBestFootballGame.TILE_SIZE;
        const tileCtx = tileCanvas.getContext('2d');
        
        // Base color
        tileCtx.fillStyle = TheBestFootballGame.FIELD_COLOR;
        tileCtx.fillRect(0, 0, TheBestFootballGame.TILE_SIZE, TheBestFootballGame.TILE_SIZE);
        
        // Darker green for texture
        const darkR = Math.max(0, 3 - 15);
        const darkG = Math.max(0, 214 - 15);
        const darkB = Math.max(0, 73 - 15);
        const darkerGreen = `rgb(${darkR}, ${darkG}, ${darkB})`;
        
        // Add texture
        for (let x = 0; x < TheBestFootballGame.TILE_SIZE; x += 2) {
            for (let y = 0; y < TheBestFootballGame.TILE_SIZE; y += 2) {
                if (Math.random() < 0.2) {
                    tileCtx.fillStyle = darkerGreen;
                    const smudgeSize = Math.floor(Math.random() * 3) + 1;
                    tileCtx.fillRect(x, y, smudgeSize, smudgeSize);
                }
            }
        }
        
        // Create full texture
        const fullCanvas = document.createElement('canvas');
        fullCanvas.width = TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE;
        fullCanvas.height = TheBestFootballGame.WINDOW_H;
        const fullCtx = fullCanvas.getContext('2d');
        
        for (let x = 0; x < TheBestFootballGame.VIEW_W; x++) {
            for (let y = 0; y < TheBestFootballGame.VIEW_H; y++) {
                fullCtx.drawImage(tileCanvas, x * TheBestFootballGame.TILE_SIZE, y * TheBestFootballGame.TILE_SIZE);
            }
        }
        
        this.grassTexture = fullCanvas;
    }
    
    playSound(soundName) {
        const sound = this.sounds[soundName];
        if (sound) {
            sound.currentTime = 0;
            sound.play().catch(e => console.warn('Audio play failed:', e));
        }
    }
    
    // Game Sequences
    initGameSession() {
        this.score = 0;
        this.attempts = TheBestFootballGame.START_ATTEMPTS;
        this.timeRemaining = TheBestFootballGame.GAME_DURATION;
        this.touchdowns = 0;
        this.gameState = TheBestFootballGame.GameState.MENU;
    }
    
    prepareField() {
        this.player = new Player(TheBestFootballGame.FIELD_END_X, Math.floor(TheBestFootballGame.VIEW_H / 2));
        this.cameraX = TheBestFootballGame.GRID_W - TheBestFootballGame.VIEW_W;
        
        this.spawnDefendersAndRefs();
        
        // Initial First Down setup
        this.firstDownMarkerX = this.player.x - TheBestFootballGame.FIRST_DOWN_DISTANCE;
        this.attemptsRemaining = TheBestFootballGame.START_ATTEMPTS;
        
        if (this.firstDownMarkerX < TheBestFootballGame.FIELD_START_X) {
            this.firstDownMarkerX = TheBestFootballGame.FIELD_START_X;
        }
        
        if (this.firstDownMarkerX === TheBestFootballGame.FIELD_START_X) {
            this.yardsToGo = this.player.x - (TheBestFootballGame.FIELD_START_X - 1);
        } else {
            this.yardsToGo = this.player.x - this.firstDownMarkerX;
        }
        
        this.stopAllTimers();
    }
    
    startFirstGameSequence() {
        this.prepareField();
        this.gameState = TheBestFootballGame.GameState.READY;
        this.playSound('cheer');
        setTimeout(() => this.playSound('seal'), 750);
        this.startPlayAfterDelay(3000);
    }
    
    resetPlaySequence() {
        this.prepareField();
        this.gameState = TheBestFootballGame.GameState.READY;
        this.startPlayAfterDelay(3000);
    }
    
    startPlayAfterDelay(delay) {
        setTimeout(() => {
            this.playSound('whistle');
            this.gameState = TheBestFootballGame.GameState.PLAYING;
            this.startGameClock();
            this.startDefenderTimer();
        }, delay);
    }
    
    startGameClock() {
        this.gameClockId = setInterval(() => {
            if (this.gameState === TheBestFootballGame.GameState.PLAYING && this.timeRemaining > 0) {
                this.timeRemaining--;
                if (this.timeRemaining <= 0) {
                    this.gameOver("TIME'S UP!");
                }
            }
        }, 1000);
    }
    
    startDefenderTimer() {
        this.defenderTimerId = setInterval(() => this.tickDefenders(), TheBestFootballGame.TURN_DELAY);
    }
    
    stopAllTimers() {
        if (this.gameClockId) clearInterval(this.gameClockId);
        if (this.defenderTimerId) clearInterval(this.defenderTimerId);
        if (this.tdBlinkTimerId) clearInterval(this.tdBlinkTimerId);
    }
    
    resetAfterTackle(newPlayerX) {
        // Clear entities
        this.defenders = this.defenders.filter(d => {
            if (d.isKnockedDown || d.x >= newPlayerX) return false;
            d.facingRight = true;
            return true;
        });
        
        this.referees = this.referees.filter(r => {
            if (r.x >= newPlayerX) return false;
            r.facingRight = true;
            return true;
        });
        
        // Check touchdown
        if (newPlayerX < TheBestFootballGame.FIELD_START_X) {
            this.scoreTouchdown();
            return;
        }
        
        // First Down logic
        if (newPlayerX <= this.firstDownMarkerX) {
            // First down achieved
            this.firstDownMarkerX = newPlayerX - TheBestFootballGame.FIRST_DOWN_DISTANCE;
            this.attemptsRemaining = TheBestFootballGame.START_ATTEMPTS;
        } else {
            // Failed to convert
            this.attemptsRemaining--;
        }
        
        if (this.firstDownMarkerX < TheBestFootballGame.FIELD_START_X) {
            this.firstDownMarkerX = TheBestFootballGame.FIELD_START_X;
        }
        
        // Check turnover
        if (this.attemptsRemaining <= 0) {
            this.gameOver("GAME OVER");
            return;
        }
        
        // Calculate yardsToGo
        if (this.firstDownMarkerX === TheBestFootballGame.FIELD_START_X) {
            this.yardsToGo = newPlayerX - (TheBestFootballGame.FIELD_START_X - 1);
        } else {
            this.yardsToGo = newPlayerX - this.firstDownMarkerX;
        }
        
        // Reset player
        this.player.x = newPlayerX;
        this.player.y = Math.floor(TheBestFootballGame.VIEW_H / 2);
        this.player.facingLeft = true;
        this.player.state = Player.State.STAND;
        
        this.updateCamera();
        this.gameState = TheBestFootballGame.GameState.READY;
        this.startPlayAfterDelay(3000);
    }
    
    spawnDefendersAndRefs() {
        this.defenders = [];
        this.referees = [];
        
        const fieldRatio = TheBestFootballGame.GRID_W / TheBestFootballGame.VIEW_W;
        const defendersPerView = Math.min(20, 10 + this.touchdowns * 2);
        const totalDefenders = Math.round(defendersPerView * fieldRatio);
        
        const minSpawnX = TheBestFootballGame.FIELD_START_X;
        const maxSpawnX = TheBestFootballGame.FIELD_END_X;
        
        for (let i = 0; i < totalDefenders; i++) {
            let dx, dy;
            do {
                dx = minSpawnX + Math.floor(Math.random() * (maxSpawnX - minSpawnX));
                dy = Math.floor(Math.random() * TheBestFootballGame.VIEW_H);
            } while (this.isOccupied(dx, dy));
            this.defenders.push(new Defender(dx, dy));
        }
        
        const totalReferees = Math.max(1, Math.floor(totalDefenders / 5));
        for (let i = 0; i < totalReferees; i++) {
            let rx, ry;
            do {
                rx = minSpawnX + Math.floor(Math.random() * (maxSpawnX - minSpawnX));
                ry = Math.floor(Math.random() * TheBestFootballGame.VIEW_H);
            } while (this.isOccupied(rx, ry));
            this.referees.push(new Referee(rx, ry));
        }
    }
    
    isOccupied(x, y) {
        if (this.player && this.player.x === x && this.player.y === y) return true;
        for (let d of this.defenders) {
            if (!d.isKnockedDown && d.x === x && d.y === y) return true;
        }
        for (let r of this.referees) {
            if (r.x === x && r.y === y) return true;
        }
        return false;
    }
    
    tickDefenders() {
        if (this.gameState !== TheBestFootballGame.GameState.PLAYING) return;
        
        for (let d of this.defenders) {
            if (d.isKnockedDown) continue;
            
            if (Math.random() < 0.6) continue;
            
            let dx = 0, dy = 0;
            if (Math.random() < 0.5) {
                dx = Math.random() < 0.5 ? 1 : -1;
            } else {
                dy = Math.random() < 0.5 ? 1 : -1;
            }
            
            if (dx > 0) d.facingRight = true;
            else if (dx < 0) d.facingRight = false;
            
            const tx = d.x + dx;
            const ty = d.y + dy;
            
            if (tx <= 0 || tx >= TheBestFootballGame.GRID_W - 1 || ty < 0 || ty >= TheBestFootballGame.VIEW_H) continue;
            
            if (tx === this.player.x && ty === this.player.y) {
                this.playerTackled(d);
                return;
            }
            
            if (!this.isOccupied(tx, ty)) {
                d.x = tx;
                d.y = ty;
            }
        }
        
        // Move referees
        for (let r of this.referees) {
            if (Math.random() < 0.7) continue;
            
            let rx = Math.random() < 0.5 ? (Math.random() < 0.5 ? 1 : -1) : 0;
            let ry = rx === 0 ? (Math.random() < 0.5 ? 1 : -1) : 0;
            
            if (rx > 0) r.facingRight = true;
            else if (rx < 0) r.facingRight = false;
            
            const tx = r.x + rx;
            const ty = r.y + ry;
            
            if (tx <= 0 || tx >= TheBestFootballGame.GRID_W - 1 || ty < 0 || ty >= TheBestFootballGame.VIEW_H) continue;
            
            if (!this.isOccupied(tx, ty)) {
                r.x = tx;
                r.y = ty;
            }
        }
    }
    
    handleKeyDown(e) {
        if (this.gameState === TheBestFootballGame.GameState.GAMEOVER && e.code === 'Space') {
            this.initGameSession();
            this.startFirstGameSequence();
            return;
        }
        
        if (this.gameState !== TheBestFootballGame.GameState.PLAYING || this.keyIsPressed) return;
        
        let dx = 0, dy = 0;
        switch (e.code) {
            case 'ArrowUp': dy = -1; break;
            case 'ArrowDown': dy = 1; break;
            case 'ArrowLeft': dx = -1; break;
            case 'ArrowRight': dx = 1; break;
            default: return;
        }
        
        e.preventDefault();
        if (dx !== 0 || dy !== 0) {
            this.keyIsPressed = true;
            this.movePlayer(dx, dy);
        }
    }
    
    handleKeyUp(e) {
        this.keyIsPressed = false;
    }
    
    handleClick(e) {
        if (this.gameState === TheBestFootballGame.GameState.MENU) {
            this.startFirstGameSequence();
        }
    }
    
    movePlayer(dx, dy) {
        if (dx !== 0) {
            this.player.facingLeft = (dx < 0);
            this.player.state = this.player.state === Player.State.RUN_SIDE ? Player.State.STAND : Player.State.RUN_SIDE;
        }
        if (dy !== 0) {
            this.player.state = dy < 0 ? Player.State.RUN_UP : Player.State.RUN_DOWN;
            this.player.stepLeftFoot = !this.player.stepLeftFoot;
        }
        
        const tx = this.player.x + dx;
        const ty = this.player.y + dy;
        
        if (tx <= 0 || tx >= TheBestFootballGame.GRID_W - 1 || ty < 0 || ty >= TheBestFootballGame.VIEW_H) {
            if (tx <= 0) {
                this.scoreTouchdown();
            }
            return;
        }
        
        // Check referee collision
        for (let r of this.referees) {
            if (r.x === tx && r.y === ty) return;
        }
        
        // Check defender collision
        let targetDef = null;
        for (let d of this.defenders) {
            if (!d.isKnockedDown && d.x === tx && d.y === ty) {
                targetDef = d;
                break;
            }
        }
        
        if (targetDef) {
            const bx = tx + dx;
            const by = ty + dy;
            
            let blockerBehind = false;
            for (let d of this.defenders) {
                if (d !== targetDef && !d.isKnockedDown && d.x === bx && d.y === by) {
                    blockerBehind = true;
                    break;
                }
            }
            
            if (blockerBehind || bx <= 0 || bx >= TheBestFootballGame.GRID_W - 1 || by < 0 || by >= TheBestFootballGame.VIEW_H) {
                this.playerTackled(targetDef);
                return;
            } else {
                targetDef.isKnockedDown = true;
                this.score++;
                this.playSound('thud');
                this.player.x = tx;
                this.player.y = ty;
            }
        } else {
            this.player.x = tx;
            this.player.y = ty;
        }
        
        this.playSound('step');
        this.updateCamera();
        
        if (this.player.x < TheBestFootballGame.FIELD_START_X) {
            this.scoreTouchdown();
        }
    }
    
    updateCamera() {
        const playerScreenX = this.player.x - this.cameraX;
        if (playerScreenX < 9 && this.cameraX > 0) this.cameraX--;
        if (playerScreenX > 11 && this.cameraX < TheBestFootballGame.GRID_W - TheBestFootballGame.VIEW_W) this.cameraX++;
    }
    
    playerTackled(tackler) {
        this.playSound('thud');
        this.gameState = TheBestFootballGame.GameState.TACKLED;
        this.tackleSource = tackler ? { x: tackler.x, y: tackler.y } : null;
        
        this.stopAllTimers();
        
        setTimeout(() => {
            this.resetAfterTackle(this.player.x);
        }, 2000);
    }
    
    scoreTouchdown() {
        this.playSound('cheer');
        this.gameState = TheBestFootballGame.GameState.TOUCHDOWN;
        this.score += 7;
        this.touchdowns++;
        this.attemptsRemaining = TheBestFootballGame.START_ATTEMPTS;
        this.attempts = TheBestFootballGame.START_ATTEMPTS;
        
        this.stopAllTimers();
        
        this.tdBlinkCount = 0;
        this.showTDSprite = true;
        
        this.tdBlinkTimerId = setInterval(() => {
            this.tdBlinkCount++;
            this.showTDSprite = (this.tdBlinkCount % 2 !== 0);
            
            if (this.tdBlinkCount >= 8) {
                clearInterval(this.tdBlinkTimerId);
                this.resetPlaySequence();
            }
        }, 625);
    }
    
    gameOver(msg) {
        this.gameState = TheBestFootballGame.GameState.GAMEOVER;
        this.stopAllTimers();
    }
    
    // Rendering
    render() {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        
        if (this.gameState === TheBestFootballGame.GameState.MENU) {
            this.drawStartScreen();
        } else {
            this.drawField();
            this.drawFirstDownMarker();
            this.drawEntities();
            this.drawSidelines();
            this.drawScoreboard();
            
            if (this.gameState === TheBestFootballGame.GameState.TOUCHDOWN && this.showTDSprite) {
                this.drawTouchdownAnim();
            }
            if (this.gameState === TheBestFootballGame.GameState.GAMEOVER) {
                this.drawGameOver();
            }
        }
        
        requestAnimationFrame(() => this.render());
    }
    
    drawStartScreen() {
        this.ctx.fillStyle = 'rgb(1, 128, 1)';
        this.ctx.fillRect(0, 0, TheBestFootballGame.WINDOW_W, TheBestFootballGame.WINDOW_H);
        
        this.ctx.fillStyle = 'rgb(1, 96, 1)';
        const circleSize = 280;
        this.ctx.beginPath();
        this.ctx.arc(TheBestFootballGame.WINDOW_W / 2, TheBestFootballGame.WINDOW_H / 2, circleSize / 2, 0, Math.PI * 2);
        this.ctx.fill();
        
        this.ctx.fillStyle = 'white';
        this.ctx.font = 'bold 22px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.fillText('Click here to start!', TheBestFootballGame.WINDOW_W / 2, TheBestFootballGame.WINDOW_H / 2 + 8);
    }
    
    drawField() {
        const offsetY = TheBestFootballGame.SIDELINE_H;
        
        // Draw grass texture
        if (this.grassTexture) {
            this.ctx.drawImage(this.grassTexture, 0, offsetY);
        }
        
        // Draw midfield logo
        const logoGridX = 20;
        const logoDrawX = (logoGridX - this.cameraX) * TheBestFootballGame.TILE_SIZE;
        const logoDrawY = (TheBestFootballGame.VIEW_H * TheBestFootballGame.TILE_SIZE / 2) - (4 * TheBestFootballGame.TILE_SIZE / 2) + offsetY;
        
        const logoImg = this.images['TBFGE - Walrus Midfield Logo.png'];
        if (logoImg && logoImg.complete) {
            if (logoDrawX + 4 * TheBestFootballGame.TILE_SIZE > 0 && logoDrawX < TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE) {
                this.ctx.drawImage(logoImg, logoDrawX, logoDrawY, 4 * TheBestFootballGame.TILE_SIZE, 4 * TheBestFootballGame.TILE_SIZE);
            }
        }
        
        // Draw yard lines
        for (let i = 0; i < TheBestFootballGame.VIEW_W; i++) {
            const gridX = this.cameraX + i;
            const drawX = i * TheBestFootballGame.TILE_SIZE;
            
            if (gridX >= TheBestFootballGame.FIELD_START_X && gridX <= TheBestFootballGame.FIELD_END_X - 1) {
                this.ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
                this.ctx.fillRect(drawX, offsetY, 2, TheBestFootballGame.VIEW_H * TheBestFootballGame.TILE_SIZE);
            }
            
            // Draw endzones
            if (gridX < TheBestFootballGame.FIELD_START_X) {
                this.drawEndzoneSlice(gridX, drawX, offsetY, 'blue', true);
            }
            if (gridX >= TheBestFootballGame.FIELD_END_X) {
                this.drawEndzoneSlice(gridX - TheBestFootballGame.FIELD_END_X, drawX, offsetY, 'red', false);
            }
        }
    }
    
    drawEndzoneSlice(sliceIndex, drawX, drawY, fallbackColor, isLeft) {
        const sliceHeight = TheBestFootballGame.VIEW_H * TheBestFootballGame.TILE_SIZE;
        const img = this.images['TBFGE - Endzone Right.png'];
        
        if (!img || !img.complete) {
            this.ctx.fillStyle = fallbackColor;
            this.ctx.fillRect(drawX, drawY, TheBestFootballGame.TILE_SIZE, sliceHeight);
            return;
        }
        
        const srcWidthPerTile = img.width / 2;
        const srcX1 = sliceIndex * srcWidthPerTile;
        const srcX2 = srcX1 + srcWidthPerTile;
        
        this.ctx.save();
        if (isLeft) {
            this.ctx.translate(drawX + TheBestFootballGame.TILE_SIZE, drawY);
            this.ctx.scale(-1, 1);
            this.ctx.drawImage(img, srcX1, 0, srcWidthPerTile, img.height, 0, 0, TheBestFootballGame.TILE_SIZE, sliceHeight);
        } else {
            this.ctx.drawImage(img, srcX1, 0, srcWidthPerTile, img.height, drawX, drawY, TheBestFootballGame.TILE_SIZE, sliceHeight);
        }
        this.ctx.restore();
    }
    
    drawFirstDownMarker() {
        const img = this.images['TBFGE - First Down Marker.png'];
        if (!img || !img.complete) return;
        
        const markerLineX = this.firstDownMarkerX;
        
        if (markerLineX <= TheBestFootballGame.FIELD_START_X || markerLineX < this.cameraX || markerLineX > this.cameraX + TheBestFootballGame.VIEW_W) {
            return;
        }
        
        const drawX = ((markerLineX + 1) - this.cameraX) * TheBestFootballGame.TILE_SIZE;
        const markerW = TheBestFootballGame.TILE_SIZE / 3;
        const markerH = TheBestFootballGame.TILE_SIZE / 4;
        const drawY = TheBestFootballGame.WINDOW_H - TheBestFootballGame.SIDELINE_H - markerH;
        
        this.ctx.drawImage(img, drawX - markerW / 2, drawY, markerW, markerH);
    }
    
    drawSidelines() {
        this.ctx.fillStyle = TheBestFootballGame.SIDELINE_COLOR;
        this.ctx.fillRect(0, 0, TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE, TheBestFootballGame.SIDELINE_H);
        this.ctx.fillRect(0, TheBestFootballGame.WINDOW_H - TheBestFootballGame.SIDELINE_H, TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE, TheBestFootballGame.SIDELINE_H);
    }
    
    drawEntities() {
        const offX = -this.cameraX * TheBestFootballGame.TILE_SIZE;
        const offY = TheBestFootballGame.SIDELINE_H;
        
        // Draw knocked down defenders
        for (let d of this.defenders) {
            if (d.isKnockedDown) {
                this.drawSprite('TBFGE - Defender Knocked Down.png', d.x, d.y, offX, offY, false);
            }
        }
        
        // Draw player
        if (this.gameState === TheBestFootballGame.GameState.TACKLED) {
            this.drawSprite('TBFGE - Player Tackled.png', this.player.x, this.player.y, offX, offY, false);
        } else {
            let spriteName = 'TBFGE - Player Standing Left.png';
            let flip = !this.player.facingLeft;
            
            if (this.player.state === Player.State.RUN_SIDE) {
                spriteName = 'TBFGE - Player Running Left.png';
            } else if (this.player.state === Player.State.RUN_UP) {
                spriteName = 'TBFGE - Player Running Up - Right Foot Down.png';
                flip = this.player.stepLeftFoot;
            } else if (this.player.state === Player.State.RUN_DOWN) {
                spriteName = 'TBFGE - Player Running Down - Right Foot Down.png';
                flip = this.player.stepLeftFoot;
            }
            
            this.drawSprite(spriteName, this.player.x, this.player.y, offX, offY, flip);
        }
        
        // Draw active defenders
        for (let d of this.defenders) {
            if (!d.isKnockedDown) {
                if (this.gameState === TheBestFootballGame.GameState.TACKLED && this.tackleSource && d.x === this.tackleSource.x && d.y === this.tackleSource.y) {
                    const flipTackler = this.player.x < d.x;
                    this.drawSprite('TBFGE - Defender Tackling.png', d.x, d.y, offX, offY, flipTackler);
                } else {
                    this.drawSprite('TBFGE - Defender Facing Right.png', d.x, d.y, offX, offY, !d.facingRight);
                }
            }
        }
        
        // Draw referees
        for (let r of this.referees) {
            this.drawSprite('TBFGE - Referee Facing Right.png', r.x, r.y, offX, offY, !r.facingRight);
        }
        
        // Draw tackle flash
        if (this.gameState === TheBestFootballGame.GameState.TACKLED && this.tackleSource) {
            const img = this.images['TBFGE - Tackle Flash.png'];
            if (img && img.complete) {
                const pX = (this.player.x * TheBestFootballGame.TILE_SIZE) + offX;
                const pY = (this.player.y * TheBestFootballGame.TILE_SIZE) + offY;
                const dX = (this.tackleSource.x * TheBestFootballGame.TILE_SIZE) + offX;
                const dY = (this.tackleSource.y * TheBestFootballGame.TILE_SIZE) + offY;
                
                const midX = (pX + dX + TheBestFootballGame.TILE_SIZE) / 2;
                const midY = (pY + dY + TheBestFootballGame.TILE_SIZE) / 2;
                
                const flashW = TheBestFootballGame.TILE_SIZE * 2;
                const flashH = TheBestFootballGame.TILE_SIZE;
                
                this.ctx.drawImage(img, midX - flashW / 2, midY - flashH / 2, flashW, flashH);
            }
        }
    }
    
    drawSprite(imageName, gridX, gridY, offsetX, offsetY, flipHorizontal) {
        const img = this.images[imageName];
        if (!img || !img.complete) return;
        if (gridX < this.cameraX || gridX >= this.cameraX + TheBestFootballGame.VIEW_W) return;
        
        const x = (gridX * TheBestFootballGame.TILE_SIZE) + offsetX;
        const y = (gridY * TheBestFootballGame.TILE_SIZE) + offsetY;
        
        this.ctx.save();
        if (flipHorizontal) {
            this.ctx.translate(x + TheBestFootballGame.TILE_SIZE, y);
            this.ctx.scale(-1, 1);
            this.ctx.drawImage(img, 0, 0, TheBestFootballGame.TILE_SIZE, TheBestFootballGame.TILE_SIZE);
        } else {
            this.ctx.drawImage(img, x, y, TheBestFootballGame.TILE_SIZE, TheBestFootballGame.TILE_SIZE);
        }
        this.ctx.restore();
    }
    
    drawTouchdownAnim() {
        const img = this.images['TBFGE - Touch Down.png'];
        if (!img || !img.complete) return;
        
        const w = img.width / 2;
        const h = img.height / 2;
        const drawX = (TheBestFootballGame.FIELD_START_X * TheBestFootballGame.TILE_SIZE) + 1;
        const drawY = (TheBestFootballGame.WINDOW_H / 2) - h / 2;
        
        this.ctx.drawImage(img, drawX, drawY, w, h);
    }
    
    drawScoreboard() {
        const x = TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE;
        
        this.ctx.fillStyle = 'black';
        this.ctx.fillRect(x, 0, TheBestFootballGame.SCOREBOARD_W, TheBestFootballGame.WINDOW_H);
        
        const img = this.images['TBFGE - Scoreboard Start.png'];
        if (img && img.complete) {
            this.ctx.drawImage(img, x, 0, TheBestFootballGame.SCOREBOARD_W, TheBestFootballGame.WINDOW_H);
        }
        
        this.ctx.fillStyle = 'black';
        this.ctx.font = '20px Impact';
        this.ctx.textAlign = 'center';
        
        this.ctx.fillText(String(this.timeRemaining), x + TheBestFootballGame.SCOREBOARD_W / 2, 74);
        this.ctx.fillText(String(this.score), x + TheBestFootballGame.SCOREBOARD_W / 2, 139);
        this.ctx.fillText(String(this.yardsToGo), x + TheBestFootballGame.SCOREBOARD_W / 2, 224);
        this.ctx.fillText(String(this.attemptsRemaining), x + TheBestFootballGame.SCOREBOARD_W / 2, 313);
    }
    
    drawGameOver() {
        this.ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
        this.ctx.fillRect(0, 0, TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE, TheBestFootballGame.WINDOW_H);
        
        this.ctx.fillStyle = 'white';
        this.ctx.font = 'bold 40px Arial';
        this.ctx.textAlign = 'center';
        
        const msg = this.timeRemaining <= 0 ? "TIME'S UP!" : "GAME OVER";
        this.ctx.fillText(msg, (TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE) / 2, TheBestFootballGame.WINDOW_H / 2);
        
        this.ctx.font = 'bold 20px Arial';
        this.ctx.fillText('Press SPACE to Restart', (TheBestFootballGame.VIEW_W * TheBestFootballGame.TILE_SIZE) / 2, TheBestFootballGame.WINDOW_H / 2 + 50);
    }
}

// Entity Classes
class Player {
    static State = {
        STAND: 'STAND',
        RUN_SIDE: 'RUN_SIDE',
        RUN_UP: 'RUN_UP',
        RUN_DOWN: 'RUN_DOWN'
    };
    
    constructor(x, y) {
        this.x = x;
        this.y = y;
        this.facingLeft = true;
        this.stepLeftFoot = false;
        this.state = Player.State.RUN_SIDE;
    }
}

class Defender {
    constructor(x, y) {
        this.x = x;
        this.y = y;
        this.isKnockedDown = false;
        this.facingRight = true;
    }
}

class Referee {
    constructor(x, y) {
        this.x = x;
        this.y = y;
        this.facingRight = true;
    }
}

// Initialize game when page loads
window.addEventListener('load', () => {
    const canvas = document.getElementById('gameCanvas');
    if (canvas) {
        new TheBestFootballGame(canvas);
    }
});
