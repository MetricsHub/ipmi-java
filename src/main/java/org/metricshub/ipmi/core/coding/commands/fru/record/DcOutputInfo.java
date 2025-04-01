package org.metricshub.ipmi.core.coding.commands.fru.record;

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
 * DC output Information record from FRU Multi Record Area
 */
public class DcOutputInfo extends MultiRecordInfo {

    private int outputNumber;

    /**
     * The unit is 10mV.
     */
    private int nominalVoltage;

    /**
     * The unit is 10mV.
     */
    private int maximumNegativeDeviation;

    /**
     * The unit is 10mV.
     */
    private int maximumPositiveDeviation;

    /**
     * Creates and populates record
     *
     * @param fruData
     *            - raw data containing record
     * @param offset
     *            - offset to the record in the data
     */
    public DcOutputInfo(byte[] fruData, int offset) {
        super();
        // TODO: Test when server containing such records will be available

        outputNumber = TypeConverter.byteToInt(fruData[offset]) & 0xf;

        nominalVoltage = TypeConverter.byteToInt(fruData[offset+1]);
        nominalVoltage |= TypeConverter.byteToInt(fruData[offset+2]) << 8;
        nominalVoltage = TypeConverter.decode2sComplement(nominalVoltage, 15);

        maximumNegativeDeviation = TypeConverter.byteToInt(fruData[offset+3]);
        maximumNegativeDeviation |= TypeConverter.byteToInt(fruData[offset+4]) << 8;
        maximumNegativeDeviation = TypeConverter.decode2sComplement(maximumNegativeDeviation, 15);

        maximumPositiveDeviation = TypeConverter.byteToInt(fruData[offset+5]);
        maximumPositiveDeviation |= TypeConverter.byteToInt(fruData[offset+6]) << 8;
        maximumPositiveDeviation = TypeConverter.decode2sComplement(maximumPositiveDeviation, 15);
    }

    public int getOutputNumber() {
        return outputNumber;
    }

    public void setOutputNumber(int outputNumber) {
        this.outputNumber = outputNumber;
    }

    public int getNominalVoltage() {
        return nominalVoltage;
    }

    public void setNominalVoltage(int nominalVoltage) {
        this.nominalVoltage = nominalVoltage;
    }

    public int getMaximumNegativeDeviation() {
        return maximumNegativeDeviation;
    }

    public void setMaximumNegativeDeviation(int maximumNegativeDeviation) {
        this.maximumNegativeDeviation = maximumNegativeDeviation;
    }

    public int getMaximumPositiveDeviation() {
        return maximumPositiveDeviation;
    }

    public void setMaximumPositiveDeviation(int maximumPositiveDeviation) {
        this.maximumPositiveDeviation = maximumPositiveDeviation;
    }

}
