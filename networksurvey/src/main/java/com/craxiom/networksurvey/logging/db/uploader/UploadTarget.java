package com.craxiom.networksurvey.logging.db.uploader;

public enum UploadTarget
{
    OpenCelliD("OpenCelliD"),
    BeaconDB("BeaconDB");

    private final String displayName;

    UploadTarget(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
