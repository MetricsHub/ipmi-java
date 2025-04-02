package org.metricshub.ipmi.core.coding;

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

import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.BaseUnit;
import org.metricshub.ipmi.core.coding.commands.fru.GetFruInventoryAreaInfo;
import org.metricshub.ipmi.core.coding.commands.fru.GetFruInventoryAreaInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.ReadFruData;
import org.metricshub.ipmi.core.coding.commands.fru.ReadFruDataResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ChassisInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdr;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrRepositoryInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrRepositoryInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdrResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReading;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepository;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepositoryResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.RateUnit;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelEntry;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelEntryResponseData;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelInfo;
import org.metricshub.ipmi.core.coding.commands.sel.GetSelInfoResponseData;
import org.metricshub.ipmi.core.coding.commands.sel.ReserveSel;
import org.metricshub.ipmi.core.coding.commands.sel.ReserveSelResponseData;
import org.metricshub.ipmi.core.coding.commands.sel.SelRecord;
import org.metricshub.ipmi.core.coding.commands.session.CloseSession;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilitiesResponseData;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelCipherSuites;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelCipherSuitesResponseData;
import org.metricshub.ipmi.core.coding.commands.session.OpenSession;
import org.metricshub.ipmi.core.coding.commands.session.OpenSessionResponseData;
import org.metricshub.ipmi.core.coding.commands.session.Rakp1;
import org.metricshub.ipmi.core.coding.commands.session.Rakp1ResponseData;
import org.metricshub.ipmi.core.coding.commands.session.Rakp3;
import org.metricshub.ipmi.core.coding.commands.session.Rakp3ResponseData;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.decoder.PlainCommandv20Decoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv15Decoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv20Decoder;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv15Encoder;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.SecurityConstants;
import org.metricshub.ipmi.core.common.TypeConverter;

import javax.crypto.NoSuchPaddingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Test driver for Encoder/Decoder
 */
public class DecoderRunner extends Thread {

    private DatagramSocket socket;

    private static int managedSeqNum;
    private static boolean lock;

    private static Rakp1 r1;
    private static Rakp1ResponseData r1rd;
    private static CipherSuite cs = new CipherSuite((byte) 0,
            SecurityConstants.AA_RAKP_HMAC_SHA1, (byte) 0, (byte) 0);

    private static Logger logger = LoggerFactory.getLogger(DecoderRunner.class);

    private static int cssrcv = 16;

    private static int reservation;

    private static int nextRecId = 0;

    private static byte[] cssrec;

    private static int fruId = 0;

    private static int fruSize = 528;

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

        logger.info(DateFormat.getInstance().format(
                new Date(new Date().getTime())));

        lock = true;

        DecoderRunner dr = new DecoderRunner();

        dr.socket = new DatagramSocket(6666);

        dr.start();

        Properties properties = new Properties();
        properties.load(new FileInputStream("src/test/resources/test.properties"));

        Thread.sleep(100);

        InetAddress ad = InetAddress.getByName((String)properties.get("testIp"));

        byte index = 0;

        while (cssrcv >= 16) {

            Thread.sleep(300);

            lock = true;

            byte[] outmsg = Encoder.encode(new Protocolv20Encoder(),
                    new GetChannelCipherSuites(TypeConverter.intToByte(0xE),
                            index), 0, 0,0);

            ++index;
            DatagramPacket packet = new DatagramPacket(outmsg, outmsg.length,
                    ad, 0x26F);

            dr.socket.send(packet);

            while (lock) {
                Thread.sleep(1);
            }
        }

        List<CipherSuite> csl = CipherSuite.getCipherSuites(cssrec);

        for (CipherSuite c : csl) {
            try {
                logger.info(c.getId() + ": "
                        + c.getAuthenticationAlgorithm().getCode() + " "
                        + c.getIntegrityAlgorithm().getCode() + " "
                        + c.getConfidentialityAlgorithm().getCode());
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

        cs = csl.get(2);

        Thread.sleep(300);

        byte[] outmsg = Encoder
                .encode(new Protocolv15Encoder(),
                        new GetChannelAuthenticationCapabilities(IpmiVersion.V15,
                                IpmiVersion.V20, cs, PrivilegeLevel.User,
                                TypeConverter.intToByte(14)), 0, 0, 0);

        DatagramPacket packet = new DatagramPacket(outmsg, outmsg.length, ad,
                0x26F);

        dr.socket.send(packet);

        Thread.sleep(150);

        outmsg = Encoder.encode(new Protocolv20Encoder(), new OpenSession(44,
                PrivilegeLevel.MaximumAvailable, cs), 0, 0, 0);

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        Thread.sleep(300);

        while (lock) {
            Thread.sleep(1);
        }

        lock = true;


        r1 = new Rakp1(managedSeqNum, PrivilegeLevel.User, (String)properties.get("username"), (String)properties.get("password"),
                null, cs);

        outmsg = Encoder.encode(new Protocolv20Encoder(), r1, 1, 1,0);

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        Thread.sleep(150);

        while (lock) {
            Thread.sleep(1);
        }

        try {
            cs.initializeAlgorithms(r1.calculateSik(r1rd));
        } catch (NoSuchPaddingException e) {
            logger.error(e.getMessage(), e);
        }

        outmsg = Encoder.encode(new Protocolv20Encoder(), new Rakp3((byte) 0,
                managedSeqNum, cs, r1, r1rd), 1, 1, 0);

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        Thread.sleep(150);

        outmsg = Encoder.encode(new Protocolv20Encoder(), new GetChassisStatus(
                IpmiVersion.V20, cs, AuthenticationType.RMCPPlus), 1, 1, r1
                .getManagedSystemSessionId());

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        Thread.sleep(300);

        outmsg = Encoder.encode(new Protocolv20Encoder(),
                new GetSdrRepositoryInfo(IpmiVersion.V20, cs,
                        AuthenticationType.RMCPPlus), 2, 2, r1
                        .getManagedSystemSessionId());

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);
        Thread.sleep(300);

        outmsg = Encoder.encode(new Protocolv20Encoder(),
                new ReserveSdrRepository(IpmiVersion.V20, cs,
                        AuthenticationType.RMCPPlus), 3, 3, r1
                        .getManagedSystemSessionId());

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        int seq = 4;

        lock = true;

        while (lock) {
            Thread.sleep(1);
        }

        while (nextRecId < 65535) {

            Thread.sleep(200);

            logger.info(">>Sending request for record " + nextRecId);

            int sequence = seq++;

            outmsg = Encoder.encode(new Protocolv20Encoder(), new GetSdr(
                    IpmiVersion.V20, cs, AuthenticationType.RMCPPlus,
                    reservation, nextRecId), sequence, sequence, r1
                    .getManagedSystemSessionId());

            packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

            dr.socket.send(packet);
            lock = true;

            while (lock) {
                Thread.sleep(1);
            }

            if (nextRecId > 0) {
                logger.info(">>Sending request for reading " + nextRecId);

                sequence = seq++;

                outmsg = Encoder.encode(new Protocolv20Encoder(),
                        new GetSensorReading(IpmiVersion.V20, cs,
                                AuthenticationType.RMCPPlus, nextRecId), sequence, sequence,
                        r1.getManagedSystemSessionId());

                packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

                dr.socket.send(packet);

                lock = true;

                while (lock && nextRecId < 65535) {
                    Thread.sleep(1);
                }
            }

        }

         nextRecId = 0;

        Thread.sleep(300);

        logger.info(">>Sending GetSelInfo");

        int sequence = seq++;

        outmsg = Encoder.encode(new Protocolv20Encoder(), new GetSelInfo(
                IpmiVersion.V20, cs, AuthenticationType.RMCPPlus), sequence, sequence, r1
                .getManagedSystemSessionId());

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        Thread.sleep(300);

        logger.info(">>Sending Reserve SEL");

        sequence = seq++;

        outmsg = Encoder.encode(new Protocolv20Encoder(), new ReserveSel(
                IpmiVersion.V20, cs, AuthenticationType.RMCPPlus), sequence, sequence, r1
                .getManagedSystemSessionId());

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        lock = true;

        while (lock) {
            Thread.sleep(1);
        }

        while (nextRecId < 65535) {

            Thread.sleep(200);

            logger.info(">>Sending request for SEL record " + nextRecId);

            sequence = seq++;

            outmsg = Encoder.encode(new Protocolv20Encoder(), new GetSelEntry(
                    IpmiVersion.V20, cs, AuthenticationType.RMCPPlus,
                    reservation, nextRecId), sequence, sequence, r1
                    .getManagedSystemSessionId());

            packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

            dr.socket.send(packet);
            lock = true;

            while (lock) {
                Thread.sleep(1);
            }
        }

        Thread.sleep(300);

        logger.info(">>Sending GetFruInventoryAreaInfo");

        sequence = seq++;

        outmsg = Encoder.encode(new Protocolv20Encoder(),
                new GetFruInventoryAreaInfo(IpmiVersion.V20, cs,
                        AuthenticationType.RMCPPlus, fruId), sequence, sequence, r1
                        .getManagedSystemSessionId());

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        for(int i = 0; i < fruSize; i += 100) {


            Thread.sleep(300);

            logger.info(">>Sending ReadFruData");

            int cnt = 100;
            if(i + cnt > fruSize) {
                cnt = fruSize % 100;
            }

            sequence = seq++;

            outmsg = Encoder.encode(new Protocolv20Encoder(),
                    new ReadFruData(IpmiVersion.V20, cs,
                            AuthenticationType.RMCPPlus, fruId, BaseUnit.Bytes, i, cnt), sequence, sequence, r1
                            .getManagedSystemSessionId());

            packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

            dr.socket.send(packet);

        }

        Thread.sleep(300);

        sequence = seq + 1;

        outmsg = Encoder.encode(
                new Protocolv20Encoder(),
                new CloseSession(IpmiVersion.V20, cs,
                        AuthenticationType.RMCPPlus, r1
                                .getManagedSystemSessionId()), sequence, sequence, r1
                        .getManagedSystemSessionId());

        packet = new DatagramPacket(outmsg, outmsg.length, ad, 0x26F);

        dr.socket.send(packet);

        Thread.sleep(1000);

        dr.socket.close();

    }

    @Override
    public void run() {

        super.run();

        cssrec = new byte[0];

        byte[] buffer = null;

        while (cssrcv >= 16) {
            DatagramPacket resp = new DatagramPacket(new byte[256], 256);

            try {
                socket.receive(resp);
                buffer = new byte[resp.getLength()];
                System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }

            GetChannelCipherSuitesResponseData data = null;

            try {
                data = (GetChannelCipherSuitesResponseData) Decoder.decode(
                        buffer, new Protocolv20Decoder(CipherSuite.getEmpty()),
                        new GetChannelCipherSuites());
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }

            if (data != null && data.getCipherSuiteData() != null) {
                cssrcv = data.getCipherSuiteData().length;

                logger.info("{}", data.getCipherSuiteData().length);

                byte[] temp = new byte[cssrec.length + cssrcv];

                System.arraycopy(cssrec, 0, temp, 0, cssrec.length);
                System.arraycopy(data.getCipherSuiteData(), 0, temp,
                        cssrec.length, cssrcv);
                cssrec = temp;

            } else {
                cssrcv = 0;
                logger.info("{}", 0);
            }

            lock = false;
        }

        DatagramPacket resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        GetChannelAuthenticationCapabilitiesResponseData data = null;

        try {
            data = (GetChannelAuthenticationCapabilitiesResponseData) Decoder
                    .decode(buffer, new Protocolv15Decoder(),
                            new GetChannelAuthenticationCapabilities(
                                    IpmiVersion.V15, IpmiVersion.V20, cs));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("---------------------------------------------");

        logger.info("{}", data.getChannelNumber());
        logger.info("{}", data.isIpmiv20Support());
        logger.info("{}", data.getAuthenticationTypes().toString());
        logger.info("{}", data.isKgEnabled());
        logger.info("{}", data.isPerMessageAuthenticationEnabled());
        logger.info("{}", data.isUserLevelAuthenticationEnabled());
        logger.info("{}", data.isNonNullUsernamesEnabled());
        logger.info("{}", data.isNullUsernamesEnabled());
        logger.info("{}", data.isAnonymusLoginEnabled());
        logger.info("{}", data.getOemId());
        logger.info("{}", data.getOemData());

        logger.info("##############################################");

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
            logger.info(">>>> " + resp.getLength());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        OpenSessionResponseData data2 = null;

        try {
            data2 = (OpenSessionResponseData) Decoder.decode(buffer,
                    new PlainCommandv20Decoder(CipherSuite.getEmpty()),
                    new OpenSession(CipherSuite.getEmpty()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("{}", data2.getMessageTag());
        logger.info("{}", data2.getStatusCode());
        logger.info("{}", data2.getPrivilegeLevel());
        logger.info("{}", data2.getRemoteConsoleSessionId());
        logger.info("{}", data2.getManagedSystemSessionId());
        logger.info("{}", data2.getAuthenticationAlgorithm());
        logger.info("{}", data2.getConfidentialityAlgorithm());
        logger.info("{}", data2.getIntegrityAlgorithm());

        managedSeqNum = data2.getManagedSystemSessionId();
        lock = false;

        logger.info("---------------------------------------------");

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        Rakp1ResponseData data3 = null;

        try {
            data3 = (Rakp1ResponseData) Decoder.decode(buffer,
                    new PlainCommandv20Decoder(CipherSuite.getEmpty()), r1);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        r1rd = data3;

        logger.info("{}", data3.getMessageTag());
        logger.info("{}", data3.getStatusCode());
        logger.info("{}", data3.getRemoteConsoleSessionId());
        logger.info("{}", data3.getManagedSystemGuid());

        logger.info("---------------------------------------------");
        lock = false;

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        Rakp3ResponseData data4 = null;

        try {
            data4 = (Rakp3ResponseData) Decoder.decode(buffer,
                    new PlainCommandv20Decoder(CipherSuite.getEmpty()),
                    new Rakp3(cs, r1, r1rd));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("{}", data4.getMessageTag());
        logger.info("{}", data4.getStatusCode());
        logger.info("{}", data4.getConsoleSessionId());

        logger.info("---------------------------------------------");

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        GetChassisStatusResponseData data5 = null;

        try {
            data5 = (GetChassisStatusResponseData) Decoder.decode(buffer,
                    new Protocolv20Decoder(cs), new GetChassisStatus(
                            IpmiVersion.V20, cs, AuthenticationType.RMCPPlus));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("{}", data5.getPowerRestorePolicy());
        logger.info("{}", data5.isPowerControlFault());
        logger.info("{}", data5.isPowerFault());
        logger.info("{}", data5.isInterlock());
        logger.info("{}", data5.isPowerOverload());
        logger.info("{}", data5.isPowerOn());

        logger.info("________");

        logger.info("{}", data5.wasIpmiPowerOn());
        logger.info("{}", data5.wasPowerFault());
        logger.info("{}", data5.wasInterlock());
        logger.info("{}", data5.wasPowerOverload());

        logger.info("________");

        logger.info("{}", data5.isChassisIdentifyCommandSupported());
        if (data5.isChassisIdentifyCommandSupported()) {
            logger.info("{}", data5.getChassisIdentifyState());
        }
        logger.info("{}", data5.coolingFaultDetected());
        logger.info("{}", data5.driveFaultDetected());
        logger.info("{}", data5.isFrontPanelLockoutActive());
        logger.info("{}", data5.isChassisIntrusionActive());

        logger.info("________");

        logger.info("{}", data5.isFrontPanelButtonCapabilitiesSet());

        if (data5.isFrontPanelButtonCapabilitiesSet()) {
            try {
                logger.info("{}", data5.isStandbyButtonDisableAllowed());
                logger.info("{}", data5
                        .isDiagnosticInterruptButtonDisableAllowed());
                logger.info("{}", data5.isResetButtonDisableAllowed());
                logger.info("{}", data5.isPowerOffButtonDisableAllowed());
                logger.info("{}", data5.isStandbyButtonDisabled());
                logger.info("{}", data5.isDiagnosticInterruptButtonDisabled());
                logger.info("{}", data5.isResetButtonDisabled());
                logger.info("{}", data5.isPowerOffButtonDisabled());
            } catch (IllegalAccessException e) {
                logger.error(e.getMessage(), e);
            }

        }

        logger.info("---------------------------------------------");

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        GetSdrRepositoryInfoResponseData data6 = null;

        try {
            data6 = (GetSdrRepositoryInfoResponseData) Decoder.decode(buffer,
                    new Protocolv20Decoder(cs), new GetSdrRepositoryInfo(
                            IpmiVersion.V20, cs, AuthenticationType.RMCPPlus));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("{}", data6.getSdrVersion());
        logger.info("{}", data6.getRecordCount());
        logger.info("{}", data6.getAddTimestamp());
        logger.info("{}", data6.getDelTimestamp());
        logger.info("{}", data6.isReserveSupported());

        logger.info("---------------------------------------------");

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        ReserveSdrRepositoryResponseData data7 = null;

        try {
            data7 = (ReserveSdrRepositoryResponseData) Decoder.decode(buffer,
                    new Protocolv20Decoder(cs), new ReserveSdrRepository(
                            IpmiVersion.V20, cs, AuthenticationType.RMCPPlus));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("{}", data7.getReservationId());

        reservation = data7.getReservationId();

        logger.info("<<Received ReserveSdrRepo response");

        lock = false;

        logger.info("---------------------------------------------");

        while (nextRecId < 65535) {

            resp = new DatagramPacket(new byte[256], 256);

            try {
                socket.receive(resp);
                buffer = new byte[resp.getLength()];
                System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            GetSdrResponseData data8 = null;

            try {
                data8 = (GetSdrResponseData) Decoder.decode(buffer,
                        new Protocolv20Decoder(cs), new GetSdr(IpmiVersion.V20,
                                cs, AuthenticationType.RMCPPlus, 0, 0));
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }

            SensorRecord record = SensorRecord.populateSensorRecord(data8.getSensorRecordData());
            logger.info(record.toString());

            if (record instanceof FullSensorRecord) {
                nextRecId = TypeConverter.byteToInt(((FullSensorRecord) record).getSensorNumber());
            } else if (record instanceof CompactSensorRecord) {
                nextRecId = TypeConverter
                        .byteToInt(((CompactSensorRecord) record).getSensorNumber());
            } else {
                nextRecId = -1;
            }

            logger.info("<<Reading Id " + nextRecId);

            if (record instanceof FullSensorRecord) {
                FullSensorRecord rec = (FullSensorRecord) record;
                logger.info("*" + rec.getName());
                logger.info("Reading type: " + rec.getEventReadingType());
                logger.info("Lower critical threshold: "
                        + rec.getLowerCriticalThreshold());
                logger.info("Upper critical threshold: "
                        + rec.getUpperCriticalThreshold());
                logger.info("Tolerance: +/- "
                        + rec.getTolerance()
                        + " "
                        + rec.getSensorBaseUnit().toString()
                        + (rec.getRateUnit() != RateUnit.None ? " per "
                                + rec.getRateUnit() : ""));
                logger.info("Resolution: "
                        + rec.getSensorResolution()
                        + " "
                        + rec.getSensorBaseUnit().toString()
                        + (rec.getRateUnit() != RateUnit.None ? " per "
                                + rec.getRateUnit() : ""));
            }
            if (record instanceof CompactSensorRecord) {
                CompactSensorRecord rec = (CompactSensorRecord) record;
                logger.info("*" + rec.getName());
                logger.info("Reading type: " + rec.getEventReadingType());
                logger.info("Sensor type: " + rec.getSensorType());
            }
            if (record instanceof FruDeviceLocatorRecord) {
                FruDeviceLocatorRecord rec = (FruDeviceLocatorRecord) record;
                logger.info(rec.getName());
                logger.info("{}", rec.getDeviceType());
                logger.info("FRU entity ID: " + rec.getFruEntityId());
                logger.info("FRU access address: " + rec.getDeviceAccessAddress());
                logger.info("FRU device ID: " + rec.getDeviceId());
                logger.info("FRU logical: " + rec.isLogical());
            }

            lock = false;
            if (nextRecId > 0) {
                resp = new DatagramPacket(new byte[256], 256);

                try {
                    socket.receive(resp);
                    buffer = new byte[resp.getLength()];
                    System.arraycopy(resp.getData(), 0, buffer, 0,
                            buffer.length);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                GetSensorReadingResponseData data9 = null;

                try {
                    data9 = (GetSensorReadingResponseData) Decoder.decode(
                            buffer, new Protocolv20Decoder(cs),
                            new GetSensorReading(IpmiVersion.V20, cs, AuthenticationType.RMCPPlus, 0));

                    if (record instanceof FullSensorRecord) {
                        FullSensorRecord rec = (FullSensorRecord) record;
                        logger.info(data9.getSensorReading(rec)
                                + " "
                                + rec.getSensorBaseUnit().toString()
                                + (rec.getRateUnit() != RateUnit.None ? " per "
                                        + rec.getRateUnit() : ""));
                    }
                    if (record instanceof CompactSensorRecord) {
                        CompactSensorRecord rec = (CompactSensorRecord) record;
                        List<ReadingType> events = data9.getStatesAsserted(
                                rec.getSensorType(), rec.getEventReadingType());
                        StringBuilder s = new StringBuilder();
                        for (int i = 0; i < events.size(); ++i) {
                            s.append(events.get(i)).append(", ");
                        }
                        logger.info(s.toString());

                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }

            nextRecId = data8.getNextRecordId();

            logger.info("---------------------------------------------");

            lock = false;
        }

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        GetSelInfoResponseData data10 = null;

        try {
            data10 = (GetSelInfoResponseData) Decoder.decode(buffer,
                    new Protocolv20Decoder(cs), new GetSelInfo(IpmiVersion.V20,
                            cs, AuthenticationType.RMCPPlus));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("{}", data10.getSelVersion());
        logger.info("{}", data10.getEntriesCount());
        logger.info(DateFormat.getInstance().format(
                data10.getAdditionTimestamp()));
        logger.info(DateFormat.getInstance().format(
                data10.getEraseTimestamp()));

        logger.info("---------------------------------------------");

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        ReserveSelResponseData data11 = null;

        try {
            data11 = (ReserveSelResponseData) Decoder.decode(buffer,
                    new Protocolv20Decoder(cs), new ReserveSel(IpmiVersion.V20,
                            cs, AuthenticationType.RMCPPlus));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("{}", data11.getReservationId());

        reservation = data11.getReservationId();
        reservation = 0;

        lock = false;

        logger.info("---------------------------------------------");

        while (nextRecId < 65535) {

            resp = new DatagramPacket(new byte[256], 256);

            try {
                socket.receive(resp);
                buffer = new byte[resp.getLength()];
                System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            GetSelEntryResponseData data12 = null;

            try {
                data12 = (GetSelEntryResponseData) Decoder.decode(buffer,
                        new Protocolv20Decoder(cs), new GetSelEntry(
                                IpmiVersion.V20, cs, AuthenticationType.RMCPPlus, 0, 0));
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }

            logger.info(data12.getSelRecord().toString());

            SelRecord rec = data12.getSelRecord();

            logger.info("Sensor: " + rec.getSensorType());
            logger.info("{}", rec.getTimestamp());
            logger.info("{}", rec.getEventDirection());
            logger.info("{}", rec.getEvent());

            nextRecId = data12.getNextRecordId();

            lock = false;

            logger.info("---------------------------------------------");
        }

        resp = new DatagramPacket(new byte[256], 256);

        try {
            socket.receive(resp);
            buffer = new byte[resp.getLength()];
            System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        GetFruInventoryAreaInfoResponseData data13 = null;

        try {
            data13 = (GetFruInventoryAreaInfoResponseData) Decoder.decode(
                    buffer, new Protocolv20Decoder(cs),
                    new GetFruInventoryAreaInfo(IpmiVersion.V20, cs, AuthenticationType.RMCPPlus, 0));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("FRU inventory area size: "
                + data13.getFruInventoryAreaSize());

        logger.info("FRU Unit: " + data13.getFruUnit());

        logger.info("---------------------------------------------");

        List<ReadFruDataResponseData> rd = new ArrayList<ReadFruDataResponseData>();

        for(int i = 0; i < fruSize; i += 100) {
            resp = new DatagramPacket(new byte[256], 256);

            try {
                socket.receive(resp);
                buffer = new byte[resp.getLength()];
                System.arraycopy(resp.getData(), 0, buffer, 0, buffer.length);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }

            ReadFruDataResponseData data14 = null;

            try {
                data14 = (ReadFruDataResponseData) Decoder.decode(buffer,
                        new Protocolv20Decoder(cs), new ReadFruData(IpmiVersion.V20,
                                cs, AuthenticationType.RMCPPlus, 0, BaseUnit.Bytes, 0, 0));
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }

            rd.add(data14);

            logger.info("{}", data14.getFruData().length);

            logger.info("---------------------------------------------");

        }

            List<FruRecord> records = ReadFruData.decodeFruData(rd);

            for(FruRecord r : records) {
                if(r instanceof ChassisInfo) {
                    ChassisInfo chassisInfo = (ChassisInfo) r;
                    logger.info("Chassis info:");
                    logger.info("Chassis type: " + chassisInfo.getChassisType());
                    logger.info("Chassis part number: " + chassisInfo.getChassisPartNumber());
                    logger.info("Chassis serial number: " + chassisInfo.getChassisSerialNumber());
                    for(String info : chassisInfo.getCustomChassisInfo()) {
                        logger.info("Custom chassis info: " + info);
                    }
                    logger.info("---------------------------------------------");
                } else if(r instanceof BoardInfo) {
                    BoardInfo boardInfo = (BoardInfo) r;
                    logger.info("Board info:");
                    logger.info("Board MFG date: " + boardInfo.getMfgDate().toString());
                    logger.info("Board manufacturer: " + boardInfo.getBoardManufacturer());
                    logger.info("Board product name: " + boardInfo.getBoardProductName());
                    logger.info("Board part number: " + boardInfo.getBoardPartNumber());
                    logger.info("Board serial number: " + boardInfo.getBoardSerialNumber());
                    for(String info : boardInfo.getCustomBoardInfo()) {
                        logger.info("Custom board info: " + info);
                    }
                    logger.info("---------------------------------------------");
                } else if(r instanceof ProductInfo) {
                    ProductInfo productInfo = (ProductInfo) r;
                    logger.info("Product info:");
                    logger.info("Product manufacturer: " + productInfo.getManufacturerName());
                    logger.info("Product product name: " + productInfo.getProductName());
                    logger.info("Product part number: " + productInfo.getProductModelNumber());
                    logger.info("Product version: " + productInfo.getProductVersion());
                    logger.info("Product serial number: " + productInfo.getProductSerialNumber());
                    logger.info("Product asset tag: " + productInfo.getAssetTag());
                    for(String info : productInfo.getCustomProductInfo()) {
                        logger.info("Custom board info: " + info);
                    }
                    logger.info("---------------------------------------------");
                }
            }
    }
}
