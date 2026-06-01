/**
 * BetaTube VIP - Core System Engine v3.0
 * Firebase-powered commercial overlay system
 * Optimized for Allwinner H313 / X96Q Android TV
 */
(function() {
  'use strict';

  // =========================================================================
  // A. Configuration
  // =========================================================================
  var FIREBASE_URL = 'https://betatube-vip-default-rtdb.firebaseio.com';
  var POLL_INTERVAL = 30000;
  var LICENSE_POLL_INTERVAL = 10000;
  var CACHE_PREFIX = 'betatube_';

  // =========================================================================
  // B. Hardware Fingerprint & Activation Code Generation
  // =========================================================================
  var _activationCode = null;

  function simpleHash(str) {
    var hash = 0;
    for (var i = 0; i < str.length; i++) {
      var ch = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + ch;
      hash = hash & hash;
    }
    return Math.abs(hash).toString(16).toUpperCase().substring(0, 6);
  }

  function generateActivationCode() {
    var cached = localStorage.getItem(CACHE_PREFIX + 'activation_code');
    if (cached) {
      _activationCode = cached;
      return Promise.resolve(cached);
    }

    var rawId = '';
    try {
      if (typeof Android !== 'undefined' && Android.getSerialNumber) {
        rawId = Android.getSerialNumber();
      } else if (typeof Android !== 'undefined' && Android.getAndroidId) {
        rawId = Android.getAndroidId();
      } else {
        rawId = navigator.userAgent + String(screen.width) + String(screen.height) + String(navigator.hardwareConcurrency || 1);
      }
    } catch (e) {
      rawId = navigator.userAgent + String(screen.width) + String(screen.height) + String(navigator.hardwareConcurrency || 1);
    }

    if (typeof crypto !== 'undefined' && crypto.subtle && crypto.subtle.digest) {
      var encoder = new TextEncoder();
      var data = encoder.encode(rawId);
      return crypto.subtle.digest('SHA-256', data).then(function(hashBuffer) {
        var hashArray = new Uint8Array(hashBuffer);
        var hexStr = '';
        for (var i = 0; i < hashArray.length; i++) {
          hexStr += hashArray[i].toString(16).padStart(2, '0');
        }
        var code = hexStr.substring(0, 6).toUpperCase();
        _activationCode = code;
        localStorage.setItem(CACHE_PREFIX + 'activation_code', code);
        return code;
      });
    } else {
      var code = simpleHash(rawId);
      while (code.length < 6) {
        code = '0' + code;
      }
      code = code.substring(0, 6).toUpperCase();
      _activationCode = code;
      localStorage.setItem(CACHE_PREFIX + 'activation_code', code);
      return Promise.resolve(code);
    }
  }

  // =========================================================================
  // C. Offline Cache Layer
  // =========================================================================
  function cacheSet(key, value) {
    try {
      localStorage.setItem(CACHE_PREFIX + key, JSON.stringify(value));
    } catch (e) {
      // Storage full or unavailable - silently ignore
    }
  }

  function cacheGet(key, defaultValue) {
    try {
      var stored = localStorage.getItem(CACHE_PREFIX + key);
      if (stored !== null) {
        return JSON.parse(stored);
      }
    } catch (e) {
      // Parse error - return default
    }
    return defaultValue !== undefined ? defaultValue : null;
  }

  // =========================================================================
  // D. License Check System
  // =========================================================================
  var _licensePollingId = null;

  function checkLicense() {
    return fetch(FIREBASE_URL + '/licenses/' + _activationCode + '/status.json')
      .then(function(response) {
        return response.json();
      })
      .then(function(status) {
        cacheSet('license_status', status);
        if (status === 'active') {
          removeLockScreen();
          initOverlays();
          startPolling();
        } else {
          showLockScreen();
          startLicensePolling();
        }
      })
      .catch(function() {
        var cachedStatus = cacheGet('license_status', null);
        if (cachedStatus === 'active') {
          removeLockScreen();
          initOverlays();
          startPolling();
        } else {
          showLockScreen();
          startLicensePolling();
        }
      });
  }

  function startLicensePolling() {
    if (_licensePollingId) return;
    _licensePollingId = setInterval(function() {
      fetch(FIREBASE_URL + '/licenses/' + _activationCode + '/status.json')
        .then(function(response) { return response.json(); })
        .then(function(status) {
          cacheSet('license_status', status);
          if (status === 'active') {
            stopLicensePolling();
            removeLockScreen();
            initOverlays();
            startPolling();
          }
        })
        .catch(function() {
          // Network failure during license poll - keep polling
        });
    }, LICENSE_POLL_INTERVAL);
  }

  function stopLicensePolling() {
    if (_licensePollingId) {
      clearInterval(_licensePollingId);
      _licensePollingId = null;
    }
  }

  // =========================================================================
  // Lock Screen UI
  // =========================================================================
  var _lockScreenEl = null;

  function showLockScreen() {
    if (_nativeLayerActive) return;
    if (_lockScreenEl) return;

    _lockScreenEl = document.createElement('div');
    _lockScreenEl.id = 'betatube-lock-screen';
    _lockScreenEl.innerHTML =
      '<div class="lock-card">' +
        '<div class="lock-icon">&#128274;</div>' +
        '<h1 class="lock-title">System Locked</h1>' +
        '<p class="lock-code">' + (_activationCode || '------') + '</p>' +
        '<p class="lock-message">System Locked. Please send this code to the Technical Director (Rajab) for activation.</p>' +
      '</div>';

    var canvas = getOrCreateCanvas();
    canvas.appendChild(_lockScreenEl);
  }

  function removeLockScreen() {
    if (_lockScreenEl && _lockScreenEl.parentNode) {
      _lockScreenEl.parentNode.removeChild(_lockScreenEl);
    }
    _lockScreenEl = null;
  }

  // =========================================================================
  // E. Real-time Data Polling (Firebase REST)
  // =========================================================================
  var _pollingId = null;

  function startPolling() {
    if (_pollingId) return;
    fetchAllData();
    _pollingId = setInterval(fetchAllData, POLL_INTERVAL);
  }

  function fetchAllData() {
    var dimensionsPromise = fetch(FIREBASE_URL + '/settings/dimensions.json')
      .then(function(r) { return r.json(); })
      .then(function(data) {
        if (data) {
          cacheSet('dimension_x', data.X || 0);
          cacheSet('dimension_y', data.Y || 0);
        }
        return data;
      })
      .catch(function() {
        return { X: cacheGet('dimension_x', 0), Y: cacheGet('dimension_y', 0) };
      });

    var adsPromise = fetch(FIREBASE_URL + '/ads.json')
      .then(function(r) { return r.json(); })
      .then(function(data) {
        if (data) {
          cacheSet('logo_url', data.logo_url || '');
          cacheSet('banner_content', data.banner_content || '');
          cacheSet('moving_text', data.moving_text || '');
        }
        return data;
      })
      .catch(function() {
        return {
          logo_url: cacheGet('logo_url', ''),
          banner_content: cacheGet('banner_content', ''),
          moving_text: cacheGet('moving_text', '')
        };
      });

    var takeoverPromise = fetch(FIREBASE_URL + '/settings/takeover_message.json')
      .then(function(r) { return r.json(); })
      .then(function(data) {
        cacheSet('takeover_message', data || '');
        return data;
      })
      .catch(function() {
        return cacheGet('takeover_message', '');
      });

    Promise.all([dimensionsPromise, adsPromise, takeoverPromise])
      .then(function(results) {
        var dimensions = results[0];
        var ads = results[1];
        var takeover = results[2];

        if (dimensions) {
          applyDimensions(dimensions.X || 0, dimensions.Y || 0);
        }
        if (ads) {
          updateLogo(ads.logo_url || '');
          updateBanner(ads.banner_content || '');
          updateTicker(ads.moving_text || '');
        }
        handleTakeover(takeover || '');
      });
  }

  // =========================================================================
  // F. Dynamic Dimension Control
  // =========================================================================
  function applyDimensions(x, y) {
    x = Math.max(0, Math.min(x, Math.floor(window.innerWidth / 3)));
    y = Math.max(0, Math.min(y, Math.floor(window.innerHeight / 3)));

    var player = document.querySelector('#player') ||
                 document.querySelector('.html5-video-container') ||
                 document.querySelector('video');
    if (!player) return;

    var parentEl = player.tagName === 'VIDEO' ? player.parentElement : player;
    if (!parentEl) return;

    // Use translate3d for GPU-composited repositioning on H313
    parentEl.style.transform = 'translate3d(' + x + 'px, ' + y + 'px, 0) scale(' +
      ((window.innerWidth - (x * 2)) / window.innerWidth) + ', ' +
      ((window.innerHeight - (y * 2)) / window.innerHeight) + ')';
    parentEl.style.transformOrigin = 'top left';
    parentEl.style.willChange = 'transform';
  }

  // =========================================================================
  // G. Overlay Components
  // =========================================================================
  var _canvasEl = null;
  var _logoEl = null;
  var _bannerEl = null;
  var _tickerBarEl = null;
  var _tickerContentEl = null;
  var _takeoverEl = null;
  var _takeoverCardEl = null;
  var _currentLogoUrl = '';
  var _currentBannerContent = '';
  var _currentTickerText = '';
  var _isTakeoverActive = false;

  function getOrCreateCanvas() {
    if (_canvasEl) return _canvasEl;
    _canvasEl = document.getElementById('betatube-vip-canvas');
    if (!_canvasEl) {
      _canvasEl = document.createElement('div');
      _canvasEl.id = 'betatube-vip-canvas';
      document.body.appendChild(_canvasEl);
    }
    return _canvasEl;
  }

  function initOverlays() {
    if (_nativeLayerActive) return;

    var canvas = getOrCreateCanvas();
    var fragment = document.createDocumentFragment();

    // Logo
    if (!_logoEl) {
      _logoEl = document.createElement('img');
      _logoEl.id = 'betatube-logo';
      _logoEl.className = 'gpu-accelerated';
      _logoEl.alt = 'BetaTube Logo';
      fragment.appendChild(_logoEl);
    }

    // Fixed Banner
    if (!_bannerEl) {
      _bannerEl = document.createElement('div');
      _bannerEl.id = 'betatube-fixed-banner';
      _bannerEl.className = 'gpu-accelerated';
      fragment.appendChild(_bannerEl);
    }

    // Ticker Bar
    if (!_tickerBarEl) {
      _tickerBarEl = document.createElement('div');
      _tickerBarEl.id = 'betatube-ticker-bar';
      _tickerBarEl.className = 'gpu-accelerated';

      _tickerContentEl = document.createElement('span');
      _tickerContentEl.id = 'betatube-ticker-content';
      _tickerBarEl.appendChild(_tickerContentEl);
      fragment.appendChild(_tickerBarEl);
    }

    // Takeover
    if (!_takeoverEl) {
      _takeoverEl = document.createElement('div');
      _takeoverEl.id = 'betatube-takeover';
      _takeoverEl.className = 'betatube-hidden';

      _takeoverCardEl = document.createElement('div');
      _takeoverCardEl.className = 'takeover-card';
      _takeoverEl.appendChild(_takeoverCardEl);
      fragment.appendChild(_takeoverEl);
    }

    canvas.appendChild(fragment);

    // Load cached values immediately
    var cachedLogo = cacheGet('logo_url', '');
    var cachedBanner = cacheGet('banner_content', '');
    var cachedTicker = cacheGet('moving_text', '');
    var cachedTakeover = cacheGet('takeover_message', '');

    if (cachedLogo) updateLogo(cachedLogo);
    if (cachedBanner) updateBanner(cachedBanner);
    if (cachedTicker) updateTicker(cachedTicker);
    if (cachedTakeover) handleTakeover(cachedTakeover);
  }

  // 1. Logo
  function updateLogo(url) {
    if (!_logoEl || url === _currentLogoUrl) return;
    _currentLogoUrl = url;
    if (url) {
      _logoEl.src = url;
      _logoEl.style.display = 'block';
    } else {
      _logoEl.style.display = 'none';
    }
  }

  // 2. Fixed Banner
  function updateBanner(content) {
    if (!_bannerEl || content === _currentBannerContent) return;
    _currentBannerContent = content;
    if (content) {
      _bannerEl.textContent = content;
      _bannerEl.style.display = 'block';
    } else {
      _bannerEl.style.display = 'none';
    }
  }

  // 3. Moving Ticker
  function updateTicker(text) {
    if (!_tickerContentEl || text === _currentTickerText) return;
    _currentTickerText = text;
    if (text) {
      _tickerContentEl.textContent = text;
      var duration = Math.max(5, text.length * 0.15);
      _tickerContentEl.style.animationDuration = duration + 's';
      _tickerBarEl.style.display = 'flex';
    } else {
      _tickerBarEl.style.display = 'none';
    }
  }

  // 4. Takeover Mode
  function handleTakeover(message) {
    if (!_takeoverEl || !_takeoverCardEl) return;

    if (message && message.trim && message.trim().length > 0) {
      if (!_isTakeoverActive) {
        _isTakeoverActive = true;
        _takeoverCardEl.textContent = message;
        _takeoverEl.className = '';
        _takeoverEl.style.display = 'flex';
        // Pause all videos
        var videos = document.querySelectorAll('video');
        for (var i = 0; i < videos.length; i++) {
          videos[i].pause();
        }
      } else if (_takeoverCardEl.textContent !== message) {
        _takeoverCardEl.textContent = message;
      }
    } else {
      if (_isTakeoverActive) {
        _isTakeoverActive = false;
        _takeoverEl.className = 'betatube-hidden';
        _takeoverEl.style.display = 'none';
        // Resume all videos
        var videos = document.querySelectorAll('video');
        for (var i = 0; i < videos.length; i++) {
          try { videos[i].play(); } catch (e) { /* autoplay policy */ }
        }
      }
    }
  }

  // =========================================================================
  // H. Memory Management / Cleanup
  // =========================================================================
  function cleanup() {
    stopLicensePolling();
    if (_pollingId) {
      clearInterval(_pollingId);
      _pollingId = null;
    }
    _lockScreenEl = null;
    _canvasEl = null;
    _logoEl = null;
    _bannerEl = null;
    _tickerBarEl = null;
    _tickerContentEl = null;
    _takeoverEl = null;
    _takeoverCardEl = null;
    _activationCode = null;
    _currentLogoUrl = '';
    _currentBannerContent = '';
    _currentTickerText = '';
    _isTakeoverActive = false;
  }

  // =========================================================================
  // I. Initialization Flow
  // =========================================================================

  // If running inside Cobalt app with native BetaTube layer, skip JS overlays
  // The Java BetaTubeManager handles overlays natively for better performance
  var _nativeLayerActive = (typeof Android !== 'undefined' && Android.getAndroidId);

  function init() {
    generateActivationCode().then(function(code) {
      _activationCode = code;
      checkLicense();
    });
  }

  // Boot on DOMContentLoaded
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  // Cleanup on unload
  window.addEventListener('unload', cleanup);

})();
