/*
 * CypressFX3Biasgen.java
 *
 * Created on 23 Jan 2008
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

import org.usb4java.Device;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Adds functionality of apsDVS sensors to based CypressFX3Biasgen class. The
 * key method is translateEvents that parses
 * the data from the sensor to construct jAER raw events.
 *
 * @author Christian/Tobi
 */
public class DAViSFX3HardwareInterface extends CypressFX3Biasgen {

	protected DAViSFX3HardwareInterface(final Device device) {
		super(device);
	}

	@Override
	synchronized public void sendConfiguration(final net.sf.jaer.biasgen.Biasgen biasgen) throws HardwareInterfaceException {
		if ((biasgen != null) && (biasgen instanceof DavisConfig)) {
			((DavisConfig) biasgen).sendConfiguration();
		}
	}

	/** The USB product ID of this device */
	static public final short PID_FX3 = (short) 0x841A;
	static public final short PID_FX2 = (short) 0x841B;
	static public final int REQUIRED_FIRMWARE_VERSION_FX3 = 4;
	static public final int REQUIRED_FIRMWARE_VERSION_FX2 = 4;
	static public final int REQUIRED_LOGIC_REVISION_FX3 = 9912;
	static public final int REQUIRED_LOGIC_REVISION_FX2 = 9912;

	static public final float FX2_USB_CLOCK_FREQ = 30.0f;
	static public final float FX3_CLOCK_CORRECTION = 1.008f;
	static public final float FX3_USB_CLOCK_FREQ = 80.0f * FX3_CLOCK_CORRECTION;

	public float adcClockFreq = 30.0f;

	/**
	 * Starts reader buffer pool thread and enables in endpoints for AEs. This
	 * method is overridden to construct
	 * our own reader with its translateEvents method
	 */
	@Override
	public void startAEReader() throws HardwareInterfaceException {
		setAeReader(new RetinaAEReader(this));
		allocateAEBuffers();

		// Update ADC clock frequency information.
		if (getPID() == PID_FX2) {
			adcClockFreq = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 4);
		}
		else {
			adcClockFreq = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 4) * FX3_CLOCK_CORRECTION;
		}

		getAeReader().startThread(); // arg is number of errors before giving up
		HardwareInterfaceException.clearException();
	}

	@Override
	protected int adjustHWParam(final short moduleAddr, final short paramAddr, int param) {
		if ((moduleAddr == FPGA_APS) && (paramAddr == 13)) {
			// Exposure multiplied by clock.
			return (int) (param * adcClockFreq);
		}

		if ((moduleAddr == FPGA_APS) && (paramAddr == 14)) {
			// FrameDelay multiplied by clock.
			return (int) (param * adcClockFreq);
		}

		if ((moduleAddr == FPGA_USB) && (paramAddr == 1)) {
			// Early packet delay is 125µs slices on host, but in cycles
			// @ USB_CLOCK_FREQ on FPGA, so we must multiply here.
			if (getPID() == PID_FX2) {
				return (int) (param * (125.0f * FX2_USB_CLOCK_FREQ));
			}

			return (int) (param * (125.0f * FX3_USB_CLOCK_FREQ));
		}

		// No change by default.
		return (param);
	}

	public static final int CHIP_DAVIS240A = 0;
	public static final int CHIP_DAVIS240B = 1;
	public static final int CHIP_DAVIS240C = 2;
	public static final int CHIP_DAVIS128 = 3;
	public static final int CHIP_DAVIS346A = 4;
	public static final int CHIP_DAVIS346B = 5;
	public static final int CHIP_DAVIS640 = 6;
	public static final int CHIP_DAVISRGB = 7;
	public static final int CHIP_DAVIS208 = 8;
	public static final int CHIP_DAVIS346C = 9;

	/**
	 * This reader understands the format of raw USB data and translates to the
	 * AEPacketRaw
	 */
	public class RetinaAEReader extends CypressFX3.AEReader implements PropertyChangeListener {
		private final int chipID;

		private int wrapAdd;
		private int lastTimestamp;
		private int currentTimestamp;

		private int dvsLastY;
		private boolean dvsGotY;
		private final boolean dvsInvertXY;
		private final int dvsSizeX;
		private final int dvsSizeY;

		private static final int APS_READOUT_TYPES_NUM = 2;
		private static final int APS_READOUT_RESET = 0;
		private static final int APS_READOUT_SIGNAL = 1;
		private boolean apsResetRead;
		private int apsCurrentReadoutType;
		private int apsRGBPixelOffset;
		private boolean apsRGBPixelOffsetDirection;
		private final short[] apsCountX;
		private final short[] apsCountY;
		private final boolean apsInvertXY;
		private final boolean apsFlipX;
		private final boolean apsFlipY;
		private final int apsSizeX;
		private final int apsSizeY;

		private static final int IMU_DATA_LENGTH = 7;
		private final short[] imuEvents;
		private final boolean imuFlipX;
		private final boolean imuFlipY;
		private final boolean imuFlipZ;
		private int imuCount;
		private byte imuTmpData;

		public RetinaAEReader(final CypressFX3 cypress) throws HardwareInterfaceException {
			super(cypress);

			if (getPID() == PID_FX2) {
				// FX2 firmware now emulates the same interface as FX3 firmware, so we support it here too.
				checkFirmwareLogic(DAViSFX3HardwareInterface.REQUIRED_FIRMWARE_VERSION_FX2,
					DAViSFX3HardwareInterface.REQUIRED_LOGIC_REVISION_FX2);
			}
			else {
				checkFirmwareLogic(DAViSFX3HardwareInterface.REQUIRED_FIRMWARE_VERSION_FX3,
					DAViSFX3HardwareInterface.REQUIRED_LOGIC_REVISION_FX3);
			}

			apsCountX = new short[RetinaAEReader.APS_READOUT_TYPES_NUM];
			apsCountY = new short[RetinaAEReader.APS_READOUT_TYPES_NUM];

			initFrame();

			imuEvents = new short[RetinaAEReader.IMU_DATA_LENGTH];

			chipID = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 1);

			apsSizeX = spiConfigReceive(CypressFX3.FPGA_APS, (short) 0);
			apsSizeY = spiConfigReceive(CypressFX3.FPGA_APS, (short) 1);

			final int chipAPSStreamStart = spiConfigReceive(CypressFX3.FPGA_APS, (short) 2);
			apsInvertXY = (chipAPSStreamStart & 0x04) != 0;
			apsFlipX = (chipAPSStreamStart & 0x02) != 0;
			apsFlipY = (chipAPSStreamStart & 0x01) != 0;

			dvsSizeX = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 0);
			dvsSizeY = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 1);

			dvsInvertXY = (spiConfigReceive(CypressFX3.FPGA_DVS, (short) 2) & 0x04) != 0;

			final int imuOrientation = spiConfigReceive(CypressFX3.FPGA_IMU, (short) 10);
			imuFlipX = (imuOrientation & 0x04) != 0;
			imuFlipY = (imuOrientation & 0x02) != 0;
			imuFlipZ = (imuOrientation & 0x01) != 0;

			updateTimestampMasterStatus();
		}

		private void checkMonotonicTimestamp() {
			if (currentTimestamp <= lastTimestamp) {
				CypressFX3.log.severe(toString() + ": non strictly-monotonic timestamp detected: lastTimestamp=" + lastTimestamp
					+ ", currentTimestamp=" + currentTimestamp + ", difference=" + (lastTimestamp - currentTimestamp) + ".");
			}
		}

		private void initFrame() {
			apsCurrentReadoutType = RetinaAEReader.APS_READOUT_RESET;
			Arrays.fill(apsCountX, 0, RetinaAEReader.APS_READOUT_TYPES_NUM, (short) 0);
			Arrays.fill(apsCountY, 0, RetinaAEReader.APS_READOUT_TYPES_NUM, (short) 0);
		}

		private boolean ensureCapacity(final AEPacketRaw buffer, final int capacity) {
			if (buffer.getCapacity() > getAEBufferSize()) {
				if (buffer.overrunOccuredFlag || (capacity > buffer.getCapacity())) {
					buffer.overrunOccuredFlag = true;
					return (false);
				}

				return (true);
			}

			buffer.ensureCapacity(capacity);
			return (true);
		}

		@Override
		protected void translateEvents(final ByteBuffer b) {
			synchronized (aePacketRawPool) {
				final AEPacketRaw buffer = aePacketRawPool.writeBuffer();

				// Truncate off any extra partial event.
				if ((b.limit() & 0x01) != 0) {
					CypressFX3.log.severe(b.limit() + " bytes received via USB, which is not a multiple of two.");
					b.limit(b.limit() & ~0x01);
				}

				buffer.lastCaptureIndex = eventCounter;

				final ShortBuffer sBuf = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

				for (int i = 0; i < sBuf.limit(); i++) {
					final short event = sBuf.get(i);

					// Check if timestamp
					if ((event & 0x8000) != 0) {
						// Is a timestamp! Expand to 32 bits. (Tick is 1us already.)
						lastTimestamp = currentTimestamp;
						currentTimestamp = wrapAdd + (event & 0x7FFF);

						// Check monotonicity of timestamps.
						checkMonotonicTimestamp();
					}
					else {
						// Look at the code, to determine event and data
						// type
						final byte code = (byte) ((event & 0x7000) >>> 12);
						final short data = (short) (event & 0x0FFF);

						switch (code) {
							case 0: // Special event
								switch (data) {
									case 0: // Ignore this, but log it.
										CypressFX3.log.severe("Caught special reserved event!");
										break;

									case 1: // Timetamp reset
										wrapAdd = 0;
										lastTimestamp = 0;
										currentTimestamp = 0;

										updateTimestampMasterStatus();

										CypressFX3.log.info("Timestamp reset event received on " + super.toString()
											+ " at System.currentTimeMillis()=" + System.currentTimeMillis());
										break;

									case 2: // External input (falling edge)
									case 3: // External input (rising edge)
									case 4: // External input (pulse)
										CypressFX3.log.fine("External input event received.");

										// Check that the buffer has space for this event. Enlarge if needed.
										if (ensureCapacity(buffer, eventCounter + 1)) {
											// tobi added data to pass thru rising falling and pulse events
											buffer.getAddresses()[eventCounter] = DavisChip.EXTERNAL_INPUT_EVENT_ADDR + data;
											buffer.getTimestamps()[eventCounter++] = currentTimestamp;
										}
										break;

									case 5: // IMU Start (6 axes)
										CypressFX3.log.fine("IMU6 Start event received.");

										imuCount = 0;

										break;

									case 7: // IMU End
										CypressFX3.log.fine("IMU End event received.");

										if (imuCount == ((2 * RetinaAEReader.IMU_DATA_LENGTH) + 1)) {
											if (ensureCapacity(buffer, eventCounter + IMUSample.SIZE_EVENTS)) {
												// Check for buffer space is also done inside writeToPacket().
												final IMUSample imuSample = new IMUSample(currentTimestamp, imuEvents);
												eventCounter += imuSample.writeToPacket(buffer, eventCounter);
											}
										}
										else {
											CypressFX3.log.info(
												"IMU End: failed to validate IMU sample count (" + imuCount + "), discarding samples.");
										}
										break;

									case 8: // APS Global Shutter Frame Start
										CypressFX3.log.fine("APS GS Frame Start event received.");
										apsResetRead = true;

										initFrame();

										break;

									case 9: // APS Rolling Shutter Frame Start
										CypressFX3.log.fine("APS RS Frame Start event received.");
										apsResetRead = true;

										initFrame();

										break;

									case 10: // APS Frame End
										CypressFX3.log.fine("APS Frame End event received.");

										for (int j = 0; j < RetinaAEReader.APS_READOUT_TYPES_NUM; j++) {
											int checkValue = apsSizeX;

											// Check reset read against zero if
											// disabled.
											if ((j == RetinaAEReader.APS_READOUT_RESET) && !apsResetRead) {
												checkValue = 0;
											}

											if (apsCountX[j] != checkValue) {
												CypressFX3.log.severe("APS Frame End: wrong column count [" + j + " - " + apsCountX[j]
													+ "] detected. You might want to enable 'Ensure APS data transfer' under 'HW Configuration -> Chip Configuration' to improve this.");
											}
										}

										break;

									case 11: // APS Reset Column Start
										CypressFX3.log.fine("APS Reset Column Start event received.");

										apsCurrentReadoutType = RetinaAEReader.APS_READOUT_RESET;
										apsCountY[apsCurrentReadoutType] = 0;

										apsRGBPixelOffsetDirection = false;
										apsRGBPixelOffset = 1; // RGB support, first pixel of row always even.

										break;

									case 12: // APS Signal Column Start
										CypressFX3.log.fine("APS Signal Column Start event received.");

										apsCurrentReadoutType = RetinaAEReader.APS_READOUT_SIGNAL;
										apsCountY[apsCurrentReadoutType] = 0;

										apsRGBPixelOffsetDirection = false;
										apsRGBPixelOffset = 1; // RGB support, first pixel of row always even.

										break;

									case 13: // APS Column End
										CypressFX3.log.fine("APS Column End event received.");

										if (apsCountY[apsCurrentReadoutType] != apsSizeY) {
											CypressFX3.log.severe("APS Column End: wrong row count [" + apsCurrentReadoutType + " - "
												+ apsCountY[apsCurrentReadoutType]
												+ "] detected. You might want to enable 'Ensure APS data transfer' under 'HW Configuration -> Chip Configuration' to improve this.");
										}

										apsCountX[apsCurrentReadoutType]++;

										break;

									case 14: // APS Global Shutter Frame Start with no Reset Read
										CypressFX3.log.fine("APS GS NORST Frame Start event received.");
										apsResetRead = false;

										initFrame();

										break;

									case 15: // APS Rolling Shutter Frame Start with no Reset Read
										CypressFX3.log.fine("APS RS NORST Frame Start event received.");
										apsResetRead = false;

										initFrame();

										break;

									case 16:
									case 17:
									case 18:
									case 19:
									case 20:
									case 21:
									case 22:
									case 23:
									case 24:
									case 25:
									case 26:
									case 27:
									case 28:
									case 29:
									case 30:
									case 31:
										CypressFX3.log.fine("IMU Scale Config event (" + data + ") received.");

										// At this point the IMU event count should be zero (reset by start).
										if (imuCount != 0) {
											CypressFX3.log.info("IMU Scale Config: previous IMU start event missed, attempting recovery.");
										}

										// Increase IMU count by one, to a total of one (0+1=1).
										// This way we can recover from the above error of missing start, and we can
										// later discover if the IMU Scale Config event actually arrived itself.
										imuCount = 1;

										break;

									case 32:
									case 33:
									case 34:
									case 35:
										// TODO: ROI OFF not exposed, so just ignore events.
										break;

									case 48:
										// TODO: APS Exposure Information, ignore for now.
										break;

									case 49:
									case 50:
									case 51:
									case 52:
										// TODO: ROI ON not exposed, so just ignore events.
										break;

									default:
										CypressFX3.log.severe("Caught special event that can't be handled.");
										break;
								}
								break;

							case 1: // Y address
								// Check range conformity.
								if (data >= dvsSizeY) {
									CypressFX3.log.severe("DVS: Y address out of range (0-" + (dvsSizeY - 1) + "): " + data + ".");
									break; // Skip invalid Y address (don't update lastY).
								}

								if (dvsGotY) {
									// Check that the buffer has space for this event. Enlarge if needed.
									if (ensureCapacity(buffer, eventCounter + 1)) {
										buffer.getAddresses()[eventCounter] = ((dvsLastY << DavisChip.YSHIFT) & DavisChip.YMASK);
										buffer.getTimestamps()[eventCounter++] = currentTimestamp;
									}

									CypressFX3.log.fine("DVS: row-only event received for address Y=" + dvsLastY + ".");
								}

								dvsLastY = data;
								dvsGotY = true;

								break;

							case 2: // X address, Polarity OFF
							case 3: // X address, Polarity ON
								// Check range conformity.
								if (data >= dvsSizeX) {
									CypressFX3.log.severe("DVS: X address out of range (0-" + (dvsSizeX - 1) + "): " + data + ".");
									break; // Skip invalid event.
								}

								// Check that the buffer has space for this event. Enlarge if needed.
								if (ensureCapacity(buffer, eventCounter + 1)) {
									// The X address comes out of the new logic such that the (0, 0) address
									// is, as expected by most, in the lower left corner. Since the DAVIS240
									// chip class data format assumes that this is still flipped, as in the
									// old logic, we have to flip it here, so that the chip class extractor
									// can flip it back. Backwards compatibility with recordings is the main
									// motivation to do this hack.
									// NOTE 09.2017: logic now uses upper left (CG format) as output.

									// Invert polarity for PixelParade high gain pixels (DavisSense), because of
									// negative gain from pre-amplifier.
									final byte polarity = ((chipID == DAViSFX3HardwareInterface.CHIP_DAVIS208) && (data < 192))
										? ((byte) (~code)) : (code);

									if (dvsInvertXY) {
										buffer.getAddresses()[eventCounter] = (((dvsSizeX - 1 - data) << DavisChip.YSHIFT) & DavisChip.YMASK)
											| (((dvsSizeY - 1 - dvsLastY) << DavisChip.XSHIFT) & DavisChip.XMASK)
											| (((polarity & 0x01) << DavisChip.POLSHIFT) & DavisChip.POLMASK);
									}
									else {
										buffer.getAddresses()[eventCounter] = (((dvsSizeY - 1 - dvsLastY) << DavisChip.YSHIFT) & DavisChip.YMASK)
											| (((dvsSizeX - 1 - data) << DavisChip.XSHIFT) & DavisChip.XMASK)
											| (((polarity & 0x01) << DavisChip.POLSHIFT) & DavisChip.POLMASK);
									}

									buffer.getTimestamps()[eventCounter++] = currentTimestamp;
								}

								dvsGotY = false;

								break;

							case 4: // APS ADC sample
								// Let's check that apsCountY is not above the maximum. This could happen
								// if start/end of column events are discarded (no wait on transfer stall).
								if (apsCountY[apsCurrentReadoutType] >= apsSizeY) {
									CypressFX3.log.fine("APS ADC sample: row count is at maximum, discarding further samples.");
									break;
								}

								// The DAVIS240c chip is flipped along the X axis. This means it's first reading
								// out the leftmost columns, and not the rightmost ones as in all the other chips.
								// So, if a 240c is detected, we don't do the artificial sign flip here.
								int xPos;
								int yPos;

								if (apsFlipX) {
									xPos = apsSizeX - 1 - apsCountX[apsCurrentReadoutType];
								}
								else {
									xPos = apsCountX[apsCurrentReadoutType];
								}

								if (apsFlipY) {
									yPos = apsSizeY - 1 - apsCountY[apsCurrentReadoutType];
								}
								else {
									yPos = apsCountY[apsCurrentReadoutType];
								}

								if (chipID == DAViSFX3HardwareInterface.CHIP_DAVISRGB) {
									yPos += apsRGBPixelOffset;
								}

								if (apsInvertXY) {
									final int temp = xPos;
									xPos = yPos;
									yPos = temp;
								}

								// NOTE 09.2017: logic now uses upper left (CG format) as output.
								yPos = (apsInvertXY) ? (apsSizeX - 1 - yPos) : (apsSizeY - 1 - yPos);

								apsCountY[apsCurrentReadoutType]++;

								// RGB support: first 320 pixels are even, then odd.
								if (!apsRGBPixelOffsetDirection) { // Increasing
									apsRGBPixelOffset++;

									if (apsRGBPixelOffset == 321) {
										// Switch to decreasing after last even pixel.
										apsRGBPixelOffsetDirection = true;
										apsRGBPixelOffset = 318;
									}
								}
								else { // Decreasing
									apsRGBPixelOffset -= 3;
								}

								// Check that the buffer has space for this event. Enlarge if needed.
								if (ensureCapacity(buffer, eventCounter + 1)) {
									buffer.getAddresses()[eventCounter] = DavisChip.ADDRESS_TYPE_APS
										| ((yPos << DavisChip.YSHIFT) & DavisChip.YMASK) | ((xPos << DavisChip.XSHIFT) & DavisChip.XMASK)
										| ((apsCurrentReadoutType << DavisChip.ADC_READCYCLE_SHIFT) & DavisChip.ADC_READCYCLE_MASK)
										| (data & DavisChip.ADC_DATA_MASK);
									buffer.getTimestamps()[eventCounter++] = currentTimestamp;
								}
								break;

							case 5: // Misc 8bit data.
								final byte misc8Code = (byte) ((data & 0x0F00) >>> 8);
								final byte misc8Data = (byte) (data & 0x00FF);

								switch (misc8Code) {
									case 0:
										// Detect missing IMU end events.
										if (imuCount >= ((2 * RetinaAEReader.IMU_DATA_LENGTH) + 1)) {
											CypressFX3.log.info("IMU data: IMU samples count is at maximum, discarding further samples.");
											break;
										}

										// IMU data event.
										switch (imuCount) {
											case 0:
												CypressFX3.log.severe(
													"IMU data: missing IMU Scale Config event. Parsing of IMU events will still be attempted, but be aware that Accel/Gyro scale conversions may be inaccurate.");
												imuCount = 1;
												// Fall through to next case, as if imuCount was equal to 1.

											case 1:
											case 3:
											case 5:
											case 7:
											case 9:
											case 11:
											case 13:
												imuTmpData = misc8Data;
												break;

											case 2: // Accel X
												imuEvents[0] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
												if (imuFlipX) {
													imuEvents[0] = (short) -imuEvents[0];
												}
												break;

											case 4: // Accel Y
												imuEvents[1] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
												if (imuFlipY) {
													imuEvents[1] = (short) -imuEvents[1];
												}
												break;

											case 6: // Accel Z
												imuEvents[2] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
												if (imuFlipZ) {
													imuEvents[2] = (short) -imuEvents[2];
												}
												break;

											case 8: // Temperature
												imuEvents[3] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
												break;

											case 10: // Gyro X
												imuEvents[4] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
												if (imuFlipX) {
													imuEvents[4] = (short) -imuEvents[4];
												}
												break;

											case 12: // Gyro Y
												imuEvents[5] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
												if (imuFlipY) {
													imuEvents[5] = (short) -imuEvents[5];
												}
												break;

											case 14: // Gyro Z
												imuEvents[6] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
												if (imuFlipZ) {
													imuEvents[6] = (short) -imuEvents[6];
												}
												break;
										}

										imuCount++;

										break;

									case 1:
									case 2:
										// Ignore ROI events.
										break;

									default:
										CypressFX3.log.severe("Caught Misc8 event that can't be handled.");
										break;
								}

								break;

							case 6: // Misc 10bit data.
								final byte misc10Code = (byte) ((data & 0x0C00) >>> 10);
								final short misc10Data = (short) (data & 0x03FF);

								switch (misc10Code) {
									case 0:
										// TODO: APS Exposure Information, ignore for now.
										break;

									default:
										CypressFX3.log.severe("Caught Misc10 event that can't be handled.");
										break;
								}

								break;

							case 7: // Timestamp wrap
								// Each wrap is 2^15 us (~32ms), and we have
								// to multiply it with the wrap counter,
								// which is located in the data part of this
								// event.
								wrapAdd += (0x8000L * data);

								lastTimestamp = currentTimestamp;
								currentTimestamp = wrapAdd;

								// Check monotonicity of timestamps.
								checkMonotonicTimestamp();

								CypressFX3.log.fine(
									String.format("Timestamp wrap event received on %s with multiplier of %d.", super.toString(), data));
								break;

							default:
								CypressFX3.log.severe("Caught event that can't be handled.");
								break;
						}
					}
				} // end loop over usb data buffer

				buffer.setNumEvents(eventCounter);
				// write capture size
				buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
			} // sync on aePacketRawPool
		}

		@Override
		public void propertyChange(final PropertyChangeEvent arg0) {
			// Do nothing here, IMU comes directly via event-stream.
		}
	}
}
