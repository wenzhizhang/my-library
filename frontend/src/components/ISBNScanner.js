import React, { useState, useEffect, useRef, useCallback } from 'react';

const COLORS = {
  pureBlack: '#000000',
  nearBlack: '#1d1d1f',
  white: '#ffffff',
  lightGray: '#f5f5f7',
  midGray: '#86868b',
  appleBlue: '#0071e3',
  appleRed: '#ff3b30',
  translucentDark: 'rgba(29, 29, 31, 0.92)',
  translucentWhite: 'rgba(255, 255, 255, 0.12)',
  cardBg: '#ffffff',
};

const TYPOGRAPHY = {
  body: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Icons", "Helvetica Neue", Helvetica, Arial, sans-serif',
    fontSize: '17px',
    fontWeight: 400,
    lineHeight: '1.47059',
    letterSpacing: '-0.022em',
  },
  bodyEmphasis: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Icons", "Helvetica Neue", Helvetica, Arial, sans-serif',
    fontSize: '13px',
    fontWeight: 600,
    lineHeight: '1.4',
    letterSpacing: '-0.01em',
  },
  title: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Icons", "Helvetica Neue", Helvetica, Arial, sans-serif',
    fontSize: '28px',
    fontWeight: 600,
    lineHeight: '1.14286',
    letterSpacing: '0.007em',
  },
};

/**
 * ISBNScanner — modal overlay that opens the back camera to scan
 * ISBN/EAN-13 barcodes. Falls back to manual text entry when camera
 * is unavailable or the user prefers typing.
 *
 * Props:
 *   open       bool    — show the scanner modal
 *   onClose    fn()    — user dismissed the modal
 *   onScan     fn(isbn:string) — called with the scanned/typed ISBN
 */
function ISBNScanner({ open, onClose, onScan }) {
  const [cameraState, setCameraState] = useState('init');    // init | starting | scanning | denied | error
  const [manualIsbn, setManualIsbn] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const scannerRef = useRef(null);
  const readerDivRef = useRef(null);

  // ── Camera scanner ────────────────────────────────────────

  const scannerTimedOut = useRef(false);

  const stopScanner = useCallback(async () => {
    scannerTimedOut.current = false;
    if (scannerRef.current) {
      try {
        await scannerRef.current.stop();
      } catch (_) { /* ignore */ }
      scannerRef.current = null;
    }
  }, []);

  const startScanner = useCallback(async () => {
    setCameraState('starting');
    setErrorMsg('');
    scannerTimedOut.current = false;

    // Dynamic import so the library is only loaded when used
    let Html5Qrcode;
    let Html5QrcodeSupportedFormats;
    try {
      const mod = await import('html5-qrcode');
      Html5Qrcode = mod.Html5Qrcode;
      Html5QrcodeSupportedFormats = mod.Html5QrcodeSupportedFormats;
    } catch (e) {
      console.error('ISBNScanner: failed to load html5-qrcode', e);
      setCameraState('error');
      setErrorMsg('Failed to load scanner library.');
      return;
    }

    // Fallback timer: if camera doesn't start within 10s, show fallback
    const timeoutId = setTimeout(() => {
      scannerTimedOut.current = true;
      setCameraState('denied');
    }, 10000);

    try {
      const scanner = new Html5Qrcode('isbn-scanner-viewfinder');
      scannerRef.current = scanner;

      await scanner.start(
        { facingMode: 'environment' },
        {
          fps: 10,
          qrbox: { width: 280, height: 120 },
          formatsToSupport: [
            Html5QrcodeSupportedFormats.EAN_13,
            Html5QrcodeSupportedFormats.EAN_8,
            Html5QrcodeSupportedFormats.UPC_A,
            Html5QrcodeSupportedFormats.UPC_E,
          ],
        },
        (decodedText) => {
          const isbn = (decodedText || '').trim();
          if (isbn) {
            stopScanner();
            onScan(isbn);
            onClose();
          }
        },
        () => {
          // Scan attempt with no decode — expected, ignore
        },
      );

      clearTimeout(timeoutId);
      if (!scannerTimedOut.current) {
        setCameraState('scanning');
      }
    } catch (err) {
      clearTimeout(timeoutId);
      console.error('ISBNScanner: camera start failed', err);
      const msg = (err && err.message) || String(err);
      if (msg.includes('NotAllowedError') || msg.includes('Permission')) {
        setCameraState('denied');
      } else {
        setCameraState('error');
        setErrorMsg(msg);
      }
    }
  }, [onScan, onClose, stopScanner]);

  // Start/stop scanner when `open` changes
  useEffect(() => {
    if (open) {
      const t = setTimeout(() => startScanner(), 200);
      return () => {
        clearTimeout(t);
        stopScanner();
      };
    } else {
      stopScanner();
      setCameraState('init');
      setManualIsbn('');
    }
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Manual submit ─────────────────────────────────────────

  const handleManualSubmit = (e) => {
    e.preventDefault();
    const cleaned = (manualIsbn || '').replace(/[^0-9Xx]/g, '');
    if (cleaned.length >= 10) {
      onScan(cleaned);
      onClose();
    }
  };

  // ── Render ────────────────────────────────────────────────

  if (!open) return null;

  const cardWidth = Math.min(480, window.innerWidth - 32);

  return (
    <div style={overlayStyle} onClick={onClose}>
      <div
        style={{ ...cardStyle, width: cardWidth }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={headerStyle}>
          <h2 style={titleStyle}>Scan ISBN Barcode</h2>
          <button onClick={onClose} style={closeBtnStyle} aria-label="Close">
            &times;
          </button>
        </div>

        {/* Camera viewfinder */}
        <div style={viewfinderWrapStyle}>
          <div
            id="isbn-scanner-viewfinder"
            ref={readerDivRef}
            style={viewfinderStyle}
          />

          {/* State overlays */}
          {cameraState === 'starting' && (
            <div style={cameraOverlayStyle}>
              <span>Starting camera&hellip;</span>
            </div>
          )}

          {cameraState === 'denied' && (
            <div style={cameraOverlayStyle}>
              <p>Camera access denied.</p>
              <p style={{ fontSize: 14, color: COLORS.midGray }}>
                Type the ISBN below, or grant camera permission in browser settings.
              </p>
            </div>
          )}

          {cameraState === 'error' && (
            <div style={cameraOverlayStyle}>
              <p>Camera error.</p>
              {errorMsg && (
                <p style={{ fontSize: 12, color: COLORS.appleRed, marginTop: 8 }}>
                  {errorMsg}
                </p>
              )}
            </div>
          )}


          {cameraState === 'scanning' && (
            <div style={scanningLabelStyle}>
              Position the barcode in view
            </div>
          )}
        </div>

        {/* Manual ISBN input — always visible as fallback */}
        <form onSubmit={handleManualSubmit} style={manualFormStyle}>
          <label style={labelStyle}>Or type ISBN manually</label>
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              type="text"
              value={manualIsbn}
              onChange={(e) => setManualIsbn(e.target.value)}
              placeholder="978-7-..."
              style={inputStyle}
            />
            <button
              type="submit"
              disabled={manualIsbn.replace(/[^0-9Xx]/g, '').length < 10}
              style={submitBtnStyle}
            >
              Go
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Styles ──────────────────────────────────────────────────

const overlayStyle = {
  position: 'fixed',
  inset: 0,
  zIndex: 10000,
  background: COLORS.translucentDark,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 16,
};

const cardStyle = {
  background: COLORS.cardBg,
  borderRadius: 20,
  overflow: 'hidden',
  boxShadow: '0 25px 50px rgba(0,0,0,0.25)',
  minHeight: 200,
};

const headerStyle = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '20px 24px 12px',
};

const titleStyle = {
  ...TYPOGRAPHY.title,
  fontSize: 22,
  margin: 0,
};

const closeBtnStyle = {
  background: 'none',
  border: 'none',
  fontSize: 28,
  color: COLORS.midGray,
  cursor: 'pointer',
  lineHeight: 1,
  padding: '0 4px',
};

const viewfinderWrapStyle = {
  position: 'relative',
  width: '100%',
  height: 360,
  background: COLORS.pureBlack,
  overflow: 'hidden',
};

const viewfinderStyle = {
  width: '100%',
  height: '100%',
};

const cameraOverlayStyle = {
  position: 'absolute',
  inset: 0,
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  justifyContent: 'center',
  color: COLORS.white,
  background: 'rgba(0,0,0,0.6)',
  padding: 24,
  textAlign: 'center',
  ...TYPOGRAPHY.body,
};

const scanningLabelStyle = {
  position: 'absolute',
  bottom: 0,
  left: 0,
  right: 0,
  padding: '8px 16px',
  background: 'rgba(0,0,0,0.55)',
  color: COLORS.white,
  fontSize: 13,
  fontWeight: 500,
  textAlign: 'center',
  pointerEvents: 'none',
  zIndex: 5,
};

const manualFormStyle = {
  padding: '16px 24px 24px',
};

const labelStyle = {
  ...TYPOGRAPHY.bodyEmphasis,
  color: COLORS.nearBlack,
  display: 'block',
  marginBottom: 8,
};

const inputStyle = {
  ...TYPOGRAPHY.body,
  flex: 1,
  border: 'none',
  borderRadius: 8,
  padding: '12px 16px',
  background: 'rgba(0,0,0,0.04)',
  outline: 'none',
  boxSizing: 'border-box',
};

const submitBtnStyle = {
  ...TYPOGRAPHY.bodyEmphasis,
  padding: '12px 20px',
  border: 'none',
  borderRadius: 8,
  background: COLORS.appleBlue,
  color: COLORS.white,
  cursor: 'pointer',
};

export default ISBNScanner;
