package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Alarm
import com.example.data.AlarmDatabase
import com.example.scheduler.AlarmScheduler
import com.example.sensor.ShakeDetector
import com.example.sound.SoundSynthesizer
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AlarmDatabase.getDatabase(application)
    private val alarmDao = db.alarmDao
    private val scheduler = AlarmScheduler(application)

    // Alarm lists
    val allAlarms: StateFlow<List<Alarm>> = alarmDao.getAllAlarms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Firing alarm state
    private val _isFiring = MutableStateFlow(false)
    val isFiring: StateFlow<Boolean> = _isFiring.asStateFlow()

    private val _remainingShakes = MutableStateFlow(20)
    val remainingShakes: StateFlow<Int> = _remainingShakes.asStateFlow()

    private val _originalShakesTarget = MutableStateFlow(20)
    val originalShakesTarget: StateFlow<Int> = _originalShakesTarget.asStateFlow()

    private val _isOverkillMode = MutableStateFlow(false)
    val isOverkillMode: StateFlow<Boolean> = _isOverkillMode.asStateFlow()

    private val _firingLabel = MutableStateFlow("Alarm")
    val firingLabel: StateFlow<String> = _firingLabel.asStateFlow()

    private var activeAlarmId: Int? = null

    // Helper controllers
    private var soundSynthesizer: SoundSynthesizer? = null
    private var shakeDetector: ShakeDetector? = null

    init {
        soundSynthesizer = SoundSynthesizer(application)
    }

    /**
     * Start shaking/noise sequence - triggered manually via Test button or by AlarmReceiver launch
     */
    fun startFiringSequence(label: String, shakesRequired: Int, alarmId: Int? = null) {
        if (_isFiring.value) return // already firing

        _isFiring.value = true
        _firingLabel.value = label
        _remainingShakes.value = shakesRequired
        _originalShakesTarget.value = shakesRequired
        _isOverkillMode.value = false
        activeAlarmId = alarmId

        Log.d("AlarmViewModel", "Firing sequence initialized. Shakes target: $shakesRequired")

        // 1. Kickstart sound and speech loops
        soundSynthesizer?.startAlarm(overkill = false)

        // 2. Start hardware shake detection
        shakeDetector = ShakeDetector(getApplication()) {
            handleShakeRegistered()
        }
        shakeDetector?.start()
    }

    /**
     * Decelerate shake counter on each physical shake
     */
    private fun handleShakeRegistered() {
        if (!_isFiring.value) return
        
        val newShakes = _remainingShakes.value - 1
        if (newShakes <= 0) {
            _remainingShakes.value = 0
            stopFiringSequence()
        } else {
            _remainingShakes.value = newShakes
        }
    }

    /**
     * Snooze triggered - SNOOZE IS EXTREME OVERKILL PENALTY MINIMIZER!
     * Doubles alarm frequencies, flashes extreme red filters, increases target by 10/15 shakes,
     * yelling insults through Text-To-Speech.
     */
    fun triggerSnoozeOverkillPenalty() {
        if (!_isFiring.value) return

        _isOverkillMode.value = true
        // Penalty: Add 10 vigorous shakes to the counter and bump the target
        _remainingShakes.value = _remainingShakes.value + 10
        _originalShakesTarget.value = _originalShakesTarget.value + 10

        Log.w("AlarmViewModel", "SNOOZE TRIGGERED. PENALTY DEPLOYED. Shakes remaining: ${_remainingShakes.value}")

        // Restart sound loop as Overkill mode (higher siren pitched, fast cadence, insults)
        soundSynthesizer?.stopAlarm()
        soundSynthesizer?.startAlarm(overkill = true)
    }

    /**
     * Terminate alert sequence successfully on shake target complete
     */
    fun stopFiringSequence() {
        _isFiring.value = false
        _isOverkillMode.value = false
        _remainingShakes.value = 0

        // Stop sirens and speak calming victory greeting
        soundSynthesizer?.stopAlarm()
        shakeDetector?.stop()
        shakeDetector = null

        // Fire triumphant speech
        soundSynthesizer?.forceMaximumVolume()
        // Play peaceful TTS
        val responses = listOf(
            "Target met. System neutralized. You are technically awake now.",
            "Protocol accomplished. Go conquer the day. Do not go back to sleep.",
            "Mission successful. Alert cleared. Stand up immediately."
        )
        // Speak completion text
        soundSynthesizer?.startAlarm(overkill = false) // Briefly trigger normal to read victory
        viewModelScope.launch {
            // Dismiss notification if possible
            val nm = getApplication<Application>().getSystemService(Application.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancelAll()
        }
        soundSynthesizer?.stopAlarm()
    }

    // Database Actions
    fun addNewAlarm(hour: Int, minute: Int, label: String, shakes: Int, repeatDays: Set<Int>) {
        viewModelScope.launch {
            val alarm = Alarm(
                hour = hour,
                minute = minute,
                label = label.ifBlank { "Heavy Sleeper Alarm" },
                shakesRequired = shakes,
                monday = repeatDays.contains(Calendar.MONDAY),
                tuesday = repeatDays.contains(Calendar.TUESDAY),
                wednesday = repeatDays.contains(Calendar.WEDNESDAY),
                thursday = repeatDays.contains(Calendar.THURSDAY),
                friday = repeatDays.contains(Calendar.FRIDAY),
                saturday = repeatDays.contains(Calendar.SATURDAY),
                sunday = repeatDays.contains(Calendar.SUNDAY)
            )
            val id = alarmDao.insertAlarm(alarm).toInt()
            scheduler.schedule(alarm.copy(id = id))
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = !alarm.isEnabled)
            alarmDao.updateAlarm(updated)
            if (updated.isEnabled) {
                scheduler.schedule(updated)
            } else {
                scheduler.cancel(updated)
            }
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmDao.updateAlarm(alarm)
            scheduler.cancel(alarm)
            if (alarm.isEnabled) {
                scheduler.schedule(alarm)
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancel(alarm)
            alarmDao.deleteAlarm(alarm)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundSynthesizer?.release()
        shakeDetector?.stop()
    }
}
