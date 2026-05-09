require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { createClient } = require('redis');
const axios = require('axios');
const path = require('path');
const youtubedl = require('youtube-dl-exec');

const app = express();
const port = process.env.PORT || 8080;

app.use(cors());
app.use(express.json());

let redisClient;
let useRedis = false;
let memoryCache = {}; // Fallback in-memory cache

async function setupRedis() {
    try {
        redisClient = createClient({
            url: `redis://${process.env.REDIS_HOST || 'localhost'}:6379`,
            socket: {
                connectTimeout: 2000,
                reconnectStrategy: false // <- Aquí estaba el problema, Redis reconecta por defecto
            }
        });
        
        redisClient.on('error', err => {
            if (useRedis) { // Solo avisar la primera vez
                 console.warn('⚠️ Conexión Redis perdida. Usando caché en memoria RAM.');
                 useRedis = false;
            }
        });
        
        await redisClient.connect();
        useRedis = true;
        console.log('✅ Conectado a Redis');
    } catch (e) {
        console.warn('⚠️ No se pudo conectar a Redis. Usando caché en memoria RAM.');
        useRedis = false;
    }
}

// Helpers abstractos de Caché
async function getCache(key) {
    if (useRedis) {
        try { return await redisClient.get(key); } catch (e) { return null; }
    } else {
        const entry = memoryCache[key];
        if (entry && entry.expiresAt > Date.now()) return entry.data;
        return null;
    }
}

async function setCache(key, seconds, data) {
    if (useRedis) {
        try { await redisClient.setEx(key, seconds, data); } catch (e) { }
    } else {
        memoryCache[key] = {
            data: data,
            expiresAt: Date.now() + (seconds * 1000)
        };
    }
}

async function startServer() {
    await setupRedis();
    
    // --- Mock Data ---
    const MOCK_CAROUSELS = [
        {
            title: "🇵🇪 Tendencias en Perú",
            type: "trending",
            items: [
                { id: "MOCK1", title: "Mix Cumbia Peruana", artist: "Varios", durationSeconds: 300, thumbnailUrl: "https://via.placeholder.com/150" },
                { id: "MOCK2", title: "Rock de los 80", artist: "Bandas Clásicas", durationSeconds: 240, thumbnailUrl: "https://via.placeholder.com/150" }
            ]
        },
        {
             title: "🔥 Éxitos de Reggaetón",
             type: "genre",
             items: [
                { id: "MOCK3", title: "Perreo Intenso", artist: "Varios Artistas", durationSeconds: 180, thumbnailUrl: "https://via.placeholder.com/150" }
             ]
        }
    ];

    // --- Endpoints ---
    
    // Health Check
    app.get('/health', (req, res) => {
        res.json({ status: 'ok', service: 'AuraMusic BFF' });
    });

    // Lista de mirrors de Piped para balancear la carga y evitar 500s
    const PIPED_INSTANCES = [
        "https://pipedapi.kavin.rocks",
        "https://api.piped.private.coffee",
        "https://pipedapi.in.projectsegfau.lt",
        "https://piped-api.garudalinux.org"
    ];

    // Helper para hacer fetch con fallback concurrente usando Promise.any
    async function pipedFetch(path) {
        // Ejecutamos peticiones en paralelo a todos los mirrors
        const promises = PIPED_INSTANCES.map(async (instance) => {
            const url = `${instance}${path}`;
            const response = await axios.get(url, { timeout: 8000 });
            if (response.status === 200 && response.data) {
                // Validación básica de forma según el path
                if (path.includes('/streams/') && !response.data.audioStreams) {
                     console.log(`[Mirror Check] ${instance} NO tiene audioStreams (Data Keys: ${Object.keys(response.data).length})`);
                     throw new Error(`Mirror ${instance} no devolvió audioStreams`);
                }
                if (path.includes('/search') && !response.data.items) {
                     console.log(`[Mirror Check] ${instance} NO tiene items`);
                     throw new Error(`Mirror ${instance} no devolvió items`);
                }
                console.log(`[Mirror Check] ✅ Éxito validado desde: ${instance}`);
                return response;
            }
            throw new Error(`HTTP ${response.status} o data inválida desde ${instance}`);
        });

        try {
            // Promise.any resuelve con la primera promesa que sea exitosa
            return await Promise.any(promises);
        } catch (aggregateError) {
            console.error(`[Piped Fallback] Todas las instancias fallaron para ${path}.`, aggregateError.errors);
            throw new Error("Todas las instancias de Piped fallaron.");
        }
    }

    // GET /api/v1/home/feed
    app.get('/api/v1/home/feed', async (req, res) => {
        try {
            const cachedFeed = await getCache('home_feed_cache');
            if (cachedFeed) {
                return res.json(JSON.parse(cachedFeed));
            }
            
            console.log('Fetching Home Feed from Piped Catalog...');
            
            // Hacemos peticiones para armar el Home usando el helper con fallback
            const [trendingResp, rockResp, popResp] = await Promise.all([
                pipedFetch(`/search?q=Top+Canciones+Peru&filter=music_songs`),
                pipedFetch(`/search?q=Top+Rock+hits&filter=music_songs`),
                pipedFetch(`/search?q=Top+Pop+hits&filter=music_songs`),
            ]);

            const mapTrack = (item) => ({
                id: (item.url || "").replace('/watch?v=', ''),
                title: item.title || "Unknown",
                artist: item.uploaderName || "Unknown Artist",
                durationSeconds: item.duration || 0,
                thumbnailUrl: item.thumbnail || ""
            });

            const trending = (trendingResp.data.items || []).slice(0, 15).map(mapTrack);
            const rock = (rockResp.data.items || []).slice(0, 10).map(mapTrack);
            const pop = (popResp.data.items || []).slice(0, 10).map(mapTrack);

            const responseData = { 
                carousels: [
                    { title: "🇵🇪 Tendencias en Perú", type: "trending", items: trending },
                    { title: "🌎 Descubrimiento Rock", type: "discovery", items: rock },
                    { title: "🎤 Éxitos Pop", type: "genre", items: pop }
                ] 
            };
            
            // Guardar por 4 horas el feed estático
            await setCache('home_feed_cache', 14400, JSON.stringify(responseData));
            
            return res.json(responseData);
        } catch (error) {
            console.error(error);
            res.status(500).json({ error: 'Error interno del servidor' });
        }
    });

    // GET /api/v1/search
    app.get('/api/v1/search', async (req, res) => {
        const { q, type } = req.query;
        if (!q) return res.status(400).json({ error: "Falta el término de búsqueda 'q'" });

        try {
            // Unificamos todo el tráfico al backend. El BFF consulta a Piped y devuelve nuestro contrato exacto.
            let filter = "music_songs"; // default tracks
            if (type === "artists") filter = "music_artists";
            else if (type === "playlists") filter = "music_playlists";
            else if (type === "all") filter = "all";

            const response = await pipedFetch(`/search?q=${encodeURIComponent(q)}&filter=${filter}`);

            const results = (response.data.items || []).map(item => {
                const itemType = item.type === 'stream' ? 'track' : (item.type === 'channel' ? 'artist' : 'playlist');
                return {
                    id: (item.url || "").replace('/watch?v=', '').replace('/channel/', '').replace('/playlist?list=', ''),
                    title: item.title || "Unknown",
                    type: itemType,
                    thumbnailUrl: item.thumbnail || "",
                    artist: item.uploaderName || "Unknown Artist",
                    durationSeconds: item.duration || 0,
                    itemCount: item.videos || 0
                };
            }).filter(i => i.id && (i.type === "track" || i.type === "artist" || i.type === "playlist"));

            res.json({ query: q, results });

        } catch (error) {
            console.error(`Search error for '${q}':`, error.message);
            res.status(500).json({ error: "Error en búsqueda" });
        }
    });

    // GET /api/v1/stream/:trackId
    app.get('/api/v1/stream/:trackId', async (req, res) => {
        const { trackId } = req.params;
        
        try {
            // 1. Verificar si la URL ya está scrapeada y fresquita en Redis (Caché por 2 horas)
            const cacheKey = `stream_url:${trackId}`;
            const cachedStream = await getCache(cacheKey);
            
            if (cachedStream) {
                console.log(`⚡ Caché HIT para track: ${trackId}`);
                const data = JSON.parse(cachedStream);
                return res.json({
                    streamUrl: data.url,
                    format: data.format,
                    expireAt: data.expireAt
                });
            }

            console.log(`⏳ Extrayendo audio para track: ${trackId}...`);
            
            let bestStreamUrl = null;
            let bestFormat = 'm4a';

            // 2. Intento via Piped API (Más rápido si los mirrors funcionan)
            try {
                const pipedResponse = await pipedFetch(`/streams/${trackId}`);
                const audioStreams = pipedResponse.data.audioStreams;
                const pipedBest = audioStreams
                    .filter(s => s.url)
                    .find(s => s.format === "M4A") || audioStreams[0];
                
                if (pipedBest && pipedBest.url) {
                    bestStreamUrl = pipedBest.url;
                    bestFormat = pipedBest.format || 'm4a';
                    console.log(`✅ Track extraído via Piped: ${trackId}`);
                }
            } catch (pipedError) {
                console.warn(`[Stream] Falló Piped para ${trackId}, intentando yt-dlp local...`);
            }

            // 3. Intento via yt-dlp local (Robusto pero más lento)
            if (!bestStreamUrl) {
                try {
                    const ytDlpPath = path.join(__dirname, 'yt-dlp.exe');
                    console.log(`[Stream] Intentando yt-dlp en: ${ytDlpPath}`);
                    const output = await youtubedl(`https://www.youtube.com/watch?v=${trackId}`, {
                        dumpSingleJson: true,
                        noCheckCertificates: true,
                        format: 'bestaudio[ext=m4a]/bestaudio',
                        executablePath: ytDlpPath
                    });
                    
                    if (output && output.url) {
                        bestStreamUrl = output.url;
                        bestFormat = 'm4a';
                        console.log(`✅ Track extraído via yt-dlp: ${trackId}`);
                    } else {
                        console.warn(`[Stream] yt-dlp no devolvió URL para ${trackId}`);
                    }
                } catch (ytdlpError) {
                    console.error(`[Stream] Falló yt-dlp para ${trackId}:`, ytdlpError.message);
                }
            }

            if (!bestStreamUrl) {
                return res.status(404).json({ error: "No se pudo extraer el audio de ninguna fuente." });
            }

            // Expiración simulada para el token de YouTube
            const expireAt = Math.floor(Date.now() / 1000) + (2 * 3600); 
            
            const responseData = {
                streamUrl: bestStreamUrl,
                format: bestFormat,
                expireAt: expireAt
            };

            // Guardar en Caché
            await setCache(cacheKey, 7200, JSON.stringify({ url: bestStreamUrl, format: bestFormat, expireAt }));
            
            res.json(responseData);

        } catch (error) {
            console.error(`❌ Error general en stream extraction para ${trackId}:`, error.message);
            res.status(500).json({ error: "Error interno procesando el audio." });
        }
    });

    // POST /api/v1/analytics/play
    app.post('/api/v1/analytics/play', async (req, res) => {
        const { trackId, source } = req.body;
        if (!trackId) return res.status(400).json({ error: "Falta trackId" });
        
        try {
            // Incrementar contador atómico en Redis si está disponible
            if (useRedis) {
                const key = `stats:plays:${trackId}`;
                await redisClient.incr(key);
            }
            console.log(`📊 [Telemetry] Play registrado para: ${trackId} via ${source || 'unknown'}`);
            res.status(200).json({ success: true });
        } catch (error) {
            console.error(`❌ [Telemetry] Error:`, error.message);
            res.status(500).json({ error: "Internal Server Error" });
        }
    });

    app.listen(port, () => {
        console.log(`🚀 AuraMusic BFF corriendo en http://localhost:${port}`);
    });
}

startServer().catch(console.error);
