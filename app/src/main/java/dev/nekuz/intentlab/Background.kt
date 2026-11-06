package dev.nekuz.intentlab

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

object Background {
    val query = Executors.newSingleThreadExecutor()
    val storage = Executors.newSingleThreadExecutor()
    val icons = Executors.newSingleThreadExecutor()

    private val main = Handler(Looper.getMainLooper())

    fun onMain(task: () -> Unit) {
        main.post(task)
    }
}
