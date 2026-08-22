package online.vaigo.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager

class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.logo)
        logo.alpha = 0f
        logo.scaleX = 0.92f
        logo.scaleY = 0.92f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520).start()

        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java).apply {
                data = intent?.data
            })
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3000L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
