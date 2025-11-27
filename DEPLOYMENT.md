# Deployment Guide for GitHub Pages

## Quick Start

Follow these steps to deploy your game to GitHub Pages:

### 1. Commit Your Changes

```bash
git add .
git commit -m "Convert Java game to JavaScript/HTML5 Canvas for web"
git push origin web-version
```

### 2. Enable GitHub Pages

1. Go to your GitHub repository: `https://github.com/dhbroad/the-best-football-game-ever`
2. Click on **Settings** (top right)
3. Scroll down to **Pages** in the left sidebar
4. Under **Source**, select:
   - Branch: `web-version`
   - Folder: `/ (root)`
5. Click **Save**

### 3. Wait for Deployment

- GitHub will automatically build and deploy your site
- This usually takes 1-2 minutes
- You'll see a green checkmark when it's ready

### 4. Access Your Game

Your game will be live at:
```
https://dhbroad.github.io/the-best-football-game-ever/
```

## Alternative: Deploy from Main Branch

If you prefer to have the web version on the main branch:

```bash
# Switch to main branch
git checkout main

# Merge web-version into main
git merge web-version

# Push to GitHub
git push origin main
```

Then in GitHub Pages settings, select the `main` branch instead.

## Troubleshooting

### Game doesn't load
- Check browser console for errors (F12)
- Ensure all asset files (PNG, WAV) are committed
- Verify the repository is public (or you have GitHub Pro for private repos)

### Assets not loading
- GitHub Pages is case-sensitive
- Verify all file paths match exactly
- Check that all image and sound files are in the repository

### 404 Error
- Wait a few minutes after enabling GitHub Pages
- Check the repository name matches the URL
- Ensure the branch and folder are correctly selected

## Custom Domain (Optional)

To use a custom domain:

1. Add a `CNAME` file to the repository root with your domain
2. Configure DNS settings with your domain provider
3. Update GitHub Pages settings with your custom domain

## Testing Locally Before Deployment

```bash
# Option 1: Simple file open
# Just open index.html in your browser

# Option 2: Local server (recommended)
npx http-server -p 8000

# Then visit: http://localhost:8000
```

## File Checklist

Ensure these files are in your repository:
- ✅ index.html
- ✅ game.js
- ✅ style.css
- ✅ README.md
- ✅ All TBFGE - *.png files (15 images)
- ✅ All *.wav files (5 sounds)
- ✅ .gitignore

## Next Steps After Deployment

1. Update the README.md with your actual GitHub Pages URL
2. Test the game on different browsers
3. Share the link with friends!
4. Consider adding:
   - Google Analytics
   - Social media meta tags
   - Favicon

---

Need help? Check the [GitHub Pages documentation](https://docs.github.com/en/pages)
