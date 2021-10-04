package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.extern.log4j.Log4j2;
import lombok.Getter;

@Log4j2
public abstract class ConfigFileUtils {

    @Getter
    private static String configFileDirectory;

    @Getter
    private static String backupDirectory;

    private static int numberOfBackupFiles;

    private static Charset standardCharset;

    public static void init(XMLConfiguration configuration) {
        ConfigFileUtils.configFileDirectory = configuration.getString("configFileDirectory", "/opt/digiverso/goobi/config/");
        ConfigFileUtils.backupDirectory = configuration.getString("configFileBackupDirectory", "/opt/digiverso/goobi/config/backup/");
        ConfigFileUtils.numberOfBackupFiles = configuration.getInt("numberOfBackupFiles", 10);
        ConfigFileUtils.standardCharset = Charset.forName("UTF-8");
    }

    public static List<ConfigFile> getAllConfigFiles() {
        StorageProviderInterface storage = StorageProvider.getInstance();
        List<Path> files = storage.listFiles(ConfigFileUtils.configFileDirectory);
        List<ConfigFile> configFiles = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            Path file = files.get(index).toAbsolutePath();
            if (storage.isFileExists(file)) {
                String name = file.getFileName().toString();
                if (name.endsWith(".xml")) {
                    configFiles.add(new ConfigFile(file, ConfigFile.Type.XML));
                } else if (name.endsWith(".properties")) {
                    configFiles.add(new ConfigFile(file, ConfigFile.Type.PROPERTIES));
                }
            }
        }
        return configFiles;
    }

    /**
     * Rotates the backup files (older files get a higher number) and creates a backup file in "fileName.xml.1".
     * The oldest file (e.g. "fileName.xml.10") will be removed
     *
     * How the algorithm works (e.g. this.NUMBER_OF_BACKUP_FILES == 10):
     *
     * delete(backup/fileName.xml.10)
     * rename(backup/fileName.xml.9, backup/fileName.xml.10)
     * rename(backup/fileName.xml.8, backup/fileName.xml.9)
     *...
     * rename(backup/fileName.xml.2, backup/fileName.xml.3)
     * rename(backup/fileName.xml.1, backup/fileName.xml.2)
     * copy(fileName.xml, backup/fileName.xml.1)
     */
    public static void createBackupFile(String fileName) {
        StorageProviderInterface storage = StorageProvider.getInstance();
        try {
            // Delete oldest file if it exists...
            String lastFileName = ConfigFileUtils.createBackupFileNameWithoutTimestamp(fileName, ConfigFileUtils.numberOfBackupFiles);
            lastFileName = ConfigFileUtils.backupDirectory + ConfigFileUtils.findBackupFileNameByEnding(lastFileName);
            if (lastFileName != null) {
                Path lastFile = Paths.get(lastFileName);
                if (storage.isFileExists(lastFile)) {
                    storage.deleteFile(lastFile);
                }
            }
            // Rename all other backup files...
            // This is the number of the file that should be renamed to the file with the higher number
            int backupId = ConfigFileUtils.numberOfBackupFiles - 1;
            while (backupId > 0) {
                String newerFileName = ConfigFileUtils.createBackupFileNameWithoutTimestamp(fileName, backupId);
                newerFileName = ConfigFileUtils.findBackupFileNameByEnding(newerFileName);
                String newerFileNameWithPath = ConfigFileUtils.backupDirectory + newerFileName;
                String olderFileName = ConfigFileUtils.createBackupFileNameWithoutTimestamp(fileName, backupId + 1);
                if (newerFileName != null) {
                    String timestamp = ConfigFileUtils.extractTimestampFromFileName(newerFileName);
                    olderFileName = timestamp + "_" + olderFileName;
                    Path newerFile = Paths.get(newerFileNameWithPath);
                    if (storage.isFileExists(newerFile)) {
                        storage.renameTo(newerFile, olderFileName);
                    }
                }
                backupId--;
            }
            // Create backup file...
            String content = ConfigFileUtils.readFile(ConfigFileUtils.configFileDirectory + fileName);
            ConfigFileUtils.writeFile(ConfigFileUtils.createBackupFileNameWithTimestamp(fileName, 1), content);
            log.info("Wrote backup file: " + fileName);
        } catch (IOException ioException) {
            log.error(ioException);
        }
    }

    private static String extractTimestampFromFileName(String fileName) {
        // The date format must be "yyyy_MM_dd_HH_mm_ss"
        return fileName.substring(0, 19);
    }

    private static String createBackupFileNameWithoutTimestamp(String fileName, int backupId) {
        return fileName + "." + backupId;
    }

    private static String createBackupFileNameWithTimestamp(String fileName, int backupId) {
        String name = ConfigFileUtils.createBackupFileNameWithoutTimestamp(fileName, backupId);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String timestamp = formatter.format(new Date());
        return ConfigFileUtils.backupDirectory + timestamp + "_" + name;
    }

    private static String findBackupFileNameByEnding(String ending) {
        StorageProviderInterface storage = StorageProvider.getInstance();
        List<String> fileNames = storage.list(ConfigFileUtils.backupDirectory);
        for (int index = 0; index < fileNames.size(); index++) {
            if (fileNames.get(index).endsWith(ending)) {
                return fileNames.get(index);
            }
        }
        return null;
    }

    public static String readFile(String fileName) {
        try {
            Charset charset = ConfigFileUtils.standardCharset;
            return FileUtils.readFileToString(new File(fileName), charset);
        } catch (IOException ioException) {
            ioException.printStackTrace();
            String message = "ConfigFileEditorAdministrationPlugin could not read file " + fileName;
            log.error(message);
            Helper.setFehlerMeldung(message);
            return "";
        }
    }

    public static void writeFile(String fileName, String content) {
        if (!Paths.get(ConfigFileUtils.backupDirectory).toFile().exists()) {
            ConfigFileUtils.createDirectory(ConfigFileUtils.backupDirectory);
        }
        if (!Paths.get(fileName).toFile().exists()) {
            ConfigFileUtils.createFile(fileName);
        }
        try {
            Charset charset = ConfigFileUtils.standardCharset;
            FileUtils.write(new File(fileName), content, charset);
        } catch (IOException ioException) {
            ioException.printStackTrace();
            String message = "ConfigFileEditorAdministrationPlugin could not write file " + fileName;
            log.error(message);
            Helper.setFehlerMeldung(message);
        }
    }

    public static void createFile(String fileName) {
        Path path = Paths.get(fileName);
        try {
            StorageProvider.getInstance().createFile(path);
        } catch (IOException ioException) {
            ioException.printStackTrace();
            log.error("ConfigFileEditorAdministrationPlugin could not create file " + fileName);
        }
    }

    public static void createDirectory(String directoryName) {
        Path path = Paths.get(directoryName);
        try {
            StorageProvider.getInstance().createDirectories(path);
        } catch (IOException ioException) {
            ioException.printStackTrace();
            log.error("ConfigFileEditorAdministrationPlugin could not create directory " + directoryName);
        }
    }

}