package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
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

    /**
     * null means that no config file is selected
     */
    private String currentConfigFileFileContentBase64 = null;

    @Getter
    private String currentConfigFileType;

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
        StorageProviderInterface storageProvider = StorageProvider.getInstance();
        for (int index = 0; index < this.configFiles.size(); index++) {
            try {
                String pathName = ConfigFileUtils.getConfigFileDirectory() + this.configFiles.get(index).getFileName();
                long lastModified = storageProvider.getLastModifiedDate(Paths.get(pathName));
                SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                this.configFiles.get(index).setLastModified(formatter.format(lastModified));
            } catch (IOException ioException) {
                this.configFiles.get(index).setLastModified("[no date available]");
            }
        }
    }

    public void setCurrentConfigFileFileContentBase64(String content) {
        if (content.equals("")) {
            // content is not set up correctly, don't write into file!
            return;
        }
        //String base64 = "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPG9wYWNDYXRhbG9ndWVzPgogICAgPGRvY3R5cGVzPgogICAgICAgIDx0eXBlIGlzQ29udGFpbmVkV29yaz0iZmFsc2UiIGlzTXVsdGlWb2x1bWU9ImZhbHNlIiBpc1BlcmlvZGljYWw9ImZhbHNlIiBydWxlc2V0VHlwZT0iTW9ub2dyYXBoIiB0aWZIZWFkZXJUeXBlPSJNb25vZ3JhcGhpZSIgdGl0bGU9Im1vbm9ncmFwaCI+CiAgICAgICAgICAgIDxsYWJlbCBsYW5ndWFnZT0iZGUiPk1vbm9ncmFwaGllPC9sYWJlbD4KICAgICAgICAgICAgPGxhYmVsIGxhbmd1YWdlPSJlbiI+TW9ub2dyYXBoPC9sYWJlbD4KICAgICAgICAgICAgPG1hcHBpbmc+QWE8L21hcHBpbmc+CiAgICAgICAgICAgIDxtYXBwaW5nPk9hPC9tYXBwaW5nPgogICAgICAgICAgICA8bWFwcGluZz5Nb25vZ3JhcGg8L21hcHBpbmc+CiAgICAgICAgPC90eXBlPgogICAgICAgIDx0eXBlIGlzQ29udGFpbmVkV29yaz0iZmFsc2UiIGlzTXVsdGlWb2x1bWU9ImZhbHNlIiBpc1BlcmlvZGljYWw9ImZhbHNlIiBydWxlc2V0VHlwZT0iTWFudXNjcmlwdCIgdGlmSGVhZGVyVHlwZT0iSGFuZHNjaHJpZnQiIHRpdGxlPSJtYW51c2NyaXB0Ij4KICAgICAgICAgICAgPGxhYmVsIGxhbmd1YWdlPSJkZSI+SGFuZHNjaHJpZnQ8L2xhYmVsPgogICAgICAgICAgICA8bGFiZWwgbGFuZ3VhZ2U9ImVuIj5NYW51c2NyaXB0PC9sYWJlbD4KICAgICAgICAgICAgPG1hcHBpbmc+SGE8L21hcHBpbmc+CiAgICAgICAgICAgIDxtYXBwaW5nPk1hbnVzY3JpcHQ8L21hcHBpbmc+CiAgICAgICAgPC90eXBlPgogICAgICAgIDx0eXBlIGlzQ29udGFpbmVkV29yaz0iZmFsc2UiIGlzTXVsdGlWb2x1bWU9ImZhbHNlIiBpc1BlcmlvZGljYWw9ImZhbHNlIiBydWxlc2V0VHlwZT0iU2luZ2xlTWFwIiB0aWZIZWFkZXJUeXBlPSJLYXJ0ZSIgdGl0bGU9Im1hcCI+CiAgICAgICAgICAgIDxsYWJlbCBsYW5ndWFnZT0iZGUiPkthcnRlPC9sYWJlbD4KICAgICAgICAgICAgPGxhYmVsIGxhbmd1YWdlPSJlbiI+TWFwPC9sYWJlbD4KICAgICAgICAgICAgPG1hcHBpbmc+S2E8L21hcHBpbmc+CiAgICAgICAgICAgIDxtYXBwaW5nPlNpbmdsZU1hcDwvbWFwcGluZz4KICAgICAgICA8L3R5cGU+CiAgICAgICAgPHR5cGUgaXNDb250YWluZWRXb3JrPSJmYWxzZSIgaXNNdWx0aVZvbHVtZT0iZmFsc2UiIGlzUGVyaW9kaWNhbD0iZmFsc2UiIHJ1bGVzZXRUeXBlPSJNdXNpY1N1cHBsaWVzIiB0aWZIZWFkZXJUeXBlPSJNdXNpa2FsaWUiIHRpdGxlPSJtdXNpY3N1cHBsaWVzIj4KICAgICAgICAgICAgPGxhYmVsIGxhbmd1YWdlPSJkZSI+TXVzaWthbGllPC9sYWJlbD4KICAgICAgICAgICAgPGxhYmVsIGxhbmd1YWdlPSJlbiI+TXVzaWMgc3VwcGxpZXM8L2xhYmVsPgogICAgICAgICAgICA8bWFwcGluZz5NYTwvbWFwcGluZz4KICAgICAgICAgICAgPG1hcHBpbmc+TXVzaWNTdXBwbGllczwvbWFwcGluZz4KICAgICAgICA8L3R5cGU+CiAgICAgICAgPHR5cGUgaXNDb250YWluZWRXb3JrPSJmYWxzZSIgaXNNdWx0aVZvbHVtZT0iZmFsc2UiIGlzUGVyaW9kaWNhbD0idHJ1ZSIgcnVsZXNldFR5cGU9IlBlcmlvZGljYWwiIHRpZkhlYWRlclR5cGU9IkJhbmRfWmVpdHNjaHJpZnQiIHRpdGxlPSJwZXJpb2RpY2FsIiBydWxlc2V0Q2hpbGRUeXBlPSJQZXJpb2RpY2FsVm9sdW1lIj4KICAgICAgICAgICAgPGxhYmVsIGxhbmd1YWdlPSJkZSI+WmVpdHNjaHJpZnQ8L2xhYmVsPgogICAgICAgICAgICA8bGFiZWwgbGFuZ3VhZ2U9ImVuIj5QZXJpb2RpY2FsPC9sYWJlbD4KICAgICAgICAgICAgPG1hcHBpbmc+QWI8L21hcHBpbmc+CiAgICAgICAgICAgIDxtYXBwaW5nPk9iPC9tYXBwaW5nPgogICAgICAgICAgICA8bWFwcGluZz5QZXJpb2RpY2FsPC9tYXBwaW5nPgogICAgICAgIDwvdHlwZT4KICAgICAgICA8dHlwZSBpc0NvbnRhaW5lZFdvcms9ImZhbHNlIiBpc011bHRpVm9sdW1lPSJ0cnVlIiBpc1BlcmlvZGljYWw9ImZhbHNlIiBydWxlc2V0VHlwZT0iTXVsdGlWb2x1bWVXb3JrIiB0aWZIZWFkZXJUeXBlPSJCYW5kX011bHRpdm9sdW1lV29yayIgdGl0bGU9Im11bHRpdm9sdW1lIiBydWxlc2V0Q2hpbGRUeXBlPSJWb2x1bWUiPgogICAgICAgICAgICA8bGFiZWwgbGFuZ3VhZ2U9ImRlIj5NZWhyYuRuZGlnZXMgV2VyazwvbGFiZWw+CiAgICAgICAgICAgIDxsYWJlbCBsYW5ndWFnZT0iZW4iPk11bHRpdm9sdW1lIHdvcms8L2xhYmVsPgogICAgICAgICAgICA8bWFwcGluZz5PZjwvbWFwcGluZz4KICAgICAgICAgICAgPG1hcHBpbmc+QWY8L21hcHBpbmc+CiAgICAgICAgICAgIDxtYXBwaW5nPk9GPC9tYXBwaW5nPgogICAgICAgICAgICA8bWFwcGluZz5BRjwvbWFwcGluZz4KICAgICAgICAgICAgPG1hcHBpbmc+TXVsdGlWb2x1bWVXb3JrPC9tYXBwaW5nPgogICAgICAgIDwvdHlwZT4KICAgIDwvZG9jdHlwZXM+CiAgICA8Y2F0YWxvZ3VlIHRpdGxlPSJLMTBQbHVzIj4KICAgICAgICA8Y29uZmlnIGFkZHJlc3M9Imt4cC5rMTBwbHVzLmRlIiBkYXRhYmFzZT0iMi4xIiBkZXNjcmlwdGlvbj0iSzEwcGx1cyIgaWt0bGlzdD0iSUtUTElTVC1HQlYueG1sIiBwb3J0PSI4MCIgdWNuZj0iVUNORj1ORkMmYW1wO1hQTk9GRj0xIiAvPgogICAgPC9jYXRhbG9ndWU+CiAgICA8Y2F0YWxvZ3VlIHRpdGxlPSJMaWJyYXJ5IG9mIENvbmdyZXNzIj4KICAgICAgICA8Y29uZmlnIGFkZHJlc3M9Imh0dHA6Ly9vcGFjLmludHJhbmRhLmNvbS9zcnUvREI9MSIgZGF0YWJhc2U9IjEiIHVjbmY9IlhQTk9GRj0xIiBkZXNjcmlwdGlvbj0iTGlicmFyeSBvZiBDb25ncmVzcyBTUlUgKFZveWFnZXIpIiBpa3RsaXN0PSJJS1RMSVNULnhtbCIgcG9ydD0iODAiIG9wYWNUeXBlPSJHQlYtTUFSQyIvPgogICAgICAgIDxzZWFyY2hGaWVsZHM+CiAgICAgICAgICAgIDxzZWFyY2hGaWVsZCBsYWJlbD0iTENDTiIgdmFsdWU9IjEyIiAvPgogICAgICAgICAgICA8c2VhcmNoRmllbGQgbGFiZWw9IklTQk4iIHZhbHVlPSI3IiAvPgogICAgICAgICAgICA8c2VhcmNoRmllbGQgbGFiZWw9IklTU04iIHZhbHVlPSI4IiAvPgogICAgICAgIDwvc2VhcmNoRmllbGRzPgogICAgPC9jYXRhbG9ndWU+Cjwvb3BhY0NhdGFsb2d1ZXM+CjxFT0YgLz4K";
        //log.error("Base64 (server):     " + base64);
        //log.error("Base64 (submitted):  " + content);
        //log.error("equal?               " + content.equals(base64));
        Base64.Decoder decoder = Base64.getDecoder();
        //byte[] decoded2 = decoder.decode(base64);
        //log.error("Decoded (server):    " + new String(decoded2));
        byte[] decoded = decoder.decode(content);
        this.currentConfigFileFileContent = new String(decoded, Charset.forName("UTF-8"));
        //log.error("Decoded (submitted): " + this.currentConfigFileFileContent);
        //this.currentConfigFileFileContentBase64 = this.currentConfigFileFileContent;
    }

    public String getCurrentConfigFileFileContentBase64() {
        // The return value must be empty to indicate that the text was not initialized until now.
        return "";
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
        fileContent = fileContent.replace("\r\n", "\n");
        fileContent = fileContent.replace("\r", "\n");
        String editorContent = this.currentConfigFileFileContent;
        //log.error("fileContent: " + fileContent);
        //log.error("editorContent: " + editorContent);
        int index = 0;
        while (index < fileContent.length() || index < editorContent.length()) {
            if (index < fileContent.length() && fileContent.charAt(index) >= 127) {
                log.error("file: " + (index < fileContent.length() ? (int)(fileContent.charAt(index)) + " (" + (char)(fileContent.charAt(index)) + ")" : -1) + " editor: " + (index < editorContent.length() ? (int)(editorContent.charAt(index)) + " (" + (char)(editorContent.charAt(index)) + ")" : -1));
            }
            index++;
        }
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