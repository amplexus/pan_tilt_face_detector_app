package org.amplexus.opencv.app;

import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

import org.apache.log4j.Logger;

import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeException;
import com.rapplogic.xbee.api.XBeeTimeoutException;
import com.rapplogic.xbee.api.wpan.TxRequest16;
import com.rapplogic.xbee.api.wpan.TxStatusResponse;

/**
*   ATSL to get the low bits.
 *   
 * PROTOCOL DATA PC TO XBEE: 2 x bytes where first byte is a command and the second byte is data
 * - motor stop:			byte 1 = 00, byte 2 = N/A
 * - pan-tilt left:			byte 1 = 05, byte 2 = N/A																					# NOT YET SUPPORTED 
 * - pan-tilt right:		byte 1 = 06, byte 2 = N/A																					# NOT YET SUPPORTED
 * - pan-tilt up:			byte 1 = 07, byte 2 = N/A																					# NOT YET SUPPORTED
 * - pan-tilt down:			byte 1 = 08, byte 2 = N/A																					# NOT YET SUPPORTED
 *																			# NOT YET SUPPORTED
 * PROTOCOL LOGIC PC TO XBEE
 *	- startup()
 *		- XBee.open(...)
 *		- flushPending()
 *	- motor<Dir>Command(speed) # where <Dir> is one of Left, Right, Forward, Backwards, Stop
 *		- flushPending()
 *		- ack = false 
 *  	- while(!ack)
 * 			- sendMotor<Dir>(speed)
 *  		- ack = getAck()
 *	- ping()
 *		- flushPending()
 *		- ack = gotPingResponse = false 
 *  	- while(!ack && !gotResponse)
 * 			- sendPing()
 *  		- ack = getAck()
 *  		- gotPingResponse = getResponse(50)
 *	- flushPending()
 * 		- while pending incoming requests
 *   		- read and ignore
 * 	- shutdown()
 * 		- XBee.close()
 * 
 * @author craig
 */
class XBeeCommunicatorTask extends SwingWorker<Integer, Integer> {
	/*
	 * Networking info
	 */
	public static final int PANID				= 0x4545;	// The network we communicate on
	public static final int XBEE_SHIELD_MY_MSB	= 0x80;		// The MY address MSB of the XBee we are talking to
	public static final int XBEE_SHIELD_MY_LSB	= 0x81;		// The MY address LSB of the XBee we are talking to
	/*
	 * Commands we send to the robot
	 */
	public static final int CMD_PAN_LEFT			= 0 ;
	public static final int CMD_PAN_RIGHT			= 1 ;
	public static final int CMD_TILT_DOWN			= 2 ;
	public static final int CMD_TILT_UP				= 3 ;
	public static final int CMD_PAN_TO				= 4 ;
	public static final int CMD_TILT_TO				= 5 ;
	public static final int CMD_PING				= 6 ;
	public static final int CMD_SELFTEST			= 7 ;
	public static final int CMD_RESET				= 8 ;

	/*
	 * Stringified command names
	 */
	public static final String[] commandName = {
		"PAN LEFT",
		"PAN RIGHT",
		"TILT DOWN",
		"TILT UP",
		"PAN TO",
		"TILT TO",
		"PING",
		"SELF TEST",
		"RESET"
	} ;
    
	private static XBee xbee = new XBee(); // We communicate with the robot via the XBee api
    
    /*
     * All the information pertaining to the command we are executing in this task
     */
    private int command ;							// The command (CMD_*) we are executing in this task 
    private int baudRate ;							// The BAUD rate we communicate at over the USB port (commPort)
	private int data ; 								// For the movement commands, data is speed (0-255). Otherwise not used
	private String commPort ;						// The USB port we communicate over
	private JLabel messageLabel;
	private JButton cancelButton;
	private String lastError = null ;				// If there was an error, the message goes here
    
	private final static Logger log = Logger.getLogger(XBeeCommunicatorTask.class);
	
    /**
	 * Constructor.
	 */
	public XBeeCommunicatorTask(int command, int data, String commPort, int baudRate, JLabel messageLabel, JButton cancelButton) {
		this.command = command ;
		this.commPort = commPort ;
		this.data = data ;
		this.baudRate = baudRate ;
		this.messageLabel = messageLabel ;
		this.cancelButton = cancelButton ;
	}
	
	/**
	 * Make this private to force use of the parameterised constructor above.
	 */
	private XBeeCommunicatorTask() {
		
	}
	
	/**
	 * Execute the command specified in the constructor.
	 * 
	 * Opens a communications channel to the XBee explorer, issues the command and closes the channel.
	 * 
	 * Performs the work in a separate task.
	 */
	@Override
	protected Integer doInBackground() throws Exception {
		log.info("Executing command: " + stringifiedCommandName(command)) ;
		synchronized(xbee) {
			
			try {
				if(! xbee.isConnected())
					xbee.open(commPort, baudRate);
				switch(command) {
				case CMD_PAN_LEFT:
					panLeft() ;
					break ;
				case CMD_PAN_RIGHT:
					panRight() ;
					break ;
				case CMD_TILT_UP:
					tiltUp() ;
					break ;
				case CMD_TILT_DOWN: 
					tiltDown() ;
					break ;
				case CMD_PAN_TO:
					panTo() ;
					break ;
				case CMD_TILT_TO:
					tiltTo() ;
					break ;
				case CMD_PING:
					ping() ;
					break ;
				case CMD_SELFTEST:
					selfTest() ;
					break ;
				case CMD_RESET:
					reset() ;
					break ;
				default:
					lastError = "Invalid command ignored: " + command ;
					log.error(lastError) ;
				}
			}
			catch(XBeeException e) {
				lastError = "Error executing: " + stringifiedCommandName(command) + ": " + e.getMessage() ;
				log.error(lastError, e) ;
				throw e ;
			} finally {
				if(xbee.isConnected())
					xbee.close() ;
			}
		}
		return 0 ;
	}
		
	/**
	 * Receives data chunks from the publish method asynchronously on the EventDispatch thread.
	 * 
	 * Not used.
	 */
	@Override
	protected void process(List<Integer> chunks) {
//		System.out.println(chunks);
	}
	
	/**
	 * Executed on the EventDispatch thread once the doInBackground method is finished.
	 * 
	 * Updates the GUI's message bar based on the completion state of the operation, and 
	 * shuts down the XBee communication channel.
	 * 
	 * Also disables the cancel button in the GUI, as there is now nothing to cancel.
	 */
	@Override
	protected void done() {
		if (isCancelled()) {
			log.warn("Cancelled command: " + stringifiedCommandName(command)) ;
			
			if(lastError != null)
				messageLabel.setText("Operation cancelled: " + lastError) ;
			else
				messageLabel.setText("Operation cancelled (no errors)") ;
		} else if(lastError != null) {
			messageLabel.setText(lastError) ;
		} else {
			messageLabel.setText("Operation completed successfully") ;
			log.info("Completed command: " + stringifiedCommandName(command)) ;
		}
		
		/*
		 * Disable the cancel button because there is now nothing to cancel.
		 */
		if(cancelButton != null)
			cancelButton.setEnabled(false) ;
	}
		
	private void panLeft() throws XBeeTimeoutException, XBeeException {
		int[] payload = new int[] { this.CMD_PAN_LEFT, 10 };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	private void panRight() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_PAN_RIGHT, 10 };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	private void tiltUp() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_TILT_UP, data };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	private void tiltDown() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_TILT_DOWN, data };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	private void tiltTo() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_TILT_TO, data };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	private void panTo() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_PAN_TO, data };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	private void ping() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_PING, 111 };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	private void selfTest() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_SELFTEST, 0 };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	private void reset() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_RESET, 0 };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}

	/**
	 * sends a command to the remote XBee.
	 * 
	 * @param destination the remote XBee's MY address
	 * @param payload an array of two 8 bit numbers - the first is the command, the second is the accompanying data.
	 * @throws XBeeTimeoutException if we timed out trying to communicate with the remote XBee
	 * @throws XBeeException if there was some other exception communicating with the remote XBee
	 */
	private void sendCommand(XBeeAddress16 destination, int[] payload) throws XBeeTimeoutException, XBeeException {
		TxRequest16 tx = new TxRequest16(destination, payload);
        log.info("Sending request to " + destination);
        TxStatusResponse status = (TxStatusResponse) xbee.sendSynchronous(tx);
        if (status.isSuccess()) {
                log.info("Sent payload to" + destination);
        } else {
                log.info("Error sending payload to" + destination);
        }
	}
	
	private String stringifiedCommandName(int commandId) {
		if(commandId >= 0 && commandId < commandName.length)
			return commandName[commandId] ;
		else
			return "invalid (" + commandId + ")" ;
	}
}	
	
