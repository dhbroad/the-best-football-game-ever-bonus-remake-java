# Leaderboard Setup Guide

## Step-by-Step Instructions

### Part 1: Deploy Cloudflare Worker

1. **Go to Cloudflare Workers**
   - Visit: https://workers.cloudflare.com/
   - Sign up or log in

2. **Create New Worker**
   - Click "Start with Hello World!"
   - Name it: `football-game-leaderboard` (or your choice)
   - Click "Deploy"

3. **Edit the Worker Code**
   - After deployment, click "Edit Code" or "Quick Edit"
   - **Delete ALL the existing code**
   - Copy the ENTIRE contents of `cloudflare-worker.js` from your project
   - Paste it into the Cloudflare editor
   - Click "Save and Deploy"

4. **Add Environment Variable (Secret)**
   - In the Worker dashboard, go to "Settings" tab
   - Find "Environment Variables" section
   - Click "Add variable"
   - Name: `DREAMLO_PRIVATE_CODE`
   - Value: Your Dreamlo private code (the long one you were given)
   - Click "Encrypt" (important!)
   - Click "Save"

5. **Get Your Worker URL**
   - Your Worker URL will be something like:
   - `https://football-game-leaderboard.YOUR-SUBDOMAIN.workers.dev`
   - Copy this URL - you'll need it in the next step

### Part 2: Update Your Game Code

1. **Open `game.js`**
   - Find line 1132 (near the bottom)
   - Look for: `const WORKER_URL = 'https://YOUR-WORKER-NAME.YOUR-SUBDOMAIN.workers.dev';`

2. **Replace with Your Worker URL**
   ```javascript
   const WORKER_URL = 'https://football-game-leaderboard.YOUR-SUBDOMAIN.workers.dev';
   ```
   (Use the actual URL from Step 1.5)

3. **Save the file**

### Part 3: Test Locally

1. **Open `index.html` in your browser**
   - You should see the leaderboard section
   - It should say "No scores yet. Be the first!" (if empty)

2. **Play the game until Game Over**
   - A modal should pop up asking for initials
   - Enter 3 letters/numbers
   - Click "Submit Score"
   - You should see "Score submitted! 🎉"

3. **Check the leaderboard**
   - Refresh the page
   - Your score should appear in the leaderboard

### Part 4: Deploy to GitHub Pages

1. **Commit your changes**
   ```bash
   git add .
   git commit -m "Add Cloudflare Worker leaderboard integration"
   git push origin web-version
   ```

2. **Wait for GitHub Pages to update** (1-2 minutes)

3. **Test on your live site**
   - Visit your GitHub Pages URL
   - Play the game and submit a score
   - Verify it appears in the leaderboard

## Troubleshooting

### Leaderboard shows "Failed to load leaderboard"
- Check that your Worker URL is correct in `game.js`
- Make sure the Worker is deployed and running
- Check browser console for errors (F12)

### "Server configuration error" when submitting
- Make sure you added the `DREAMLO_PRIVATE_CODE` environment variable
- Make sure you clicked "Encrypt" on the variable
- Redeploy the Worker after adding the variable

### Scores not appearing
- Check that Dreamlo is working by visiting:
  `http://dreamlo.com/lb/693870268f40bb1864794c6f`
- This should show your scores in JSON format

### CORS errors in console
- The Worker code already handles CORS
- Make sure you deployed the Worker code exactly as provided
- Try clearing browser cache (Ctrl+Shift+R)

## Testing the Worker Directly

You can test your Worker endpoints directly:

**Get Leaderboard:**
```
https://YOUR-WORKER-URL.workers.dev/leaderboard?limit=10
```

**Submit Score (using curl or Postman):**
```bash
curl -X POST https://YOUR-WORKER-URL.workers.dev/submit \
  -H "Content-Type: application/json" \
  -d '{"name":"ABC","score":100}'
```

## Features

✅ **Top 10 scores** displayed by default
✅ **"Show All Scores"** button to see all 25
✅ **Modal popup** when game ends
✅ **Input validation** - only 3 alphanumeric characters
✅ **Score validation** - prevents impossible scores
✅ **Secure** - Private Dreamlo URL hidden in Worker
✅ **Auto-refresh** - Leaderboard updates after submission

## Customization

### Change number of scores displayed
In `game.js`, find the `LeaderboardManager` class and modify:
- Line 1001: Change `25` to show more/fewer total scores
- Line 1006: Change `10` to show different default count

### Change modal styling
Edit `style.css` starting at line 214 (Modal Styles section)

### Change leaderboard styling
Edit `style.css` starting at line 120 (Leaderboard Section)

---

**Need help?** Check the Cloudflare Workers docs: https://developers.cloudflare.com/workers/
