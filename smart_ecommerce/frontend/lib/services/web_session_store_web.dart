import 'dart:js_interop';

// sessionStorage is scoped to one browser tab and survives F5/reload.
// It is cleared when that tab session ends (subject to browser session restore).
@JS('window.sessionStorage')
external _SessionStorage get _storage;

extension type _SessionStorage(JSObject _) implements JSObject {
  external JSString? getItem(JSString key);
  external void setItem(JSString key, JSString value);
  external void removeItem(JSString key);
}

String? read(String key) {
  try {
    return _storage.getItem(key.toJS)?.toDart;
  } catch (_) {
    return null;
  }
}

void write(String key, String value) {
  try {
    _storage.setItem(key.toJS, value.toJS);
  } catch (_) {
    // Private-mode/browser policy may deny sessionStorage. Login continues
    // in memory, but cannot survive a reload when storage is blocked.
  }
}

void remove(String key) {
  try {
    _storage.removeItem(key.toJS);
  } catch (_) {}
}
