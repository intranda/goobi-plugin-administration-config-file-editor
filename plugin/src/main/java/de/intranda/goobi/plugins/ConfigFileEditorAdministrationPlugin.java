package de.intranda.goobi.plugins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationRuntimeException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPushPlugin;
import org.omnifaces.cdi.PushContext;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.intranda.goobi.plugins.xml.ReportErrorsErrorHandler;
import de.intranda.goobi.plugins.xml.XMLError;
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
public class ConfigFileEditorAdministrationPlugin implements IAdministrationPlugin, IPushPlugin {

    public static final String MESSAGE_KEY_PREFIX = "plugin_administration_config_file_editor";

    // The name of this file is needed to exclude it from the list that is shown in the GUI
    public static final String CONFIGURATION_FILE = "plugin_intranda_administration_config_file_editor.xml";

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

    @Getter
    private String currentConfigFileType;

    @Getter
    private boolean validationError;

    private PushContext pusher;

    /**
     * Constructor
     */
    public ConfigFileEditorAdministrationPlugin() {
        XMLConfiguration configuration = ConfigPlugins.getPluginConfig(this.title);
        ConfigFileUtils.init(configuration);
        this.getConfigFiles();
    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_administration_config_file_editor.xhtml";
    }

    @Override
    public void setPushContext(PushContext pusher) {
        this.pusher = pusher;
    }

    // TODO: In a future version this method can decide whether the current user is a super admin. The permission must be added by hand.
    /*
    public boolean isUserSuperAdmin() {
        return Helper.getLoginBean().hasRole("Plugin_administration_config_file_editor_superadmin");
    }
    */

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
                ConfigFile file = this.configFiles.get(index);
                String pathName = file.getConfigDirectory().getDirectory() + file.getFileName();
                long lastModified = storageProvider.getLastModifiedDate(Paths.get(pathName));
                SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                this.configFiles.get(index).setLastModified(formatter.format(lastModified));
            } catch (IOException ioException) {
                this.configFiles.get(index).setLastModified("[no date available]");
            }
        }
    }

    public String getLastModifiedDateOfConfigurationFile(ConfigFile configFile) {
        return configFile.getLastModified();
    }

    public void setCurrentConfigFileFileContentBase64(String content) {
        if (content.equals("")) {
            // content is not set up correctly, don't write into file!
            return;
        }
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] decoded = decoder.decode(content);
        this.currentConfigFileFileContent = new String(decoded, Charset.forName("UTF-8"));
    }

    public String getCurrentConfigFileFileContentBase64() {
        // The return value must be empty to indicate that the text was not initialized until now.
        return "";
    }

    public List<String> getWarningMessages() {
        return ConfigFileUtils.getWarningMessages();
    }

    public boolean isWarningListNotEmpty() {
        if (ConfigFileUtils.getWarningMessages() == null) {
            return true;
        }
        return ConfigFileUtils.getWarningMessages().size() > 0;
    }

    public List<ConfigFile> getConfigFiles() {
        if (this.configFiles == null) {
            this.configFiles = ConfigFileUtils.getAllConfigFiles();
            this.initConfigFileDates();
        }

        // This code block is only needed if the warnings for wrongly configured paths are used in the ConfigFileUtils.
        /*
        if (this.pusher != null) {
            this.pusher.send("update");
            log.debug("Updated GUI");
        } else {
            log.error("pusher is null in ConfigFileEditorPlugin!");
        }
        */

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
        return this.currentConfigFile.getConfigDirectory().getDirectory() + this.currentConfigFile.getFileName();
    }

    public boolean isActiveConfigFile(ConfigFile configFile) {
        return this.findConfigFileIndex(configFile) == this.currentConfigFileIndex;
    }

    public void editConfigFile(ConfigFile configFile) {
        int index = this.findConfigFileIndex(configFile);
        if (this.hasFileContentChanged()) {
            this.configFileContentChanged = true;
            this.configFileIndexAfterSaveOrIgnore = index;
            this.validationError = false;
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

    public void save() throws ParserConfigurationException, SAXException, IOException {
        if (this.getCurrentConfigFileFileName().endsWith(".xml") && !checkXML()) {
            return;
        }
        if (this.getCurrentConfigFileFileName().endsWith(".properties") && !checkProperties()) {
            return;
        }
        // Only create a backup if the new file content differs from the existing file content
        if (this.hasFileContentChanged()) {
            ConfigFileUtils.createBackup(this.currentConfigFile);
        }
        String directory = this.currentConfigFile.getConfigDirectory().getDirectory();
        ConfigFileUtils.writeFile(directory, this.getCurrentConfigFileFileName(), this.currentConfigFileFileContent);
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

    private boolean checkProperties() throws UnsupportedEncodingException, IOException {
        PropertiesConfiguration apacheProp = new PropertiesConfiguration();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(this.currentConfigFileFileContent.getBytes("UTF-8"))) {
            apacheProp.load(bais);
        } catch (ConfigurationException | ConfigurationRuntimeException e) {
            Helper.setFehlerMeldung("configFileEditor", e.getMessage(), "");
            Helper.setFehlerMeldung("configFileEditor", "File was not saved, because the properties format is not well-formed", "");
            return false;
        }
        return true;
    }

    private boolean checkXML() throws ParserConfigurationException, SAXException, IOException {
        boolean ok = true;
        List<XMLError> errors = checkXMLWellformed(this.currentConfigFileFileContent);
        if (!errors.isEmpty()) {
            for (XMLError error : errors) {
                Helper.setFehlerMeldung("configFileEditor",
                        String.format("Line %d column %d: %s", error.getLine(), error.getColumn(), error.getMessage()), "");
            }
            if (errors.stream().anyMatch(e -> e.getSeverity().equals("ERROR") || e.getSeverity().equals("FATAL"))) {
                this.validationError = true;
                //this needs to be done, so the modal won't appear repeatedly and ask the user if he wants to save.
                this.configFileIndexAfterSaveOrIgnore = -1;
                this.configFileContentChanged = false;
                Helper.setFehlerMeldung("configFileEditor", "File was not saved, because the XML is not well-formed", "");
                ok = false;
            }
        } else {
            this.validationError = false;
        }
        return ok;
    }

    private boolean hasFileContentChanged() {
        if (this.currentConfigFile == null) {
            return false;
        }
        String fileContent = ConfigFileUtils.readFile(this.getCurrentConfigFileFileName());
        fileContent = fileContent.replace("\r\n", "\n");
        fileContent = fileContent.replace("\r", "\n");
        String editorContent = this.currentConfigFileFileContent;
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
        this.validationError = false;
    }

    public String getExplanationTitle() {
        String key = ConfigFileEditorAdministrationPlugin.MESSAGE_KEY_PREFIX + "_explanation_for";
        String translation = Helper.getTranslation(key);
        String fileName = this.currentConfigFile.getFileName();
        return translation + " " + fileName;
    }

    private List<XMLError> checkXMLWellformed(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        ReportErrorsErrorHandler eh = new ReportErrorsErrorHandler();
        builder.setErrorHandler(eh);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes("UTF-8"))) {
            builder.parse(bais);
        } catch (SAXParseException e) {
            //ignore this, because we collect the errors in the errorhandler and give them to the user.
        }

        return eh.getErrors();
    }
}