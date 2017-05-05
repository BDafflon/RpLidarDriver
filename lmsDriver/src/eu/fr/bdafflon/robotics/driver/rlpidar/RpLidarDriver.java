package eu.fr.bdafflon.robotics.driver.rlpidar;


import eu.fr.bdafflon.robotics.driver.rlpidar.example.RpLidarInfo;
import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author BDAFFLON
 */

public class RpLidarDriver {

	// out going packet types
	public static final byte SYNC_BYTE0 = (byte) 0xA5;
	public static final byte SYNC_BYTE1 = (byte) 0x5A;
	public static final byte STOP = (byte) 0x25;
	public static final byte RESET = (byte) 0x40;
	public static final byte SCAN = (byte) 0x20;
	public static final byte FORCE_SCAN = (byte) 0x21;
	public static final byte GET_INFO = (byte) 0x50;
	public static final byte GET_HEALTH = (byte) 0x52;

	// in coming packet types
	public static final byte RCV_INFO = (byte) 0x04;
	public static final byte RCV_HEALTH = (byte) 0x06;
	public static final byte RCV_SCAN = (byte) 0x81;

	public static final byte START_MOTOR  = (byte)   0xF0 ;
	public static final byte RPLIDAR_CMD_GET_ACC_BOARD_FLAG = (byte) 0xFF ;

	SerialPort serialPort;
	InputStream in;
	OutputStream out;
	boolean initialized = false;

	// buffer for out going data
	byte[] dataOut = new byte[1024];

	// flag to turn on and off verbose debugging output
	boolean verbose = false;

	// thread for reading serial data
	private ReadSerialThread readThread;

	// Storage for incoming packets
	RpLidarHeath health = new RpLidarHeath();
	RpLidarInfo deviceInfo = new RpLidarInfo();
	RpLidarData measurement = new RpLidarData();


	// if it is in scanning mode.  When in scanning mode it just parses measurement packets
	boolean scanning = false;

	// Type of packet last recieved
	int lastReceived = 0;

	public RpLidarDriver(String portName ) throws Exception {
		System.out.println("Opening port " + portName);


		CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
		CommPort commPort = portIdentifier.open("FOO", 2000);
		serialPort = (SerialPort) commPort;
		serialPort.setSerialPortParams(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
		serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
		serialPort.setDTR(false); // lovely undocumented feature where if true the motor stops spinning

		in = serialPort.getInputStream();
		out = serialPort.getOutputStream();

		readThread = new ReadSerialThread();
		new Thread(readThread).start();
	}


	public void pause(long milli) {
		synchronized (this) {
			try {
				wait(milli);
			} catch (InterruptedException e) {
			}
		}
	}

	public void shutdown() {

		serialPort.close();

		if (readThread != null) {
			readThread.requestStop();
			readThread = null;
		}
	}


	public boolean sendScan(long timeout) {
		System.out.println("send Scan");
		return sendBlocking(SCAN, RCV_SCAN, timeout);
	}

	public boolean sendForceScan(long timeout) {
		System.out.println("send Force Scan");
		return sendBlocking(FORCE_SCAN, RCV_SCAN, timeout);
	}


	public void sendStop() {
		System.out.println("send Stop");
		scanning = false;
		sendNoPayLoad(STOP);
	}

	public void sendStartMotor(int speed) {
		System.out.println("send Start Motor");
	sendPayLoad(START_MOTOR, speed);
	}


	public void sendStopMotor() {
		// TODO Auto-generated method stub
		System.out.println("send Stop Motor");
		sendPayLoad(START_MOTOR, 0);
	}

	public void sendReset() {
		System.out.println("send Reset");
		scanning = false;
		sendNoPayLoad(RESET);
	}

	public boolean sendGetInfo(long timeout) {
		System.out.println("send GetInfo");
		return sendBlocking(GET_INFO, RCV_INFO, timeout);
	}


	public boolean sendGetHealth(long timeout) {
		System.out.println("send GetHealth");
		return sendBlocking(GET_HEALTH, RCV_HEALTH, timeout);
	}

	protected boolean sendBlocking(byte command, byte expected, long timeout) {
		if (timeout <= 0) {
			sendNoPayLoad(command);
			return true;
		} else {
			lastReceived = 0;
			long endTime = System.currentTimeMillis() + timeout;
			do {
				sendNoPayLoad(command);
				pause(20);
			} while (endTime >= System.currentTimeMillis() && lastReceived != expected);
			return lastReceived == expected;
		}
	}


	protected void sendPayLoad(byte command, int payLoadInt) {
		byte[] payLoad = new byte[2];

		//load payload little Endian
		payLoad[0] = (byte) payLoadInt;
		payLoad[1] = (byte) (payLoadInt >> 8);

		sendPayLoad(command, payLoad);
	}

	protected void sendPayLoad(byte command, byte[] payLoad) {
		if (verbose) {
			System.out.printf("Sending command 0x%02x\n", command & 0xFF);
		}

		dataOut[0] = SYNC_BYTE0;
		dataOut[1] = command;

		//add payLoad and calculate checksum
		dataOut[2] = (byte) payLoad.length;
		int checksum = 0 ^ dataOut[0] ^ dataOut[1] ^ (dataOut[2] & 0xFF);

		for(int i=0; i<payLoad.length; i++){
			dataOut[3 + i] = payLoad[i];
			checksum ^= dataOut[3 + i];
		}

		//add checksum - now total length is 3 + payLoad.length + 1
		dataOut[3 + payLoad.length] = (byte) checksum;

		try {
			out.write(dataOut, 0, 3 + payLoad.length + 1);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	protected void sendNoPayLoad(byte command) {
		if (verbose) {
			System.out.printf("Sending command 0x%02x\n", command & 0xFF);
		}

		dataOut[0] = SYNC_BYTE0;
		dataOut[1] = command;

		try {
			out.write(dataOut, 0, 2);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	protected int parseData(byte[] data, int length) {

		int offset = 0;

		if (verbose) {
			System.out.println("parseData length = " + length);
			for (int i = 0; i < length; i++) {
				System.out.printf("%02x ", (data[i] & 0xFF));
			}
			System.out.println();
		}

		// search for the first good packet it can find
		while (true) {
			 if (scanning) {
				if (offset + 5 > length) {
					return length;
				}

				if (parseScan(data, offset, 5)) {
					offset += 5;
				} else {
					if( verbose )
						System.out.println("--- Bad Packet ---");
					offset += 1;
				}
			} else {
				// see if it has consumed all the data
				if (offset + 1 + 4 + 1 > length) {
					return length;
				}

				byte start0 = data[offset];
				byte start1 = data[offset + 1];

				if (start0 == SYNC_BYTE0 && start1 == SYNC_BYTE1) {
					int info = ((data[offset + 2] & 0xFF)) | ((data[offset + 3] & 0xFF) << 8) | ((data[offset + 4] & 0xFF) << 16) | ((data[offset + 5] & 0xFF) << 24);

					int packetLength = info & 0x3FFFFFFF;
					int sendMode = (info >> 30) & 0xFF;
					byte dataType = data[offset + 6];

					if (verbose) {
						System.out.printf("packet 0x%02x length = %d\n", dataType, packetLength);
					}
					// see if it has received the entire packet
					if (offset + 2 + 5 + packetLength > length) {

						if (verbose) {
							System.out.println("  waiting for rest of the packet");
							for (int i = 0; i < length; i++) {
								System.out.printf("%02x ", (data[i] & 0xFF));
							}
						}

						return offset;
					}

					if (parsePacket(data, offset + 2 + 4 + 1, packetLength, dataType)) {
						lastReceived = dataType & 0xFF;
						offset += 2 + 5 + packetLength;
					} else {
						offset += 2;
					}
				} else {
					offset++;
				}
			}
		}
	}

	protected boolean parsePacket(byte[] data, int offset, int length, byte type) {
		switch (type) {
		case (byte) RCV_INFO: // INFO
			return parseDeviceInfo(data, offset, length);

		case (byte) RCV_HEALTH: // health
			return parseHealth(data, offset, length);

		case (byte) RCV_SCAN: // scan and force-scan
			System.out.println("---------------SCANNING-----------------------");
			if (parseScan(data, offset, length)) {
				scanning = true;
				return true;
			}
		break;
		default:
			System.out.printf("Unknown packet type = 0x%02x\n", type);
		}
		return false;
	}

	protected boolean parseHealth(byte[] data, int offset, int length) {
		if (length != 3) {
			System.out.println("  bad health packet");
			return false;
		}

		health.status = data[offset] & 0xFF;
		health.error_code = (data[offset + 1] & 0xFF) | ((data[offset + 2] & 0xFF) << 8);
		System.out.println("---------------HEALTH-----------------------");
		System.out.println("health.status :"+health.status);
		System.out.println("health.error_code :"+health.error_code);

		return true;
	}

	protected boolean parseDeviceInfo(byte[] data, int offset, int length) {
		if (length != 20) {
			System.out.println("  bad device info");
			return false;
		}

		deviceInfo.model = data[offset] & 0xFF;
		deviceInfo.firmware_minor = data[offset + 1] & 0xFF;
		deviceInfo.firmware_major = data[offset + 2] & 0xFF;
		deviceInfo.hardware = data[offset + 3] & 0xFF;

		for (int i = 0; i < 16; i++) {
			deviceInfo.serialNumber[i] = data[offset + 4 + i];
		}

		System.out.println("---------------INFO-----------------------");
		System.out.println("Model :"+deviceInfo.model);
		System.out.println("firmware_minor :"+deviceInfo.firmware_minor);
		System.out.println("firmware_major :"+deviceInfo.firmware_major);
		System.out.println("hardware :"+deviceInfo.hardware);

		return true;
	}

	protected boolean parseScan(byte[] data, int offset, int length) {

		if (length != 5)
			return false;

		byte b0 = data[offset];
		byte b1 = data[offset + 1];

		boolean start0 = (b0 & 0x01) != 0;
		boolean start1 = (b0 & 0x02) != 0;

		if (start0 == start1)
			return false;

		if ((b1 & 0x01) != 1)
			return false;

		measurement.timeMilli = System.currentTimeMillis();
		measurement.start = start0;
		measurement.quality = (b0 & 0xFF) >> 2;
		measurement.angle = ((b1 & 0xFF)  | ((data[offset + 2] & 0xFF) << 8)) >> 1;
		measurement.distance = ((data[offset + 3] & 0xFF) | ((data[offset + 4] & 0xFF) << 8))/4;



		System.out.println("["+String.format("%.2f", measurement.angle/64.0) +"] :"+measurement.distance+"mm");

		return true;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	/**
	 * Thread which reads in coming serial data from the LIDAR
	 */
	public class ReadSerialThread implements Runnable {

		byte data[] = new byte[1024 * 2];
		int size = 0;
		volatile boolean run = true;

		public void requestStop() {
			run = false;
		}

		@Override
		public void run() {
			while (run) {
				try {
					if (in.available() > 0) {
						int totalRead = in.read(data, size, data.length - size);

						size += totalRead;

						int used = parseData(data, size);

						// shift the buffer over by the amount read
						for (int i = 0; i < size - used; i++) {
							data[i] = data[i + used];
						}
						size -= used;

					}

					Thread.sleep(5);
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

}