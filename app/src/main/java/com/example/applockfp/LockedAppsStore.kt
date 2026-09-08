package com.example.applockfp

import android.content.Context

/**
 * يخزن قائمة حزم التطبيقات المختارة للقفل (SharedPreferences)،
 * ويتتبع أي تطبيق تم فتح قفله مؤخراً حتى لا يعاد طلب البصمة
 * بشكل متكرر أثناء الاستخدام العادي لنفس التطبيق.
 */
object LockedAppsStore {
    private const val PREFS = "app_lock_prefs"
    private const val KEY_LOCKED_PACKAGES = "locked_packages"

    fun getLockedPackages(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HashSet(prefs.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet())
    }

    fun setLocked(context: Context, packageName: String, locked: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = getLockedPackages(context)
        if (locked) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
    }

    // ---- تتبع الجلسات المفتوحة مؤقتاً (في الذاكرة فقط، تُمسح عند إغلاق الخدمة) ----

    private val unlockedPackages = HashSet<String>()

    fun markUnlocked(packageName: String) {
        unlockedPackages.add(packageName)
    }

    fun isUnlocked(packageName: String): Boolean = unlockedPackages.contains(packageName)

    fun clearUnlocked(packageName: String) {
        unlockedPackages.remove(packageName)
    }

    fun clearAllUnlocked() {
        unlockedPackages.clear()
    }
}
