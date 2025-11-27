# The Best Football Game

A retro-style football game where you run towards the endzone, avoid defenders, and score touchdowns!

## 🎮 Play Online

[Play the game here](https://dhbroad.github.io/the-best-football-game-ever/) *(Update this URL after deploying)*

## 📋 Game Features

- **Grid-based gameplay** with smooth camera scrolling
- **First down system** - Advance 10 yards in 4 attempts
- **Dynamic difficulty** - More defenders spawn after each touchdown
- **60-second time limit** - Score as many points as possible
- **Retro graphics** with custom sprites and animations

## 🕹️ Controls

- **Arrow Keys** - Move the player (Up, Down, Left, Right)
- **Space** - Restart game (when game over)
- **Click** - Start game from menu

## 🎯 How to Play

1. Click to start the game
2. Use arrow keys to move towards the left endzone (blue)
3. Avoid defenders (red jerseys) or knock them down by running into them
4. Referees (striped shirts) cannot be knocked down - avoid them!
5. Score touchdowns (7 points each) by reaching the endzone
6. Advance 10 yards within 4 attempts to get a first down
7. Game ends when time runs out or you fail to get a first down

## 🚀 Local Development

### Running Locally

1. Clone the repository
2. Open `index.html` in a web browser, or
3. Use a local web server:
   ```bash
   # Using Python
   python -m http.server 8000
   
   # Using Node.js
   npx http-server -p 8000
   ```
4. Navigate to `http://localhost:8000`

### Project Structure

```
├── index.html          # Main HTML file
├── game.js            # Game logic and rendering
├── style.css          # Styling
├── TBFGE - *.png      # Game sprites
├── *.wav              # Sound effects
└── README.md          # This file
```

## 🌐 GitHub Pages Deployment

### Option 1: Deploy from Branch (Recommended)

1. Push the `web-version` branch to GitHub:
   ```bash
   git add .
   git commit -m "Add web version of the game"
   git push origin web-version
   ```

2. Go to your repository settings on GitHub
3. Navigate to **Pages** section
4. Under "Source", select the `web-version` branch
5. Select the root folder `/`
6. Click **Save**
7. Your game will be available at: `https://[username].github.io/[repo-name]/`

### Option 2: Deploy from Main Branch

If you want to merge the web version to main:

```bash
git checkout main
git merge web-version
git push origin main
```

Then follow the same GitHub Pages setup steps above, but select the `main` branch.

## 🎨 Assets

All game assets (sprites and sounds) are included in the repository:
- Player sprites (running, standing, tackled)
- Defender sprites (running, knocked down, tackling)
- Referee sprites
- Endzone graphics
- Scoreboard
- Sound effects (cheer, whistle, steps, etc.)

## 🔧 Technical Details

- **Original Version**: Java Swing application
- **Web Version**: Pure JavaScript with HTML5 Canvas
- **No dependencies** - Runs entirely in the browser
- **Responsive design** - Adapts to different screen sizes

## 📝 Version History

- **Java Version** (main branch) - Original Swing-based desktop game
- **Web Version** (web-version branch) - JavaScript/Canvas port for web browsers

## 📄 License

This is a personal project. Feel free to fork and modify for your own use!

## 🐛 Known Issues

- Sound may not play on first interaction due to browser autoplay policies
- Mobile touch controls not yet implemented (keyboard only)

## 🎯 Future Enhancements

- [ ] Touch/mobile controls
- [ ] High score tracking
- [ ] Multiple difficulty levels
- [ ] Power-ups and special moves
- [ ] Multiplayer mode

---

Made with ❤️ by dhbroad
