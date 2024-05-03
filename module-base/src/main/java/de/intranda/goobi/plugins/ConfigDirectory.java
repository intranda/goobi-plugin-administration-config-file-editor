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

    @Getter
    @Setter
    private String fileRegex;

    ConfigDirectory(String directory, String backupDirectory, int numberOfBackups, String fileRegex) {
        this.directory = directory;
        this.backupDirectory = backupDirectory;
        this.numberOfBackups = numberOfBackups;
        this.fileRegex = fileRegex;
    }

}
