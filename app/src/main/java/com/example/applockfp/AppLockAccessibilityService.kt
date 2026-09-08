package com.example.applockfp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * خدمة إتاحة (Accessibility) تراقب تغيّر النافذة الأمامية (أي تطبيق يفتح).
 * إذا كان التطبيق ضمن القائمة المقفلة ولم يُفتح قفله بعد في هذه الجلسة،
 * تشغّل LockActivity فوراً لطلب البصمة قبل السماح بالدخول.
 */
class AppLockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName) {
            // نافذة تابعة لتطبيقنا نفسه (شاشة القفل أو الشاشة الرئيسية) - تجاهل
            return
        }

        val lockedPackages = LockedAppsStore.getLockedPackages(this)

        if (lockedPackages.contains(foregroundPackage)) {
            if (!LockedAppsStore.isUnlocked(foregroundPackage)) {
                launchLockScreen(foregroundPackage)
            }
        } else {
            // المستخدم انتقل لتطبيق غير مقفل (أو الشاشة الرئيسية) -
            // نعيد قفل كل التطبيقات المقفلة حتى تُطلب البصمة مجدداً عند العودة إليها
            LockedAppsStore.clearAllUnlocked()
        }
    }

    private fun launchLockScreen(targetPackage: String) {
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(LockActivity.EXTRA_TARGET_PACKAGE, targetPackage)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // لا حاجة لمعالجة خاصة هنا
    }
}
