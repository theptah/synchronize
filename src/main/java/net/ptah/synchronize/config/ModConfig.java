package net.ptah.synchronize.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "synchronize/config")
public class ModConfig implements ConfigData {
    public String language;
    public TransferMethod fileTransferMethod;
    public int externalPort;
     
     
    public boolean limboEnabled;
    public int limboDurationSeconds;
     
     
    public int rateLimitMaxRequests;
    public int rateLimitWindowSeconds;
    public String handshake;
    public String additionalSharedDirectories;

    public ModConfig() {
        this.language = "en_us";
        this.fileTransferMethod = ModConfig.TransferMethod.MINECRAFT;
        this.externalPort = 25652;
         
         
        this.limboEnabled = false;
        this.limboDurationSeconds = 30;
         
         
        this.rateLimitMaxRequests = 5;
        this.rateLimitWindowSeconds = 60;
        this.handshake = "";
        this.additionalSharedDirectories = "";
    }

    public static enum TransferMethod {
        MINECRAFT,
        EXTERNAL;
    }
}