package com.craxiom.networksurvey.model;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.google.protobuf.GeneratedMessage;

import java.util.Objects;

/**
 * Wraps the various cellular records so that we can include a variable that specifies which record type it is.
 *
 * @since 1.6.0
 */
public class CellularRecordWrapper
{
    public final CellularProtocol cellularProtocol;
    public final GeneratedMessage cellularRecord;
    private final int hash;
    private final String comparableString;

    public CellularRecordWrapper(CellularProtocol cellularProtocol, GeneratedMessage cellularRecord)
    {
        this.cellularProtocol = cellularProtocol;
        this.cellularRecord = cellularRecord;

        comparableString = getComparableString(this);
        hash = Objects.hash(cellularProtocol, comparableString);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CellularRecordWrapper that = (CellularRecordWrapper) o;

        if (cellularProtocol != that.cellularProtocol) return false;
        return comparableString.equals(that.comparableString);
    }

    @Override
    public int hashCode()
    {
        return hash;
    }

    /**
     * @return The PLMN associated with the cellular record.
     */
    public Plmn getPlmn()
    {
        return switch (cellularProtocol)
        {
            case GSM ->
            {
                GsmRecordData gsmData = ((GsmRecord) cellularRecord).getData();
                yield new Plmn(gsmData.getMcc().getValue(), gsmData.getMnc().getValue());
            }
            case CDMA ->
            {
                CdmaRecordData cdmaData = ((CdmaRecord) cellularRecord).getData();
                yield new Plmn(cdmaData.getSid().getValue(), cdmaData.getNid().getValue());
            }
            case UMTS ->
            {
                UmtsRecordData umtsData = ((UmtsRecord) cellularRecord).getData();
                yield new Plmn(umtsData.getMcc().getValue(), umtsData.getMnc().getValue());
            }
            case LTE ->
            {
                LteRecordData lteData = ((LteRecord) cellularRecord).getData();
                yield new Plmn(lteData.getMcc().getValue(), lteData.getMnc().getValue());
            }
            case NR ->
            {
                NrRecordData nrData = ((NrRecord) cellularRecord).getData();
                yield new Plmn(nrData.getMcc().getValue(), nrData.getMnc().getValue());
            }
            default -> new Plmn(0, 0);
        };
    }

    private static String getComparableString(CellularRecordWrapper wrapper)
    {
        return switch (wrapper.cellularProtocol)
        {
            case GSM ->
            {
                GsmRecordData gsmData = ((GsmRecord) wrapper.cellularRecord).getData();
                yield "" + gsmData.getMcc().getValue() + gsmData.getMnc().getValue() + gsmData.getLac().getValue() + gsmData.getCi().getValue();
            }
            case UMTS ->
            {
                UmtsRecordData umtsData = ((UmtsRecord) wrapper.cellularRecord).getData();
                yield "" + umtsData.getMcc().getValue() + umtsData.getMnc().getValue() + umtsData.getLac().getValue() + umtsData.getCid().getValue();
            }
            case LTE ->
            {
                LteRecordData lteData = ((LteRecord) wrapper.cellularRecord).getData();
                yield "" + lteData.getMcc().getValue() + lteData.getMnc().getValue() + lteData.getTac().getValue() + lteData.getEci().getValue();
            }
            case NR ->
            {
                NrRecordData nrData = ((NrRecord) wrapper.cellularRecord).getData();
                yield "" + nrData.getMcc().getValue() + nrData.getMnc().getValue() + nrData.getTac().getValue() + nrData.getNci().getValue();
            }
            default -> "";
        };
    }
}
