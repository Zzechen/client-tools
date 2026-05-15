package com.clienttools.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clienttools.demo.model.EmailLoginRequest
import com.clienttools.demo.model.LoginResponse
import com.clienttools.demo.model.PasswordLoginRequest
import com.clienttools.demo.model.SendSmsRequest
import com.clienttools.demo.model.SmsLoginRequest
import com.clienttools.demo.model.UserInfo
import com.clienttools.demo.network.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: UserInfo, val token: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel : ViewModel() {

    private val authService = RetrofitClient.authService

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _smsCountdown = MutableStateFlow(0)
    val smsCountdown: StateFlow<Int> = _smsCountdown.asStateFlow()

    private var countdownJob: Job? = null

    fun sendSmsCode(phone: String) {
        viewModelScope.launch {
            try {
                val resp = authService.sendSmsCode(SendSmsRequest(phone))
                if (resp.code == 0) startCountdown()
                else _uiState.value = LoginUiState.Error(resp.message)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "网络错误")
            }
        }
    }

    fun loginSms(phone: String, code: String) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                handleLoginResponse(authService.loginSms(SmsLoginRequest(phone, code)))
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "网络错误")
            }
        }
    }

    fun loginPassword(phone: String, password: String) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                handleLoginResponse(authService.loginPassword(PasswordLoginRequest(phone, password)))
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "网络错误")
            }
        }
    }

    fun loginEmail(email: String, password: String) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                handleLoginResponse(authService.loginEmail(EmailLoginRequest(email, password)))
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "网络错误")
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    private fun handleLoginResponse(resp: LoginResponse) {
        if (resp.code == 0 && resp.data != null) {
            _uiState.value = LoginUiState.Success(resp.data.user, resp.data.token)
        } else {
            _uiState.value = LoginUiState.Error(resp.message)
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 60 downTo 1) {
                _smsCountdown.value = i
                delay(1000L)
            }
            _smsCountdown.value = 0
        }
    }
}
