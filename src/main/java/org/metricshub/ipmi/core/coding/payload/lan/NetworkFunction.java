package org.metricshub.ipmi.core.coding.payload.lan;

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

/**
 * IPMB network functions
 */
public enum NetworkFunction {
    ChassisRequest(NetworkFunction.CHASSISREQUEST), ChassisResponse(
            NetworkFunction.CHASSISRESPONSE), StorageRequest(
            NetworkFunction.STORAGEREQUEST), StorageResponse(
            NetworkFunction.STORAGERESPONSE), BridgeRequest(
            NetworkFunction.BRIDGEREQUEST), BridgeResponse(
            NetworkFunction.BRIDGERESPONSE),
    /**
     * Sensor/Event Request
     */
    SensorRequest(NetworkFunction.SENSORREQUEST),
    /**
     * Sensor/Event Response
     */
    SensorResponse(NetworkFunction.SENSORRESPONSE), ApplicationRequest(
            NetworkFunction.APPLICATIONREQUEST), ApplicationResponse(
            NetworkFunction.APPLICATIONRESPONSE), FirmwareRequest(
            NetworkFunction.FIRMWAREREQUEST), FirmwareResponse(
            NetworkFunction.FIRMWARERESPONSE), ;
    private static final int CHASSISREQUEST = 0;
    private static final int CHASSISRESPONSE = 1;
    private static final int STORAGEREQUEST = 10;
    private static final int STORAGERESPONSE = 11;
    private static final int BRIDGEREQUEST = 2;
    private static final int BRIDGERESPONSE = 3;
    private static final int SENSORREQUEST = 4;
    private static final int SENSORRESPONSE = 5;
    private static final int APPLICATIONREQUEST = 6;
    private static final int APPLICATIONRESPONSE = 7;
    private static final int FIRMWAREREQUEST = 8;
    private static final int FIRMWARERESPONSE = 9;

    private int code;

    NetworkFunction(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static NetworkFunction parseInt(int value) {
        switch (value) {
        case CHASSISREQUEST:
            return ChassisRequest;
        case CHASSISRESPONSE:
            return ChassisResponse;
        case STORAGEREQUEST:
            return StorageRequest;
        case STORAGERESPONSE:
            return StorageResponse;
        case BRIDGEREQUEST:
            return BridgeRequest;
        case BRIDGERESPONSE:
            return BridgeResponse;
        case SENSORREQUEST:
            return SensorRequest;
        case SENSORRESPONSE:
            return SensorResponse;
        case APPLICATIONREQUEST:
            return ApplicationRequest;
        case APPLICATIONRESPONSE:
            return ApplicationResponse;
        case FIRMWAREREQUEST:
            return FirmwareRequest;
        case FIRMWARERESPONSE:
            return FirmwareResponse;
        default:
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
}
