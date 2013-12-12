package in.orangecounty.impl;

import in.orangecounty.ListenerSenderInterface;
import in.orangecounty.SenderInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.*;

import static in.orangecounty.impl.Constants.*;

/**
 * User: Nagendra
 * Date: 11/12/13
 * Time: 12:58 PM
 * <p/>
 * Modified by: thomas
 * Modified on: 25/11/13
 * Modified at: 7:17 PM
 */
public class SenderImpl implements ListenerSenderInterface, SenderInterface {
    final Logger log = LoggerFactory.getLogger(SenderImpl.class);
    private byte[] currentMessage = null;
    private final Object receivingLock = new Object();

    private OutputStream os;

    private static final long TIMER_1_TIME_INTERVAL = 1000l;
    private static final int MAX_INIT_ATTEMPTS = 16;
    private static final int MAX_MSG_ATTEMPTS = 32;

    private static int counter = 0;

    private boolean msgSent = false;
    private boolean selectSequenceSent = false;
    private boolean receiving = false;
    ScheduledFuture future;
    ScheduledExecutorService ex;


    public SenderImpl(OutputStream op) {
        this.os = op;
    }

    public void stop() {
        stopScheduler();
    }

    //Private Methods

    private void initCommunication() {
        log.debug("Writing Selecting Sequence");
        selectSequenceSent = true;
        msgSent = false;
        startScheduler(SELECTING_SEQUENCE, MAX_INIT_ATTEMPTS);
    }

    private void write(byte[] payload) {
        log.debug("Writing to Output : " + new String(payload));
        try {
            os.write(payload);
            os.flush();
        } catch (IOException e) {
            log.error("IOException :", e);
        }
    }

    private void write(byte payload) {
        byte[] a = new byte[1];
        a[0] = payload;
        write(a);
    }


    @Override
    public void ackReceived() {
        log.debug("Ack Received Called");
        if (selectSequenceSent) {
            stopScheduler();
            startScheduler(currentMessage, MAX_MSG_ATTEMPTS);
            selectSequenceSent = false;
            msgSent = true;
        } else if (msgSent) {
            stopScheduler();
            write(EOT);
            currentMessage = null;
            selectSequenceSent = false;
            msgSent = false;
        } else {
            log.error("Received Extra Ack We should not be here");
        }
        //To change body of implemented methods use File | Settings | File Templates.
    }

    private void stopScheduler() {
        if (future != null) {
            future.cancel(true);
        }
        if (ex != null) {
            ex.shutdown();
        }
        counter = 0;
    }

    @Override
    public void sendACK() {
        log.debug("Writing ACK");
        write(ACK);
    }

    @Override
    public void sendNAK() {
        log.debug("Writing NAK");
        write(NAK);
    }

    @Override
    public void sendEOT() {
        log.debug("Writing EOT");
        write(EOT);
    }

    @Override
    public void setReceiving(boolean receiving) {
        synchronized (receivingLock) {
            this.receiving = receiving;
        }
    }

    @Override
    public void nakReceived() {
        log.debug("NAK Received ......");
        //TODO implement what has to be done when receiving a NAK
    }

    @Override
    public boolean isSending() {
        boolean rv = msgSent || selectSequenceSent;
        log.debug("Is Sending:" + rv);
        return rv;
    }

    @Override
    public void interrupt() {
        log.debug("Interrupt Received");
        sendEOT();
        receiving = false;
        selectSequenceSent = false;
        msgSent = false;
    }

    @Override
    public void resendSelectSequence() {
        log.debug("Resending Select Sequence");
        write(SELECTING_SEQUENCE);
    }

    private void startScheduler(final byte[] payload, final int tries) {
        ex = Executors.newSingleThreadScheduledExecutor();
        future = ex.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (counter < tries) {
                    write(payload);
                    counter += 1;
                } else {
                    log.debug("Msg Write attempts reached maximum number;");
                    write(EOT);
                    currentMessage = null;
                    selectSequenceSent = false;
                    msgSent = false;
                    stopScheduler();
                }
            }
        }, 0l, TIMER_1_TIME_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public boolean sendMessage(byte[] payload) {
        synchronized (receivingLock) {
            if (!receiving && !isSending()) {
                currentMessage = payload;
                initCommunication();
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean canSend() {
        return !isSending() && !receiving;
    }

}

