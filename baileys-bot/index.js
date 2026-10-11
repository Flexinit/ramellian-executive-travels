require('dotenv').config();

const {
    default: makeWASocket,
    useMultiFileAuthState,
    DisconnectReason,
    fetchLatestBaileysVersion,
} = require('@whiskeysockets/baileys');
const { Boom } = require('@hapi/boom');
const express = require('express');
const axios = require('axios');
const fs = require('fs');
const pino = require('pino');
const qrcode = require('qrcode-terminal');
const QRCode = require('qrcode');

const app = express();
app.use(express.json({ limit: '50mb' }));

const SPRING_URL = process.env.SPRING_BOOT_URL || 'http://localhost:8080';
const PORT = parseInt(process.env.PORT || '3001');
const logger = pino({ level: process.env.LOG_LEVEL || 'warn' });

let sock = null;

// ── WhatsApp connection ───────────────────────────────────────────────────────

async function connectToWhatsApp() {
    const { state, saveCreds } = await useMultiFileAuthState('./auth_info_baileys');
    const { version } = await fetchLatestBaileysVersion();

    sock = makeWASocket({
        version,
        auth: state,
        logger,
        browser: ['Ramellian Bot', 'Chrome', '1.0.0'],
        generateHighQualityLinkPreview: false,
    });

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('connection.update', async (update) => {
        const { connection, lastDisconnect, qr } = update;

        if (qr) {
            const qrPath = './qr.png';
            QRCode.toFile(qrPath, qr, { scale: 8 }, (err) => {
                if (!err) console.log(`\n📱 QR code saved → open this file and scan it in WhatsApp:\n   ${require('path').resolve(qrPath)}\n\nWhatsApp → Settings → Linked Devices → Link a Device\n`);
            });
        }
        if (connection === 'close') {
            const code = (lastDisconnect?.error instanceof Boom)
                ? lastDisconnect.error.output.statusCode
                : 0;
            const shouldReconnect = code !== DisconnectReason.loggedOut;
            console.log(`Connection closed (${code}). Reconnecting: ${shouldReconnect}`);
            if (shouldReconnect) {
                setTimeout(connectToWhatsApp, 3000);
            } else {
                console.log('Logged out. Delete auth_info_baileys/ and restart to re-pair.');
                process.exit(1);
            }
        } else if (connection === 'open') {
            console.log('✅ WhatsApp connected');
        }
    });

    sock.ev.on('messages.upsert', async ({ messages, type }) => {
        if (type !== 'notify') return;

        for (const msg of messages) {
            if (msg.key.fromMe) continue;
            const from = msg.key.remoteJid;
            if (!from || !from.endsWith('@s.whatsapp.net')) continue;

            const text = extractText(msg);
            if (!text) continue;

            // Phone without the @s.whatsapp.net suffix, digits only
            const phone = from.replace('@s.whatsapp.net', '');

            try {
                await axios.post(`${SPRING_URL}/api/v1/bot/whatsapp/inbound`,
                    { phone, message: text },
                    { timeout: 60000 }
                );
            } catch (err) {
                console.error(`Failed to forward message from ${phone}:`, err.message);
                await safeReply(from, "Sorry, I'm having a brief issue. Please try again in a moment.");
            }
        }
    });
}

function extractText(msg) {
    return msg.message?.conversation
        || msg.message?.extendedTextMessage?.text
        || msg.message?.imageMessage?.caption
        || null;
}

async function safeReply(jid, text) {
    try {
        await sock.sendMessage(jid, { text });
    } catch (e) {
        console.error('Failed to send fallback reply:', e.message);
    }
}

function toJid(phone) {
    // Ensure digits only, no + prefix
    const digits = phone.replace(/[^0-9]/g, '');
    return digits + '@s.whatsapp.net';
}

// ── Outbound API (called by Spring Boot) ─────────────────────────────────────

app.post('/send/text', async (req, res) => {
    const { phone, message } = req.body;
    if (!sock) return res.status(503).json({ error: 'Not connected' });
    try {
        await sock.sendMessage(toJid(phone), { text: message });
        res.json({ ok: true });
    } catch (e) {
        console.error('send/text error:', e.message);
        res.status(500).json({ error: e.message });
    }
});

app.post('/send/image', async (req, res) => {
    const { phone, imageUrl, caption } = req.body;
    if (!sock) return res.status(503).json({ error: 'Not connected' });
    try {
        await sock.sendMessage(toJid(phone), {
            image: { url: imageUrl },
            caption: caption || '',
        });
        res.json({ ok: true });
    } catch (e) {
        console.error('send/image error:', e.message);
        res.status(500).json({ error: e.message });
    }
});

app.post('/send/document', async (req, res) => {
    const { phone, filePath, fileName, mimetype } = req.body;
    if (!sock) return res.status(503).json({ error: 'Not connected' });
    try {
        const buffer = fs.readFileSync(filePath);
        await sock.sendMessage(toJid(phone), {
            document: buffer,
            mimetype: mimetype || 'application/pdf',
            fileName: fileName || 'document.pdf',
        });
        res.json({ ok: true });
    } catch (e) {
        console.error('send/document error:', e.message);
        res.status(500).json({ error: e.message });
    }
});

app.get('/health', (req, res) => {
    res.json({ status: 'ok', connected: !!sock, springUrl: SPRING_URL });
});

// ── Start ─────────────────────────────────────────────────────────────────────

app.listen(PORT, () => {
    console.log(`Baileys bot HTTP server listening on port ${PORT}`);
    console.log(`Forwarding messages to Spring Boot at ${SPRING_URL}`);
    connectToWhatsApp();
});
