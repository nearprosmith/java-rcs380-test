package jp.shanimnni;


import com.igormaznitsa.jbbp.io.JBBPOut;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import javax.usb.*;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static org.usb4java.LibUsb.*;

public class RCS380 {
    static int VENDOR_ID = 0x054C;
    static int PRODUCT_ID = 0x06C3;
    private UsbDevice rcs380;
    private UsbPipe pipeIn;
    private UsbPipe pipeOut;
    private UsbInterface iface;
    private String manufacturer;
    private String productName;

    RCS380() {
        try {
            UsbServices services = UsbHostManager.getUsbServices();

            //javax.usb.*だけだとConfiguration is not Activeとなるので、LibUSBでConfigurationをActive化する・・・。
            DeviceHandle dh = LibUsb.openDeviceWithVidPid(null, (short) RCS380.VENDOR_ID, (short) RCS380.PRODUCT_ID);
            LibUsb.setAutoDetachKernelDriver(dh, true);
            LibUsb.setConfiguration(dh, 1);

            UsbHub rootHub = services.getRootUsbHub();
            rcs380 = this.findDevice(rootHub, RCS380.VENDOR_ID, RCS380.PRODUCT_ID);

            this.manufacturer = rcs380.getManufacturerString();
            this.productName = rcs380.getProductString();

            UsbConfiguration configuration = (UsbConfiguration) rcs380.getUsbConfigurations().get(0);
            this.iface = (UsbInterface) configuration.getUsbInterfaces().get(0);


            UsbEndpoint endpointOut = null, endpointIn = null;
            for (int i = 0; i < iface.getUsbEndpoints().size(); i++) {
                byte endpointAddr = (byte) ((UsbEndpoint) (iface.getUsbEndpoints().get(i))).getUsbEndpointDescriptor().bEndpointAddress();
                if (((endpointAddr & 0x80) == 0x80)) {
                    endpointIn = (UsbEndpoint) (iface.getUsbEndpoints().get(i));
                } else if ((endpointAddr & 0x80) == 0x00) {
                    endpointOut = (UsbEndpoint) (iface.getUsbEndpoints().get(i))
                    ;
                }
            }
            //0x02 : OUT, 0x081 IN
            endpointOut = (UsbEndpoint) iface.getUsbEndpoint((byte) 0x02);
            endpointIn = (UsbEndpoint) iface.getUsbEndpoint((byte) 0x81);

            this.pipeOut = endpointOut.getUsbPipe();
            this.pipeIn = endpointIn.getUsbPipe();


        } catch (
                UsbException e) {
            e.printStackTrace();
        } catch (
                UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (
                Exception e) {
            e.printStackTrace();
        }

    }

    public void open() throws UsbException{
        this.iface.claim();
        this.pipeIn.open();
        this.pipeOut.open();
        this.pipeOut.syncSubmit(Frame.ACK);
    }

    public void close() throws UsbException{
        this.pipeIn.close();
        this.pipeOut.close();
        this.iface.release();
    }



    public ByteBuffer sendCommand(byte commandCode) {
        return this.sendCommand(this.pipeOut, this.pipeIn, commandCode, new byte[]{});
    }

    public ByteBuffer sendCommand(byte commandCode, byte[] commandData) {
        return this.sendCommand(this.pipeOut, this.pipeIn, commandCode, commandData);
    }

    public ByteBuffer sendCommand(UsbPipe pipeOut, UsbPipe pipeIn, byte commandCode) {
        return this.sendCommand(pipeOut, pipeIn, commandCode, new byte[]{});
    }

    public ByteBuffer sendCommand(UsbPipe pipeOut, UsbPipe pipeIn, byte commandCode, byte[] commandData) {
        ByteBuffer ret;
        Frame frame;
        byte[] data = new byte[255];
        try {
            frame = new Frame(ArrayUtils.addAll(new byte[]{(byte) 0xD6, commandCode}, commandData));
            pipeOut.syncSubmit(frame.frame);
            pipeIn.syncSubmit(data);
            frame = new Frame(data);

            if (frame.type == Frame.TYPE_ACK) {
                data = new byte[255];

                int received = pipeIn.syncSubmit(data);
                frame = new Frame(data);
                if (frame.data[0] == (byte) 0xD7 && frame.data[1] == (byte) commandCode + 1) {
                    return ByteBuffer.wrap(Arrays.copyOfRange(frame.data, 2, frame.data.length));
                }
            } else {
                return null;
            }
        } catch (UsbException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    public UsbDevice findDevice(UsbHub hub, int vendorId, int productId) throws UsbException, UnsupportedEncodingException {
        for (UsbDevice device : (List<UsbDevice>) hub.getAttachedUsbDevices()) {
            UsbDeviceDescriptor desc = device.getUsbDeviceDescriptor();

            if (desc.idVendor() == vendorId && desc.idProduct() == productId) return device;
            if (device.isUsbHub()) {
                device = findDevice((UsbHub) device, vendorId, productId);
                if (device != null) return device;
            }
        }
        return null;
    }

    public void setPipeIn(UsbPipe pipeIn) {
        this.pipeIn = pipeIn;
    }

    public void setPipeOut(UsbPipe pipeOut) {
        this.pipeOut = pipeOut;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getProductName() {
        return productName;
    }
}

