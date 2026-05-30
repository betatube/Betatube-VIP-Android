/**
 * ====================================================================
 * 🚀 BETATUBE VIP — CORE SYSTEM ENGINE (v2.0.0)
 * ====================================================================
 * Secure Hardware Binding via Serial Control & Multi-Video Playlist
 * Property of: Betatube-VIP-Android (Tunisia 2026)
 * ====================================================================
 */

const BetaTubeEngine = {
    config: {
        databaseUrl: "https://betatube-vip-default-rtdb.firebaseio.com/clients/", 
        clientId: null,
        serialNumber: null,
        checkInterval: 60000 * 5, // فحص دوري كل 5 دقائق
        localPlaylist: [],
        currentVideoIndex: 0
    },

    // 1. سحب بصمة العتاد والـ Serial Number الأصلي للشاشة
    getHardwareFingerprint: function() {
        try {
            if (typeof Android !== 'undefined' && Android.getSerialNumber) {
                return Android.getSerialNumber();
            }
            if (typeof Android !== 'undefined' && Android.getAndroidId) {
                return Android.getAndroidId();
            }
            if (navigator.userAgent) {
                let rawId = navigator.userAgent + navigator.hardwareConcurrency + navigator.language;
                return "SN-" + btoa(rawId).substring(0, 15).toUpperCase();
            }
            return "SN-FALLBACK-99999";
        } catch (e) {
            console.error("BetaTube Security Error:", e);
            return "SN-SECURITY-BLOCKED";
        }
    },

    // 2. التحقق من الرخصة، عدد الأجهزة المتصلة، وجلب البيانات
    initialize: async function() {
        this.config.serialNumber = this.getHardwareFingerprint();
        this.config.clientId = localStorage.getItem("betatube_client_id");

        if (!this.config.clientId) {
            console.log("BetaTube: Mode YouTube Pro Clean (Pas de Pubs).");
            this.bootCleanMode();
            return;
        }

        try {
            let response = await fetch(`${this.config.databaseUrl}${this.config.clientId}.json`);
            let clientData = await response.json();

            if (!clientData || !clientData.license_security || !clientData.license_security.is_active) {
                this.bootCleanMode();
                return;
            }

            let security = clientData.license_security;
            let isDeviceAuthorized = security.authorized_serial_numbers && security.authorized_serial_numbers.includes(this.config.serialNumber);

            if (!isDeviceAuthorized) {
                if (security.current_connected_screens < security.max_connections) {
                    await this.registerNewDeviceSerial(security.authorized_serial_numbers || [], security.current_connected_screens);
                } else {
                    this.showSecurityOverlay("Limite de licence atteinte. Contactez l'administration.");
                    return;
                }
            }

            this.injectVipLayout(clientData.custom_layout, clientData.marketing_data);
            this.startBackgroundSync();

        } catch (error) {
            console.error("BetaTube: Mode Offline actif.", error);
            this.bootOfflineResiliencyMode();
        }
    },

    registerNewDeviceSerial: async function(existingSerials, currentCount) {
        existingSerials.push(this.config.serialNumber);
        let updatePayload = {
            "license_security/current_connected_screens": currentCount + 1,
            "license_security/authorized_serial_numbers": existingSerials
        };
        await fetch(`${this.config.databaseUrl}${this.config.clientId}.json`, {
            method: 'PATCH',
            body: JSON.stringify(updatePayload)
        });
    },

    // 3. حقن الواجهة البصرية بالأبعاد الديناميكية من لوحتك
    injectVipLayout: function(layout, marketing) {
        this.removeExistingOverlays();

        let mainCanvas = document.createElement('div');
        mainCanvas.id = 'betatube-vip-canvas';

        // حقن شريط الإعلانات السفلي
        if (layout.bottom_ticker && layout.bottom_ticker.is_active) {
            let ticker = document.createElement('div');
            ticker.id = 'betatube-ticker';
            ticker.style.height = `${layout.bottom_ticker.height_pixels || 60}px`;
            ticker.style.position = 'absolute';
            ticker.style.bottom = '0'; ticker.style.left = '0'; ticker.style.width = '100%';
            ticker.style.background = 'rgba(15, 23, 42, 0.75)';
            ticker.style.backdropFilter = 'blur(10px)';
            ticker.style.color = '#ffffff'; ticker.style.display = 'flex'; ticker.style.alignItems = 'center'; ticker.style.overflow = 'hidden'; ticker.style.fontSize = '22px'; ticker.style.fontWeight = 'bold';

            let contentText = document.createElement('div');
            if (layout.bottom_ticker.mode === 'marquee') {
                contentText.innerHTML = `<marquee scrollamount="6">${marketing.ticker_text}</marquee>`;
            } else {
                contentText.style.width = '100%'; contentText.style.textAlign = 'center';
                contentText.innerText = marketing.ticker_text;
            }
            ticker.appendChild(contentText);
            mainCanvas.appendChild(ticker);
        }

        // حقن الـ QR Code الذكي (Wi-Fi + Menu) والأبعاد الديناميكية
        if (layout.qr_code) {
            let qrContainer = document.createElement('div');
            qrContainer.id = 'betatube-qrcode-zone';
            qrContainer.style.position = 'absolute';
            qrContainer.style.left = `${layout.qr_code.position_x_pct}%`;
            qrContainer.style.top = `${layout.qr_code.position_y_pct}%`;
            qrContainer.style.width = `${layout.qr_code.width_pct}%`;
            qrContainer.style.height = `${layout.qr_code.height_pct}%`;

            let qrImg = document.createElement('img');
            qrImg.style.width = '100%'; qrImg.style.height = '100%';
            let qrData = `WIFI:S:${layout.wifi_automation.ssid};T:WPA;P:${layout.wifi_automation.password};;URL:${layout.wifi_automation.menu_url}`;
            qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(qrData)}`;
            
            qrContainer.appendChild(qrImg);
            mainCanvas.appendChild(qrContainer);
        }

        // حقن مشغل مصفوفة الفيديوهات المتعددة وتكرارها اللانهائي محلياً
        if (layout.video_ad_zone && layout.video_ad_zone.is_active) {
            let videoContainer = document.createElement('div');
            videoContainer.id = 'betatube-video-ad-zone';
            videoContainer.style.position = 'absolute';
            videoContainer.style.left = `${layout.video_ad_zone.position_x_pct}%`;
            videoContainer.style.top = `${layout.video_ad_zone.position_y_pct}%`;
            videoContainer.style.width = `${layout.video_ad_zone.width_pct}%`;
            videoContainer.style.height = `${layout.video_ad_zone.height_pct}%`;

            let adVideo = document.createElement('video');
            adVideo.id = 'betatube-ads-player';
            adVideo.style.width = '100%'; adVideo.style.height = '100%'; adVideo.style.objectFit = 'cover';
            adVideo.muted = true; adVideo.autoplay = true;

            this.config.localPlaylist = layout.video_ad_zone.videos_list || [];
            this.config.currentVideoIndex = 0;

            if (this.config.localPlaylist.length > 0) {
                adVideo.src = this.config.localPlaylist[this.config.currentVideoIndex];
                adVideo.onended = () => {
                    this.config.currentVideoIndex++;
                    if (this.config.currentVideoIndex >= this.config.localPlaylist.length) {
                        this.config.currentVideoIndex = 0;
                    }
                    adVideo.src = this.config.localPlaylist[this.config.currentVideoIndex];
                    adVideo.play();
                };
            }
            videoContainer.appendChild(adVideo);
            mainCanvas.appendChild(videoContainer);
        }

        document.body.appendChild(mainCanvas);
    },

    bootCleanMode: function() { this.removeExistingOverlays(); },
    bootOfflineResiliencyMode: function() { /* تشغيل بآخر كاش محفوظ */ },
    removeExistingOverlays: function() {
        let oldCanvas = document.getElementById('betatube-vip-canvas');
        if (oldCanvas) oldCanvas.remove();
        let oldError = document.getElementById('betatube-security-error');
        if (oldError) oldError.remove();
    },

    showSecurityOverlay: function(msg) {
        this.removeExistingOverlays();
        let errDiv = document.createElement('div');
        errDiv.id = 'betatube-security-error';
        errDiv.style.position = 'absolute'; errDiv.style.top = '0'; errDiv.style.left = '0'; errDiv.style.width = '100%'; errDiv.style.height = '100%';
        errDiv.style.background = '#0f172a'; errDiv.style.color = '#ef4444'; errDiv.style.display = 'flex'; errDiv.style.justifyContent = 'center'; errDiv.style.alignItems = 'center'; errDiv.style.zIndex = '9999999';
        errDiv.innerHTML = `<h1 style="font-size:30px;">🔒 SÉCURITÉ BETATUBE: ${msg}</h1>`;
        document.body.appendChild(errDiv);
    },

    startBackgroundSync: function() {
        setInterval(async () => {
            try {
                let response = await fetch(`${this.config.databaseUrl}${this.config.clientId}.json`);
                let clientData = await response.json();
                if (clientData) { this.injectVipLayout(clientData.custom_layout, clientData.marketing_data); }
            } catch (e) {}
        }, this.config.checkInterval);
    }
};

window.addEventListener('DOMContentLoaded', () => { BetaTubeEngine.initialize(); });
