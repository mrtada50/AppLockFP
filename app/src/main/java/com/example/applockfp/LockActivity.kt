package com.example.applockfp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.applockfp.databinding.ActivityLockBinding

class LockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }

    private lateinit var binding: ActivityLockBinding
    private var targetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)

        val appLabel = try {
            targetPackage?.let {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString()
            }
        } catch (e: Exception) {
            null
        }
        binding.lockedAppLabel.text = appLabel ?: getString(R.string.locked_app_generic)

        binding.retryButton.setOnClickListener { showBiometricPrompt() }

        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            binding.statusText.text = getString(R.string.biometric_unavailable)
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    targetPackage?.let { LockedAppsStore.markUnlocked(it) }
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    binding.statusText.text = errString
                    // أي إلغاء أو فشل نهائي يعيد المستخدم للشاشة الرئيسية
                    // بدون أي خيار بديل (لا يوجد رمز أو كلمة سر)
                    goHome()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    binding.statusText.text = getString(R.string.biometric_failed_try_again)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setSubtitle(getString(R.string.unlock_subtitle))
            .setNegativeButtonText(getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onBackPressed() {
        // منع تجاوز شاشة القفل بزر الرجوع - يذهب للرئيسية بدلاً من كشف التطبيق
        goHome()
    }
}
