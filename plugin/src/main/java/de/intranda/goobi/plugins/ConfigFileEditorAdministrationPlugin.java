package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@Log4j2
@PluginImplementation
public class ConfigFileEditorAdministrationPlugin implements IAdministrationPlugin {

    @Getter
    private String title = "intranda_administration_config_file_editor";

    private List<ConfigFile> configFiles;

    /**
     * -1 means that no config file is selected
     */
    @Getter
    private int currentConfigFileIndex = -1;

    private int configFileIndexAfterSaveOrIgnore = -1;

    @Getter
    private boolean configFileContentChanged = false;

    /**
     * null means that no config file is selected
     */
    @Getter
    private ConfigFile currentConfigFile = null;

    /**
     * null means that no config file is selected
     */
    @Getter
    @Setter
    private String currentConfigFileFileContent = null;

    private String currentConfigFileType;

    @Getter
    private List<String> configFileDates;

    /**
     * Constructor
     */
    public ConfigFileEditorAdministrationPlugin() {
        XMLConfiguration configuration = ConfigPlugins.getPluginConfig(this.title);
        ConfigFileUtils.init(configuration);
    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_administration_config_file_editor.xhtml";
    }

    public String getCurrentEditorTitle() {
        if (this.currentConfigFile != null) {
            return this.currentConfigFile.getFileName();
        } else {
            return "";
        }
    }

    private void initConfigFileDates() {
        this.configFileDates = new ArrayList<>();
        StorageProviderInterface storageProvider = StorageProvider.getInstance();
        for (int index = 0; index < this.configFiles.size(); index++) {
            try {
                long lastModified =
                        storageProvider.getLastModifiedDate(Paths.get(ConfigFileUtils.getConfigFileDirectory() + this.configFiles.get(index).getFileName()));
                SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                this.configFileDates.add(formatter.format(lastModified));
            } catch (IOException ioException) {
                this.configFileDates.add("[no date available]");
            }
        }
    }

    public List<ConfigFile> getConfigFiles() {
        if (this.configFiles == null) {
            this.configFiles = ConfigFileUtils.getAllConfigFiles();
            this.initConfigFileDates();
        }
        if (this.configFiles != null) {
            return this.configFiles;
        } else {
            return new ArrayList<>();
        }
    }

    public void setCurrentConfigFileIndex(int index) {
        this.setConfigFile(index);
    }

    public String getCurrentConfigFileType() {
        log.error("Type: " + this.currentConfigFileType);
        return this.currentConfigFileType;
    }

    public String getCurrentConfigFileFileName() {
        return ConfigFileUtils.getConfigFileDirectory() + this.currentConfigFile.getFileName();
    }

    public boolean isActiveConfigFile(ConfigFile configFile) {
        return this.findConfigFileIndex(configFile) == this.currentConfigFileIndex;
    }

    public void editConfigFile(ConfigFile configFile) {
        int index = this.findConfigFileIndex(configFile);
        if (this.hasFileContentChanged()) {
            this.configFileContentChanged = true;
            this.configFileIndexAfterSaveOrIgnore = index;
            return;
        }
        this.setConfigFile(index);
    }

    public void editConfigFileIgnore() {
        this.configFileContentChanged = false;
        this.setConfigFile(this.configFileIndexAfterSaveOrIgnore);
    }

    public int findConfigFileIndex(ConfigFile configFile) {
        for (int index = 0; index < this.configFiles.size(); index++) {
            if (configFile == this.configFiles.get(index)) {
                return index;
            }
        }
        return -1;
    }

    public void save() {
        // Only create a backup if the new file content differs from the existing file content
        if (this.hasFileContentChanged()) {
            ConfigFileUtils.createBackupFile(this.currentConfigFile.getFileName());
        }
        ConfigFileUtils.writeFile(this.getCurrentConfigFileFileName(), this.currentConfigFileFileContent);
        // Uncomment this when the file should be closed after saving
        // this.setConfigFile(-1);
        Helper.setMeldung("configFileEditor", Helper.getTranslation("savedConfigFileSuccessfully"), "");
        // Switch to an other file (configFileIndexAfterSaveOrIgnore) when "Save" was clicked
        // because the file should be changed and an other file is already selected
        if (this.configFileIndexAfterSaveOrIgnore != -1) {
            if (this.configFileIndexAfterSaveOrIgnore != this.currentConfigFileIndex) {
                this.setConfigFile(this.configFileIndexAfterSaveOrIgnore);
            }
            this.configFileIndexAfterSaveOrIgnore = -1;
        }
        this.configFileContentChanged = false;
    }

    public void saveAndChangeConfigFile() {
        this.save();
    }

    private boolean hasFileContentChanged() {
        if (this.currentConfigFile == null) {
            return false;
        }
        String fileContent = ConfigFileUtils.readFile(this.getCurrentConfigFileFileName());
        String editorContent = this.currentConfigFileFileContent.replace("\n", "\r\n");
        return !fileContent.equals(editorContent);
    }

    public void cancel() {
        this.setConfigFile(-1);
    }

    private void setConfigFile(int index) {
        // Change the (saved or unchanged) file
        if (index >= 0 && index < this.configFiles.size()) {
            this.currentConfigFileIndex = index;
            this.currentConfigFile = this.configFiles.get(index);
            this.currentConfigFileFileContent = ConfigFileUtils.readFile(this.getCurrentConfigFileFileName());
            this.currentConfigFileType = this.currentConfigFile.getType().toString();
        } else {
            // Close the file
            this.currentConfigFileIndex = -1;
            this.currentConfigFile = null;
            this.currentConfigFileFileContent = null;
        }
    }

}