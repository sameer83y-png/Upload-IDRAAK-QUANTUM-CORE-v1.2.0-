package com.idraak.quantum

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore

data class BatteryInfo(
    val percentage: Int,
    val charging: Boolean,
    val temperatureCelsius: Float,
    val pluggedIn: Boolean
)

data class SensorInfo(
    val name: String,
    val vendor: String,
    val type: Int,
    val maximumRange: Float,
    val resolution: Float
)

data class SensorReading(
    val sensorName: String,
    val sensorType: Int,
    val values: List<Float>,
    val accuracy: Int,
    val timestamp: Long
)

class HardwareController(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext

    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    @Volatile private var activeSensor: Sensor? = null
    @Volatile private var sensorCallback: ((SensorReading) -> Unit)? = null
    @Volatile private var lastError: String = ""

    fun isCameraAvailable(): Boolean =
        try {
            appContext.packageManager.hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY
            )
        } catch (_: Exception) {
            false
        }

    fun openCamera(): Boolean = try {
        if (!isCameraAvailable()) {
            lastError = "Camera hardware is unavailable"
            return false
        }
        appContext.startActivity(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
        lastError = ""
        true
    } catch (e: Exception) {
        lastError = e.message ?: "Unable to open camera"
        false
    }

    private fun getFlashCameraId(): String? = try {
        val ids = cameraManager.cameraIdList
        val rear = ids.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
        rear ?: ids.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: Exception) {
        null
    }

    fun isFlashlightAvailable(): Boolean = getFlashCameraId() != null

    fun setFlashlight(enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            lastError = "Flashlight control requires Android 6.0 or newer"
            return false
        }
        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            lastError = "Camera permission is required"
            return false
        }
        return try {
            val id = getFlashCameraId()
            if (id == null) {
                lastError = "Flashlight is unavailable"
                return false
            }
            cameraManager.setTorchMode(id, enabled)
            lastError = ""
            true
        } catch (e: SecurityException) {
            lastError = "Camera permission denied"
            false
        } catch (e: Exception) {
            lastError = e.message ?: "Flashlight operation failed"
            false
        }
    }

    fun flashlightOn(): Boolean = setFlashlight(true)
    fun flashlightOff(): Boolean = setFlashlight(false)

    fun getBatteryInfo(): BatteryInfo = try {
        val intent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            ).takeIf { it in 0..100 } ?: -1
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val temperature =
            intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

        BatteryInfo(
            percentage = percentage,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            temperatureCelsius =
                if (temperature > 0) temperature / 10f else -1f,
            pluggedIn = plugged != 0
        )
    } catch (e: Exception) {
        lastError = e.message ?: "Unable to read battery information"
        BatteryInfo(-1, false, -1f, false)
    }

    fun getAvailableSensors(): List<SensorInfo> = try {
        sensorManager.getSensorList(Sensor.TYPE_ALL).map {
            SensorInfo(
                name = it.name,
                vendor = it.vendor,
                type = it.type,
                maximumRange = it.maximumRange,
                resolution = it.resolution
            )
        }
    } catch (e: Exception) {
        lastError = e.message ?: "Unable to read available sensors"
        emptyList()
    }

    fun hasSensor(sensorType: Int): Boolean =
        try {
            sensorManager.getDefaultSensor(sensorType) != null
        } catch (_: Exception) {
            false
        }

    @Synchronized
    fun startSensorMonitoring(
        sensorType: Int,
        callback: (SensorReading) -> Unit
    ): Boolean {
        stopSensorMonitoring()
        return try {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor == null) {
                lastError = "Requested sensor is unavailable"
                return false
            }
            val registered = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (!registered) {
                sensorManager.unregisterListener(this)
                activeSensor = null
                sensorCallback = null
                lastError = "Unable to register sensor listener"
                return false
            }
            activeSensor = sensor
            sensorCallback = callback
            lastError = ""
            true
        } catch (e: Exception) {
            sensorManager.unregisterListener(this)
            activeSensor = null
            sensorCallback = null
            lastError = e.message ?: "Sensor monitoring failed"
            false
        }
    }

    @Synchronized
    fun stopSensorMonitoring() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            lastError = e.message ?: "Unable to stop sensor monitoring"
        }
        activeSensor = null
        sensorCallback = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor != activeSensor) return
        val reading = SensorReading(
            sensorName = event.sensor.name,
            sensorType = event.sensor.type,
            values = event.values.toList(),
            accuracy = event.accuracy,
            timestamp = event.timestamp
        )
        try {
            sensorCallback?.invoke(reading)
        } catch (e: Exception) {
            lastError = e.message ?: "Sensor callback failed"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun getLastError(): String = lastError

    fun release() {
        stopSensorMonitoring()
    }
}
