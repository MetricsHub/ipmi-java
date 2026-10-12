package org.metricshub.ipmi.core.transport;

import static org.metricshub.ipmi.core.transport.FakeSdrRepository.NETFN_STORAGE;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The System Event Log of a {@link FakeRmcpPlusBmc}: Get SEL Info, Reserve SEL and Get SEL Entry (IPMI 2.0 section
 * 31) over a list of 16-byte records, with the faults a real BMC shows.
 */
public final class FakeSel {

	public static final int GET_SEL_INFO = 0x40;
	public static final int RESERVE_SEL = 0x42;
	public static final int GET_SEL_ENTRY = 0x43;

	/** The generator ID of the events: the BMC itself (slave address 20h, LUN 0). */
	public static final int GENERATOR_BMC = 0x0020;

	private static final int LAST_RECORD_ID = 0xffff;
	private static final int SEL_VERSION = 0x51;
	private static final int UNSPECIFIED = 0xffffffff;
	private static final long LAST_PRE_INIT_TIMESTAMP = 0x20000000L;
	private static final int RECORD_SIZE = 16;

	private final List<byte[]> records = new CopyOnWriteArrayList<>();
	private final Map<Integer, Integer> cancelOnce = new ConcurrentHashMap<>();
	private volatile boolean infoSupported = true;
	private volatile int reservationId;
	private volatile int additionTimestamp = UNSPECIFIED;
	private volatile int eraseTimestamp = UNSPECIFIED;

	/**
	 * Installs the SEL commands on the BMC.
	 *
	 * @param bmc the BMC
	 */
	public void install(FakeRmcpPlusBmc bmc) {
		bmc.handle(NETFN_STORAGE, GET_SEL_INFO, this::info);
		bmc.handle(NETFN_STORAGE, RESERVE_SEL, this::reserve);
		bmc.handle(NETFN_STORAGE, GET_SEL_ENTRY, this::entry);
	}

	/**
	 * Appends a record to the log.
	 *
	 * @param record the 16 bytes of the record, its record ID first
	 * @return this log
	 */
	public FakeSel add(byte[] record) {
		records.add(record.clone());
		// A BMC with a clock stamps the addition with it: a record logged with a pre-init timestamp does not change it
		if ((record[2] & 0xff) < 0xe0) {
			int timestamp = ByteBuffer.wrap(record, 3, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
			if ((timestamp & 0xffffffffL) > LAST_PRE_INIT_TIMESTAMP) {
				additionTimestamp = timestamp;
			}
		}
		return this;
	}

	/** Clears the log, as Clear SEL does: the record IDs of the next entries start over. */
	public void clear() {
		records.clear();
		eraseTimestamp = (int) (System.currentTimeMillis() / 1000);
	}

	/** Refuses Get SEL Info with C1h from now on. */
	public void unsupportedInfo() {
		infoSupported = false;
	}

	/**
	 * Answers "reservation canceled" (C5h) to the next Get SEL Entry of the given record.
	 *
	 * @param recordId the record ID
	 * @return this log
	 */
	public FakeSel cancelReservationOnce(int recordId) {
		cancelOnce.merge(recordId, 1, Integer::sum);
		return this;
	}

	private byte[] info(byte[] data) {
		if (!infoSupported) {
			return new byte[] { (byte) 0xc1 };
		}
		return ByteBuffer
				.allocate(15)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0x00)
				.put((byte) SEL_VERSION)
				.putShort((short) records.size())
				.putShort((short) 0xffff)
				.putInt(additionTimestamp)
				.putInt(eraseTimestamp)
				.put((byte) 0x02)
				.array();
	}

	private byte[] reserve(byte[] data) {
		reservationId++;
		return new byte[] { 0x00, (byte) reservationId, (byte) (reservationId >> 8) };
	}

	private byte[] entry(byte[] data) {
		int recordId = (data[2] & 0xff) | (data[3] & 0xff) << 8;
		Integer pending = cancelOnce.get(recordId);
		if (pending != null) {
			if (pending > 1) {
				cancelOnce.put(recordId, pending - 1);
			} else {
				cancelOnce.remove(recordId);
			}
			return new byte[] { (byte) 0xc5 };
		}
		int index = recordId == 0 ? 0 : recordId == LAST_RECORD_ID ? records.size() - 1 : indexOf(recordId);
		if (index < 0 || index >= records.size()) {
			return new byte[] { (byte) 0xcb }; // requested record not present
		}
		int next = index + 1 < records.size() ? id(records.get(index + 1)) : LAST_RECORD_ID;
		return ByteBuffer
				.allocate(3 + RECORD_SIZE)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0x00)
				.putShort((short) next)
				.put(records.get(index))
				.array();
	}

	private int indexOf(int recordId) {
		for (int i = 0; i < records.size(); i++) {
			if (id(records.get(i)) == recordId) {
				return i;
			}
		}
		return -1;
	}

	private static int id(byte[] record) {
		return (record[0] & 0xff) | (record[1] & 0xff) << 8;
	}

	/**
	 * Builds a system event record (IPMI 2.0 section 32.1) logged by the BMC itself.
	 *
	 * @param recordId the record ID
	 * @param timestamp the timestamp, seconds since 1970 (or since initialization when at most 20000000h)
	 * @param sensorType the sensor type code
	 * @param sensorNumber the sensor number
	 * @param eventType the event/reading type code
	 * @param assertion true for an assertion, false for a deassertion
	 * @param eventData the three event data bytes, the event offset in the low nibble of the first
	 * @return the 16 bytes of the record
	 */
	public static byte[] systemEvent(
			int recordId,
			long timestamp,
			int sensorType,
			int sensorNumber,
			int eventType,
			boolean assertion,
			int... eventData) {
		return ByteBuffer
				.allocate(RECORD_SIZE)
				.order(ByteOrder.LITTLE_ENDIAN)
				.putShort((short) recordId)
				.put((byte) 0x02)
				.putInt((int) timestamp)
				.putShort((short) GENERATOR_BMC)
				.put((byte) 0x04) // EvM rev: IPMI 2.0
				.put((byte) sensorType)
				.put((byte) sensorNumber)
				.put((byte) ((assertion ? 0x00 : 0x80) | eventType))
				.put((byte) eventData[0])
				.put((byte) eventData[1])
				.put((byte) eventData[2])
				.array();
	}

	/**
	 * Builds a timestamped OEM record (IPMI 2.0 section 32.2).
	 *
	 * @param recordId the record ID
	 * @param recordType the record type, C0h to DFh
	 * @param timestamp the timestamp, seconds since 1970
	 * @param manufacturerId the IANA enterprise number
	 * @param oemData the six OEM bytes
	 * @return the 16 bytes of the record
	 */
	public static byte[] oemTimestamped(
			int recordId,
			int recordType,
			long timestamp,
			int manufacturerId,
			int... oemData) {
		ByteBuffer record = ByteBuffer
				.allocate(RECORD_SIZE)
				.order(ByteOrder.LITTLE_ENDIAN)
				.putShort((short) recordId)
				.put((byte) recordType)
				.putInt((int) timestamp)
				.put((byte) manufacturerId)
				.put((byte) (manufacturerId >> 8))
				.put((byte) (manufacturerId >> 16));
		for (int b : oemData) {
			record.put((byte) b);
		}
		return record.array();
	}

	/**
	 * Builds a record of any other type (IPMI 2.0 section 32.3 for E0h to FFh, reserved otherwise).
	 *
	 * @param recordId the record ID
	 * @param recordType the record type
	 * @param body the 13 bytes after the record type
	 * @return the 16 bytes of the record
	 */
	public static byte[] selRecord(int recordId, int recordType, int... body) {
		ByteBuffer record = ByteBuffer
				.allocate(RECORD_SIZE)
				.order(ByteOrder.LITTLE_ENDIAN)
				.putShort((short) recordId)
				.put((byte) recordType);
		for (int b : body) {
			record.put((byte) b);
		}
		return record.array();
	}
}
