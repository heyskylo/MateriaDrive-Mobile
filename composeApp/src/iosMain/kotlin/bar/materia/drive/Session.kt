package bar.materia.drive

import platform.Foundation.NSUserDefaults

// shortcut: token in UserDefaults, not the Keychain; move to Keychain if the app ships beyond personal use.
actual fun loadSession(): String? = NSUserDefaults.standardUserDefaults.stringForKey("md_session")
actual fun saveSession(token: String?) {
    if (token == null) NSUserDefaults.standardUserDefaults.removeObjectForKey("md_session")
    else NSUserDefaults.standardUserDefaults.setObject(token, "md_session")
}
