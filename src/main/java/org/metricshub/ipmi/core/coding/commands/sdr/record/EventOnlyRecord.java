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

/**
 * This record provides a mechanism to associate FRU and Entity information with
 * a physical or logical sensor that generates events, but cannot otherwise be
 * accessed. This is typical of software-generated events, such as events
 * generated by BIOS.
 */
public class EventOnlyRecord extends SensorRecord {

    private byte sensorOwnerId;

    private AddressType addressType;

    private byte channelNumber;

    private byte sensorOwnerLun;

    private byte sensorNumber;

    private EntityId entityId;

    /**
     * Entity is physical if true, logical otherwise.
     */
    private boolean entityPhysical;

    private byte entityInstanceNumber;

    private SensorType sensorType;

    private int eventReadingType;

    private SensorDirection sensorDirection;

    private String name;

    /**
     * The instance modifier is a character(s) that software can append to the
     * end of the ID String. This field selects whether the appended
     * character(s) will be numeric or alpha.
     */
    private InstanceModifierType idInstanceModifierType;

    /**
     * Sensor numbers sharing this record are sequential starting with the
     * sensor number specified by the Sensor Number field for this record.
     */
    private int shareCount;

    private boolean entityInstanceIncrements;

    /**
     * Suppose sensor ID is 'Temp' for 'Temperature Sensor', share count = 3, ID
     * string instance modifier = numeric, instance modifier offset = 5 - then
     * the sensors could be identified as: Temp 5, Temp 6, Temp 7 <br>
     * If the modifier = alpha, offset=0 corresponds to 'A', offset=25
     * corresponds to 'Z', and offset = 26 corresponds to 'AA', thus, for
     * offset=26 the sensors could be identified as: Temp AA, Temp AB, Temp AC
     */
    private int idInstanceModifierOffset;


    @Override
    protected void populateTypeSpecficValues(byte[] recordData,
            SensorRecord record) {

        setSensorOwnerId(TypeConverter.intToByte((TypeConverter
                .byteToInt(recordData[5]) & 0xfe) >> 1));

        setAddressType(AddressType.parseInt(TypeConverter
                .byteToInt(recordData[5]) & 0x01));

        setChannelNumber(TypeConverter.intToByte((TypeConverter
                .byteToInt(recordData[6]) & 0xf0) >> 4));

        setSensorOwnerLun(TypeConverter.intToByte(TypeConverter
                .byteToInt(recordData[6]) & 0x3));

        setSensorNumber(recordData[7]);

        setEntityId(EntityId.parseInt(TypeConverter.byteToInt(recordData[8])));

        setEntityPhysical((TypeConverter.byteToInt(recordData[9]) & 0x80) == 0);

        setEntityInstanceNumber(TypeConverter.intToByte(TypeConverter
                .byteToInt(recordData[9]) & 0x7f));

        setSensorType(SensorType.parseInt(TypeConverter
                .byteToInt(recordData[10])));

        setEventReadingType(TypeConverter.byteToInt(recordData[11]));

        setSensorDirection(SensorDirection.parseInt((TypeConverter
                .byteToInt(recordData[12]) & 0xc0) >> 6));

        setIdInstanceModifierType(InstanceModifierType.parseInt((TypeConverter
                .byteToInt(recordData[12]) & 0x30) >> 4));

        setShareCount(TypeConverter.byteToInt(recordData[12]) & 0xf);

        setEntityInstanceIncrements((TypeConverter.byteToInt(recordData[13]) & 0x80) != 0);

        setIdInstanceModifierOffset(TypeConverter.byteToInt(recordData[13]) & 0x7f);

        byte[] nameData = new byte[recordData.length - 17];

        System.arraycopy(recordData, 17, nameData, 0, nameData.length);

        setName(decodeName(recordData[16], nameData));

    }


    public byte getSensorOwnerId() {
        return sensorOwnerId;
    }


    public void setSensorOwnerId(byte sensorOwnerId) {
        this.sensorOwnerId = sensorOwnerId;
    }


    public AddressType getAddressType() {
        return addressType;
    }


    public void setAddressType(AddressType addressType) {
        this.addressType = addressType;
    }


    public byte getChannelNumber() {
        return channelNumber;
    }


    public void setChannelNumber(byte channelNumber) {
        this.channelNumber = channelNumber;
    }


    public byte getSensorOwnerLun() {
        return sensorOwnerLun;
    }


    public void setSensorOwnerLun(byte sensorOwnerLun) {
        this.sensorOwnerLun = sensorOwnerLun;
    }


    public byte getSensorNumber() {
        return sensorNumber;
    }


    public void setSensorNumber(byte sensorNumber) {
        this.sensorNumber = sensorNumber;
    }


    public EntityId getEntityId() {
        return entityId;
    }


    public void setEntityId(EntityId entityId) {
        this.entityId = entityId;
    }


    public boolean isEntityPhysical() {
        return entityPhysical;
    }


    public void setEntityPhysical(boolean entityPhysical) {
        this.entityPhysical = entityPhysical;
    }


    public byte getEntityInstanceNumber() {
        return entityInstanceNumber;
    }


    public void setEntityInstanceNumber(byte entityInstanceNumber) {
        this.entityInstanceNumber = entityInstanceNumber;
    }


    public SensorType getSensorType() {
        return sensorType;
    }


    public void setSensorType(SensorType sensorType) {
        this.sensorType = sensorType;
    }


    public int getEventReadingType() {
        return eventReadingType;
    }


    public void setEventReadingType(int eventReadingType) {
        this.eventReadingType = eventReadingType;
    }


    public SensorDirection getSensorDirection() {
        return sensorDirection;
    }


    public void setSensorDirection(SensorDirection sensorDirection) {
        this.sensorDirection = sensorDirection;
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }


    public InstanceModifierType getIdInstanceModifierType() {
        return idInstanceModifierType;
    }


    public void setIdInstanceModifierType(
            InstanceModifierType idInstanceModifierType) {
        this.idInstanceModifierType = idInstanceModifierType;
    }


    public int getShareCount() {
        return shareCount;
    }


    public void setShareCount(int shareCount) {
        this.shareCount = shareCount;
    }


    public boolean isEntityInstanceIncrements() {
        return entityInstanceIncrements;
    }


    public void setEntityInstanceIncrements(boolean entityInstanceIncrements) {
        this.entityInstanceIncrements = entityInstanceIncrements;
    }


    public int getIdInstanceModifierOffset() {
        return idInstanceModifierOffset;
    }


    public void setIdInstanceModifierOffset(int idInstanceModifierOffset) {
        this.idInstanceModifierOffset = idInstanceModifierOffset;
    }

}
