package de.intranda.goobi.plugins;

import java.nio.file.Path;

import lombok.Getter;
import lombok.Setter;

public class ConfigFile {

    @Getter
    @Setter
    private ConfigDirectory directory;

    @Getter
    private String fileName;

    @Getter
    private Type type;

    @Getter
    @Setter
    private String lastModified;

    public ConfigFile(Path path, Type type) {
        this.fileName = path.getFileName().toString();
        this.type = type;
    }

    public enum Type {
        XML,
        PROPERTIES;

        /**
         * These strings are the mime types for the code mirror text editor
         */
        @Override
        public String toString() {
            switch (this) {
                case XML:
                    return "xml";
                case PROPERTIES:
                    return "text/x-properties";
                default:
                    return "";
            }
        }
    }

}