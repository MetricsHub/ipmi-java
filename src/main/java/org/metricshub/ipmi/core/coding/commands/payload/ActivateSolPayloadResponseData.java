package org.metricshub.ipmi.core.coding.commands.payload;

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
 * Concrete implementation of {@link ActivatePayloadResponseData} for {@link org.metricshub.ipmi.core.coding.protocol.PayloadType#SOL}.
 */
public class ActivateSolPayloadResponseData extends ActivatePayloadResponseData {

    /**
     * Information whether test mode is enabled or not.
     */
    private boolean testMode;

    @Override
    public void setAuxilaryInformationData(byte[] auxilaryInformationData) {
        if (auxilaryInformationData == null || auxilaryInformationData.length < 1 || auxilaryInformationData.length > 4) {
            throw new IllegalArgumentException("Auxilary information data must consists of 1 to 4 bytes");
        }

        this.testMode = TypeConverter.isBitSetOnPosition(0, auxilaryInformationData[0]);
    }

    /**
     * 
     * @return true if the test mode is enabled otherwise false
     */
    public boolean isTestMode() {
        return testMode;
    }
}
