
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
        appContext.getSystemService(Context.SENSOR_SERVICE)
                as SensorManager

    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE)
                as CameraManager

    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE)
                as BatteryManager

    private var activeSensor: Sensor? = null

    private var sensorCallback:
        ((SensorReading) -> Unit)? = null

    @Volatile
    private var lastError: String = ""

    // -----------------------------------------
    // CAMERA
    // -----------------------------------------

    fun isCameraAvailable(): Boolean {
        return try {
            appContext.packageManager.hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY
            )
        } catch (_: Exception) {
            false
        }
    }

    fun openCamera(): Boolean {
        return try {
            val intent = Intent(
                MediaStore.ACTION_IMAGE_CAPTURE
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(
                    appContext.packageManager
                ) == null
            ) {
                lastError = "Camera application unavailable"
                return false
            }

            appContext.startActivity(intent)
            lastError = ""
            true

        } catch (e: Exception) {
            lastError = e.message ?: "Unable to open camera"
            false
        }
    }

    // -----------------------------------------
    // FLASHLIGHT
    // -----------------------------------------

    fun isFlashlightAvailable(): Boolean {
        return try {
            cameraManager.cameraIdList.any { id ->
                val characteristics =
                    cameraManager.getCameraCharacteristics(id)

                characteristics.get(
                    CameraCharacteristics.FLASH_INFO_AVAILABLE
                ) == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getFlashCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->

                val characteristics =
                    cameraManager.getCameraCharacteristics(id)

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
        } catch (_: Exception) {
            null
        }
    }

    fun setFlashlight(enabled: Boolean): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            lastError = "Flashlight control requires Android 6.0 or newer"
            return false
        }

        if (
            appContext.checkSelfPermission(
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            lastError = "Camera permission is required"
            return false
        }

        return try {
            val cameraId = getFlashCameraId()

            if (cameraId == null) {
                lastError = "Flashlight is unavailable"
                return false
            }

            cameraManager.setTorchMode(
                cameraId,
                enabled
            )

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

    fun flashlightOn(): Boolean {
        return setFlashlight(true)
    }

    fun flashlightOff(): Boolean {
        return setFlashlight(false)
    }

    // -----------------------------------------
    // BATTERY
    // -----------------------------------------

    fun getBatteryInfo(): BatteryInfo {

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
            100
        ) ?: 100

        val percentage = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            ).coerceIn(0, 100)
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
            0
        ) ?: 0

        return BatteryInfo(
            percentage = percentage,
            charging = charging,
            temperatureCelsius = temperature / 10f,
            pluggedIn = plugged != 0
        )
    }

    // -----------------------------------------
    // SENSORS
    // -----------------------------------------

    fun getAvailableSensors(): List<SensorInfo> {

        return try {
            sensorManager
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasSensor(sensorType: Int): Boolean {
        return sensorManager.getDefaultSensor(sensorType) != null
    }

    fun startSensorMonitoring(
        sensorType: Int,
        callback: (SensorReading) -> Unit
    ): Boolean {

        return try {

            stopSensorMonitoring()

            val sensor = sensorManager.getDefaultSensor(
                sensorType
            ) ?: run {
                lastError = "Requested sensor is unavailable"
                return false
            }

            activeSensor = sensor
            sensorCallback = callback

            val registered = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )

            if (!registered) {
                activeSensor = null
                sensorCallback = null
                lastError = "Unable to register sensor listener"
                return false
            }

            lastError = ""
            true

        } catch (e: Exception) {
            lastError = e.message ?: "Sensor monitoring failed"
            false
        }
    }

    fun stopSensorMonitoring() {

        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }

        activeSensor = null
        sensorCallback = null
    }

    override fun onSensorChanged(event: SensorEvent?) {

        if (event == null) return

        val reading = SensorReading(
            sensorName = event.sensor.name,
            sensorType = event.sensor.type,
            values = event.values.toList(),
            accuracy = event.accuracy,
            timestamp = event.timestamp
        )

        sensorCallback?.invoke(reading)
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // Sensor accuracy update
    }

    // -----------------------------------------
    // ERROR INFORMATION
    // -----------------------------------------

    fun getLastError(): String {
        return lastError
    }

    // -----------------------------------------
    // RELEASE RESOURCES
    // -----------------------------------------

    fun release() {
        stopSensorMonitoring()
    }
}
