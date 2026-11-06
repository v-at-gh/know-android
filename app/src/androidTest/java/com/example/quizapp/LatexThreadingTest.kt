package com.example.quizapp

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class LatexThreadingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun measure_onBackgroundThread() = runBlocking {
        var measurerRef: LatexMeasurerState? = null

        composeRule.setContent {
            measurerRef = rememberLatexMeasurer()
        }
        composeRule.waitForIdle()
        val measurer = checkNotNull(measurerRef) { "measurer not created" }

        // 1. Sanity: measure on main thread (baseline)
        val mainTimes = (1..5).map {
            measureTimeMillis {
                measurer.inlineContent("x^2 + y^2 + \\frac{a}{b}", LatexConfig(16.sp))
            }
        }
        println("MAIN timings: $mainTimes, avg=${mainTimes.average()}")

        // 2. The actual question: does it crash on Dispatchers.Default?
        val result = withContext(Dispatchers.Default) {
            try {
                val t = (1..5).map {
                    measureTimeMillis {
                        measurer.inlineContent("x^2 + y^2 + \\frac{a}{b}", LatexConfig(16.sp))
                    }
                }
                "OK: $t, avg=${t.average()}"
            } catch (t: Throwable) {
                "CRASH: ${t::class.simpleName}: ${t.message}"
            }
        }
        println("BACKGROUND result: $result")
    }
}