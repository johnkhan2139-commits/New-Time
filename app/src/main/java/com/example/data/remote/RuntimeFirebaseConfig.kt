package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.json.JSONObject

data class ParsedGoogleServices(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val storageBucket: String?,
    val gcmSenderId: String?,
    val webClientId: String?
)

object RuntimeFirebaseConfig {
    private const val TAG = "RuntimeFirebaseConfig"
    private const val PREFS_NAME = "time_runtime_firebase"
    private const val KEY_PROJECT_ID = "project_id"
    private const val KEY_APP_ID = "application_id"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_STORAGE_BUCKET = "storage_bucket"
    private const val KEY_GCM_SENDER_ID = "gcm_sender_id"
    private const val KEY_WEB_CLIENT_ID = "web_client_id"

    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext
        val alreadyReady = runCatching {
            FirebaseApp.getApps(appContext).isNotEmpty() || FirebaseApp.initializeApp(appContext) != null
        }.getOrDefault(false)
        if (alreadyReady) return true

        val saved = loadSavedConfig(appContext) ?: return false
        return initializeWithConfig(appContext, saved).isSuccess
    }

    fun getWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            val resVal = runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
            if (resVal != null) return resVal
        }
        return loadSavedConfig(context.applicationContext)?.webClientId?.takeIf { it.isNotBlank() }
    }

    fun importFromUri(context: Context, uri: Uri): Result<Unit> {
        return try {
            val rawJson = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return Result.failure(IllegalStateException("Unable to read selected file."))
            importFromJson(context, rawJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read google-services.json from Uri", e)
            Result.failure(e)
        }
    }

    fun importFromJson(context: Context, rawJson: String): Result<Unit> {
        return try {
            val parsed = parseGoogleServicesJson(rawJson, context.packageName)
            val appContext = context.applicationContext
            saveConfig(appContext, parsed)
            initializeWithConfig(appContext, parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid google-services.json", e)
            Result.failure(e)
        }
    }

    internal fun parseGoogleServicesJson(rawJson: String, targetPackageName: String): ParsedGoogleServices {
        val root = JSONObject(rawJson.trim())
        val projectInfo = root.optJSONObject("project_info")
            ?: throw IllegalArgumentException("Missing project_info in google-services.json")
        val projectId = projectInfo.optString("project_id").trim()
        require(projectId.isNotEmpty()) { "Missing project_id in google-services.json" }

        val gcmSenderId = projectInfo.optString("project_number").trim().takeIf { it.isNotEmpty() }
        val storageBucket = projectInfo.optString("storage_bucket").trim().takeIf { it.isNotEmpty() }

        val clients = root.optJSONArray("client")
            ?: throw IllegalArgumentException("Missing client array in google-services.json")
        require(clients.length() > 0) { "No Android client entries found in google-services.json" }

        var chosenClient: JSONObject? = null
        for (i in 0 until clients.length()) {
            val clientObj = clients.optJSONObject(i) ?: continue
            val pkg = clientObj.optJSONObject("client_info")
                ?.optJSONObject("android_client_info")
                ?.optString("package_name")
                ?.trim()
            if (pkg == targetPackageName) {
                chosenClient = clientObj
                break
            }
            if (chosenClient == null) {
                chosenClient = clientObj
            }
        }

        val client = chosenClient ?: throw IllegalArgumentException("No valid client entry found.")
        val appId = client.optJSONObject("client_info")
            ?.optString("mobilesdk_app_id")
            ?.trim()
            .orEmpty()
        require(appId.isNotEmpty()) { "Missing mobilesdk_app_id in google-services.json" }

        val apiKeys = client.optJSONArray("api_key")
        val apiKey = if (apiKeys != null && apiKeys.length() > 0) {
            apiKeys.optJSONObject(0)?.optString("current_key")?.trim().orEmpty()
        } else {
            ""
        }
        require(apiKey.isNotEmpty()) { "Missing api_key.current_key in google-services.json" }

        var webClientId: String? = null
        val oauthClients = client.optJSONArray("oauth_client")
        if (oauthClients != null) {
            for (j in 0 until oauthClients.length()) {
                val oauth = oauthClients.optJSONObject(j) ?: continue
                if (oauth.optInt("client_type") == 3) {
                    webClientId = oauth.optString("client_id").trim().takeIf { it.isNotEmpty() }
                    break
                }
            }
        }

        return ParsedGoogleServices(
            projectId = projectId,
            applicationId = appId,
            apiKey = apiKey,
            storageBucket = storageBucket,
            gcmSenderId = gcmSenderId,
            webClientId = webClientId
        )
    }

    private fun initializeWithConfig(context: Context, config: ParsedGoogleServices): Result<Unit> {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                return Result.success(Unit)
            }
            val builder = FirebaseOptions.Builder()
                .setProjectId(config.projectId)
                .setApplicationId(config.applicationId)
                .setApiKey(config.apiKey)
            config.storageBucket?.let { builder.setStorageBucket(it) }
            config.gcmSenderId?.let { builder.setGcmSenderId(it) }
            FirebaseApp.initializeApp(context, builder.build())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseOptions initialization failed", e)
            Result.failure(e)
        }
    }

    private fun saveConfig(context: Context, config: ParsedGoogleServices) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROJECT_ID, config.projectId)
            .putString(KEY_APP_ID, config.applicationId)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_STORAGE_BUCKET, config.storageBucket)
            .putString(KEY_GCM_SENDER_ID, config.gcmSenderId)
            .putString(KEY_WEB_CLIENT_ID, config.webClientId)
            .apply()
    }

    private fun loadSavedConfig(context: Context): ParsedGoogleServices? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val projectId = prefs.getString(KEY_PROJECT_ID, null)?.trim().orEmpty()
        val appId = prefs.getString(KEY_APP_ID, null)?.trim().orEmpty()
        val apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
        if (projectId.isEmpty() || appId.isEmpty() || apiKey.isEmpty()) return null
        return ParsedGoogleServices(
            projectId = projectId,
            applicationId = appId,
            apiKey = apiKey,
            storageBucket = prefs.getString(KEY_STORAGE_BUCKET, null),
            gcmSenderId = prefs.getString(KEY_GCM_SENDER_ID, null),
            webClientId = prefs.getString(KEY_WEB_CLIENT_ID, null)
        )
    }
}
