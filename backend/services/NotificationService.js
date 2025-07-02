// services/NotificationService.js
const admin = require('firebase-admin');
const path = require('path');

// Load service account JSON (you can also use env vars)
const serviceAccount = require(path.join(__dirname, '../firebase-service-account.json'));

// Initialize Firebase Admin (only once)
if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

// Send generic push notification
async function sendNotification(token, title, body, data = {}) {
  try {
    const message = {
      token,
      notification: {
        title,
        body
      },
      data
    };

    const response = await admin.messaging().send(message);
    console.log('✅ Notification sent:', response);
    return { success: true, messageId: response };
  } catch (err) {
    console.error('❌ Notification error:', err);
    return { success: false, error: err.message };
  }
}

module.exports = {
  sendNotification,
  sendNewMessageNotification: (token, sender, msg, platform) =>
    sendNotification(token, `New ${platform} message from ${sender}`, msg, { platform }),
  sendAIResponseNotification: (token, reply) =>
    sendNotification(token, 'AI Assistant Reply', reply),
  sendMoodNotification: (token, mood, confidence) =>
    sendNotification(token, 'Mood Detected', `${mood} (${Math.round(confidence * 100)}%)`, { mood })
};
