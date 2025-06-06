package app.aaps

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.utils.JsonHelper
import app.aaps.database.persistence.CompatDBHelper
import app.aaps.di.DaggerAppComponent
import app.aaps.implementation.lifecycle.ProcessLifecycleListener
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.plugins.aps.openAPSAIMI.StepService
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.themes.ThemeSwitcherPlugin
import app.aaps.receivers.BTReceiver
import app.aaps.receivers.ChargingStateReceiver
import app.aaps.receivers.KeepAliveWorker
import app.aaps.receivers.TimeDateOrTZChangeReceiver
import app.aaps.ui.activityMonitor.ActivityMonitor
import app.aaps.ui.widget.Widget
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.ktx.remoteConfig
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import rxdogtag2.RxDogTag
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import android.provider.Settings

class MainApp : DaggerApplication() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var versionCheckersUtils: VersionCheckerUtils
    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var plugins: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var compatDBHelper: CompatDBHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var processLifecycleListener: Provider<ProcessLifecycleListener>
    @Inject lateinit var themeSwitcherPlugin: ThemeSwitcherPlugin
    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var rh: Provider<ResourceHelper>

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug("onCreate")
        aapsLogger.debug("onCreate - début")
        copyModelToInternalStorage(this)
        aapsLogger.debug("onCreate - après copyModelToFileSystem")
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener.get())
        scope.launch {
            RxDogTag.install()
            setRxErrorHandler()
            LocaleHelper.update(this@MainApp)

            var gitRemote: String? = config.REMOTE
            var commitHash: String? = BuildConfig.HEAD
            if (gitRemote?.contains("NoGitSystemAvailable") == true) {
                gitRemote = null
                commitHash = null
            }
            disposable += compatDBHelper.dbChangeDisposable()
            registerActivityLifecycleCallbacks(activityMonitor)
            runOnUiThread { themeSwitcherPlugin.setThemeMode() }
            aapsLogger.debug("Version: " + config.VERSION_NAME)
            aapsLogger.debug("BuildVersion: " + config.BUILD_VERSION)
            aapsLogger.debug("Remote: " + config.REMOTE)
            registerLocalBroadcastReceiver()
            setupRemoteConfig()

            // trigger here to see the new version on app start after an update
            handler.postDelayed({ versionCheckersUtils.triggerCheckVersion() }, 30000)

            // Register all tabs in app here
            pluginStore.plugins = plugins
            configBuilder.initialize()

            // delayed actions to make rh context updated for translations
            handler.postDelayed(
                {
                    // log version
                    disposable += persistenceLayer.insertVersionChangeIfChanged(config.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash).subscribe()
                    // log app start
                    if (preferences.get(BooleanKey.NsClientLogAppStart))
                        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                            therapyEvent = TE(
                                timestamp = dateUtil.now(),
                                type = TE.Type.NOTE,
                                note = rh.get().gs(app.aaps.core.ui.R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL,
                                glucoseUnit = GlucoseUnit.MGDL
                            ),
                            action = Action.START_AAPS,
                            source = Sources.Aaps, note = "", listValues = listOf()
                        ).subscribe()
                }, 10000
            )
            KeepAliveWorker.schedule(this@MainApp)
            localAlertUtils.shortenSnoozeInterval()
            localAlertUtils.preSnoozeAlarms()
            doMigrations()

            //  schedule widget update
            refreshWidget = Runnable {
                handler.postDelayed(refreshWidget, 60000)
                Widget.updateWidget(this@MainApp, "ScheduleEveryMin")
            }
            handler.postDelayed(refreshWidget, 60000)
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            sensorManager.registerListener(StepService, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            config.appInitialized = true
        }
    }

    private fun copyModelToInternalStorage(context: Context) {
        aapsLogger.debug("copyModelToInternalStorage - début")
        try {
            val assetManager = context.assets
            aapsLogger.debug("copyModelToInternalStorage - assetManager : $assetManager")

            // Définition du répertoire cible dans le stockage externe
            val externalDir = File(Environment.getExternalStorageDirectory().absolutePath + "/Documents/AAPS/ml")
            if (!externalDir.exists() && !externalDir.mkdirs()) {
                Log.e("ModelCopyError", "Impossible de créer le répertoire : ${externalDir.absolutePath}")
                return
            }

            // Fonction générique pour copier les fichiers
            fun copyAssetToFile(assetName: String, destinationFile: File) {
                try {
                    aapsLogger.debug("copyModelToInternalStorage - Copie de $assetName vers ${destinationFile.absolutePath}")
                    assetManager.open(assetName).use { inputStream ->
                        FileOutputStream(destinationFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Log.d("ModelCopy", "Fichier '$assetName' copié dans ${destinationFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("ModelCopyError", "Erreur lors de la copie de $assetName : ${e.message}")
                }
            }

            // Copie des fichiers nécessaires
            copyAssetToFile("model.tflite", File(externalDir, "model.tflite"))
            copyAssetToFile("modelUAM.tflite", File(externalDir, "modelUAM.tflite"))

            // Vérification si les fichiers existent après copie
            val modelFilePath = "${externalDir.absolutePath}/model.tflite"
            val modelFile = File(modelFilePath)
            if (modelFile.exists()) {
                Log.d("FileCheck", "Le fichier existe à l'emplacement $modelFilePath")
            } else {
                Log.e("FileCheck", "Le fichier n'existe pas à l'emplacement $modelFilePath")
            }

            val uamFilePath = "${externalDir.absolutePath}/modelUAM.tflite"
            val uamFile = File(uamFilePath)
            if (uamFile.exists()) {
                Log.d("FileCheck", "Le fichier existe à l'emplacement $uamFilePath")
            } else {
                Log.e("FileCheck", "Le fichier n'existe pas à l'emplacement $uamFilePath")
            }

            aapsLogger.debug("copyModelToInternalStorage - Copie terminée")

        } catch (e: Exception) {
            Log.e("ModelCopyError", "Erreur globale lors de la copie: ${e.message}")
        }
    }

    private fun setRxErrorHandler() {
        RxJavaPlugins.setErrorHandler { t: Throwable ->
            var e = t
            if (e is UndeliverableException) {
                e = e.cause!!
            }
            if (e is IOException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (e is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (e is NullPointerException || e is IllegalArgumentException) {
                // that's likely a bug in the application
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            if (e is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            aapsLogger.warn(LTag.CORE, "Undeliverable exception received, not sure what to do", e.localizedMessage)
        }
    }

    private fun doMigrations() {
        // set values for different builds
        // 3.1.0
        if (preferences.getIfExists(StringKey.MaintenanceEmail) == "logs@androidaps.org")
            preferences.put(StringKey.MaintenanceEmail, "logs@aaps.app")
        // fix values for theme switching
        sp.putString(app.aaps.plugins.main.R.string.value_dark_theme, "dark")
        sp.putString(app.aaps.plugins.main.R.string.value_light_theme, "light")
        sp.putString(app.aaps.plugins.main.R.string.value_system_theme, "system")
        // 3.3
        if (preferences.get(IntKey.OverviewEatingSoonDuration) == 0) preferences.remove(IntKey.OverviewEatingSoonDuration)
        if (preferences.get(UnitDoubleKey.OverviewEatingSoonTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewEatingSoonTarget)
        if (preferences.get(IntKey.OverviewActivityDuration) == 0) preferences.remove(IntKey.OverviewActivityDuration)
        if (preferences.get(UnitDoubleKey.OverviewActivityTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewActivityTarget)
        if (preferences.get(IntKey.OverviewHypoDuration) == 0) preferences.remove(IntKey.OverviewHypoDuration)
        if (preferences.get(UnitDoubleKey.OverviewHypoTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewHypoTarget)
        if (preferences.get(UnitDoubleKey.OverviewLowMark) == 0.0) preferences.remove(UnitDoubleKey.OverviewLowMark)
        if (preferences.get(UnitDoubleKey.OverviewHighMark) == 0.0) preferences.remove(UnitDoubleKey.OverviewHighMark)
        if (preferences.getIfExists(BooleanKey.GeneralSimpleMode) == null)
            preferences.put(BooleanKey.GeneralSimpleMode, !preferences.get(BooleanKey.GeneralSetupWizardProcessed))
        // Migrate from OpenAPSSMBDynamicISFPlugin
        if (sp.getBoolean("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled", false)) {
            sp.remove("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled")
            sp.remove("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Visible")
            sp.putBoolean("ConfigBuilder_APS_OpenAPSSMB_Enabled", true)
            preferences.put(BooleanKey.ApsUseDynamicSensitivity, true)
        }
        // convert Double to Int
        try {
            val dynIsf = sp.getDouble("DynISFAdjust", 0.0)
            if (dynIsf != 0.0 && dynIsf.toInt() != preferences.get(IntKey.ApsDynIsfAdjustmentFactor))
                preferences.put(IntKey.ApsDynIsfAdjustmentFactor, dynIsf.toInt())
        } catch (_: Exception) { /* ignore */
        }
        // Clear SmsOtpPassword if wrongly replaced
        if (preferences.get(StringKey.SmsOtpPassword).length > 10) preferences.put(StringKey.SmsOtpPassword, "")
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerAppComponent
            .builder()
            .application(this)
            .build()
    }

    private fun registerLocalBroadcastReceiver() {
        var filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(TimeDateOrTZChangeReceiver(), filter)
        filter = IntentFilter()
        @Suppress("DEPRECATION")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(NetworkChangeReceiver(), filter)
        filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(ChargingStateReceiver(), filter)
        filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(BTReceiver(), filter)
    }

    private fun setupRemoteConfig() {
        FirebaseApp.initializeApp(this)
        Firebase.remoteConfig.also { firebaseRemoteConfig ->

            firebaseRemoteConfig.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings
                    .Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
            )
            firebaseRemoteConfig
                .fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        aapsLogger.debug("RemoteConfig received successfully")
                        @Suppress("UNCHECKED_CAST")
                        (versionCheckersUtils::class.declaredMemberProperties.find { it.name == "definition" } as KMutableProperty<Any>?)
                            ?.let {
                                val merged = JsonHelper.merge(it.getter.call(versionCheckersUtils) as JSONObject, JSONObject(firebaseRemoteConfig.getString("defs")))
                                it.setter.call(versionCheckersUtils, merged)
                            }
                    } else aapsLogger.error("RemoteConfig fetch failed")
                }
        }
    }

    override fun onTerminate() {
        aapsLogger.debug(LTag.CORE, "onTerminate")
        unregisterActivityLifecycleCallbacks(activityMonitor)
        uiInteraction.stopAlarm("onTerminate")
        super.onTerminate()
    }
}
