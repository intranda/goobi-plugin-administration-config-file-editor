package de.intranda.goobi.plugins;

import lombok.Getter;
import lombok.Setter;

public class ConfigDirectory {

    @Getter
    @Setter
    private String directory;

    @Getter
    @Setter
    private String backupDirectory;

    @Getter
    @Setter
    private int numberOfBackups;

    ConfigDirectory(String directory, String backupDirectory, int numberOfBackups) {
        this.directory = directory;
        this.backupDirectory = backupDirectory;
        this.numberOfBackups = numberOfBackups;
    }

}
