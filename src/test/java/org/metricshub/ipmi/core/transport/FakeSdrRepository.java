package org.metricshub.ipmi.core.transport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An SDR repository, sensor readings and FRU inventories served by a {@link FakeRmcpPlusBmc}: Get SDR Repository
 * Info, Reserve SDR Repository, Get SDR (whole or in chunks), Get Sensor Reading, Get FRU Inventory Area Info and
 * Read FRU Data.
 */
public final class FakeSdrRepository {

	/** Network function of the storage commands. */
	public static final int NETFN_STORAGE = 0x0a;
	/** Network function of the sensor/event commands. */
	public static final int NETFN_SENSOR = 0x04;
	/** Command code of Get SDR Repository Info. */
	public static final int GET_SDR_REPOSITORY_INFO = 0x20;
	/** Command code of Reserve SDR Repository. */
	public static final int RESERVE_SDR_REPOSITORY = 0x22;
	/** Command code of Get SDR. */
	public static final int GET_SDR = 0x23;
	/** Command code of Get Sensor Reading Factors. */
	public static final int GET_SENSOR_READING_FACTORS = 0x23;
	/** Command code of Get Sensor Reading. */
	public static final int GET_SENSOR_READING = 0x2d;
	/** Command code of Get FRU Inventory Area Info. */
	public static final int GET_FRU_INVENTORY_AREA_INFO = 0x10;
	/** Command code of Read FRU Data. */
	public static final int READ_FRU_DATA = 0x11;

	/** SDR record type of a Full Sensor Record. */
	public static final int FULL_SENSOR_RECORD = 0x01;
	/** SDR record type of a Compact Sensor Record. */
	public static final int COMPACT_SENSOR_RECORD = 0x02;
	/** SDR record type of a FRU Device Locator Record. */
	public static final int FRU_DEVICE_LOCATOR_RECORD = 0x11;

	private static final int LAST_RECORD_ID = 0xffff;
	private static final int SDR_VERSION = 0x51;

	private final List<byte[]> records = new ArrayList<>();
	private final Map<Integer, byte[]> readings = new ConcurrentHashMap<>();
	private final Map<Integer, byte[]> factors = new ConcurrentHashMap<>();
	private final Map<Integer, byte[]> frus = new ConcurrentHashMap<>();
	private final java.util.Set<Integer> chunkedOnly = ConcurrentHashMap.newKeySet();
	private final Map<Integer, Integer> cancelOnce = new ConcurrentHashMap<>();
	private volatile int addTimestamp = 1;
	private volatile int deleteTimestamp = 1;
	private volatile boolean infoSupported = true;

	/**
	 * Registers the handlers on the BMC.
	 *
	 * @param bmc the BMC
	 */
	public void install(FakeRmcpPlusBmc bmc) {
		bmc.handle(NETFN_STORAGE, GET_SDR_REPOSITORY_INFO, this::repositoryInfo);
		bmc.handle(NETFN_STORAGE, RESERVE_SDR_REPOSITORY, data -> new byte[] { 0x00, 0x01, 0x00 });
		bmc.handle(NETFN_STORAGE, GET_SDR, this::getSdr);
		bmc.handle(NETFN_SENSOR, GET_SENSOR_READING, this::getSensorReading);
		bmc.handle(NETFN_SENSOR, GET_SENSOR_READING_FACTORS, this::getSensorReadingFactors);
		bmc.handle(NETFN_STORAGE, GET_FRU_INVENTORY_AREA_INFO, this::fruInventoryAreaInfo);
		bmc.handle(NETFN_STORAGE, READ_FRU_DATA, this::readFruData);
	}

	/**
	 * Adds a record, as the BMC stores it (5-byte header included).
	 *
	 * @param record the record bytes
	 * @return this repository
	 */
	public FakeSdrRepository add(byte[] record) {
		records.add(record.clone());
		return this;
	}

	/**
	 * @return the number of records
	 */
	public int size() {
		return records.size();
	}

	/**
	 * Sets the reply of Get Sensor Reading for a sensor number: reading, flags, states.
	 *
	 * @param sensorNumber the sensor number
	 * @param reply the reply bytes after the completion code
	 * @return this repository
	 */
	public FakeSdrRepository reading(int sensorNumber, int... reply) {
		readings.put(sensorNumber, bytes(reply));
		return this;
	}

	/**
	 * Sets the reply of Get Sensor Reading Factors for a sensor number and a reading byte: next reading, factors (or a
	 * completion code alone). The BMC answers Invalid Command to any other request, as a BMC without the command does.
	 *
	 * @param sensorNumber the sensor number
	 * @param reading the reading byte
	 * @param reply the reply bytes after the completion code
	 * @return this repository
	 */
	public FakeSdrRepository factors(int sensorNumber, int reading, int... reply) {
		factors.put(sensorNumber << 8 | reading, bytes(reply));
		return this;
	}

	/**
	 * Sets the inventory of a FRU.
	 *
	 * @param fruId the FRU device ID
	 * @param image the FRU inventory bytes
	 * @return this repository
	 */
	public FakeSdrRepository fru(int fruId, byte[] image) {
		frus.put(fruId, image.clone());
		return this;
	}

	/**
	 * Makes the BMC refuse to return the given record whole ("Cannot return number of requested data bytes"), so
	 * that the walk reads it in chunks.
	 *
	 * @param recordId the record ID
	 * @return this repository
	 */
	public FakeSdrRepository chunkedOnly(int recordId) {
		chunkedOnly.add(recordId);
		return this;
	}

	/**
	 * Makes the BMC answer "Reservation canceled" once, to the chunk of the given record at the given offset, as a BMC
	 * does when another session appears during the walk.
	 *
	 * @param recordId the record ID
	 * @param offset the offset of the chunk
	 * @return this repository
	 */
	public FakeSdrRepository cancelReservationOnce(int recordId, int offset) {
		cancelOnce.put(recordId, offset);
		return this;
	}

	/**
	 * Changes the repository: a new most-recent-addition timestamp, as after an SDR update.
	 */
	public void touch() {
		addTimestamp++;
	}

	/**
	 * Makes Get SDR Repository Info fail with Invalid Command, as on a BMC that does not support it.
	 */
	public void unsupportedInfo() {
		infoSupported = false;
	}

	private byte[] repositoryInfo(byte[] data) {
		if (!infoSupported) {
			return new byte[] { (byte) 0xc1 };
		}
		return ByteBuffer
				.allocate(15)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0x00)
				.put((byte) SDR_VERSION)
				.putShort((short) records.size())
				.putShort((short) 0x1000)
				.putInt(addTimestamp)
				.putInt(deleteTimestamp)
				.put((byte) 0x02)
				.array();
	}

	private byte[] getSdr(byte[] data) {
		ByteBuffer request = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		int recordId = request.getShort(2) & 0xffff;
		int offset = data[4] & 0xff;
		int count = data[5] & 0xff;
		int index = recordId == 0 ? 0 : indexOf(recordId);
		if (index < 0 || index >= records.size()) {
			return new byte[] { (byte) 0xcb }; // requested record not present
		}
		byte[] record = records.get(index);
		if (count == 0xff && chunkedOnly.contains(id(record))) {
			return new byte[] { (byte) 0xca }; // cannot return number of requested data bytes
		}
		if (count != 0xff && cancelOnce.remove(id(record), offset)) {
			return new byte[] { (byte) 0xc5 }; // reservation canceled
		}
		int next = index + 1 < records.size() ? id(records.get(index + 1)) : LAST_RECORD_ID;
		int end = count == 0xff ? record.length : Math.min(record.length, offset + count);
		byte[] chunk = Arrays.copyOfRange(record, Math.min(offset, record.length), end);
		return ByteBuffer
				.allocate(3 + chunk.length)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0x00)
				.putShort((short) next)
				.put(chunk)
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

	private byte[] getSensorReading(byte[] data) {
		byte[] reply = readings.get(data[0] & 0xff);
		if (reply == null) {
			return new byte[] { (byte) 0xcb }; // requested sensor not present
		}
		if (reply.length == 1) {
			return reply; // a completion code alone
		}
		return concat(new byte[1], reply);
	}

	private byte[] getSensorReadingFactors(byte[] data) {
		byte[] reply = factors.get((data[0] & 0xff) << 8 | data[1] & 0xff);
		if (reply == null) {
			return new byte[] { (byte) 0xc1 }; // invalid command
		}
		return reply.length == 1 ? reply : concat(new byte[1], reply);
	}

	private byte[] fruInventoryAreaInfo(byte[] data) {
		byte[] image = frus.get(data[0] & 0xff);
		if (image == null) {
			return new byte[] { (byte) 0xc3 }; // timeout while processing the command: the FRU is absent
		}
		return ByteBuffer
				.allocate(4)
				.order(ByteOrder.LITTLE_ENDIAN)
				.put((byte) 0x00)
				.putShort((short) image.length)
				.put((byte) 0x00)
				.array();
	}

	private byte[] readFruData(byte[] data) {
		byte[] image = frus.get(data[0] & 0xff);
		if (image == null) {
			return new byte[] { (byte) 0xc3 };
		}
		int offset = (data[1] & 0xff) | (data[2] & 0xff) << 8;
		int count = data[3] & 0xff;
		if (offset >= image.length) {
			return new byte[] { (byte) 0xc9 }; // parameter out of range
		}
		byte[] chunk = Arrays.copyOfRange(image, offset, Math.min(image.length, offset + count));
		return concat(new byte[] { 0x00, (byte) chunk.length }, chunk);
	}

	/**
	 * Builds a Compact Sensor Record (IPMI 2.0 table 43-2).
	 *
	 * @param recordId the record ID
	 * @param sensorNumber the sensor number
	 * @param entityId the entity ID
	 * @param entityInstance the entity instance
	 * @param sensorType the sensor type
	 * @param eventReadingType the event/reading type
	 * @param name the ID string
	 * @return the record bytes
	 */
	public static byte[] compactSensorRecord(
			int recordId,
			int sensorNumber,
			int entityId,
			int entityInstance,
			int sensorType,
			int eventReadingType,
			String name) {
		byte[] body = new byte[27 + name.length()];
		body[0] = 0x20; // sensor owner ID
		body[1] = 0x00; // owner LUN
		body[2] = (byte) sensorNumber;
		body[3] = (byte) entityId;
		body[4] = (byte) entityInstance;
		body[5] = 0x7f; // sensor initialization
		body[6] = 0x68; // sensor capabilities
		body[7] = (byte) sensorType;
		body[8] = (byte) eventReadingType;
		body[26] = (byte) (0xc0 | name.length()); // 8-bit ASCII ID string
		System.arraycopy(name.getBytes(StandardCharsets.US_ASCII), 0, body, 27, name.length());
		return record(recordId, COMPACT_SENSOR_RECORD, body);
	}

	/**
	 * Builds a FRU Device Locator Record (IPMI 2.0 table 43-7) of a logical FRU device.
	 *
	 * @param recordId the record ID
	 * @param fruId the FRU device ID
	 * @param entityId the entity ID
	 * @param entityInstance the entity instance
	 * @param name the ID string
	 * @return the record bytes
	 */
	public static byte[] fruDeviceLocatorRecord(int recordId, int fruId, int entityId, int entityInstance, String name) {
		byte[] body = new byte[11 + name.length()];
		body[0] = 0x20; // device access address
		body[1] = (byte) fruId;
		body[2] = (byte) 0x80; // logical FRU device
		body[3] = 0x00; // channel number
		body[5] = 0x10; // device type: FRU inventory device
		body[7] = (byte) entityId;
		body[8] = (byte) entityInstance;
		body[10] = (byte) (0xc0 | name.length());
		System.arraycopy(name.getBytes(StandardCharsets.US_ASCII), 0, body, 11, name.length());
		return record(recordId, FRU_DEVICE_LOCATOR_RECORD, body);
	}

	/**
	 * Builds a record of any type: the 5-byte header and the body.
	 *
	 * @param recordId the record ID
	 * @param recordType the record type
	 * @param body the body
	 * @return the record bytes
	 */
	public static byte[] record(int recordId, int recordType, byte[] body) {
		byte[] record = new byte[5 + body.length];
		record[0] = (byte) recordId;
		record[1] = (byte) (recordId >> 8);
		record[2] = (byte) SDR_VERSION;
		record[3] = (byte) recordType;
		record[4] = (byte) body.length;
		System.arraycopy(body, 0, record, 5, body.length);
		return record;
	}

	/**
	 * Returns a copy of a record with another record ID.
	 *
	 * @param record the record
	 * @param recordId the new ID
	 * @return the copy
	 */
	public static byte[] withId(byte[] record, int recordId) {
		byte[] copy = record.clone();
		copy[0] = (byte) recordId;
		copy[1] = (byte) (recordId >> 8);
		return copy;
	}

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	private static byte[] concat(byte[] first, byte[] second) {
		byte[] result = Arrays.copyOf(first, first.length + second.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}
}
