package com.lagradost.cloudstream3

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log

/**
 * Central manager for resetting / deleting all plugins and plugin-related settings.
 *
 * Usage:
 *   PluginResetManager.showResetDialog(this) // from an Activity/Context
 *
 * Optionally pass a callback to be notified when reset completes:
 *   PluginResetManager.showResetDialog(this) { success -> /* update UI */ }
 *
 * IMPORTANT: Replace candidatePaths and prefs keys (KEYS_TO_REMOVE) to match your app.
 */
object PluginResetManager {
    private const val TAG = "PluginResetManager"

    /**
     * Show a confirmation dialog. If user confirms, performs reset on a background thread.
     * @param ctx Activity context (used for dialog + toasts). Should be an Activity context.
     * @param onFinished Optional callback invoked on main thread with success flag.
     */
    fun showResetDialog(ctx: Context, onFinished: ((Boolean) -> Unit)? = null) {
        AlertDialog.Builder(ctx)
            .setTitle("Reset plugins")
            .setMessage("This will delete all installed plugins and reset plugin-related settings. This cannot be undone. Continue?")
            .setPositiveButton("Reset") { dialog, _ ->
                dialog.dismiss()
                performReset(ctx, onFinished)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /**
     * Perform the reset: deletes files, clears preferences, clears in-memory caches.
     * Runs on Dispatchers.IO and invokes onFinished on Dispatchers.Main.
     */
    fun performReset(ctx: Context, onFinished: ((Boolean) -> Unit)? = null) {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        scope.launch {
            var overallSuccess = true
            try {
                Log.d(TAG, "Starting plugin reset")

                // 1) Delete plugin files
                val deletedAny = deletePluginFiles(ctx)
                Log.d(TAG, "Plugin file deletion completed: deletedAny=$deletedAny")

                // 2) Clear plugin-related shared preferences
                val prefsCleared = clearPluginPreferences(ctx)
                Log.d(TAG, "Plugin prefs cleared: $prefsCleared")

                // 3) Clear in-memory structures via callback hooks (if app provides)
                // We can't access MainActivity internals here; provide a callback to let
                // the caller clear in-memory caches (e.g., allProviders, apis, APIHolder.apiMap).
                // If caller didn't supply a callback, we still null local singleton caches if any.
                // Example: APIHolder.apiMap = null  (uncomment if available)
                try {
                    // If your app has a global cache holder, clear it here:
                    // APIHolder.apiMap = null
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to clear APIHolder or app caches here: ${e.message}")
                }

                overallSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Error during plugin reset: ${e.message}", e)
                overallSuccess = false
            }

            withContext(Dispatchers.Main) {
                try {
                    if (overallSuccess) {
                        Toast.makeText(ctx, "Plugins reset completed.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(ctx, "Plugins reset failed. Check logs.", Toast.LENGTH_LONG).show()
                    }
                    onFinished?.invoke(overallSuccess)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to show toast or run callback: ${e.message}")
                } finally {
                    // cancel job if still active
                    job.cancel()
                }
            }
        }
    }

    /**
     * Delete plugin directories and plugin-like files. Return true if any deletion happened.
     *
     * Adjust candidateDirs and the filename patterns to match how your plugin loader stores files.
     */
    private fun deletePluginFiles(ctx: Context): Boolean {
        var anyDeleted = false

        val filesDir = ctx.filesDir
        val cacheDir = ctx.cacheDir
        val externalFiles = ctx.getExternalFilesDir(null)

        val candidateDirs = mutableListOf<File?>(
            File(filesDir, "plugins"),
            File(cacheDir, "plugins"),
            externalFiles?.let { File(it, "plugins") },
            File(filesDir, "installed_plugins"),
            File(filesDir, "apks"),
        ).filterNotNull()

        candidateDirs.forEach { dir ->
            if (dir.exists()) {
                try {
                    if (dir.deleteRecursively()) {
                        Log.d(TAG, "Deleted plugin dir: ${dir.absolutePath}")
                        anyDeleted = true
                    } else {
                        Log.w(TAG, "Could not fully delete ${dir.absolutePath}")
                        // Try to delete contents individually
                        dir.listFiles()?.forEach { f ->
                            try {
                                if (f.deleteRecursively()) {
                                    anyDeleted = true
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed deleting child ${f.absolutePath}: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting ${dir.absolutePath}: ${e.message}")
                }
            }
        }

        // Also delete plugin-like files placed directly inside filesDir
        filesDir.listFiles()?.forEach { f ->
            try {
                val name = f.name.lowercase()
                if (name.startsWith("plugin_") || name.endsWith(".plugin") || name.endsWith(".jar") || name.endsWith(".apk")) {
                    if (f.delete()) {
                        Log.d(TAG, "Deleted plugin file: ${f.absolutePath}")
                        anyDeleted = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting file ${f?.absolutePath}: ${e.message}")
            }
        }

        return anyDeleted
    }

    /**
     * Clear plugin-related SharedPreferences entries. Returns true if any keys were removed.
     *
     * IMPORTANT: Edit the KEYS_TO_REMOVE list below to match the actual keys used by your app.
     */
    private fun clearPluginPreferences(ctx: Context): Boolean {
        try {
            val prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val originalKeys = prefs.all.keys

            // Replace these keys with keys actually used by your app.
            val KEYS_TO_REMOVE = listOf(
                "USER_PROVIDER_API",
                "installed_plugins",
                "custom_providers",
                "last_plugin_scan_time"
            )

            var removed = false
            KEYS_TO_REMOVE.forEach { k ->
                if (prefs.contains(k)) {
                    editor.remove(k)
                    removed = true
                    Log.d(TAG, "Removed pref key: $k")
                }
            }

            // Remove any keys with common prefixes used by plugin system
            originalKeys.filter { it.startsWith("plugin_") || it.startsWith("provider_") || it.startsWith("custom_") }
                .forEach { key ->
                    editor.remove(key)
                    removed = true
                    Log.d(TAG, "Removed pref by prefix: $key")
                }

            editor.apply()
            return removed
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing plugin prefs: ${e.message}")
            return false
        }
    }
}
