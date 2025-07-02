// services/NotificationService.js
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_CREDENTIAL_JSON);

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

async function sendNotification(token, title, body, data = {}) {
  try {
    const message = {
      token,
      notification: { title, body },
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
