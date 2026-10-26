package org.metricshub.ipmi.core.coding.commands.sdr.record;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import org.metricshub.ipmi.core.common.TypeConverter;

import java.util.Arrays;

/**
 * Fields shared by the Full, Compact and Event-Only sensor records (IPMI 2.0 tables 43-1, 43-2 and 43-3), and the
 * methods decoding them.
 * <p>
 * Every sensor record has the sensor owner, sensor number, entity, sensor type, event/reading type, direction and ID
 * string. The capabilities and units are only defined for Full and Compact records: an Event-Only record has no
 * reading, so its units are <code>null</code> and its hysteresis and thresholds are not readable. The record sharing
 * fields are only defined for Compact and Event-Only records: a Full record describes a single sensor, so its share
 * count is 0 and it has no ID string instance modifier.
 */
public abstract class AbstractSensorRecord extends SensorRecord {

	private byte sensorOwnerId;

	private AddressType addressType;

	private byte channelNumber;

	private byte sensorOwnerLun;

	private byte sensorNumber;

	private EntityId entityId;

	private boolean entityPhysical;

	private byte entityInstanceNumber;

	private boolean hysteresisReadable;

	private boolean thresholdsReadable;

	private SensorType sensorType;

	private int sensorTypeCode;

	private int eventReadingType;

	private boolean percentage;

	private RateUnit rateUnit;

	private ModifierUnitUsage modifierUnitUsage;

	private SensorUnit sensorBaseUnit;

	private SensorUnit sensorModifierUnit;

	private SensorDirection sensorDirection;

	private String name = "";

	private InstanceModifierType idInstanceModifierType;

	private int shareCount;

	private boolean entityInstanceIncrements;

	private int idInstanceModifierOffset;

	/**
	 * Decodes the sensor owner, sensor number and entity (record bytes 6 to 10, at the same position in every sensor
	 * record), then the sensor type and the event/reading type.
	 *
	 * @param recordData
	 *        - raw data containing the whole record
	 * @param sensorTypeIndex
	 *        - index of the sensor type byte, followed by the event/reading type byte
	 */
	protected void populateSensorHeader(byte[] recordData, int sensorTypeIndex) {
		setSensorOwnerId(TypeConverter.intToByte((TypeConverter.byteToInt(recordData[5]) & 0xfe) >> 1));
		setAddressType(AddressType.parseInt(TypeConverter.byteToInt(recordData[5]) & 0x01));
		setChannelNumber(TypeConverter.intToByte((TypeConverter.byteToInt(recordData[6]) & 0xf0) >> 4));
		setSensorOwnerLun(TypeConverter.intToByte(TypeConverter.byteToInt(recordData[6]) & 0x3));
		setSensorNumber(recordData[7]);
		setEntityId(EntityId.parseInt(TypeConverter.byteToInt(recordData[8])));
		setEntityPhysical((TypeConverter.byteToInt(recordData[9]) & 0x80) == 0);
		setEntityInstanceNumber(TypeConverter.intToByte(TypeConverter.byteToInt(recordData[9]) & 0x7f));
		setSensorTypeCode(TypeConverter.byteToInt(recordData[sensorTypeIndex]));
		setSensorType(SensorType.parseInt(getSensorTypeCode()));
		setEventReadingType(TypeConverter.byteToInt(recordData[sensorTypeIndex + 1]));
	}

	/**
	 * Decodes the sensor capabilities (record byte 12) and the sensor units (record bytes 21 to 23) of Full and
	 * Compact records.
	 *
	 * @param recordData
	 *        - raw data containing the whole record
	 */
	protected void populateCapabilitiesAndUnits(byte[] recordData) {
		setHysteresisReadable(isFieldReadable((TypeConverter.byteToInt(recordData[11]) & 0x30) >> 4));
		setThresholdsReadable(isFieldReadable((TypeConverter.byteToInt(recordData[11]) & 0xc) >> 2));
		setRateUnit(RateUnit.parseInt((TypeConverter.byteToInt(recordData[20]) & 0x38) >> 3));
		setModifierUnitUsage(ModifierUnitUsage.parseInt((TypeConverter.byteToInt(recordData[20]) & 0x6) >> 1));
		setPercentage((TypeConverter.byteToInt(recordData[20]) & 0x1) != 0);
		setSensorBaseUnit(SensorUnit.parseInt(TypeConverter.byteToInt(recordData[21])));
		setSensorModifierUnit(SensorUnit.parseInt(TypeConverter.byteToInt(recordData[22])));
	}

	/**
	 * @param field
	 *        - 2-bit hysteresis or threshold access support field of the sensor capabilities
	 * @return whether the field is readable (readable, or readable and settable)
	 */
	private static boolean isFieldReadable(int field) {
		return field == 1 || field == 2;
	}

	/**
	 * Decodes the sensor direction and the record sharing fields of Compact and Event-Only records.
	 *
	 * @param recordData
	 *        - raw data containing the whole record
	 * @param index
	 *        - index of the byte holding the sensor direction, ID string instance modifier type and share count,
	 *        followed by the byte holding the entity instance sharing and ID string instance modifier offset
	 */
	protected void populateSharing(byte[] recordData, int index) {
		setSensorDirection(SensorDirection.parseInt((TypeConverter.byteToInt(recordData[index]) & 0xc0) >> 6));
		setIdInstanceModifierType(InstanceModifierType.parseInt((TypeConverter.byteToInt(recordData[index]) & 0x30) >> 4));
		setShareCount(TypeConverter.byteToInt(recordData[index]) & 0xf);
		setEntityInstanceIncrements((TypeConverter.byteToInt(recordData[index + 1]) & 0x80) != 0);
		setIdInstanceModifierOffset(TypeConverter.byteToInt(recordData[index + 1]) & 0x7f);
	}

	/**
	 * Decodes the sensor ID string, from its type/length byte to the end of the record (see
	 * {@link #decodeName(byte, byte[])}).
	 *
	 * @param recordData
	 *        - raw data containing the whole record
	 * @param typeIndex
	 *        - index of the ID string type/length byte, followed by the ID string bytes
	 */
	protected void populateName(byte[] recordData, int typeIndex) {
		setName(decodeName(recordData[typeIndex], Arrays.copyOfRange(recordData, typeIndex + 1, recordData.length)));
	}

	/**
	 * Returns the sensor owner ID (byte 6 bits 7:1 of the record): the 7-bit I2C slave address of the controller that
	 * owns the sensor, or a system software ID ({@link #getAddressType()}).
	 * {@link org.metricshub.ipmi.core.coding.commands.app.SendMessage} takes the slave address in its 8-bit form: this
	 * value shifted left by one.
	 *
	 * @return the 7-bit I2C slave address or system software ID of the sensor owner
	 */
	public byte getSensorOwnerId() {
		return sensorOwnerId;
	}

	/**
	 * Sets the sensor owner ID.
	 *
	 * @param sensorOwnerId
	 *        - the 7-bit I2C slave address or system software ID of the sensor owner
	 */
	public void setSensorOwnerId(byte sensorOwnerId) {
		this.sensorOwnerId = sensorOwnerId;
	}

	/**
	 * Tells whether the sensor owner ID is an IPMB slave address or a system software ID (byte 6 bit 0 of the record).
	 *
	 * @return whether {@link #getSensorOwnerId()} is an IPMB slave address or a system software ID
	 */
	public AddressType getAddressType() {
		return addressType;
	}

	/**
	 * Sets whether the sensor owner ID is an IPMB slave address or a system software ID.
	 *
	 * @param addressType
	 *        - whether the sensor owner ID is an IPMB slave address or a system software ID
	 */
	public void setAddressType(AddressType addressType) {
		this.addressType = addressType;
	}

	/**
	 * Returns the channel number of the sensor owner (byte 7 bits 7:4 of the record), 0 for the primary IPMB.
	 *
	 * @return the channel number of the sensor owner
	 */
	public byte getChannelNumber() {
		return channelNumber;
	}

	/**
	 * Sets the channel number of the sensor owner.
	 *
	 * @param channelNumber
	 *        - the channel number of the sensor owner
	 */
	public void setChannelNumber(byte channelNumber) {
		this.channelNumber = channelNumber;
	}

	/**
	 * Returns the LUN of the sensor owner (byte 7 bits 1:0 of the record), the LUN of the commands that read the
	 * sensor.
	 *
	 * @return the LUN of the sensor owner
	 */
	public byte getSensorOwnerLun() {
		return sensorOwnerLun;
	}

	/**
	 * Sets the LUN of the sensor owner.
	 *
	 * @param sensorOwnerLun
	 *        - the LUN of the sensor owner
	 */
	public void setSensorOwnerLun(byte sensorOwnerLun) {
		this.sensorOwnerLun = sensorOwnerLun;
	}

	/**
	 * Returns the number of the sensor (byte 8 of the record), the one the commands that read the sensor take.
	 *
	 * @return the sensor number, unique for its owner and LUN
	 */
	public byte getSensorNumber() {
		return sensorNumber;
	}

	/**
	 * Sets the number of the sensor.
	 *
	 * @param sensorNumber
	 *        - the sensor number, unique for its owner and LUN
	 */
	public void setSensorNumber(byte sensorNumber) {
		this.sensorNumber = sensorNumber;
	}

	/**
	 * Returns the entity the sensor monitors (byte 9 of the record).
	 *
	 * @return the physical entity the sensor is monitoring
	 */
	public EntityId getEntityId() {
		return entityId;
	}

	/**
	 * Sets the entity the sensor monitors.
	 *
	 * @param entityId
	 *        - the physical entity the sensor is monitoring
	 */
	public void setEntityId(EntityId entityId) {
		this.entityId = entityId;
	}

	/**
	 * Tells whether the entity is a physical entity or a logical container entity (byte 10 bit 7 of the record).
	 *
	 * @return <code>true</code> if the entity is physical, <code>false</code> if it is logical
	 */
	public boolean isEntityPhysical() {
		return entityPhysical;
	}

	/**
	 * Sets whether the entity is a physical entity or a logical container entity.
	 *
	 * @param entityPhysical
	 *        - <code>true</code> if the entity is physical, <code>false</code> if it is logical
	 */
	public void setEntityPhysical(boolean entityPhysical) {
		this.entityPhysical = entityPhysical;
	}

	/**
	 * Returns the instance number of the entity (byte 10 bits 6:0 of the record).
	 *
	 * @return the instance number of the entity
	 */
	public byte getEntityInstanceNumber() {
		return entityInstanceNumber;
	}

	/**
	 * Sets the instance number of the entity.
	 *
	 * @param entityInstanceNumber
	 *        - the instance number of the entity
	 */
	public void setEntityInstanceNumber(byte entityInstanceNumber) {
		this.entityInstanceNumber = entityInstanceNumber;
	}

	/**
	 * Tells whether the hysteresis of the sensor can be read with Get Sensor Hysteresis (byte 12 bits 5:4 of a Full or
	 * Compact record).
	 *
	 * @return whether the hysteresis of the sensor can be read (always <code>false</code> for Event-Only records)
	 */
	public boolean isHysteresisReadable() {
		return hysteresisReadable;
	}

	/**
	 * Sets whether the hysteresis of the sensor can be read.
	 *
	 * @param hysteresisReadable
	 *        - whether the hysteresis of the sensor can be read
	 */
	public void setHysteresisReadable(boolean hysteresisReadable) {
		this.hysteresisReadable = hysteresisReadable;
	}

	/**
	 * Tells whether the thresholds of the sensor can be read with Get Sensor Thresholds (byte 12 bits 3:2 of a Full or
	 * Compact record).
	 *
	 * @return whether the thresholds of the sensor can be read (always <code>false</code> for Event-Only records)
	 */
	public boolean isThresholdsReadable() {
		return thresholdsReadable;
	}

	/**
	 * Sets whether the thresholds of the sensor can be read.
	 *
	 * @param thresholdsReadable
	 *        - whether the thresholds of the sensor can be read
	 */
	public void setThresholdsReadable(boolean thresholdsReadable) {
		this.thresholdsReadable = thresholdsReadable;
	}

	/**
	 * Returns the type of the sensor (IPMI 2.0 table 42-3).
	 *
	 * @return the sensor type
	 */
	public SensorType getSensorType() {
		return sensorType;
	}

	/**
	 * Sets the type of the sensor.
	 *
	 * @param sensorType
	 *        - the sensor type
	 */
	public void setSensorType(SensorType sensorType) {
		this.sensorType = sensorType;
	}

	/**
	 * Returns the sensor type code of the record (IPMI 2.0 table 42-3), which {@link #getSensorType()} does not keep
	 * for an OEM (C0h to FFh) or reserved code.
	 *
	 * @return the sensor type code, 0 to 255
	 */
	public int getSensorTypeCode() {
		return sensorTypeCode;
	}

	/**
	 * Sets the sensor type code of the record.
	 *
	 * @param sensorTypeCode
	 *        - the sensor type code, 0 to 255
	 */
	public void setSensorTypeCode(int sensorTypeCode) {
		this.sensorTypeCode = sensorTypeCode;
	}

	/**
	 * Tells whether the reading and the thresholds of the sensor are a percentage (Sensor Units 1 bit 0 of a Full or
	 * Compact record, IPMI 2.0 tables 43-1 and 43-2), of the base unit when the record gives one.
	 *
	 * @return whether the reading is a percentage (always <code>false</code> for Event-Only records)
	 */
	public boolean isPercentage() {
		return percentage;
	}

	/**
	 * Sets whether the reading and the thresholds of the sensor are a percentage.
	 *
	 * @param percentage
	 *        - whether the reading is a percentage
	 */
	public void setPercentage(boolean percentage) {
		this.percentage = percentage;
	}

	/**
	 * Returns the event/reading type code of the sensor: 01h for a threshold sensor, 02h-0Ch for a generic discrete
	 * one, 6Fh for a sensor-specific one, 70h-7Fh for an OEM one.
	 *
	 * @return the event/reading type code (IPMI 2.0 table 42-1)
	 */
	public int getEventReadingType() {
		return eventReadingType;
	}

	/**
	 * Sets the event/reading type code of the sensor.
	 *
	 * @param eventReadingType
	 *        - the event/reading type code (IPMI 2.0 table 42-1)
	 */
	public void setEventReadingType(int eventReadingType) {
		this.eventReadingType = eventReadingType;
	}

	/**
	 * Returns the rate unit of the reading (Sensor Units 1 bits 5:3).
	 *
	 * @return the rate unit of the reading (<code>null</code> for Event-Only records)
	 */
	public RateUnit getRateUnit() {
		return rateUnit;
	}

	/**
	 * Sets the rate unit of the reading.
	 *
	 * @param rateUnit
	 *        - the rate unit of the reading
	 */
	public void setRateUnit(RateUnit rateUnit) {
		this.rateUnit = rateUnit;
	}

	/**
	 * Returns how the modifier unit combines with the base unit (Sensor Units 1 bits 2:1): none, divided by or
	 * multiplied by.
	 *
	 * @return how the modifier unit combines with the base unit (<code>null</code> for Event-Only records)
	 */
	public ModifierUnitUsage getModifierUnitUsage() {
		return modifierUnitUsage;
	}

	/**
	 * Sets how the modifier unit combines with the base unit.
	 *
	 * @param modifierUnitUsage
	 *        - how the modifier unit combines with the base unit
	 */
	public void setModifierUnitUsage(ModifierUnitUsage modifierUnitUsage) {
		this.modifierUnitUsage = modifierUnitUsage;
	}

	/**
	 * Returns the base unit of the reading (Sensor Units 2, IPMI 2.0 table 43-15).
	 *
	 * @return the base unit of the reading (<code>null</code> for Event-Only records)
	 */
	public SensorUnit getSensorBaseUnit() {
		return sensorBaseUnit;
	}

	/**
	 * Sets the base unit of the reading.
	 *
	 * @param sensorBaseUnit
	 *        - the base unit of the reading
	 */
	public void setSensorBaseUnit(SensorUnit sensorBaseUnit) {
		this.sensorBaseUnit = sensorBaseUnit;
	}

	/**
	 * Returns the modifier unit of the reading (Sensor Units 3, IPMI 2.0 table 43-15).
	 *
	 * @return the modifier unit of the reading (<code>null</code> for Event-Only records)
	 */
	public SensorUnit getSensorModifierUnit() {
		return sensorModifierUnit;
	}

	/**
	 * Sets the modifier unit of the reading.
	 *
	 * @param sensorModifierUnit
	 *        - the modifier unit of the reading
	 */
	public void setSensorModifierUnit(SensorUnit sensorModifierUnit) {
		this.sensorModifierUnit = sensorModifierUnit;
	}

	/**
	 * Returns the direction of the sensor: whether it monitors an input or an output of the entity.
	 *
	 * @return whether the sensor monitors an input or an output of the entity
	 */
	public SensorDirection getSensorDirection() {
		return sensorDirection;
	}

	/**
	 * Sets the direction of the sensor.
	 *
	 * @param sensorDirection
	 *        - whether the sensor monitors an input or an output of the entity
	 */
	public void setSensorDirection(SensorDirection sensorDirection) {
		this.sensorDirection = sensorDirection;
	}

	/**
	 * Returns the sensor ID string of the record. For a Compact or Event-Only record that several sensors share
	 * ({@link #getShareCount()}), it is the string to which each sensor appends its instance modifier.
	 *
	 * @return the sensor ID string, empty when the record has none
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the sensor ID string.
	 *
	 * @param name
	 *        - the sensor ID string, {@code null} for none (stored as the empty string)
	 */
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	/**
	 * The instance modifier is a character(s) that software can append to the end of the ID String. This field
	 * selects whether the appended character(s) will be numeric or alpha.
	 *
	 * @return the type of the ID string instance modifier (<code>null</code> for Full records)
	 */
	public InstanceModifierType getIdInstanceModifierType() {
		return idInstanceModifierType;
	}

	/**
	 * Sets the type of the ID string instance modifier.
	 *
	 * @param idInstanceModifierType
	 *        - the type of the ID string instance modifier
	 */
	public void setIdInstanceModifierType(InstanceModifierType idInstanceModifierType) {
		this.idInstanceModifierType = idInstanceModifierType;
	}

	/**
	 * Sensor numbers sharing this record are sequential starting with the sensor number specified by the Sensor
	 * Number field for this record.
	 *
	 * @return the number of sensors sharing this record (0 for Full records)
	 */
	public int getShareCount() {
		return shareCount;
	}

	/**
	 * Sets the number of sensors sharing this record.
	 *
	 * @param shareCount
	 *        - the number of sensors sharing this record
	 */
	public void setShareCount(int shareCount) {
		this.shareCount = shareCount;
	}

	/**
	 * Tells whether the entity instance number increments for each sensor sharing this record, rather than being the
	 * same for all of them.
	 *
	 * @return whether the entity instance number increments for each sensor sharing this record
	 */
	public boolean isEntityInstanceIncrements() {
		return entityInstanceIncrements;
	}

	/**
	 * Sets whether the entity instance number increments for each sensor sharing this record.
	 *
	 * @param entityInstanceIncrements
	 *        - whether the entity instance number increments for each sensor sharing this record
	 */
	public void setEntityInstanceIncrements(boolean entityInstanceIncrements) {
		this.entityInstanceIncrements = entityInstanceIncrements;
	}

	/**
	 * Suppose sensor ID is 'Temp' for 'Temperature Sensor', share count = 3, ID string instance modifier = numeric,
	 * instance modifier offset = 5 - then the sensors could be identified as: Temp 5, Temp 6, Temp 7 <br>
	 * If the modifier = alpha, offset=0 corresponds to 'A', offset=25 corresponds to 'Z', and offset = 26 corresponds
	 * to 'AA', thus, for offset=26 the sensors could be identified as: Temp AA, Temp AB, Temp AC
	 *
	 * @return the offset of the ID string instance modifier
	 */
	public int getIdInstanceModifierOffset() {
		return idInstanceModifierOffset;
	}

	/**
	 * Sets the offset of the ID string instance modifier.
	 *
	 * @param idInstanceModifierOffset
	 *        - the offset of the ID string instance modifier
	 */
	public void setIdInstanceModifierOffset(int idInstanceModifierOffset) {
		this.idInstanceModifierOffset = idInstanceModifierOffset;
	}
}
