// Firebase en la web: App Check (protege la base de datos) y Analytics (uso del juego, sin cookies).
// Se activan solo si están configurados en js/firebase-config.js (appCheckSiteKey, measurementId).
// Mismos nombres de evento que Android (analytics/Metrics.kt).
(function () {
  'use strict';
  const cfg = window.FIREBASE_CONFIG || {};
  let analytics = null;

  // Modo de consentimiento: sin cookies ni datos para anuncios. Analytics envía solo datos agregados.
  window.dataLayer = window.dataLayer || [];
  window.dataLayer.push(['consent', 'default', {
    ad_storage: 'denied', ad_user_data: 'denied', ad_personalization: 'denied', analytics_storage: 'denied',
  }]);

  try {
    if (window.firebase && !firebase.apps.length) firebase.initializeApp(cfg);
    // En local (pruebas) App Check usa un token de depuración que se da de alta en la consola
    if (/^(localhost|127\.0\.0\.1)$/.test(location.hostname)) self.FIREBASE_APPCHECK_DEBUG_TOKEN = true;
    if (cfg.appCheckSiteKey && firebase.appCheck) {
      firebase.appCheck().activate(new firebase.appCheck.ReCaptchaEnterpriseProvider(cfg.appCheckSiteKey), true);
    }
    if (cfg.measurementId && firebase.analytics) analytics = firebase.analytics();
  } catch (e) {
    console.warn('[Para el Carro] Firebase no disponible', e);
  }

  window.Metricas = {
    /** Un evento de uso; si Analytics no está o falla, no pasa nada. */
    evento(nombre, params) {
      try { if (analytics) analytics.logEvent(nombre, params || {}); } catch (e) { /* sin estadísticas */ }
    },
  };
})();
