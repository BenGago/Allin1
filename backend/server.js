const express = require("express")
const cors = require("cors")
const bodyParser = require("body-parser")
const sqlite3 = require("sqlite3").verbose()
const multer = require("multer")
const path = require("path")
const fs = require('fs')
const axios = require('axios')
const jwt = require('jsonwebtoken')
const bcrypt = require('bcryptjs')
const helmet = require('helmet')
const rateLimit = require('express-rate-limit')
const NotificationService = require('./services/NotificationService')

const app = express()
const PORT = process.env.PORT || 3000

// Security middleware
app.use(helmet())
app.use(cors({
  origin: process.env.NODE_ENV === 'production' 
    ? ['https://your-frontend-domain.com'] 
    : ['http://localhost:3000', 'http://localhost:8080'],
  credentials: true
}))

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // limit each IP to 100 requests per windowMs
  message: 'Too many requests from this IP, please try again later.'
})
app.use('/api/', limiter)

// Body parsing middleware
app.use(bodyParser.json({ limit: '50mb' }))
app.use(bodyParser.urlencoded({ extended: true, limit: '50mb' }))

// Static files
app.use("/uploads", express.static("uploads"))

// Ensure uploads directory exists
if (!fs.existsSync('uploads')) {
  fs.mkdirSync('uploads')
}

// Configure multer for file uploads
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, 'uploads/')
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9)
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname))
  }
})

const upload = multer({ 
  storage: storage,
  limits: {
    fileSize: 50 * 1024 * 1024 // 50MB limit
  },
  fileFilter: (req, file, cb) => {
    // Allow images, videos, documents
    const allowedTypes = /jpeg|jpg|png|gif|mp4|mov|avi|pdf|doc|docx|txt/
    const extname = allowedTypes.test(path.extname(file.originalname).toLowerCase())
    const mimetype = allowedTypes.test(file.mimetype)
    
    if (mimetype && extname) {
      return cb(null, true)
    } else {
      cb(new Error('Invalid file type'))
    }
  }
})

// Initialize SQLite database
const db = new sqlite3.Database("./messages.db", (err) => {
  if (err) {
    console.error('Error opening database:', err.message)
  } else {
    console.log('Connected to SQLite database.')
    initializeDatabase()
  }
})

// Initialize database tables
function initializeDatabase() {
  // Messages table
  db.run(`CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    platform TEXT NOT NULL,
    sender TEXT NOT NULL,
    recipient TEXT,
    message TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    message_type TEXT DEFAULT 'text',
    file_path TEXT,
    metadata TEXT,
    mood TEXT,
    ai_response TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  )`)

  // Devices table
  db.run(`CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT UNIQUE NOT NULL,
    device_name TEXT NOT NULL,
    fcm_token TEXT,
    last_seen INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  )`)

  // Outgoing SMS table
  db.run(`CREATE TABLE IF NOT EXISTS outgoing_sms (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    recipient TEXT NOT NULL,
    message TEXT NOT NULL,
    status TEXT DEFAULT 'pending',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  )`)

  // User preferences table
  db.run(`CREATE TABLE IF NOT EXISTS user_preferences (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    notification_enabled BOOLEAN DEFAULT 1,
    ai_enabled BOOLEAN DEFAULT 1,
    mood_detection_enabled BOOLEAN DEFAULT 1,
    voice_replies_enabled BOOLEAN DEFAULT 1,
    auto_translate BOOLEAN DEFAULT 0,
    theme TEXT DEFAULT 'light',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  )`)

  // Message reactions table
  db.run(`CREATE TABLE IF NOT EXISTS message_reactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id INTEGER NOT NULL,
    device_id TEXT NOT NULL,
    reaction TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (message_id) REFERENCES messages (id)
  )`)

  console.log('Database tables initialized.')
}

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    timestamp: new Date().toISOString(),
    version: '1.0.0',
    features: [
      'SMS Integration',
      'Multi-Platform Messaging',
      'AI Voice Replies',
      'Mood Detection',
      'Image Analysis',
      'Push Notifications',
      'Real-time Sync'
    ]
  })
})

// Device registration endpoint
app.post('/api/device/register', async (req, res) => {
  const { deviceId, deviceName, fcmToken } = req.body
  
  if (!deviceId || !deviceName) {
    return res.status(400).json({ error: 'Device ID and name are required' })
  }

  try {
    // Insert or update device
    db.run(
      `INSERT OR REPLACE INTO devices (device_id, device_name, fcm_token, last_seen) 
       VALUES (?, ?, ?, ?)`,
      [deviceId, deviceName, fcmToken, Date.now()],
      function(err) {
        if (err) {
          console.error('Error registering device:', err)
          return res.status(500).json({ error: 'Failed to register device' })
        }
        
        console.log(`Device registered: ${deviceId} (${deviceName})`)
        res.json({ 
          success: true, 
          deviceId: deviceId,
          message: 'Device registered successfully' 
        })
      }
    )
  } catch (error) {
    console.error('Error in device registration:', error)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// Update FCM token endpoint
app.post('/api/device/update-token', async (req, res) => {
  const { deviceId, fcmToken } = req.body
  
  if (!deviceId || !fcmToken) {
    return res.status(400).json({ error: 'Device ID and FCM token are required' })
  }

  try {
    db.run(
      `UPDATE devices SET fcm_token = ?, last_seen = ? WHERE device_id = ?`,
      [fcmToken, Date.now(), deviceId],
      function(err) {
        if (err) {
          console.error('Error updating FCM token:', err)
          return res.status(500).json({ error: 'Failed to update FCM token' })
        }
        
        console.log(`FCM token updated for device: ${deviceId}`)
        res.json({ success: true, message: 'FCM token updated successfully' })
      }
    )
  } catch (error) {
    console.error('Error updating FCM token:', error)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// Incoming SMS endpoint
app.post('/api/sms/incoming', async (req, res) => {
  const { sender, message, timestamp, deviceId, messageType = 'text', filePath, metadata } = req.body
  
  if (!sender || !message || !deviceId) {
    return res.status(400).json({ error: 'Sender, message, and device ID are required' })
  }

  try {
    // Save message to database
    db.run(
      `INSERT INTO messages (device_id, platform, sender, message, timestamp, message_type, file_path, metadata) 
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [deviceId, 'sms', sender, message, timestamp || Date.now(), messageType, filePath, metadata],
      async function(err) {
        if (err) {
          console.error('Error saving SMS:', err)
          return res.status(500).json({ error: 'Failed to save message' })
        }
        
        console.log(`SMS received from ${sender}: ${message}`)
        
        // Get device FCM token for push notification
        db.get(
          `SELECT fcm_token FROM devices WHERE device_id = ?`,
          [deviceId],
          async (err, row) => {
            if (!err && row && row.fcm_token) {
              // Send push notification
              await NotificationService.sendNewMessageNotification(
                row.fcm_token,
                sender,
                message,
                'SMS'
              )
            }
          }
        )
        
        res.json({ success: true, messageId: this.lastID })
      }
    )
  } catch (error) {
    console.error('Error processing incoming SMS:', error)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// Get messages endpoint
app.get('/api/messages', (req, res) => {
  const { deviceId, platform, limit = 50, offset = 0 } = req.query
  
  if (!deviceId) {
    return res.status(400).json({ error: 'Device ID is required' })
  }

  let query = `SELECT * FROM messages WHERE device_id = ?`
  let params = [deviceId]
  
  if (platform) {
    query += ` AND platform = ?`
    params.push(platform)
  }
  
  query += ` ORDER BY timestamp DESC LIMIT ? OFFSET ?`
  params.push(parseInt(limit), parseInt(offset))

  db.all(query, params, (err, rows) => {
    if (err) {
      console.error('Error fetching messages:', err)
      return res.status(500).json({ error: 'Failed to fetch messages' })
    }
    
    res.json(rows)
  })
})

// Send reply endpoint
app.post('/api/messages/reply', async (req, res) => {
  const { platform, recipientId, message, deviceId, messageType = 'text' } = req.body
  
  if (!platform || !recipientId || !message || !deviceId) {
    return res.status(400).json({ error: 'Platform, recipient ID, message, and device ID are required' })
  }

  try {
    // Save outgoing message to database
    db.run(
      `INSERT INTO messages (device_id, platform, sender, recipient, message, timestamp, message_type) 
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [deviceId, platform, 'You', recipientId, message, Date.now(), messageType],
      async function(err) {
        if (err) {
          console.error('Error saving reply:', err)
          return res.status(500).json({ error: 'Failed to save reply' })
        }
        
        // Send message via appropriate platform
        let success = false
        let error = null
        
        try {
          switch (platform.toLowerCase()) {
            case 'telegram':
              success = await sendTelegramMessage(recipientId, message)
              break
            case 'messenger':
              success = await sendMessengerMessage(recipientId, message)
              break
            case 'twitter':
              success = await sendTwitterDM(recipientId, message)
              break
            case 'sms':
              // For SMS, add to outgoing queue
              db.run(
                `INSERT INTO outgoing_sms (device_id, recipient, message) VALUES (?, ?, ?)`,
                [deviceId, recipientId, message]
              )
              success = true
              break
            default:
              error = 'Unsupported platform'
          }
        } catch (platformError) {
          console.error(`Error sending ${platform} message:`, platformError)
          error = platformError.message
        }
        
        if (success) {
          console.log(`Reply sent via ${platform} to ${recipientId}: ${message}`)
          res.json({ success: true, messageId: this.lastID })
        } else {
          res.status(500).json({ error: error || 'Failed to send message' })
        }
      }
    )
  } catch (error) {
    console.error('Error sending reply:', error)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// File upload endpoint
app.post("/api/upload", upload.single("file"), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: "No file uploaded" })
  }
  
  const fileUrl = `${req.protocol}://${req.get("host")}/uploads/${req.file.filename}`
  
  res.json({
    success: true,
    filename: req.file.filename,
    originalName: req.file.originalname,
    size: req.file.size,
    mimetype: req.file.mimetype,
    url: fileUrl
  })
})

// AI voice reply endpoint
app.post('/api/ai/voice-reply', async (req, res) => {
  const { message, deviceId, context, mood } = req.body
  
  if (!message || !deviceId) {
    return res.status(400).json({ error: 'Message and device ID are required' })
  }

  try {
    // Call OpenAI API for intelligent response
    const response = await axios.post('https://api.openai.com/v1/chat/completions', {
      model: 'gpt-4',
      messages: [
        {
          role: 'system',
          content: `You are an AI assistant helping with message replies. Consider the user's mood: ${mood || 'neutral'}. Provide a helpful, contextual response that matches the conversation tone.`
        },
        {
          role: 'user',
          content: `Context: ${context || 'General conversation'}\nMessage to reply to: ${message}\nGenerate an appropriate reply:`
        }
      ],
      max_tokens: 150,
      temperature: 0.7
    }, {
      headers: {
        'Authorization': `Bearer ${process.env.OPENAI_API_KEY}`,
        'Content-Type': 'application/json'
      }
    })

    const aiReply = response.data.choices[0].message.content.trim()
    
    // Save AI response to database
    db.run(
      `INSERT INTO messages (device_id, platform, sender, message, timestamp, message_type, ai_response) 
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [deviceId, 'ai', 'AI Assistant', aiReply, Date.now(), 'ai_response', aiReply],
      async function(err) {
        if (!err) {
          // Send push notification for AI response
          db.get(
            `SELECT fcm_token FROM devices WHERE device_id = ?`,
            [deviceId],
            async (err, row) => {
              if (!err && row && row.fcm_token) {
                await NotificationService.sendAIResponseNotification(
                  row.fcm_token,
                  aiReply
                )
              }
            }
          )
        }
      }
    )
    
    res.json({ 
      success: true, 
      reply: aiReply,
      model: 'gpt-4',
      timestamp: Date.now()
    })
    
  } catch (error) {
    console.error('Error generating AI reply:', error)
    res.status(500).json({ 
      error: 'Failed to generate AI reply',
      details: error.response?.data?.error?.message || error.message
    })
  }
})

// Mood detection endpoint
app.post('/api/ai/detect-mood', async (req, res) => {
  const { message, deviceId } = req.body
  
  if (!message || !deviceId) {
    return res.status(400).json({ error: 'Message and device ID are required' })
  }

  try {
    const response = await axios.post('https://api.openai.com/v1/chat/completions', {
      model: 'gpt-3.5-turbo',
      messages: [
        {
          role: 'system',
          content: 'You are a mood detection AI. Analyze the emotional tone of messages and respond with a JSON object containing "mood" (happy, sad, angry, excited, neutral, anxious, romantic, frustrated) and "confidence" (0.0 to 1.0).'
        },
        {
          role: 'user',
          content: `Analyze the mood of this message: "${message}"`
        }
      ],
      max_tokens: 100,
      temperature: 0.3
    }, {
      headers: {
        'Authorization': `Bearer ${process.env.OPENAI_API_KEY}`,
        'Content-Type': 'application/json'
      }
    })

    const moodAnalysis = response.data.choices[0].message.content.trim()
    let moodData
    
    try {
      moodData = JSON.parse(moodAnalysis)
    } catch (parseError) {
      // Fallback if JSON parsing fails
      moodData = { mood: 'neutral', confidence: 0.5 }
    }
    
    // Update message with mood data
    db.run(
      `UPDATE messages SET mood = ? WHERE device_id = ? AND message = ? ORDER BY timestamp DESC LIMIT 1`,
      [JSON.stringify(moodData), deviceId, message]
    )
    
    // Send mood notification if confidence is high
    if (moodData.confidence > 0.7) {
      db.get(
        `SELECT fcm_token FROM devices WHERE device_id = ?`,
        [deviceId],
        async (err, row) => {
          if (!err && row && row.fcm_token) {
            await NotificationService.sendMoodNotification(
              row.fcm_token,
              moodData.mood,
              moodData.confidence
            )
          }
        }
      )
    }
    
    res.json({ 
      success: true, 
      mood: moodData.mood,
      confidence: moodData.confidence,
      timestamp: Date.now()
    })
    
  } catch (error) {
    console.error('Error detecting mood:', error)
    res.status(500).json({ 
      error: 'Failed to detect mood',
      details: error.response?.data?.error?.message || error.message
    })
  }
})

// Push notification test endpoint
app.post('/api/notifications/test', async (req, res) => {
  const { deviceId, title, body } = req.body
  
  if (!deviceId) {
    return res.status(400).json({ error: 'Device ID is required' })
  }

  try {
    // Get device FCM token
    db.get(
      `SELECT fcm_token FROM devices WHERE device_id = ?`,
      [deviceId],
      async (err, row) => {
        if (err) {
          return res.status(500).json({ error: 'Database error' })
        }
        
        if (!row || !row.fcm_token) {
          return res.status(404).json({ error: 'Device not found or no FCM token' })
        }
        
        // Send test notification
        const result = await NotificationService.sendNotification(
          row.fcm_token,
          title || 'Test Notification',
          body || 'This is a test notification from your messaging hub!',
          { type: 'test' }
        )
        
        if (result.success) {
          res.json({ success: true, messageId: result.messageId })
        } else {
          res.status(500).json({ error: result.error })
        }
      }
    )
  } catch (error) {
    console.error('Error sending test notification:', error)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// Platform-specific message sending functions
async function sendTelegramMessage(chatId, message) {
  try {
    const response = await axios.post(`https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
      chat_id: chatId,
      text: message,
      parse_mode: 'HTML'
    })
    return response.data.ok
  } catch (error) {
    console.error('Telegram API error:', error.response?.data || error.message)
    return false
  }
}

async function sendMessengerMessage(recipientId, message) {
  try {
    const response = await axios.post(`https://graph.facebook.com/v18.0/me/messages`, {
      recipient: { id: recipientId },
      message: { text: message }
    }, {
      params: {
        access_token: process.env.FB_PAGE_ACCESS_TOKEN
      }
    })
    return response.data.message_id ? true : false
  } catch (error) {
    console.error('Messenger API error:', error.response?.data || error.message)
    return false
  }
}

async function sendTwitterDM(recipientId, message) {
  try {
    const response = await axios.post('https://api.twitter.com/2/dm_conversations/with/{participant_id}/messages', {
      text: message
    }, {
      headers: {
        'Authorization': `Bearer ${process.env.TWITTER_BEARER_TOKEN}`,
        'Content-Type': 'application/json'
      }
    })
    return response.data.data ? true : false
  } catch (error) {
    console.error('Twitter API error:', error.response?.data || error.message)
    return false
  }
}

// Webhook endpoints
app.post('/webhook/telegram', async (req, res) => {
  try {
    const update = req.body
    
    if (update.message) {
      const message = update.message
      const chatId = message.chat.id
      const text = message.text || ''
      const from = message.from
      
      console.log(`Telegram message from ${from.first_name}: ${text}`)
      
      // Save message to database and send notifications to all registered devices
      db.all(
        `SELECT device_id, fcm_token FROM devices WHERE fcm_token IS NOT NULL`,
        [],
        async (err, devices) => {
          if (!err && devices.length > 0) {
            for (const device of devices) {
              // Save message
              db.run(
                `INSERT INTO messages (device_id, platform, sender, message, timestamp) 
                 VALUES (?, ?, ?, ?, ?)`,
                [device.device_id, 'telegram', from.first_name || from.username, text, Date.now()]
              )
              
              // Send push notification
              if (device.fcm_token) {
                await NotificationService.sendNewMessageNotification(
                  device.fcm_token,
                  from.first_name || from.username,
                  text,
                  'Telegram'
                )
              }
            }
          }
        }
      )
    }
    
    res.status(200).json({ ok: true })
  } catch (error) {
    console.error('Telegram webhook error:', error)
    res.status(500).json({ error: 'Webhook processing failed' })
  }
})

app.post('/webhook/messenger', async (req, res) => {
  try {
    const body = req.body
    
    if (body.object === 'page') {
      body.entry.forEach(entry => {
        entry.messaging.forEach(async (messagingEvent) => {
          if (messagingEvent.message) {
            const senderId = messagingEvent.sender.id
            const messageText = messagingEvent.message.text
            
            console.log(`Messenger message from ${senderId}: ${messageText}`)
            
            // Save and notify
            db.all(
              `SELECT device_id, fcm_token FROM devices WHERE fcm_token IS NOT NULL`,
              [],
              async (err, devices) => {
                if (!err && devices.length > 0) {
                  for (const device of devices) {
                    db.run(
                      `INSERT INTO messages (device_id, platform, sender, message, timestamp) 
                       VALUES (?, ?, ?, ?, ?)`,
                      [device.device_id, 'messenger', senderId, messageText, Date.now()]
                    )
                    
                    if (device.fcm_token) {
                      await NotificationService.sendNewMessageNotification(
                        device.fcm_token,
                        'Messenger User',
                        messageText,
                        'Messenger'
                      )
                    }
                  }
                }
              }
            )
          }
        })
      })
    }
    
    res.status(200).json({ status: 'ok' })
  } catch (error) {
    console.error('Messenger webhook error:', error)
    res.status(500).json({ error: 'Webhook processing failed' })
  }
})

// Messenger webhook verification
app.get('/webhook/messenger', (req, res) => {
  const mode = req.query['hub.mode']
  const token = req.query['hub.verify_token']
  const challenge = req.query['hub.challenge']
  
  if (mode === 'subscribe' && token === process.env.FB_VERIFY_TOKEN) {
    console.log('Messenger webhook verified')
    res.status(200).send(challenge)
  } else {
    res.status(403).send('Verification failed')
  }
})

app.post('/webhook/twitter', async (req, res) => {
  try {
    const event = req.body
    
    if (event.direct_message_events) {
      event.direct_message_events.forEach(async (dmEvent) => {
        const senderId = dmEvent.message_create.sender_id
        const messageText = dmEvent.message_create.message_data.text
        
        console.log(`Twitter DM from ${senderId}: ${messageText}`)
        
        // Save and notify
        db.all(
          `SELECT device_id, fcm_token FROM devices WHERE fcm_token IS NOT NULL`,
          [],
          async (err, devices) => {
            if (!err && devices.length > 0) {
              for (const device of devices) {
                db.run(
                  `INSERT INTO messages (device_id, platform, sender, message, timestamp) 
                   VALUES (?, ?, ?, ?, ?)`,
                  [device.device_id, 'twitter', senderId, messageText, Date.now()]
                )
                
                if (device.fcm_token) {
                  await NotificationService.sendNewMessageNotification(
                    device.fcm_token,
                    'Twitter User',
                    messageText,
                    'Twitter'
                  )
                }
              }
            }
          }
        )
      })
    }
    
    res.status(200).json({ status: 'ok' })
  } catch (error) {
    console.error('Twitter webhook error:', error)
    res.status(500).json({ error: 'Webhook processing failed' })
  }
})

// Get outgoing SMS queue
app.get('/api/sms/outgoing', (req, res) => {
  const { deviceId } = req.query
  
  if (!deviceId) {
    return res.status(400).json({ error: 'Device ID is required' })
  }

  db.all(
    `SELECT * FROM outgoing_sms WHERE device_id = ? AND status = 'pending' ORDER BY created_at ASC`,
    [deviceId],
    (err, rows) => {
      if (err) {
        console.error('Error fetching outgoing SMS:', err)
        return res.status(500).json({ error: 'Failed to fetch outgoing SMS' })
      }
      
      res.json(rows)
    }
  )
})

// Mark SMS as sent
app.post('/api/sms/sent/:id', (req, res) => {
  const { id } = req.params
  
  db.run(
    `UPDATE outgoing_sms SET status = 'sent' WHERE id = ?`,
    [id],
    function(err) {
      if (err) {
        console.error('Error marking SMS as sent:', err)
        return res.status(500).json({ error: 'Failed to update SMS status' })
      }
      
      res.json({ success: true })
    }
  )
})

// Error handling middleware
app.use((error, req, res, next) => {
  console.error('Unhandled error:', error)
  res.status(500).json({ 
    error: 'Internal server error',
    message: process.env.NODE_ENV === 'development' ? error.message : 'Something went wrong'
  })
})

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint not found' })
})

// Start server
app.listen(PORT, () => {
  console.log(`🚀 Android Messaging Hub Backend running on port ${PORT}`)
  console.log(`📱 Health check: http://localhost:${PORT}/health`)
  console.log(`🔔 Push notifications: ENABLED`)
  console.log(`🤖 AI features: ENABLED`)
  console.log(`📊 Database: SQLite`)
  console.log(`🌐 Environment: ${process.env.NODE_ENV || 'development'}`)
})

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n🛑 Shutting down server...')
  db.close((err) => {
    if (err) {
      console.error('Error closing database:', err.message)
    } else {
      console.log('Database connection closed.')
    }
    process.exit(0)
  })
})

module.exports = app
