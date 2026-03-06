import Paho from 'paho-mqtt';
import { browser } from '$app/environment';

export interface RealtimeMessage {
    topic: string;
    payload: any;
}

class MqttStore {
    private client: Paho.Client | null = null;
    isConnected = $state(false);
    lastAnnouncement = $state<any>(null);
    leaderboardUpdateTrigger = $state(0);

    connect() {
        if (!browser || this.client) return;

        // Verbindungsparameter für Web-MQTT (Port 15675)
        const host = window.location.hostname;
        const port = 15675;
        const clientId = `codepath_client_${Math.random().toString(16).slice(2, 10)}`;

        this.client = new Paho.Client(host, port, '/ws', clientId);

        this.client.onConnectionLost = (responseObject) => {
            console.warn('MQTT Verbindung verloren:', responseObject.errorMessage);
            this.isConnected = false;
            // Reconnect nach 5 Sekunden
            setTimeout(() => this.connect(), 5000);
        };

        this.client.onMessageArrived = (message) => {
            try {
                const topic = message.destinationName;
                const payload = JSON.parse(message.payloadString);
                this.handleMessage(topic, payload);
            } catch (e) {
                console.error('Fehler beim Verarbeiten der MQTT Nachricht:', e);
            }
        };

        this.client.connect({
            userName: 'codepath',
            password: 'codepath',
            onSuccess: () => {
                console.log('MQTT verbunden');
                this.isConnected = true;
                this.subscribe();
            },
            onFailure: (e) => {
                console.error('MQTT Verbindung fehlgeschlagen:', e.errorMessage);
                setTimeout(() => this.connect(), 5000);
            },
            useSSL: false // In Produktion auf true setzen wenn HTTPS genutzt wird
        });
    }

    private subscribe() {
        if (!this.client) return;
        this.client.subscribe('codepath/announcements');
        this.client.subscribe('codepath/leaderboard/update');
    }

    private handleMessage(topic: string, payload: any) {
        if (topic === 'codepath/announcements') {
            this.lastAnnouncement = payload;
        } else if (topic === 'codepath/leaderboard/update') {
            this.leaderboardUpdateTrigger++;
        }
    }
}

export const realtime = new MqttStore();
