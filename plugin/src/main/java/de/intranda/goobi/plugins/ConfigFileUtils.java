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

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class ConfigFileUtils {

    private static Charset standardCharset;

    private static XMLConfiguration xmlConfiguration;

    private static List<String> warningMessages;

    public static void init(XMLConfiguration configuration) {
        ConfigFileUtils.xmlConfiguration = configuration;
        ConfigFileUtils.standardCharset = Charset.forName("UTF-8");
    }

    public static List<ConfigFile> getAllConfigFiles() {
        return ConfigFileUtils.getAllConfigFiles(ConfigFileUtils.xmlConfiguration);
    }

    // TODO: Later an own permission for super admins should solve the decision if configuration files are displayed or not
    // Until now the configuration file of this plugin is filtered out to avoid to be edited in the browser interface by "default" admins
    // For that the warnings can be used and displayed above the plugin in the GUI.
    public static List<ConfigFile> getAllConfigFiles(XMLConfiguration xml) {

        // If the default config directory was not included, it is included afterwards
        ConfigurationHelper configuration = ConfigurationHelper.getInstance();
        String defaultConfigDirectory = configuration.getConfigurationFolder();
        boolean foundDefaultConfigDirectory = false;

        //ConfigFileUtils.warningMessages = new ArrayList<>();
        List<ConfigFile> configFiles = new ArrayList<>();

        int index = 0;
        ConfigDirectory directory;
        do {
            directory = ConfigFileUtils.tryToParseConfigDirectory(xml, index);
            if (directory != null) {

                //if (ConfigFileUtils.isDirectoryAllowed(directory.getDirectory())) {
                if (!ConfigFileUtils.containsHiddenDirectory(directory.getDirectory())) {
                    configFiles.addAll(ConfigFileUtils.getAllConfigFilesFromDirectory(directory));
                }
                //} else {
                //    String message = "The configured directory is not a subdirectory of the goobi parent directory and may not be used: ";
                //    ConfigFileUtils.warningMessages.add(message + directory.getDirectory());
                //}
                if (defaultConfigDirectory.equals(directory.getDirectory())) {
                    foundDefaultConfigDirectory = true;
                }
            }
            index++;
        } while (directory != null);

        if (!foundDefaultConfigDirectory) {
            ConfigDirectory defaultDirectory = new ConfigDirectory(defaultConfigDirectory, "backup/", 8, "");
            configFiles.addAll(ConfigFileUtils.getAllConfigFilesFromDirectory(defaultDirectory));
        }

        return configFiles;
    }

    /**
     * Returns true if at least one of the path elements is a hidden directory (starts with a '.').
     *
     * @param path The path to check
     * @return true If the path contains a hidden directory
     */
    public static boolean containsHiddenDirectory(String path) {
        Path file = Paths.get(path);
        for (int index = 0; index < file.getNameCount(); index++) {
            Path subPath = file.getName(index);
            if (subPath.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    // Until now this method only returns an empty list because warnings are not used.
    public static List<String> getWarningMessages() {
        //return ConfigFileUtils.warningMessages;
        return new ArrayList<>();
    }

    /**
     * This security check is needed to only allow directories that are inside the parent directory of the configured goobi directory. Especially
     * paths that contain "../" (parent directory) may not direct to directories outside of that directory.
     *
     * @param directory
     * @return
     */
    public static boolean isDirectoryAllowed(String directory) {
        String goobiDirectoryName = ConfigurationHelper.getInstance().getGoobiFolder();
        Path goobiDirectory = Paths.get(goobiDirectoryName).toAbsolutePath();
        Path goobiParentDirectory = goobiDirectory.getParent().toAbsolutePath();
        String absoluteGoobiParentDirectoryName = goobiParentDirectory.toString();

        String absoluteRequestedDirectoryName = Paths.get(directory).toAbsolutePath().toString();
        return absoluteRequestedDirectoryName.startsWith(absoluteGoobiParentDirectoryName);
    }

    private static ConfigDirectory tryToParseConfigDirectory(XMLConfiguration xml, int index) {
        String element = "configFileDirectories.directory(" + index + ")";

        // Get the directory name where the configuration files are (or null)
        String directory = null;
        try {
            directory = xml.getString(element);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        // No directory with this index found, loop breaks to look for further directories
        if (directory == null || directory.length() == 0) {
            return null;
        }
        if (!directory.endsWith("/")) {
            directory += "/";
        }

        // Get the backup directory if it is defined in the parameter. If it is not defined, set it to "backup/"
        String backupDirectory = null;
        try {
            backupDirectory = xml.getString(element + "[@backupFolder]");
        } catch (Exception exception) {
            // No value is set, standard value should be used.
            // exception.printStackTrace();
        }
        // The default backup directory is the subdirectory "backup/" in the directory where the configuration files are.
        // If the backupDirectory is an empty string (""), the configuration directory is used for the backups.
        if (backupDirectory == null) {
            backupDirectory = "backup/";
        }
        if (backupDirectory.length() > 0 && !backupDirectory.endsWith("/")) {
            backupDirectory += "/";
        }
        backupDirectory = directory + backupDirectory;

        // Get the number of backup files that should be rotated before old files are deleted. The default value is 8.
        int numberOfBackups = 8;
        try {
            numberOfBackups = xml.getInt(element + "[@backupFiles]");
        } catch (Exception exception) {
            // No value is set, standard value should be used.
            // exception.printStackTrace();
        }
        if (numberOfBackups < 0) {
            numberOfBackups = 0;
        }

        // Get the file regex parameter. If the regex is not used, it is set to null and not used in the file selection algorithm.
        String fileRegex = null;
        try {
            fileRegex = xml.getString(element + "[@fileRegex]");
        } catch (Exception exception) {
            // No value is set, standard value should be used.
            // exception.printStackTrace();
        }

        return new ConfigDirectory(directory, backupDirectory, numberOfBackups, fileRegex);
    }

    private static List<ConfigFile> getAllConfigFilesFromDirectory(ConfigDirectory configDirectory) {
        StorageProviderInterface storage = StorageProvider.getInstance();
        String regex = configDirectory.getFileRegex();

        // listFiles(String folder) already contains an empty try-catch-block without warning message
        // In case of an error an empty list is provided here.
        List<Path> files = storage.listFiles(configDirectory.getDirectory());
        List<ConfigFile> configFiles = new ArrayList<>();

        for (int index = 0; index < files.size(); index++) {
            Path file = files.get(index).toAbsolutePath();

            // The own configuration file is excluded because normal admins should not be able to add custom paths
            if (storage.isFileExists(file) && !ConfigFileUtils.isOwnConfigFile(files.get(index).getFileName().toString())) {
                String name = file.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                if (regex != null && regex.length() > 0 && !name.matches(regex)) {
                    continue;
                }
                ConfigFile configFile = new ConfigFile(file);
                configFile.setConfigDirectory(configDirectory);
                if (name.endsWith(".xml")) {
                    configFile.setType(ConfigFile.Type.XML);
                    configFiles.add(configFile);
                } else if (name.endsWith(".properties")) {
                    configFile.setType(ConfigFile.Type.PROPERTIES);
                    configFiles.add(configFile);
                }
            }
        }
        return configFiles;
    }

    private static boolean isOwnConfigFile(String fileName) {
        return fileName.equals(ConfigFileEditorAdministrationPlugin.CONFIGURATION_FILE);
    }

    /**
     * Rotates the backup files (older files get a higher number) and creates a backup file in "fileName.xml.1". The oldest file (e.g.
     * "fileName.xml.10") will be removed
     *
     * How the algorithm works (e.g. this.NUMBER_OF_BACKUP_FILES == 10):
     *
     * delete(backup/fileName.xml.10) rename(backup/fileName.xml.9, backup/fileName.xml.10) rename(backup/fileName.xml.8, backup/fileName.xml.9) ...
     * rename(backup/fileName.xml.2, backup/fileName.xml.3) rename(backup/fileName.xml.1, backup/fileName.xml.2) copy(fileName.xml,
     * backup/fileName.xml.1)
     */
    public static void createBackupFile(ConfigFile configFile) {
        ConfigDirectory configDirectory = configFile.getConfigDirectory();
        String configDirectoryName = configDirectory.getDirectory();
        String backupDirectory = configDirectory.getBackupDirectory();
        String fileName = configFile.getFileName();
        int numberOfBackups = configDirectory.getNumberOfBackups();

        StorageProviderInterface storage = StorageProvider.getInstance();
        try {
            // Delete oldest file if it exists...
            String lastFileName = ConfigFileUtils.createBackupFileNameWithoutTimestamp(fileName, numberOfBackups);
            lastFileName = backupDirectory + ConfigFileUtils.findBackupFileNameByEnding(backupDirectory, lastFileName);
            if (lastFileName != null) {
                Path lastFile = Paths.get(lastFileName);
                if (storage.isFileExists(lastFile)) {
                    storage.deleteFile(lastFile);
                }
            }
            // Rename all other backup files...
            // This is the number of the file that should be renamed to the file with the higher number
            int backupId = numberOfBackups - 1;
            while (backupId > 0) {
                String newerFileName = ConfigFileUtils.createBackupFileNameWithoutTimestamp(fileName, backupId);
                newerFileName = ConfigFileUtils.findBackupFileNameByEnding(backupDirectory, newerFileName);
                String newerFileNameWithPath = backupDirectory + newerFileName;
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
            String backupFileName = ConfigFileUtils.createBackupFileNameWithTimestamp(configFile, 1);
            String content = ConfigFileUtils.readFile(configDirectoryName + fileName);
            ConfigFileUtils.writeFile(backupDirectory, backupFileName, content);
            log.info("Wrote backup file: " + fileName);
        } catch (IOException ioException) {
            log.error(ioException);
        }
    }

    private static String extractTimestampFromFileName(String fileName) {
        // The date format must be "yyyy_MM_dd_HH_mm_ss"
        return fileName.substring(0, 19);
    }

    private static String createBackupFileNameWithTimestamp(ConfigFile configFile, int backupId) {
        String fileName = configFile.getFileName();
        String name = ConfigFileUtils.createBackupFileNameWithoutTimestamp(fileName, backupId);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String timestamp = formatter.format(new Date());
        return configFile.getConfigDirectory().getBackupDirectory() + timestamp + "_" + name;
    }

    private static String createBackupFileNameWithoutTimestamp(String fileName, int backupId) {
        return fileName + "." + backupId;
    }

    private static String findBackupFileNameByEnding(String backupDirectory, String ending) {
        StorageProviderInterface storage = StorageProvider.getInstance();
        List<String> fileNames = storage.list(backupDirectory);
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

    /**
     * The fileName parameter must already contain the backup directory. The backupDirectory parameter is used to check whether the directory exists.
     *
     * @param backupDirectory
     * @param fileName
     * @param content
     */
    public static void writeFile(String backupDirectory, String fileName, String content) {
        if (!Paths.get(backupDirectory).toFile().exists()) {
            ConfigFileUtils.createDirectory(backupDirectory);
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