package com.example.minimal

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private var count = 0
    private var isDark = false

    // 感測器管理與狀態
    private lateinit var sensorManager: SensorManager
    private var vibrator: Vibrator? = null
    private var isSensorPaused = false

    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var magnetSensor: Sensor? = null
    private var proximitySensor: Sensor? = null

    // 電子羅盤矩陣與資料快取
    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    // 搖一搖偵測狀態
    private var shakeCount = 0
    private var lastShakeTime = 0L

    // UI 元件參照（供感測器即時更新）
    private lateinit var compassText: TextView
    private lateinit var accelText: TextView
    private lateinit var shakeStatusText: TextView
    private lateinit var gyroText: TextView
    private lateinit var lightText: TextView
    private lateinit var lightBar: ProgressBar
    private lateinit var proximityText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        val dp = resources.displayMetrics.density
        fun Int.dp(): Int = (this * dp).toInt()

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 40.dp(), 20.dp(), 32.dp())
        }
        scrollView.addView(root)

        val title = TextView(this).apply {
            text = "⚡ 極簡原生 Android UI"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        }
        val subtitle = TextView(this).apply {
            text = "純網頁建立・GitHub 雲端直編・即時感測器儀表板"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 6.dp(), 0, 20.dp())
        }
        root.addView(title)
        root.addView(subtitle)

        val cards = mutableListOf<LinearLayout>()

        fun createCard(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            background = GradientDrawable().apply {
                cornerRadius = 16.dp().toFloat()
                setColor(Color.WHITE)
            }
            elevation = 4.dp().toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp() }
            cards.add(this)
        }

        fun createSectionLabel(label: String): TextView = TextView(this).apply {
            text = label
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 0, 0, 8.dp())
        }

        // ==========================================
        // 卡片 1：🧭 即時感測器儀表板 (SensorManager)
        // ==========================================
        val sensorCard = createCard()

        val sensorHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8.dp())
        }
        val sensorTitle = TextView(this).apply {
            text = "🧭 即時感測器儀表板"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pauseResumeBtn = Button(this).apply {
            text = "⏸ 暫停"
            textSize = 12f
            setOnClickListener {
                isSensorPaused = !isSensorPaused
                if (isSensorPaused) {
                    unregisterSensors()
                    text = "▶ 繼續"
                    Toast.makeText(context, "已暫停感測器讀取（鎖定當前數值）", Toast.LENGTH_SHORT).show()
                } else {
                    registerSensors()
                    text = "⏸ 暫停"
                }
            }
        }
        sensorHeaderRow.addView(sensorTitle)
        sensorHeaderRow.addView(pauseResumeBtn)
        sensorCard.addView(sensorHeaderRow)

        // 1. 電子羅盤
        compassText = TextView(this).apply {
            text = if (accelSensor != null && magnetSensor != null) {
                "🧭 電子羅盤：校準中... 請平放或轉動手機"
            } else {
                "🧭 電子羅盤：此設備缺少磁力計"
            }
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, 4.dp(), 0, 8.dp())
        }
        sensorCard.addView(compassText)

        // 2. 加速度計 + 搖一搖偵測
        accelText = TextView(this).apply {
            text = if (accelSensor != null) "📐 加速度計 (m/s²)：讀取中..." else "📐 加速度計：不支援"
            textSize = 13.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 4.dp(), 0, 4.dp())
        }
        sensorCard.addView(accelText)

        shakeStatusText = TextView(this).apply {
            text = "📳 搖一搖偵測：尚未觸發（大力搖晃手機試試！）"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#059669"))
            setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
            background = GradientDrawable().apply {
                cornerRadius = 8.dp().toFloat()
                setColor(Color.parseColor("#ECFDF5"))
            }
        }
        sensorCard.addView(shakeStatusText)

        // 3. 陀螺儀
        gyroText = TextView(this).apply {
            text = if (gyroSensor != null) "🌀 陀螺儀 (rad/s)：讀取中..." else "🌀 陀螺儀：此設備未配備"
            textSize = 13.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 10.dp(), 0, 6.dp())
        }
        sensorCard.addView(gyroText)

        // 4. 環境光感測器
        lightText = TextView(this).apply {
            text = if (lightSensor != null) "💡 環境光感測：讀取中..." else "💡 環境光感測：此設備未配備"
            textSize = 13.5f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 4.dp(), 0, 4.dp())
        }
        lightBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                10.dp()
            ).apply { bottomMargin = 8.dp() }
        }
        sensorCard.addView(lightText)
        sensorCard.addView(lightBar)

        // 5. 距離感測器（手遮聽筒測試）
        proximityText = TextView(this).apply {
            text = if (proximitySensor != null) "🖐️ 距離感測器：讀取中..." else "🖐️ 距離感測器：此設備未配備"
            textSize = 13.5f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 4.dp(), 0, 2.dp())
        }
        sensorCard.addView(proximityText)

        root.addView(sensorCard)

        // ==========================================
        // 卡片 2：互動計數器
        // ==========================================
        val counterCard = createCard()
        counterCard.addView(createSectionLabel("🔢 互動計數器"))
        val countText = TextView(this).apply {
            text = "點擊次數：0"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            setPadding(0, 4.dp(), 0, 12.dp())
        }
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val addBtn = Button(this).apply {
            text = "＋ 增加計數"
            setOnClickListener {
                count++
                countText.text = "點擊次數：$count"
            }
        }
        val resetBtn = Button(this).apply {
            text = "歸零"
            setOnClickListener {
                count = 0
                shakeCount = 0
                countText.text = "點擊次數：0"
                shakeStatusText.text = "📳 搖一搖偵測：已歸零（大力搖晃手機試試！）"
                shakeStatusText.setTextColor(Color.parseColor("#059669"))
                (shakeStatusText.background as? GradientDrawable)?.setColor(Color.parseColor("#ECFDF5"))
                Toast.makeText(context, "計數與搖晃次數已歸零", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(addBtn)
        btnRow.addView(resetBtn)
        counterCard.addView(countText)
        counterCard.addView(btnRow)
        root.addView(counterCard)

        // ==========================================
        // 卡片 3：文字輸入與即時互動
        // ==========================================
        val inputCard = createCard()
        inputCard.addView(createSectionLabel("💬 系統通知測試"))
        val editText = EditText(this).apply {
            hint = "輸入任意文字測試..."
            isSingleLine = true
        }
        val toastBtn = Button(this).apply {
            text = "發送系統通知 (Toast)"
            setOnClickListener {
                val msg = editText.text.toString().ifBlank { "哈囉！這是雲端直編的原生 UI！" }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
        inputCard.addView(editText)
        inputCard.addView(toastBtn)
        root.addView(inputCard)

        // ==========================================
        // 卡片 4：主題切換與設備資訊
        // ==========================================
        val infoCard = createCard()
        val themeSwitch = Switch(this).apply {
            text = "🌙 深色背景模式"
            textSize = 16f
            setOnCheckedChangeListener { _, isChecked ->
                isDark = isChecked
                scrollView.setBackgroundColor(
                    if (isDark) Color.parseColor("#0F172A") else Color.parseColor("#F5F7FA")
                )
                title.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#1E293B"))
                subtitle.setTextColor(
                    if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
                )
            }
        }
        val sysInfo = TextView(this).apply {
            text = "\n📱 設備型號：${Build.MANUFACTURER} ${Build.MODEL}" +
                "\n🤖 Android 版本：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            textSize = 13f
            setTextColor(Color.parseColor("#475569"))
        }
        infoCard.addView(themeSwitch)
        infoCard.addView(sysInfo)
        root.addView(infoCard)

        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        if (!isSensorPaused) {
            registerSensors()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterSensors()
    }

    private fun registerSensors() {
        val rate = SensorManager.SENSOR_DELAY_UI
        accelSensor?.let { sensorManager.registerListener(this, it, rate) }
        gyroSensor?.let { sensorManager.registerListener(this, it, rate) }
        lightSensor?.let { sensorManager.registerListener(this, it, rate) }
        magnetSensor?.let { sensorManager.registerListener(this, it, rate) }
        proximitySensor?.let { sensorManager.registerListener(this, it, rate) }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true

                val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                accelText.text = String.format(
                    "📐 加速度 (m/s²)\n   X: %+6.2f | Y: %+6.2f | Z: %+6.2f (%.2f G)",
                    x, y, z, gForce
                )

                // 搖一搖偵測：總加速度超過 1.7G 且距離上次觸發超過 600ms
                if (gForce > 1.7f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 600L) {
                        lastShakeTime = now
                        shakeCount++
                        onPhoneShaken(gForce)
                    }
                }

                updateCompass()
            }

            Sensor.TYPE_GYROSCOPE -> {
                val rx = event.values[0]
                val ry = event.values[1]
                val rz = event.values[2]
                gyroText.text = String.format(
                    "🌀 陀螺儀 (rad/s)\n   X: %+6.2f | Y: %+6.2f | Z: %+6.2f",
                    rx, ry, rz
                )
            }

            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                val levelDesc = when {
                    lux < 10f -> "🌙 極暗環境"
                    lux < 100f -> "🏠 昏暗室內"
                    lux < 500f -> "🏢 一般室內照明"
                    lux < 2000f -> "🌤️ 明亮窗邊 / 陰天"
                    else -> "☀️ 戶外強光"
                }
                lightText.text = String.format("💡 環境光：%.0f lux (%s)", lux, levelDesc)
                lightBar.progress = lux.roundToInt().coerceIn(0, 1000)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagneticValues, 0, 3)
                hasGeomagnetic = true
                updateCompass()
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5f
                val isNear = distance < maxRange && distance < 3f
                proximityText.text = if (isNear) {
                    "🖐️ 距離感測：靠近遮擋 (${distance} cm)"
                } else {
                    "✨ 距離感測：遠離無遮擋 (${distance} cm)"
                }
            }
        }
    }

    private fun updateCompass() {
        if (!hasGravity || !hasGeomagnetic) return
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravityValues, geomagneticValues)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(r, orientation)
            val azimuthRad = orientation[0]
            val deg = ((Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0).roundToInt() % 360
            val dirName = when (deg) {
                in 0..22, in 338..360 -> "北 (N)"
                in 23..67 -> "東北 (NE)"
                in 68..112 -> "東 (E)"
                in 113..157 -> "東南 (SE)"
                in 158..202 -> "南 (S)"
                in 203..247 -> "西南 (SW)"
                in 248..292 -> "西 (W)"
                else -> "西北 (NW)"
            }
            compassText.text = "🧭 電子羅盤：$dirName ${deg}°"
        }
    }

    private fun onPhoneShaken(gForce: Float) {
        count++
        shakeStatusText.text = String.format(
            "🔥 偵測到搖晃！累計 %d 次 (強度 %.1f G)",
            shakeCount, gForce
        )
        shakeStatusText.setTextColor(Color.parseColor("#DC2626"))
        (shakeStatusText.background as? GradientDrawable)?.setColor(Color.parseColor("#FEF2F2"))

        // 觸覺震動回饋 (80ms)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) {
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需額外處理
    }
}