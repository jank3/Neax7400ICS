package in.orangecounty.impl;

import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * SerialListener
 * Created by thomas on 19/12/13.
 */
public class SerialListener implements SerialPortEventListener {
    private Logger log = LoggerFactory.getLogger(SerialListener.class);

    /**
     * End of Transmission.
     */
    private static final int ETX = 3;
    /**
     * PositiveAcknowledge.
     */
    private static final int ACK = 6;
    /**
     * Negative Acknowlegement.
     */
    private static final int NAK = 21;
    /**
     * Enquiry.
     */
    private static final int ENQ = 5;
    /**
     * End of Transmission.
     */
    private static final int EOT = 4;
    /**
     * Permission to Send Char.
     */
    private static final int PSC = 60;



    private InputStream inputStream;
    private SenderImpl sender;
    private StringBuilder buffer = new StringBuilder();

    public SerialListener(InputStream inputStream, SenderImpl sender) {
        this.inputStream = inputStream;
        this.sender = sender;
    }

    @Override
    public final void serialEvent(SerialPortEvent ev) {
        log.debug("Serial Event Received :" + ev.getEventType());
        switch (ev.getEventType()){
            case(SerialPortEvent.DATA_AVAILABLE):
                log.debug("Data Available Event Received");
                int dataGot;
                byte[] readBuffer = new byte[1024];
                try {
                    dataGot = inputStream.read(readBuffer);
                    for(int x = 0; x<dataGot; x++){
                        process(readBuffer[x]& 0xff);
                    }
                } catch (IOException e) {
                    log.error("IOException : ", e);
                }
                break;
            default:
                log.debug("Unhandled Event Received");
        }
    }

    private void process(int b){
        buffer.append((char) b);
        String bufferString = buffer.toString();
        log.debug("Buffer : " + bufferString + " : " + Arrays.toString(bufferString.getBytes()));
        if (buffer.length() > 2 && buffer.charAt(buffer.length() - 2) == ETX) {
            boolean bccCheck = checkLrc(bufferString);
            boolean countCheck = checkCount(bufferString);
            log.warn("check bcc:" + bccCheck + " | check count :" + countCheck);
            if (bccCheck && countCheck) {
                sender.sendACK();
                processMessage(bufferString);
            } else {
                sender.sendNAK();
            }
            resetBuffer();
            return;
        }
        log.debug("Proceeding to switch ");
        switch (b) {
            case (ACK):
                log.debug("ACK Case");
                sender.ackReceived();
                resetBuffer();
                break;
            case (NAK):
                log.debug("NAK Case");
                sender.nakReceived();
                resetBuffer();
                break;
            case (ENQ):
                log.debug("ENQ Case");
                sender.sendACK();
                sender.setReceiving(true);
                resetBuffer();
                break;
            case (EOT):
                log.debug("EOT Case");
                sender.setReceiving(false);
                resetBuffer();
                break;
            case (PSC):
                log.debug("PSC Case");
                if (buffer.toString().equals("16")) {
                    sender.sendEOT();
                }
                break;
            default:
                log.debug("p");
        }

    }

    private void resetBuffer() {
        buffer = new StringBuilder();
    }

    private boolean checkCount(String msg) {
        return ((msg.length() - 6) == Integer.parseInt(msg.substring(6, 8)));
    }

    private boolean checkLrc(final String msg) {
        byte lrc = 0;
        byte[] byteArray = msg.getBytes();
        for (int x = 1; x < byteArray.length - 1; x++) {
            lrc = (byte) (lrc ^ byteArray[x]);
        }
        return lrc == byteArray[byteArray.length - 1];

    }

    private void processMessage(String s) {
        log.debug("Message Received : " + s);
    }
}
