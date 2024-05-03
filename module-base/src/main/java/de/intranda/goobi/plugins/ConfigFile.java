package de.intranda.goobi.plugins;

import java.nio.file.Path;

import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.Getter;
import lombok.Setter;

public class ConfigFile {

    @Getter
    @Setter
    private ConfigDirectory configDirectory;

    @Getter
    private String fileName;

    @Getter
    @Setter
    private Type type;

    @Getter
    @Setter
    private String lastModified;

    @Getter
    private boolean writable;

    public ConfigFile(Path path) {
        this(path, null);
    }

    public ConfigFile(Path path, Type type) {
        this.fileName = path.getFileName().toString();
        this.type = type;
        StorageProviderInterface provider = StorageProvider.getInstance();
        this.writable = provider.isWritable(path);
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