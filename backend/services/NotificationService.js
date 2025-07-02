// services/NotificationService.js
const axios = require('axios');
const FCM_SERVER_KEY = process.env.FCM_SERVER_KEY;
const FCM_URL = 'https://fcm.googleapis.com/fcm/send';

async function sendNotification(token, title, body, data = {}) {
  if (!token) return { success: false, error: 'Missing FCM token' };
  try {
    const res = await axios.post(FCM_URL, {
      to: token,
      notification: { title, body, sound: 'default' },
      data
    }, {
      headers: {
        Authorization: `key=${FCM_SERVER_KEY}`,
        'Content-Type': 'application/json'
      }
    });
    return { success: true, messageId: res.data.message_id };
  } catch (err) {
    console.error('Push failed', err.response?.data || err.message);
    return { success: false, error: err.message };
  }
}

module.exports = {
  sendNotification,
  sendNewMessageNotification: (token, sender, msg, platform) =>
    sendNotification(token, `New ${platform} from ${sender}`, msg, { platform }),
  sendAIResponseNotification: (token, reply) =>
    sendNotification(token, 'AI Assistant Reply', reply),
  sendMoodNotification: (token, mood, confidence) =>
    sendNotification(token, 'Mood Detected', `${mood} (${Math.round(confidence * 100)}%)`, { mood })
};
