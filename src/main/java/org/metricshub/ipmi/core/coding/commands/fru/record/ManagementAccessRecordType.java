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

public enum ManagementAccessRecordType {
    SystemManagementUrl(ManagementAccessRecordType.SYSTEMMANAGEMENTURL), SystemName(
            ManagementAccessRecordType.SYSTEMNAME), SystemPingAddress(ManagementAccessRecordType.SYSTEMPINGADDRESS), ComponentManagementURL(
            ManagementAccessRecordType.COMPONENTMANAGEMENTURL), Unspecified(ManagementAccessRecordType.UNSPECIFIED), ;
    private static final int SYSTEMMANAGEMENTURL = 1;

    private static final int SYSTEMNAME = 2;

    private static final int SYSTEMPINGADDRESS = 3;

    private static final int COMPONENTMANAGEMENTURL = 4;

    private static final int UNSPECIFIED = 0;

    private static Logger logger = LoggerFactory.getLogger(ManagementAccessRecordType.class);

    private int code;

    ManagementAccessRecordType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ManagementAccessRecordType parseInt(int value) {
        switch (value) {
        case SYSTEMMANAGEMENTURL:
            return SystemManagementUrl;
        case SYSTEMNAME:
            return SystemName;
        case SYSTEMPINGADDRESS:
            return SystemPingAddress;
        case COMPONENTMANAGEMENTURL:
            return ComponentManagementURL;
        default:
            logger.error("Invalid value: " + value);
            return Unspecified;
        }
    }
}
