package org.thatdev.arduinobuddy

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import org.thatdev.arduinobuddy.ui.theme.ArduinoBuddyTheme
import java.io.IOException
import kotlin.concurrent.thread

class SerialTestActivity : ComponentActivity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "org.thatdev.arduinobuddy.USB_PERMISSION"
        private const val TAG = "SerialTestActivity"
    }

    private lateinit var usbManager: UsbManager
    private lateinit var permissionIntent: PendingIntent
    private var serialPort: UsbSerialPort? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? =
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { openDevice(it) }
                    } else {
                        Log.e(TAG, "Permission denied for $device")
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? =
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let { closeDevice() }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        usbManager = getSystemService(USB_SERVICE) as UsbManager
        permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Register USB receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(
            usbReceiver,
            filter,
            RECEIVER_NOT_EXPORTED
        )

        setContent {
            ArduinoBuddyTheme {
                SerialTestScreen(usbManager) { driver, logUpdate ->
                    handleConnect(driver, logUpdate)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        closeDevice()
    }

    private fun handleConnect(driver: UsbSerialDriver, logUpdate: (String) -> Unit) {
        val device = driver.device
        if (!usbManager.hasPermission(device)) {
            usbManager.requestPermission(device, permissionIntent)
            logUpdate("Requesting permission for device ${device.deviceName}\n")
        } else {
            openDevice(device)
            logUpdate("Permission already granted for device ${device.deviceName}\n")
        }
    }

    private fun openDevice(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return
        val connection: UsbDeviceConnection? = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Cannot open connection to $device")
            return
        }

        serialPort = driver.ports.firstOrNull()
        serialPort?.apply {
            try {
                open(connection)
                setParameters(
                    115200,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                startReadingThread()
                Log.d(TAG, "Serial port opened: $device")
            } catch (e: IOException) {
                Log.e(TAG, "Error opening serial port: ${e.message}")
                closeDevice()
            }
        }
    }

    private fun closeDevice() {
        try {
            serialPort?.close()
        } catch (_: IOException) {
        }
        serialPort = null
    }

    private fun startReadingThread() {
        serialPort?.let { port ->
            thread(start = true) {
                val buffer = ByteArray(64)
                while (true) {
                    try {
                        val numBytes = port.read(buffer, 100)
                        if (numBytes > 0) {
                            val readData = buffer.copyOf(numBytes).decodeToString()
                            Log.d(TAG, "Received: $readData")
                            // TODO: update Compose state via e.g. mutableStateOf / callback
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Serial read error: ${e.message}")
                        break
                    }
                }
            }
        }
    }

    fun sendData(data: String) {
        serialPort?.let { port ->
            thread(start = true) {
                try {
                    port.write(data.toByteArray(), 1000)
                } catch (e: IOException) {
                    Log.e(TAG, "Serial write error: ${e.message}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialTestScreen(
    manager: UsbManager,
    onConnect: (UsbSerialDriver, (String) -> Unit) -> Unit
) {
    var log by remember { mutableStateOf("Logs:\n") }
    var baudRate by remember { mutableStateOf("115200") }
    var sendData by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = baudRate,
            onValueChange = { baudRate = it },
            label = { Text("Baud Rate") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            if (availableDrivers.isNotEmpty()) {
                onConnect(availableDrivers[0]) { message ->
                    log += message
                }
            } else {
                log += "No USB devices found\n"
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Connect")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = sendData,
            onValueChange = { sendData = it },
            label = { Text("Data to send") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            log += "Sending: $sendData\n"
            // Call SerialTestActivity.sendData via Context / rememberCoroutineScope if needed
            // For simplicity, you might expose sendData as a lambda or pass a reference
            sendData = ""
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Send")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = log,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}
