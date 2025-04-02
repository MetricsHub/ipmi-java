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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identifies type of the information stored in FRU's MultiRecord Area entry.
 */
public enum FruMultiRecordType {
    PowerSupplyInformation(FruMultiRecordType.POWERSUPPLYINFORMATION), DcOutput(FruMultiRecordType.DCOUTPUT), OemRecord(
            FruMultiRecordType.OEMRECORD), DcLoad(FruMultiRecordType.DCLOAD), ManagementAccessRecord(
            FruMultiRecordType.MANAGEMENTACCESSRECORD), BaseCompatibilityRecord(
            FruMultiRecordType.BASECOMPATIBILITYRECORD), ExtendedCompatibilityRecord(
            FruMultiRecordType.EXTENDEDCOMPATIBILITYRECORD), Unspecified(FruMultiRecordType.UNSPECIFIED), ;
    private static final int POWERSUPPLYINFORMATION = 0;

    private static final int DCOUTPUT = 1;

    private static final int OEMRECORD = 192;

    private static final int DCLOAD = 2;

    private static final int MANAGEMENTACCESSRECORD = 3;

    private static final int BASECOMPATIBILITYRECORD = 4;

    private static final int EXTENDEDCOMPATIBILITYRECORD = 5;

    private static final int UNSPECIFIED = -1;

    private int code;

    private static Logger logger = LoggerFactory.getLogger(FruMultiRecordType.class);

    FruMultiRecordType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static FruMultiRecordType parseInt(int value) {
        if (value >= OEMRECORD) {
            return OemRecord;
        }
        switch (value) {
        case POWERSUPPLYINFORMATION:
            return PowerSupplyInformation;
        case DCOUTPUT:
            return DcOutput;
        case DCLOAD:
            return DcLoad;
        case MANAGEMENTACCESSRECORD:
            return ManagementAccessRecord;
        case BASECOMPATIBILITYRECORD:
            return BaseCompatibilityRecord;
        case EXTENDEDCOMPATIBILITYRECORD:
            return ExtendedCompatibilityRecord;
        default:
            logger.error("Invalid value: " + value);
            return Unspecified;
        }
    }
}
