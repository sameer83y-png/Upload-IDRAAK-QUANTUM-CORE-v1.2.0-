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

    private val appContext: Context = context.applicationContext

    private val sensorManager: SensorManager? =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val cameraManager: CameraManager? =
        appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val batteryManager: BatteryManager? =
        appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    @Volatile
    private var activeSensor: Sensor? = null

    @Volatile
    private var sensorCallback: ((SensorReading) -> Unit)? = null

    @Volatile
    private var lastError: String = ""

    // --------------------------------------------------
    // CAMERA
    // --------------------------------------------------

    fun isCameraAvailable(): Boolean {
        return try {
            appContext.packageManager.hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY
            )
        } catch (e: Exception) {
            lastError = e.message ?: "Unable to check camera availability"
            false
        }
    }

    fun openCamera(): Boolean {
        try {
            if (!isCameraAvailable()) {
                lastError = "Camera hardware is unavailable"
                return false
            }

            val intent = Intent(
                MediaStore.ACTION_IMAGE_CAPTURE
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            appContext.startActivity(intent)

            lastError = ""
            return true

        } catch (e: Exception) {
            lastError = e.message ?: "Unable to open camera"
            return false
        }
    }

    // --------------------------------------------------
    // FLASHLIGHT
    // --------------------------------------------------

    private fun getFlashCameraId(): String? {
        try {
            val manager = cameraManager ?: run {
                lastError = "Camera service is unavailable"
                return null
            }

            val cameraIds = manager.cameraIdList

            val rearCamera = cameraIds.firstOrNull { id ->
                val characteristics =
                    manager.getCameraCharacteristics(id)

                val hasFlash =
                    characteristics.get(
                        CameraCharacteristics.FLASH_INFO_AVAILABLE
                    ) == true

                val facing =
                    characteristics.get(
                        CameraCharacteristics.LENS_FACING
                    )

                hasFlash &&
                    facing == CameraCharacteristics.LENS_FACING_BACK
            }

            if (rearCamera != null) {
                return rearCamera
            }

            return cameraIds.firstOrNull { id ->
                val characteristics =
                    manager.getCameraCharacteristics(id)

                characteristics.get(
                    CameraCharacteristics.FLASH_INFO_AVAILABLE
                ) == true
            }

        } catch (e: Exception) {
            lastError = e.message ?: "Unable to detect flashlight"
            return null
        }
    }

    fun isFlashlightAvailable(): Boolean {
        return getFlashCameraId() != null
    }

    fun setFlashlight(enabled: Boolean): Boolean {
        if (
            appContext.checkSelfPermission(
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            lastError = "Camera permission is required for flashlight control"
            return false
        }

        try {
            val manager = cameraManager ?: run {
                lastError = "Camera service is unavailable"
                return false
            }

            val cameraId = getFlashCameraId()

            if (cameraId == null) {
                if (lastError.isBlank()) {
                    lastError = "Flashlight is unavailable"
                }
                return false
            }

            manager.setTorchMode(cameraId, enabled)

            lastError = ""
            return true

        } catch (e: SecurityException) {
            lastError = "Camera permission denied"
            return false

        } catch (e: Exception) {
            lastError = e.message ?: "Flashlight operation failed"
            return false
        }
    }

    fun flashlightOn(): Boolean {
        return setFlashlight(true)
    }

    fun flashlightOff(): Boolean {
        return setFlashlight(false)
    }

    // --------------------------------------------------
    // BATTERY
    // --------------------------------------------------

    fun getBatteryInfo(): BatteryInfo {
        try {
            val batteryIntent = appContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val level = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_LEVEL,
                -1
            ) ?: -1

            val scale = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_SCALE,
                -1
            ) ?: -1

            val percentage = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale)
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                val capacity = batteryManager?.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                ) ?: -1

                if (capacity in 0..100) capacity else -1
            }

            val status = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                -1
            ) ?: -1

            val charging =
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val plugged = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                0
            ) ?: 0

            val temperature = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                -1
            ) ?: -1

            lastError = ""

            return BatteryInfo(
                percentage = percentage,
                charging = charging,
                temperatureCelsius = if (temperature >= 0) {
                    temperature / 10f
                } else {
                    -1f
                },
                pluggedIn = plugged != 0
            )

        } catch (e: Exception) {
            lastError = e.message ?: "Unable to read battery information"

            return BatteryInfo(
                percentage = -1,
                charging = false,
                temperatureCelsius = -1f,
                pluggedIn = false
            )
        }
    }

    // --------------------------------------------------
    // SENSOR INFORMATION
    // --------------------------------------------------

    fun getAvailableSensors(): List<SensorInfo> {
        try {
            val manager = sensorManager ?: run {
                lastError = "Sensor service is unavailable"
                return emptyList()
            }

            val result = manager
                .getSensorList(Sensor.TYPE_ALL)
                .map { sensor ->
                    SensorInfo(
                        name = sensor.name,
                        vendor = sensor.vendor,
                        type = sensor.type,
                        maximumRange = sensor.maximumRange,
                        resolution = sensor.resolution
                    )
                }

            lastError = ""
            return result

        } catch (e: Exception) {
            lastError = e.message ?: "Unable to read available sensors"
            return emptyList()
        }
    }

    fun hasSensor(sensorType: Int): Boolean {
        return try {
            val manager = sensorManager ?: return false
            manager.getDefaultSensor(sensorType) != null
        } catch (e: Exception) {
            lastError = e.message ?: "Unable to check sensor"
            false
        }
    }

    // --------------------------------------------------
    // SENSOR MONITORING
    // --------------------------------------------------

    @Synchronized
    fun startSensorMonitoring(
        sensorType: Int,
        callback: (SensorReading) -> Unit
    ): Boolean {
        stopSensorMonitoring()

        val manager = sensorManager

        if (manager == null) {
            lastError = "Sensor service is unavailable"
            return false
        }

        try {
            val sensor = manager.getDefaultSensor(sensorType)

            if (sensor == null) {
                lastError = "Requested sensor is unavailable"
                return false
            }

            val registered = manager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )

            if (!registered) {
                manager.unregisterListener(this)
                activeSensor = null
                sensorCallback = null
                lastError = "Unable to register sensor listener"
                return false
            }

            activeSensor = sensor
            sensorCallback = callback

            lastError = ""
            return true

        } catch (e: Exception) {
            manager.unregisterListener(this)

            activeSensor = null
            sensorCallback = null

            lastError = e.message ?: "Sensor monitoring failed"
            return false
        }
    }

    @Synchronized
    fun stopSensorMonitoring() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            lastError = e.message ?: "Unable to stop sensor monitoring"
        } finally {
            activeSensor = null
            sensorCallback = null
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor != activeSensor) return

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

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // Sensor accuracy updates are optional.
    }

    // --------------------------------------------------
    // ERROR INFORMATION
    // --------------------------------------------------

    fun getLastError(): String {
        return lastError
    }

    fun clearLastError() {
        lastError = ""
    }

    // --------------------------------------------------
    // RESOURCE CLEANUP
    // --------------------------------------------------

    fun release() {
        stopSensorMonitoring()
    }
}
