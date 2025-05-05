package com.pedrobressane.appcronometro

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pedrobressane.appcronometro.ui.theme.AppCronometroTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted - no action needed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppCronometroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CardioTimerScreen(
                        onRequestPermission = { permission ->
                            requestPermissionLauncher.launch(permission)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CardioTimerScreen(
    onRequestPermission: (String) -> Unit = {}
) {
    var prepareTime by remember { mutableStateOf("10") }
    var exerciseTime by remember { mutableStateOf("30") }
    var breakTime by remember { mutableStateOf("15") }
    var seriesCount by remember { mutableStateOf("3") }
    var validatedSeriesCount by remember { mutableStateOf(3) }

    var currentState by remember { mutableStateOf("") }
    var remainingTime by remember { mutableStateOf(0) }
    var currentSeries by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf(0) } // 0=prepare1, 1=exercise, 2=prepare2, 3=break

    val context = LocalContext.current
    var timer by remember { mutableStateOf<CountDownTimer?>(null) }

    fun checkVibrationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.VIBRATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun vibrate(times: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkVibrationPermission()) {
                onRequestPermission(android.Manifest.permission.VIBRATE)
                return
            }
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            val pattern = longArrayOf(0, 200, 100, 200, 100, 200, 100, 200).take(times * 2).toLongArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    fun stopTimer() {
        timer?.cancel()
        isRunning = false
        currentState = context.getString(R.string.state_finished)
        remainingTime = 0
    }

    fun startPhase() {
        timer?.cancel()

        when (currentPhase) {
            0 -> { // First Prepare phase
                currentState = context.getString(R.string.state_prepare)
                timer = object : CountDownTimer(prepareTime.toLong() * 1000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        remainingTime = (millisUntilFinished / 1000).toInt()
                    }

                    override fun onFinish() {
                        remainingTime = 0
                        vibrate(4)
                        currentPhase = 1 // Move to exercise
                        startPhase()
                    }
                }.start()
            }
            1 -> { // Exercise phase
                currentState = context.getString(R.string.state_exercise)
                timer = object : CountDownTimer(exerciseTime.toLong() * 1000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        remainingTime = (millisUntilFinished / 1000).toInt()
                    }

                    override fun onFinish() {
                        remainingTime = 0
                        vibrate(2)
                        currentPhase = 2 // Move to second prepare
                        startPhase()
                    }
                }.start()
            }
            2 -> { // Second Prepare phase
                currentState = context.getString(R.string.state_prepare)
                timer = object : CountDownTimer(prepareTime.toLong() * 1000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        remainingTime = (millisUntilFinished / 1000).toInt()
                    }

                    override fun onFinish() {
                        remainingTime = 0
                        vibrate(4)
                        currentPhase = 3 // Move to break
                        startPhase()
                    }
                }.start()
            }
            3 -> { // Break phase
                currentState = context.getString(R.string.state_break)
                timer = object : CountDownTimer(breakTime.toLong() * 1000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        remainingTime = (millisUntilFinished / 1000).toInt()
                    }

                    override fun onFinish() {
                        remainingTime = 0
                        currentSeries++
                        if (currentSeries > validatedSeriesCount) {
                            stopTimer()
                        } else {
                            currentPhase = 0 // Start new series with prepare
                            startPhase()
                        }
                    }
                }.start()
            }
        }
    }

    fun startTimer() {
        validatedSeriesCount = seriesCount.toIntOrNull() ?: 3
        isRunning = true
        currentSeries = 1
        currentPhase = 0 // Start with first prepare phase
        startPhase()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.current_state, currentState),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.remaining_time, remainingTime),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.current_series, currentSeries, validatedSeriesCount),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (!isRunning) {
            TimeInputField(
                label = R.string.prepare_time,
                value = prepareTime,
                onValueChange = { prepareTime = it }
            )
            TimeInputField(
                label = R.string.exercise_time,
                value = exerciseTime,
                onValueChange = { exerciseTime = it }
            )
            TimeInputField(
                label = R.string.break_time,
                value = breakTime,
                onValueChange = { breakTime = it }
            )
            TimeInputField(
                label = R.string.series_count,
                value = seriesCount,
                onValueChange = {
                    if (it.isEmpty() || it.toIntOrNull()?.let { num -> num > 0 } == true) {
                        seriesCount = it
                        if (!isRunning) {
                            validatedSeriesCount = it.toIntOrNull() ?: 3
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { if (isRunning) stopTimer() else startTimer() },
                enabled = !isRunning || true
            ) {
                Text(if (isRunning) stringResource(R.string.stop) else stringResource(R.string.start))
            }
        }
    }
}

@Composable
fun TimeInputField(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (it.isEmpty() || it.toIntOrNull() != null) {
                onValueChange(it)
            }
        },
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun CardioTimerPreview() {
    AppCronometroTheme {
        CardioTimerScreen()
    }
}