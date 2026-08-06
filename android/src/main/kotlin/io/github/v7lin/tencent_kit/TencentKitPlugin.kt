package io.github.v7lin.tencent_kit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import com.tencent.connect.common.Constants
import com.tencent.connect.share.QQShare
import com.tencent.connect.share.QzonePublish
import com.tencent.connect.share.QzoneShare
import com.tencent.tauth.IUiListener
import com.tencent.tauth.Tencent
import com.tencent.tauth.UiError
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import org.json.JSONException
import org.json.JSONObject

/**
 * TencentKitPlugin
 */
class TencentKitPlugin : FlutterPlugin, ActivityAware, ActivityResultListener, MethodCallHandler {

    private object TencentScene {
        const val SCENE_QQ = 0
        const val SCENE_QZONE = 1
    }

    private object TencentRetCode {
        // 网络请求成功发送至服务器，并且服务器返回数据格式正确
        // 这里包括所请求业务操作失败的情况，例如没有授权等原因导致
        const val RET_SUCCESS = 0
        // 网络异常，或服务器返回的数据格式不正确导致无法解析
        const val RET_FAILED = 1
        const val RET_COMMON = -1
        const val RET_USERCANCEL = -2
    }

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private var channel: MethodChannel? = null
    private var applicationContext: Context? = null
    private var activityPluginBinding: ActivityPluginBinding? = null

    private var tencent: Tencent? = null

    // --- FlutterPlugin

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "v7lin.github.io/tencent_kit")
        channel?.setMethodCallHandler(this)
        applicationContext = binding.applicationContext
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
        applicationContext = null
    }

    // --- ActivityAware

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
        activityPluginBinding?.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        activityPluginBinding?.removeActivityResultListener(this)
        activityPluginBinding = null
    }

    // --- ActivityResultListener

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            Constants.REQUEST_LOGIN ->
                Tencent.onActivityResultData(requestCode, resultCode, data, loginListener)
            Constants.REQUEST_QQ_SHARE,
            Constants.REQUEST_QZONE_SHARE ->
                Tencent.onActivityResultData(requestCode, resultCode, data, shareListener)
            else -> false
        }
    }

    // --- MethodCallHandler

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "setIsPermissionGranted" -> {
                val granted = call.argument<Boolean>("granted") == true
                val buildModel = call.argument<String>("build_model")
                if (!TextUtils.isEmpty(buildModel)) {
                    Tencent.setIsPermissionGranted(granted, buildModel)
                } else {
                    Tencent.setIsPermissionGranted(granted)
                }
                result.success(null)
            }

            "registerApp" -> {
                val appId = call.argument<String>("appId")
                // val universalLink = call.argument<String>("universalLink")
                var authority: String? = null
                try {
                    val context = applicationContext!!
                    val providerInfo = context.packageManager.getProviderInfo(
                        ComponentName(context, TencentKitFileProvider::class.java),
                        PackageManager.MATCH_DEFAULT_ONLY,
                    )
                    authority = providerInfo.authority
                } catch (_: PackageManager.NameNotFoundException) {
                    // ignore
                }
                tencent = if (!TextUtils.isEmpty(authority)) {
                    Tencent.createInstance(appId, applicationContext, authority)
                } else {
                    Tencent.createInstance(appId, applicationContext)
                }
                result.success(null)
            }

            "isQQInstalled" -> {
                result.success(tencent != null && isAppInstalled(applicationContext, "com.tencent.mobileqq"))
            }

            "isTIMInstalled" -> {
                result.success(tencent != null && isAppInstalled(applicationContext, "com.tencent.tim"))
            }

            "login" -> login(call, result)
            "loginServerSide" -> loginServerSide(call, result)
            "logout" -> logout(call, result)
            "shareMood" -> shareMood(call, result)
            "shareText" -> shareText(call, result)
            "shareImage" -> shareImage(call, result)
            "shareMusic" -> shareMusic(call, result)
            "shareWebpage" -> shareWebpage(call, result)
            else -> result.notImplemented()
        }
    }

    private fun login(call: MethodCall, result: Result) {
        val scope = call.argument<String>("scope")
        val qrcode = call.argument<Boolean>("qrcode") == true
        val activity = activityPluginBinding?.activity
        if (tencent != null && activity != null) {
            tencent!!.login(activity, scope, loginListener, qrcode)
        }
        result.success(null)
    }

    private fun loginServerSide(call: MethodCall, result: Result) {
        val scope = call.argument<String>("scope")
        val qrcode = call.argument<Boolean>("qrcode") == true
        val activity = activityPluginBinding?.activity
        if (tencent != null && activity != null) {
            tencent!!.loginServerSide(activity, scope, loginListener, qrcode)
        }
        result.success(null)
    }

    private val loginListener = object : IUiListener {
        override fun onComplete(o: Any?) {
            val map = HashMap<String, Any?>()
            try {
                if (o is JSONObject) {
                    val ret = if (!o.isNull("ret")) o.getInt("ret") else TencentRetCode.RET_FAILED
                    val msg = if (!o.isNull("msg")) o.getString("msg") else null
                    if (ret == TencentRetCode.RET_SUCCESS) {
                        val openId = if (!o.isNull("openid")) o.getString("openid") else null
                        val accessToken = if (!o.isNull("access_token")) o.getString("access_token") else null
                        val expiresIn = if (!o.isNull("expires_in")) o.getInt("expires_in") else 0
                        val createAt = System.currentTimeMillis()
                        if (!TextUtils.isEmpty(openId) && !TextUtils.isEmpty(accessToken)) {
                            map["ret"] = TencentRetCode.RET_SUCCESS
                            map["openid"] = openId
                            map["access_token"] = accessToken
                            map["expires_in"] = expiresIn
                            map["create_at"] = createAt
                        } else {
                            map["ret"] = TencentRetCode.RET_COMMON
                            map["msg"] = "openId or accessToken is null."
                        }
                    } else {
                        map["ret"] = TencentRetCode.RET_COMMON
                        map["msg"] = msg
                    }
                }
            } catch (e: JSONException) {
                map["ret"] = TencentRetCode.RET_COMMON
                map["msg"] = e.message
            }
            channel?.invokeMethod("onLoginResp", map)
        }

        override fun onError(uiError: UiError) {
            // 登录失败
            val map = HashMap<String, Any?>()
            map["ret"] = TencentRetCode.RET_COMMON
            map["msg"] = uiError.errorMessage
            channel?.invokeMethod("onLoginResp", map)
        }

        override fun onCancel() {
            // 取消登录
            val map = HashMap<String, Any?>()
            map["ret"] = TencentRetCode.RET_USERCANCEL
            channel?.invokeMethod("onLoginResp", map)
        }

        override fun onWarning(code: Int) {
        }
    }

    private fun logout(call: MethodCall, result: Result) {
        tencent?.logout(applicationContext)
        result.success(null)
    }

    private fun shareMood(call: MethodCall, result: Result) {
        val scene = call.argument<Int>("scene") ?: 0
        if (scene == TencentScene.SCENE_QZONE) {
            val summary = call.argument<String>("summary")
            val imageUris = call.argument<List<String>>("imageUris")
            val videoUri = call.argument<String>("videoUri")

            val params = Bundle()
            if (!TextUtils.isEmpty(summary)) {
                params.putString(QzonePublish.PUBLISH_TO_QZONE_SUMMARY, summary)
            }
            if (imageUris != null && imageUris.isNotEmpty()) {
                val uris = ArrayList<String>()
                for (imageUri in imageUris) {
                    uris.add(Uri.parse(imageUri).path!!)
                }
                params.putStringArrayList(QzonePublish.PUBLISH_TO_QZONE_IMAGE_URL, uris)
            }
            if (!TextUtils.isEmpty(videoUri)) {
                val videoPath = Uri.parse(videoUri).path
                params.putString(QzonePublish.PUBLISH_TO_QZONE_VIDEO_PATH, videoPath)
                params.putInt(QzonePublish.PUBLISH_TO_QZONE_KEY_TYPE, QzonePublish.PUBLISH_TO_QZONE_TYPE_PUBLISHVIDEO)
            } else {
                params.putInt(QzonePublish.PUBLISH_TO_QZONE_KEY_TYPE, QzonePublish.PUBLISH_TO_QZONE_TYPE_PUBLISHMOOD)
            }
            val activity = activityPluginBinding?.activity
            if (tencent != null && activity != null) {
                tencent!!.publishToQzone(activity, params, shareListener)
            }
        }
        result.success(null)
    }

    private fun shareText(call: MethodCall, result: Result) {
        val scene = call.argument<Int>("scene") ?: 0
        if (scene == TencentScene.SCENE_QQ) {
            val summary = call.argument<String>("summary")
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, summary)
            sendIntent.type = "text/*"
            // 普通大众版 > 办公简洁版 > 急速轻聊版
            val context = applicationContext!!
            @Suppress("DEPRECATION")
            val infos = context.packageManager.getInstalledPackages(0)
            if (infos != null && infos.isNotEmpty()) {
                for (packageName in listOf("com.tencent.mobileqq", "com.tencent.tim", "com.tencent.qqlite")) {
                    for (info in infos) {
                        if (packageName == info.packageName) {
                            sendIntent.setPackage(packageName)
                            if (sendIntent.resolveActivity(context.packageManager) != null) {
                                sendIntent.component = ComponentName(packageName, "com.tencent.mobileqq.activity.JumpActivity")
                                activityPluginBinding?.activity?.startActivity(sendIntent)
                                break
                            }
                        }
                    }
                }
            }
        }
        result.success(null)
    }

    private fun shareImage(call: MethodCall, result: Result) {
        val scene = call.argument<Int>("scene") ?: 0
        if (scene == TencentScene.SCENE_QQ) {
            val imageUri = call.argument<String>("imageUri")
            val appName = call.argument<String>("appName")
            val extInt = call.argument<Int>("extInt") ?: 0

            val params = Bundle()
            params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_IMAGE)
            params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL, Uri.parse(imageUri).path)
            if (!TextUtils.isEmpty(appName)) {
                params.putString(QQShare.SHARE_TO_QQ_APP_NAME, appName)
            }
            params.putInt(QQShare.SHARE_TO_QQ_EXT_INT, extInt)
            val activity = activityPluginBinding?.activity
            if (tencent != null && activity != null) {
                tencent!!.shareToQQ(activity, params, shareListener)
            }
        }
        result.success(null)
    }

    private fun shareMusic(call: MethodCall, result: Result) {
        val scene = call.argument<Int>("scene") ?: 0
        if (scene == TencentScene.SCENE_QQ) {
            val title = call.argument<String>("title")
            val summary = call.argument<String>("summary")
            val imageUri = call.argument<String>("imageUri")
            val musicUrl = call.argument<String>("musicUrl")
            val targetUrl = call.argument<String>("targetUrl")
            val appName = call.argument<String>("appName")
            val extInt = call.argument<Int>("extInt") ?: 0

            val params = Bundle()
            params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_AUDIO)
            params.putString(QQShare.SHARE_TO_QQ_TITLE, title)
            if (!TextUtils.isEmpty(summary)) {
                params.putString(QQShare.SHARE_TO_QQ_SUMMARY, summary)
            }
            if (!TextUtils.isEmpty(imageUri)) {
                val uri = Uri.parse(imageUri)
                if (TextUtils.equals("file", uri.scheme)) {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, uri.path)
                } else {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, imageUri)
                }
            }
            params.putString(QQShare.SHARE_TO_QQ_AUDIO_URL, musicUrl)
            params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, targetUrl)
            if (!TextUtils.isEmpty(appName)) {
                params.putString(QQShare.SHARE_TO_QQ_APP_NAME, appName)
            }
            params.putInt(QQShare.SHARE_TO_QQ_EXT_INT, extInt)
            val activity = activityPluginBinding?.activity
            if (tencent != null && activity != null) {
                tencent!!.shareToQQ(activity, params, shareListener)
            }
        }
        result.success(null)
    }

    private fun shareWebpage(call: MethodCall, result: Result) {
        val scene = call.argument<Int>("scene") ?: 0
        val title = call.argument<String>("title")
        val summary = call.argument<String>("summary")
        val imageUri = call.argument<String>("imageUri")
        val targetUrl = call.argument<String>("targetUrl")
        val appName = call.argument<String>("appName")
        val extInt = call.argument<Int>("extInt") ?: 0

        val params = Bundle()
        when (scene) {
            TencentScene.SCENE_QQ -> {
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT)
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title)
                if (!TextUtils.isEmpty(summary)) {
                    params.putString(QQShare.SHARE_TO_QQ_SUMMARY, summary)
                }
                if (!TextUtils.isEmpty(imageUri)) {
                    val uri = Uri.parse(imageUri)
                    if (TextUtils.equals("file", uri.scheme)) {
                        params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, uri.path)
                    } else {
                        params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, imageUri)
                    }
                }
                params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, targetUrl)
                if (!TextUtils.isEmpty(appName)) {
                    params.putString(QQShare.SHARE_TO_QQ_APP_NAME, appName)
                }
                params.putInt(QQShare.SHARE_TO_QQ_EXT_INT, extInt)
                val activity = activityPluginBinding?.activity
                if (tencent != null && activity != null) {
                    tencent!!.shareToQQ(activity, params, shareListener)
                }
            }

            TencentScene.SCENE_QZONE -> {
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzoneShare.SHARE_TO_QZONE_TYPE_IMAGE_TEXT)
                params.putString(QzoneShare.SHARE_TO_QQ_TITLE, title)
                if (!TextUtils.isEmpty(summary)) {
                    params.putString(QzoneShare.SHARE_TO_QQ_SUMMARY, summary)
                }
                if (!TextUtils.isEmpty(imageUri)) {
                    val uris = ArrayList<String>()
                    val uri = Uri.parse(imageUri)
                    if (TextUtils.equals("file", uri.scheme)) {
                        uris.add(uri.path!!)
                    } else {
                        uris.add(imageUri!!)
                    }
                    params.putStringArrayList(QzoneShare.SHARE_TO_QQ_IMAGE_URL, uris)
                }
                params.putString(QzoneShare.SHARE_TO_QQ_TARGET_URL, targetUrl)
                val activity = activityPluginBinding?.activity
                if (tencent != null && activity != null) {
                    tencent!!.shareToQzone(activity, params, shareListener)
                }
            }
        }
        result.success(null)
    }

    private val shareListener = object : IUiListener {
        override fun onComplete(o: Any?) {
            val map = HashMap<String, Any?>()
            try {
                if (o is JSONObject) {
                    val ret = if (!o.isNull("ret")) o.getInt("ret") else TencentRetCode.RET_FAILED
                    val msg = if (!o.isNull("msg")) o.getString("msg") else null
                    if (ret == TencentRetCode.RET_SUCCESS) {
                        map["ret"] = TencentRetCode.RET_SUCCESS
                    } else {
                        map["ret"] = TencentRetCode.RET_COMMON
                        map["msg"] = msg
                    }
                }
            } catch (e: JSONException) {
                map["ret"] = TencentRetCode.RET_COMMON
                map["msg"] = e.message
            }
            channel?.invokeMethod("onShareResp", map)
        }

        override fun onError(error: UiError) {
            val map = HashMap<String, Any?>()
            map["ret"] = TencentRetCode.RET_COMMON
            map["msg"] = error.errorMessage
            channel?.invokeMethod("onShareResp", map)
        }

        override fun onCancel() {
            val map = HashMap<String, Any?>()
            map["ret"] = TencentRetCode.RET_USERCANCEL
            channel?.invokeMethod("onShareResp", map)
        }

        override fun onWarning(code: Int) {
            if (code == Constants.ERROR_NO_AUTHORITY) {
                // 如果authorities为空，sdk会回调这个接口，提醒开发者适配FileProvider
            }
        }
    }

    // ---

    companion object {
        private fun isAppInstalled(context: Context?, packageName: String): Boolean {
            if (context == null) return false
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
