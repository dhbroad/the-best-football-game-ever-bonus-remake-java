// Cloudflare Worker for The Best Football Game Ever Leaderboard
// This proxies requests to Dreamlo and keeps the private URL secret

export default {
  async fetch(request, env) {
    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type',
        },
      });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    // CORS headers for all responses
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Content-Type': 'application/json',
    };

    try {
      // GET /leaderboard - Fetch scores
      if (path === '/leaderboard' && request.method === 'GET') {
        const limit = url.searchParams.get('limit') || '10';
        const dreamloPublicCode = '693870268f40bb1864794c6f';
        const dreamloUrl = `http://dreamlo.com/lb/${dreamloPublicCode}/json/${limit}`;
        
        const response = await fetch(dreamloUrl);
        const data = await response.text();
        
        // Parse Dreamlo response
        let scores = [];
        try {
          const parsed = JSON.parse(data);
          if (parsed.dreamlo && parsed.dreamlo.leaderboard) {
            // Multiple entries
            const entries = parsed.dreamlo.leaderboard.entry;
            scores = Array.isArray(entries) ? entries : [entries];
          }
        } catch (e) {
          // Empty leaderboard or error
          scores = [];
        }
        
        return new Response(JSON.stringify({ scores }), {
          headers: corsHeaders,
        });
      }

      // POST /submit - Submit a new score
      if (path === '/submit' && request.method === 'POST') {
        const body = await request.json();
        const { name, score } = body;

        // Validation
        if (!name || !score) {
          return new Response(JSON.stringify({ error: 'Name and score required' }), {
            status: 400,
            headers: corsHeaders,
          });
        }

        // Sanitize name (max 3 chars, alphanumeric only)
        const sanitizedName = String(name).slice(0, 3).toUpperCase().replace(/[^A-Z0-9]/g, '');
        if (sanitizedName.length === 0) {
          return new Response(JSON.stringify({ error: 'Invalid name' }), {
            status: 400,
            headers: corsHeaders,
          });
        }

        // Validate score (must be positive integer, reasonable range)
        const numScore = parseInt(score);
        if (isNaN(numScore) || numScore < 0 || numScore > 10000) {
          return new Response(JSON.stringify({ error: 'Invalid score' }), {
            status: 400,
            headers: corsHeaders,
          });
        }

        // Get private code from environment variable
        const dreamloPrivateCode = env.DREAMLO_PRIVATE_CODE;
        if (!dreamloPrivateCode) {
          return new Response(JSON.stringify({ error: 'Server configuration error' }), {
            status: 500,
            headers: corsHeaders,
          });
        }

        // Submit to Dreamlo
        const dreamloUrl = `http://dreamlo.com/lb/${dreamloPrivateCode}/add/${sanitizedName}/${numScore}`;
        const response = await fetch(dreamloUrl);
        
        if (response.ok) {
          return new Response(JSON.stringify({ success: true, name: sanitizedName, score: numScore }), {
            headers: corsHeaders,
          });
        } else {
          return new Response(JSON.stringify({ error: 'Failed to submit score' }), {
            status: 500,
            headers: corsHeaders,
          });
        }
      }

      // Invalid endpoint
      return new Response(JSON.stringify({ error: 'Not found' }), {
        status: 404,
        headers: corsHeaders,
      });

    } catch (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: corsHeaders,
      });
    }
  },
};
