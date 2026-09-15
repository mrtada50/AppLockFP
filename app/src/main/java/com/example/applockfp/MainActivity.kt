package com.example.applockfp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.applockfp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private val appList = mutableListOf<AppInfo>()

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private var settingsUnlocked = false
    private var isPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AppDeviceAdminReceiver::class.java)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppListAdapter(appList) { app, locked ->
            LockedAppsStore.setLocked(this, app.packageName, locked)
        }
        binding.recyclerView.adapter = adapter

        binding.enableAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.enableDeviceAdminButton.setOnClickListener {
            requestDeviceAdmin()
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText ?: "")
                return true
            }
        })

        binding.settingsRetryButton.setOnClickListener { showSettingsBiometricPrompt() }

        loadApps()
    }

    override fun onStart() {
        super.onStart()
        if (!settingsUnlocked) {
            binding.settingsLockOverlay.visibility = View.VISIBLE
            showSettingsBiometricPrompt()
        } else {
            binding.settingsLockOverlay.visibility = View.GONE
        }
    }

    override fun onStop() {
        super.onStop()
        settingsUnlocked = false
    }

    override fun onResume() {
        super.onResume()
        updateStatusLabels()
    }

    private fun requestDeviceAdmin() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_explanation)
                )
            }
            startActivity(intent)
        }
    }

    private fun showSettingsBiometricPrompt() {
        if (isPromptShowing) return

        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            binding.settingsLockStatus.text = getString(R.string.biometric_unavailable)
            return
        }

        isPromptShowing = true
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isPromptShowing = false
                    settingsUnlocked = true
                    binding.settingsLockOverlay.visibility = View.GONE
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isPromptShowing = false
                    binding.settingsLockStatus.text = errString
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    binding.settingsLockStatus.text = getString(R.string.biometric_failed_try_again)
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

    private fun updateStatusLabels() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        binding.biometricStatus.text = if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            getString(R.string.biometric_ready)
        } else {
            getString(R.string.biometric_not_ready)
        }

        binding.accessibilityStatus.text = if (isAccessibilityServiceEnabled()) {
            getString(R.string.accessibility_enabled)
        } else {
            getString(R.string.accessibility_disabled)
        }

        binding.deviceAdminStatus.text = if (devicePolicyManager.isAdminActive(adminComponent)) {
            getString(R.string.device_admin_enabled)
        } else {
            getString(R.string.device_admin_disabled)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${AppLockAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) return true
        }
        return false
    }

    private fun loadApps() {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val lockedPackages = LockedAppsStore.getLockedPackages(this@MainActivity)

            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedApps = pm.queryIntentActivities(intent, 0)

            val infos = resolvedApps
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    AppInfo(
                        packageName = pkg,
                        label = resolveInfo.loadLabel(pm).toString(),
                        icon = resolveInfo.loadIcon(pm),
                        locked = lockedPackages.contains(pkg)
                    )
                }
                .sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                adapter.submitFullList(infos)
            }
        }
    }
}
