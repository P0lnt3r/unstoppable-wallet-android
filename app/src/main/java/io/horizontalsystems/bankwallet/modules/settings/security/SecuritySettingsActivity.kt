package io.horizontalsystems.bankwallet.modules.settings.security

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.bankwallet.core.App
import io.horizontalsystems.bankwallet.core.BaseActivity
import io.horizontalsystems.bankwallet.modules.main.MainModule
import io.horizontalsystems.bankwallet.modules.tor.TorConnectionActivity
import io.horizontalsystems.pin.PinModule
import io.horizontalsystems.views.AlertDialogFragment
import io.horizontalsystems.views.TopMenuItem
import kotlinx.android.synthetic.main.activity_settings_security.*
import kotlin.system.exitProcess

class SecuritySettingsActivity : BaseActivity() {

    private lateinit var viewModel: SecuritySettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_security)

        viewModel = ViewModelProvider(this).get(SecuritySettingsViewModel::class.java)
        viewModel.init()

        shadowlessToolbar.bind(getString(R.string.Settings_SecurityCenter), TopMenuItem(R.drawable.ic_back, onClick = { onBackPressed() }))

        changePin.setOnClickListener { viewModel.delegate.didTapEditPin() }

        fingerprint.switchOnCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.delegate.didSwitchBiometricEnabled(isChecked)
        }

        torConnectionSwitch.switchOnCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.delegate.didSwitchTorEnabled(isChecked)
        }

        fingerprint.setOnClickListener {
            fingerprint.switchToggle()
        }

        enablePin.switchOnCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.delegate.didSwitchPinSet(isChecked)
        }

        enablePin.setOnClickListener {
            enablePin.switchToggle()
        }

        //  Handling view model live events

        viewModel.pinSetLiveData.observe(this, Observer { pinEnabled ->
            enablePin.switchIsChecked = pinEnabled
        })

        viewModel.editPinVisibleLiveData.observe(this, Observer { pinEnabled ->
            changePin.visibility = if (pinEnabled) View.VISIBLE else View.GONE
            enablePin.bottomBorder = !pinEnabled
        })

        viewModel.biometricSettingsVisibleLiveData.observe(this, Observer { enabled ->
            fingerprint.visibility = if (enabled) View.VISIBLE else View.GONE
        })

        viewModel.biometricEnabledLiveData.observe(this, Observer {
            fingerprint.switchIsChecked = it
        })

        viewModel.torEnabledLiveData.observe(this, Observer {
            torConnectionSwitch.switchIsChecked = it
        })

        viewModel.showAppRestartAlertForTor.observe(this, Observer { checked->
            showAppRestartAlert(checked)
        })

        viewModel.showNotificationsNotEnabledAlert.observe(this, Observer {
            showNotificationsNotEnabledAlert()
        })

        //router

        viewModel.openEditPinLiveEvent.observe(this, Observer {
            PinModule.startForEditPin(this)
        })

        viewModel.openSetPinLiveEvent.observe(this, Observer {
            PinModule.startForSetPin(this, REQUEST_CODE_SET_PIN)
        })

        viewModel.openUnlockPinLiveEvent.observe(this, Observer {
            PinModule.startForUnlock(this, REQUEST_CODE_UNLOCK_PIN_TO_DISABLE_PIN)
        })

        viewModel.restartApp.observe(this, Observer {
            restartApp()
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SET_PIN) {
            when (resultCode) {
                PinModule.RESULT_OK -> viewModel.delegate.didSetPin()
                PinModule.RESULT_CANCELLED -> viewModel.delegate.didCancelSetPin()
            }
        }

        if (requestCode == REQUEST_CODE_UNLOCK_PIN_TO_DISABLE_PIN) {
            when (resultCode) {
                PinModule.RESULT_OK -> viewModel.delegate.didUnlockPinToDisablePin()
                PinModule.RESULT_CANCELLED -> viewModel.delegate.didCancelUnlockPinToDisablePin()
            }
        }
    }

    private fun showNotificationsNotEnabledAlert() {
        AlertDialogFragment.newInstance(
                descriptionString = getString(R.string.SettingsSecurity_NotificationsDisabledWarning),
                buttonText = R.string.Button_Enable,
                cancelButtonText = R.string.Alert_Cancel,
                cancelable = true,
                listener = object : AlertDialogFragment.Listener {
                    override fun onButtonClick() {
                        openAppNotificationSettings()
                    }

                    override fun onCancel() {
                        torConnectionSwitch.switchIsChecked = false
                    }
                }).show(supportFragmentManager, "alert_dialog_notification")
    }

    private fun openAppNotificationSettings() {
        val intent = Intent()
        intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
        intent.putExtra("android.provider.extra.APP_PACKAGE", packageName)
        startActivity(intent)
    }

    private fun showAppRestartAlert(checked: Boolean) {
        AlertDialogFragment.newInstance(
                descriptionString = getString(R.string.SettingsSecurity_AppRestartWarning),
                buttonText = R.string.Alert_Restart,
                cancelButtonText = R.string.Alert_Cancel,
                cancelable = true,
                listener = object : AlertDialogFragment.Listener {
                    override fun onButtonClick() {
                        viewModel.delegate.setTorEnabled(checked)
                    }

                    override fun onCancel() {
                        torConnectionSwitch.switchIsChecked = !checked
                    }
                }).show(supportFragmentManager, "alert_dialog")
    }

    private fun restartApp() {
        MainModule.startAsNewTask(this)
        if (App.localStorage.torEnabled) {
            val intent = Intent(this, TorConnectionActivity::class.java)
            startActivity(intent)
        }
        exitProcess(0)
    }

    companion object {
        const val REQUEST_CODE_SET_PIN = 1
        const val REQUEST_CODE_UNLOCK_PIN_TO_DISABLE_PIN = 2
    }
}
