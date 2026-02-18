package org.thatdev.arduinobuddy;

import android.hardware.usb.*;
import android.content.Context;

import com.hoho.android.usbserial.driver.*;

import java.io.IOException;
import java.util.*;

public class SerialBridge {

    private final UsbManager usbManager;
    private UsbSerialPort port;
    private UsbDeviceConnection connection;

    public SerialBridge(Context context) {
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public void open(String deviceName, int baudRate) throws IOException {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();

        for (UsbDevice d : devices.values()) {
            if (d.getDeviceName().equals(deviceName)) {

                UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(d);
                if (driver == null) {
                    throw new IOException("No compatible USB serial driver");
                }

                connection = usbManager.openDevice(d);
                if (connection == null) {
                    throw new IOException("Permission denied or device unavailable");
                }

                port = driver.getPorts().get(0);
                port.open(connection);

                port.setParameters(
                        baudRate,
                        8,
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_NONE
                );

                return;
            }
        }

        throw new IOException("Device not found");
    }

    public void close() {
        try {
            if (port != null) port.close();
        } catch (IOException ignored) {}
        port = null;

        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    public byte[] read(int size) throws IOException {
        if (port == null) return new byte[0];

        byte[] buffer = new byte[size];
        int len = port.read(buffer, 1000);

        if (len <= 0) return new byte[0];
        return Arrays.copyOf(buffer, len);
    }

    public void write(byte[] data) throws IOException {
        if (port == null) return;
        port.write(data, 1000);
    }

    public int inWaiting() throws IOException {
        if (port == null) return 0;
        return port.getInputBufferSize();
    }

    public void clearInputBuffer() throws IOException {
        if (port != null) port.purgeHwBuffers(true, false);
    }

    public void clearOutputBuffer() throws IOException {
        if (port != null) port.purgeHwBuffers(false, true);
    }

    // ---- Port enumeration ----

    public static List<Map<String, Object>> getPorts(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        List<Map<String, Object>> result = new ArrayList<>();

        for (UsbSerialDriver driver : drivers) {
            UsbDevice device = driver.getDevice();

            Map<String, Object> info = new HashMap<>();
            info.put("deviceName", device.getDeviceName());
            info.put("vendorId", device.getVendorId());
            info.put("productId", device.getProductId());
            info.put("manufacturer", device.getManufacturerName());
            info.put("serialNumber", device.getSerialNumber());

            result.add(info);
        }

        return result;
    }
}
