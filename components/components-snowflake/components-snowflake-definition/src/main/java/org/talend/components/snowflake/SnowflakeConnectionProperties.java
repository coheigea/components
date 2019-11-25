// ============================================================================
//
// Copyright (C) 2006-2019 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.components.snowflake;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.properties.ComponentPropertiesImpl;
import org.talend.components.api.properties.ComponentReferenceProperties;
import org.talend.components.common.UserPasswordProperties;
import org.talend.components.snowflake.tsnowflakeconnection.AuthenticationType;
import org.talend.components.snowflake.tsnowflakeconnection.TSnowflakeConnectionDefinition;
import org.talend.daikon.i18n.GlobalI18N;
import org.talend.daikon.i18n.I18nMessages;
import org.talend.daikon.properties.PresentationItem;
import org.talend.daikon.properties.ValidationResult;
import org.talend.daikon.properties.ValidationResultMutable;
import org.talend.daikon.properties.presentation.Form;
import org.talend.daikon.properties.presentation.Widget;
import org.talend.daikon.properties.property.Property;
import org.talend.daikon.sandbox.SandboxedInstance;
import org.talend.daikon.serialize.PostDeserializeSetup;
import org.talend.daikon.serialize.migration.SerializeSetVersion;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import static org.talend.components.snowflake.SnowflakeDefinition.SOURCE_OR_SINK_CLASS;
import static org.talend.components.snowflake.SnowflakeDefinition.USE_CURRENT_JVM_PROPS;
import static org.talend.components.snowflake.SnowflakeDefinition.getSandboxedInstance;
import static org.talend.daikon.properties.presentation.Widget.widget;
import static org.talend.daikon.properties.property.PropertyFactory.newBoolean;
import static org.talend.daikon.properties.property.PropertyFactory.newEnum;
import static org.talend.daikon.properties.property.PropertyFactory.newInteger;
import static org.talend.daikon.properties.property.PropertyFactory.newString;

public class SnowflakeConnectionProperties extends ComponentPropertiesImpl
        implements SnowflakeProvideConnectionProperties, SerializeSetVersion {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowflakeConnectionProperties.class);

    private static final I18nMessages i18nMessages =
            GlobalI18N.getI18nMessageProvider().getI18nMessages(SnowflakeConnectionProperties.class);

    private static final int SPECIFY_LOGIN_TIMEOUT_VERSION_NUMBER = 1;

    protected static final int DEFAULT_LOGIN_TIMEOUT = 15;

    private static final String USERPASSWORD = "userPassword";

    public static final String FORM_WIZARD = "Wizard";

    // Only for the wizard use
    public Property<String> name = newString("name").setRequired();

    public Property<Integer> loginTimeout = newInteger("loginTimeout");

    public Property<String> account = newString("account").setRequired(); //$NON-NLS-1$

    public Property<SnowflakeRegion> region = newEnum("region", SnowflakeRegion.class);

    public Property<AuthenticationType> authenticationType = newEnum("authenticationType", AuthenticationType.class);

    public UserPasswordProperties userPassword = new UserPasswordProperties(USERPASSWORD);

    public Property<String> keyPath = newString("keyPath");

    public Property<String> keyPhrase =
            newString("keyPhrase").setFlags(EnumSet.of(Property.Flags.ENCRYPT, Property.Flags.SUPPRESS_LOGGING));

    public Property<String> warehouse = newString("warehouse"); //$NON-NLS-1$

    public Property<String> db = newString("db").setRequired(); //$NON-NLS-1$

    public Property<String> schemaName = newString("schemaName").setRequired(); //$NON-NLS-1$

    public Property<Boolean> useCustomRegion = newBoolean("useCustomRegion");

    public Property<String> customRegionID = newString("customRegionID").setRequired();

    public Property<String> role = newString("role"); //$NON-NLS-1$

    public Property<String> jdbcParameters = newString("jdbcParameters");

    public Property<Boolean> autoCommit = newBoolean("autoCommit", true);

    public String talendProductVersion;

    // Presentation items
    public PresentationItem testConnection = new PresentationItem("testConnection", "Test connection");

    public PresentationItem advanced = new PresentationItem("advanced", "Advanced...");

    public ComponentReferenceProperties<SnowflakeConnectionProperties> referencedComponent =
            new ComponentReferenceProperties<>("referencedComponent", TSnowflakeConnectionDefinition.COMPONENT_NAME);

    public SnowflakeConnectionProperties(String name) {
        super(name);
    }

    @Override
    public void setupProperties() {
        super.setupProperties();
        loginTimeout.setValue(DEFAULT_LOGIN_TIMEOUT);
        region.setValue(SnowflakeRegion.AWS_US_WEST);
        authenticationType.setValue(AuthenticationType.BASIC);
        keyPath.setValue("");
        useCustomRegion.setValue(false);
    }

    @Override
    public void setupLayout() {
        super.setupLayout();

        Form wizardForm = Form.create(this, FORM_WIZARD);
        wizardForm.addRow(name);
        wizardForm.addRow(account);
        wizardForm.addRow(region);
        wizardForm.addRow(authenticationType);
        wizardForm.addRow(userPassword.getForm(Form.MAIN));
        wizardForm.addRow(widget(keyPath).setWidgetType(Widget.FILE_WIDGET_TYPE));
        wizardForm.addColumn(widget(keyPhrase).setWidgetType(Widget.HIDDEN_TEXT_WIDGET_TYPE));
        wizardForm.addRow(warehouse);
        wizardForm.addRow(schemaName);
        wizardForm.addRow(db);
        wizardForm.addRow(widget(advanced).setWidgetType(Widget.BUTTON_WIDGET_TYPE));
        wizardForm.addColumn(widget(testConnection).setLongRunning(true).setWidgetType(Widget.BUTTON_WIDGET_TYPE));

        Form mainForm = Form.create(this, Form.MAIN);
        mainForm.addRow(account);
        mainForm.addRow(region);
        mainForm.addRow(authenticationType);
        mainForm.addRow(userPassword.getForm(Form.MAIN));
        mainForm.addRow(widget(keyPath).setWidgetType(Widget.FILE_WIDGET_TYPE));
        mainForm.addColumn(widget(keyPhrase).setWidgetType(Widget.HIDDEN_TEXT_WIDGET_TYPE));
        mainForm.addRow(warehouse);
        mainForm.addRow(schemaName);
        mainForm.addRow(db);

        Form advancedForm = Form.create(this, Form.ADVANCED);
        advancedForm.addRow(autoCommit);
        advancedForm.addRow(jdbcParameters);
        advancedForm.addRow(useCustomRegion);
        advancedForm.addColumn(customRegionID);
        advancedForm.addRow(loginTimeout);
        advancedForm.addRow(role);
        advanced.setFormtoShow(advancedForm);

        // A form for a reference to a connection, used in a tSnowflakeInput for example
        Form refForm = Form.create(this, Form.REFERENCE);
        Widget compListWidget = widget(referencedComponent).setWidgetType(Widget.COMPONENT_REFERENCE_WIDGET_TYPE);
        refForm.addRow(compListWidget);
        refForm.addRow(mainForm);
    }

    public void afterAdvanced() {
        refreshLayout(getForm(FORM_WIZARD));
    }

    public void afterAuthenticationType() {
        refreshLayout(getForm(Form.MAIN));
        refreshLayout(getForm(FORM_WIZARD));
    }

    public void afterUseCustomRegion() {
        refreshLayout(getForm(Form.MAIN));
        refreshLayout(getForm(Form.REFERENCE));
        refreshLayout(getForm(Form.ADVANCED));
        refreshLayout(getForm(FORM_WIZARD));
    }

    public void afterReferencedComponent() {
        refreshLayout(getForm(Form.MAIN));
        refreshLayout(getForm(Form.REFERENCE));
        refreshLayout(getForm(Form.ADVANCED));
    }

    protected void setHiddenProps(Form form, boolean hidden) {
        form.getWidget(USERPASSWORD).setHidden(hidden);
        form.getWidget(account.getName()).setHidden(hidden);
        form.getWidget(region.getName()).setHidden(hidden);
        form.getWidget(authenticationType.getName()).setHidden(hidden);
        form.getWidget(warehouse.getName()).setHidden(hidden);
        form.getWidget(schemaName.getName()).setHidden(hidden);
        form.getWidget(db.getName()).setHidden(hidden);
    }

    @Override
    public void refreshLayout(Form form) {
        super.refreshLayout(form);

        boolean useOtherConnection = getReferencedComponentId() != null;
        if (form.getName().equals(Form.MAIN) || form.getName().equals(FORM_WIZARD)) {
            if (useOtherConnection) {
                setHiddenProps(form, true);
            } else {
                setHiddenProps(form, false);
                // Do nothing
                form.setHidden(false);
                boolean isBasicAuth = authenticationType.getValue() == AuthenticationType.BASIC;
                userPassword.getForm(Form.MAIN).getWidget(userPassword.password.getName()).setHidden(!isBasicAuth);
                form.getWidget(keyPath.getName()).setHidden(isBasicAuth);
                form.getWidget(keyPhrase.getName()).setHidden(isBasicAuth);
                form.getWidget(region.getName()).setHidden(useCustomRegion.getValue());
            }
        }

        if (form.getName().equals(Form.ADVANCED)) {
            if (useOtherConnection) {
                form.setHidden(true);
            } else {
                form.setHidden(false);

                getForm(Form.ADVANCED).getWidget(customRegionID).setVisible(useCustomRegion.getValue());
            }
        }
    }

    public ValidationResult validateTestConnection() throws Exception {
        try (SandboxedInstance sandboxedInstance = getSandboxedInstance(SOURCE_OR_SINK_CLASS, USE_CURRENT_JVM_PROPS)) {
            SnowflakeRuntimeSourceOrSink ss = (SnowflakeRuntimeSourceOrSink) sandboxedInstance.getInstance();
            ss.initialize(null, this);
            ValidationResultMutable vr = new ValidationResultMutable(ss.validateConnection(this));
            if (vr.getStatus() == ValidationResult.Result.OK) {
                vr.setMessage(i18nMessages.getMessage("messages.connectionSuccessful"));
                getForm(FORM_WIZARD).setAllowForward(true);
            } else {
                getForm(FORM_WIZARD).setAllowForward(false);
            }
            return vr;
        }
    }

    @Override
    public SnowflakeConnectionProperties getConnectionProperties() {
        if (referencedComponent.referenceType.getValue() == null || referencedComponent.referenceType
                .getValue() == ComponentReferenceProperties.ReferenceType.THIS_COMPONENT) {
            return this;
        }
        return referencedComponent.getReference();
    }

    public String getReferencedComponentId() {
        return referencedComponent.componentInstanceId.getStringValue();
    }

    public SnowflakeConnectionProperties getReferencedConnectionProperties() {
        SnowflakeConnectionProperties refProps = referencedComponent.getReference();
        if (refProps != null) {
            return refProps;
        }
        return null;
    }

    public Properties getJdbcProperties() throws Exception {
        String user = userPassword.userId.getStringValue();
        String password = userPassword.password.getStringValue();
        String loginTimeout = String.valueOf(this.loginTimeout.getValue());

        Properties properties = new Properties();

        if (user != null) {
            properties.put("user", user);
        }
        if (AuthenticationType.BASIC == authenticationType.getValue()) {
            if (password != null) {
                properties.put("password", password);
            }
        } else {
            if (keyPath.getValue() != null) {
                properties.put("privateKey", getPrivateKey());
            }
        }

        if (loginTimeout != null) {
            properties.put("loginTimeout", String.valueOf(this.loginTimeout.getValue()));
        }

        return properties;
    }

    private PrivateKey getPrivateKey() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(keyPath.getValue()));
        String encrypted = new String(bytes)
                .replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "")
                .replace("-----END ENCRYPTED PRIVATE KEY-----", "");
        EncryptedPrivateKeyInfo pkInfo = new EncryptedPrivateKeyInfo(Base64.getMimeDecoder().decode(encrypted));
        SecretKeyFactory pbeKeyFactory = SecretKeyFactory.getInstance(pkInfo.getAlgName());
        PBEKeySpec keySpec = new PBEKeySpec(keyPhrase.getValue().toCharArray());
        PKCS8EncodedKeySpec encodedKeySpec = pkInfo.getKeySpec(pbeKeyFactory.generateSecret(keySpec));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(encodedKeySpec);
    }

    public String getConnectionUrl() {
        StringBuilder connectionParams = new StringBuilder();
        String account = this.account.getStringValue();

        if (account == null || account.isEmpty()) {
            throw new IllegalArgumentException(i18nMessages.getMessage("error.missingAccount"));
        }

        String regionID = useCustomRegion.getValue() ? customRegionID.getValue() : region.getValue().getRegionID();
        String warehouse = this.warehouse.getStringValue();
        String db = this.db.getStringValue();
        String schema = schemaName.getStringValue();
        String role = this.role.getStringValue();

        appendProperty("warehouse", warehouse, connectionParams);
        appendProperty("db", db, connectionParams);
        appendProperty("schema", schema, connectionParams);
        appendProperty("role", role, connectionParams);
        appendProperty("application", getApplication(), connectionParams);

        StringBuilder url = new StringBuilder().append("jdbc:snowflake://").append(account);

        if (!StringUtils.isEmpty(regionID)) {
            url.append(".").append(regionID);
        }

        url.append(".snowflakecomputing.com").append("/?");

        String jdbcParameters = this.jdbcParameters.getStringValue();
        if (jdbcParameters != null && !jdbcParameters.isEmpty() && !"\"\"".equals(jdbcParameters)) {
            if (connectionParams.length() > 0) {
                connectionParams.append("&");
            }
            connectionParams.append(jdbcParameters);
        }
        url.append(connectionParams);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Snowflake connection jdbc URL : " + url);
        }

        return url.toString();
    }

    /**
     * Returns schema for permanent table specified either in this properties or in referenced properties
     * (other Connection component or metadata)
     *
     * @return schema for permanent table
     */
    public String getSchemaPermanent() {
        SnowflakeConnectionProperties connection = getConnectionProperties();
        String schemaValue = connection.schemaName.getValue();
        if (schemaValue == null) {
            return "";
        }
        return schemaValue;
    }

    /**
     * Returns database value specified by user either in this properties or in referenced properties
     * (other Connection component or metadata)
     *
     * @return database
     */
    public String getDatabase() {
        SnowflakeConnectionProperties connection = getConnectionProperties();
        String dbValue = connection.db.getValue();
        if (dbValue == null) {
            return "";
        }
        return dbValue;
    }

    private String getApplication() {
        StringBuilder application = new StringBuilder();
        application.append("Talend");
        if (StringUtils.isNotEmpty(talendProductVersion)) {
            application.append('-');
            application.append(talendProductVersion);
        }
        return application.toString();
    }

    private void appendProperty(String propertyName, String propertyValue, StringBuilder builder) {
        if (propertyValue != null && !propertyValue.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(propertyName).append("=").append(propertyValue);
        }
    }

    @Override
    public boolean postDeserialize(int version, PostDeserializeSetup setup, boolean persistent) {
        boolean migrated = super.postDeserialize(version, setup, persistent);
        if (version < SPECIFY_LOGIN_TIMEOUT_VERSION_NUMBER && loginTimeout.getValue() == null) {
            loginTimeout.setValue(DEFAULT_LOGIN_TIMEOUT);
            migrated = true;
        }
        return migrated;
    }

    @Override
    public int getVersionNumber() {
        return 1;
    }

}
