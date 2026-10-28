package app.breathe

import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.dreams.DreamService
import android.view.View
import kotlin.math.abs

/**
 * A screensaver, like StandBy on iPhone: charging and on its side, the phone
 * breathes for a few minutes and then goes to sleep. Upright or flat, it gets out
 * of the way and the screen turns off as usual.
 */
class BreatheDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(View(this).apply { setBackgroundColor(Color.BLACK) })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        whenSettled { x, y ->
            // Sideways, and tilted up more than about 30° from lying flat. Dreams
            // follow the sensor even with auto-rotate off, so it turns upright itself.
            if (abs(x) > abs(y) && abs(x) > SensorManager.GRAVITY_EARTH / 2) {
                setContentView(BreatheView(this))
                handler.postDelayed(::sleep, DURATION_MS)
            } else {
                sleep()
            }
        }
    }

    /**
     * Dreams keep the screen on, and a dream that finishes while the screen is kept
     * on wakes the phone up again. So let go first, then finish once the screen
     * timeout has passed: the screen dims as usual, then goes off.
     */
    private fun sleep() {
        isScreenBright = false
        val timeout = Settings.System.getLong(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
        handler.postDelayed(::finish, timeout + 1_000)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacksAndMessages(null)
        super.onDreamingStopped()
    }

    /** Reads gravity once, along the phone's short and long edges. */
    private fun whenSettled(onResult: (x: Float, y: Float) -> Unit) {
        val sensors = getSystemService(SensorManager::class.java)
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return onResult(SensorManager.GRAVITY_EARTH, 0f)
        sensors.registerListener(object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sensors.unregisterListener(this)
                onResult(event.values[0], event.values[1])
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private companion object {
        const val DURATION_MS = 5 * 60 * 1000L
    }
}
