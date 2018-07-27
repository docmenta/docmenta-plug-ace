/*
 * AceHighlightPlugin.java
 * 
 *  Copyright (C) 2016  Manfred Paula, http://www.docmenta.org
 *   
 *  This file is part of Docmenta. Docmenta is free software: you can 
 *  redistribute it and/or modify it under the terms of the GNU Lesser 
 *  General Public License as published by the Free Software Foundation, 
 *  either version 3 of the License, or (at your option) any later version.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Docmenta.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.docma.plugins.ace;

import java.io.*;
import java.util.*;

import org.docma.plugin.*;
import org.docma.plugin.web.*;
import org.docma.coreapi.DocConstants;
import org.docma.plugin.internals.TextFileHandler;
import org.docma.util.Log;

import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Window;


public class AceHighlightPlugin implements WebPlugin, UIListener
{
    public static final String ACE_PROPS_FILENAME = "ace.properties";
    private static final String ACE_INSTALL_PATH = "apps" + File.separator + 
                                                  "internal_text_editor" + File.separator +
                                                  "plugin_ace" + File.separator + 
                                                  "src-min-noconf-1-2-8";
    public static final String HIGHLIGHT_FEATURE_NAME = "highlight";

    public static final String PROP_EXTENSIONS = "extensions";
    public static final String PROP_THEME = "theme";
    public static final String PROP_EDIT_BGCOLOR = "edit_bgcolor";
    public static final String PROP_VIEW_BGCOLOR = "view_bgcolor";
    public static final String PROP_TAB_SIZE = "tab_size";
    public static final String PROP_SOFT_TABS = "soft_tabs";
    public static final String PROP_FONT_SIZE = "font_size";
    public static final String PROP_LINE_HIGHLIGHT = "line_highlight";
    public static final String PROP_EDITOR_INIT = "editor_init";
    public static final String PROP_MODE = "mode";
    public static final String PROP_SYNTAX_CHECK = "syntax_check";
    public static final String PROP_WORD_WRAP = "word_wrap";
    public static final String PROP_AUTO_COMPLETE = "auto_complete";

    private String configDialogId = null;
    private String plugId = null;
    private WebPluginContext webCtx = null;
    // Loaded Properties
    private Properties defaultProps = null;
    private String[] aceThemes = null;
    private String[] aceModes = null;
    private long lastListUpdate = -1;
    
    private final Map<String, SortedMap<String, String>> extKeyCache = new HashMap();
    

    /* --------------- Interface WebPlugin ------------------- */
    
    @Override
    public void onLoad(PluginContext ctx) throws Exception 
    {
        plugId = ctx.getPluginId();

        // Read templates        
        File plugDir = ctx.getPluginDirectory();

        // Read properties
        loadPluginProps(ctx);
        AceInsertion ins = new AceInsertion(plugId, HIGHLIGHT_FEATURE_NAME, this, plugDir);

        // Add Ace to internal text editor
        TextFileHandler.clearPluginInsertions(plugId);  // remove previous settings, just to be sure
        TextFileHandler.insertScript(ins);
    }

    @Override
    public void onUnload(PluginContext ctx) throws Exception
    {
        TextFileHandler.clearPluginInsertions(plugId);
    }
    
    @Override
    public void onShowConfigDialog(WebPluginContext ctx, WebUserSession webSess)
    {
//        Window dialog = getConfigDialog(webSess);
//        AceConfigComposer composer = (AceConfigComposer) dialog.getAttribute("$composer");
//        if (pluginProps == null) {
//            loadPluginProps(ctx);
//        }
//        composer.showConfigDialog(this, ctx, webSess);
    }
    
    @Override
    public void onInitMainWindow(WebPluginContext ctx, WebUserSession webSess) 
    {
        this.webCtx = ctx;
        // Add user tab
        String tabId = getAceTabId();
        String tabTitle = "Ace";
        webSess.addUserTab(ctx, tabId, tabTitle, -1, "plugins/ace_text_highlight/ace_config.zul");
        webSess.setUIListener(ctx, this);
    }
    
    /* --------------- Interface UIListener ------------------- */
    
    @Override
    public void onEvent(UIEvent evt) 
    {
        if (DocConstants.DEBUG) {
            Log.info("Ace plugin has received event: " + evt.getName() + " for component " + evt.getTargetId());
        }
        String ename = evt.getName();
        String targetId = evt.getTargetId();
        String aceTabId = getAceTabId();
        WebUserSession sess = evt.getSession();
        Component comp = (Component) sess.getTabComponent(aceTabId, "aceConfigContainer");
        if (comp != null) {
            AceConfigComposer composer = (AceConfigComposer) comp.getAttribute("$composer");
            if (aceTabId.equals(targetId)) {  // ace tab event
                if ("onSelect".equalsIgnoreCase(ename)) {   // tab has been selected
                    composer.onSelectTab(this, webCtx, sess);
                }
            } else if (composer.getUserDialogId().equalsIgnoreCase(targetId)) { 
                if ("onOpen".equalsIgnoreCase(ename)) {         // dialog is opened
                    composer.onDialogOpen();
                } else if ("onClose".equalsIgnoreCase(ename)) { // dialog is closed
                    composer.onDialogClose();
                }
            } else if ("onClick".equalsIgnoreCase(ename)) {
                if (composer.getOkayButtonId().equalsIgnoreCase(targetId)) {  
                    // dialog OK button clicked
                    composer.onOkayClick();
                } else if (composer.getCancelButtonId().equalsIgnoreCase(targetId)) {
                    // dialog Cancel button clicked
                    composer.onCancelClick();
                }
            }
        }
    }

    /* --------------- Package local ------------------- */
    
    String getAceTabId()
    {
        return plugId + "UserTab";
    }
    
    File getAceInstallDir(WebPluginContext ctx)
    {
        return new File(ctx.getWebAppDirectory(), ACE_INSTALL_PATH);
    }
    
    String[] getAvailableThemes(WebPluginContext ctx)
    {
        readAceModesAndThemes(ctx);
        return (aceThemes == null) ? new String[0] : aceThemes;
    }

    String[] getAvailableModes(WebPluginContext ctx)
    {
        readAceModesAndThemes(ctx);
        return (aceModes == null) ? new String[0] : aceModes;
    }

    void saveUserProps(User usr, Properties props)
    {
        String[] names = props.keySet().toArray(new String[props.size()]);
        String[] values = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            values[i] = props.getProperty(names[i]);
        }
        usr.setProperties(names, values);
    }
    
    SortedMap<String, String> getExtensionMap(User usr)
    {
        String exts = getProp(usr, PROP_EXTENSIONS, "");
        if (extKeyCache.size() > 1000) {  // clear cache if too large
            extKeyCache.clear();
        }
        SortedMap<String, String> extKeyMap = extKeyCache.get(exts);
        if (extKeyMap == null) {
            extKeyMap = createExtMap(exts);
            extKeyCache.put(exts, extKeyMap);
        }
        return extKeyMap;
    }
    
    SortedSet<String> getExtensionKeys(User usr)
    {
        return new TreeSet(getExtensionMap(usr).values());
    }
    
    void setExtensionKeys(Properties props, SortedSet<String> extKeys)
    {
        StringBuilder sb = new StringBuilder();
        for (String k : extKeys) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(k);
        }
        // Note: If length of sb is 0, then set a single space instead of an  
        // empty string. Setting an empty string would cause fall back to the 
        // default settings. Setting a space prevents fall back to the default.
        String s = (sb.length() > 0) ? sb.toString() : " ";
        setProp(props, PROP_EXTENSIONS, s);
    }
    
    String getDefaultTheme(User usr)
    {
        return getProp(usr, PROP_THEME, "").trim();
    }

    void setDefaultTheme(Properties props, String theme)
    {
        setProp(props, PROP_THEME, theme);
    }

    String getDefaultEditBgColor(User usr) 
    {
        return getProp(usr, PROP_EDIT_BGCOLOR, "").trim();
    }

    void setDefaultEditBgColor(Properties props, String col)
    {
        setProp(props, PROP_EDIT_BGCOLOR, col);
    }
    
    String getDefaultViewBgColor(User usr) 
    {
        return getProp(usr, PROP_VIEW_BGCOLOR, "").trim();
    }

    void setDefaultViewBgColor(Properties props, String col)
    {
        setProp(props, PROP_VIEW_BGCOLOR, col);
    }
    
    int getTabSize(User usr) 
    {
        final int DEFAULT_SIZE = 4;
        String val = getProp(usr, PROP_TAB_SIZE, Integer.toString(DEFAULT_SIZE));
        try {
            int sz = Integer.parseInt(val);
            return (sz < 1) ? DEFAULT_SIZE : sz;
        } catch (Exception ex) {
            return DEFAULT_SIZE;
        }
    }

    void setTabSize(Properties props, int sz)
    {
        setProp(props, PROP_TAB_SIZE, Integer.toString(sz));
    }
    
    boolean isSoftTabs(User usr) 
    {
        return getProp(usr, PROP_SOFT_TABS, "true").trim().equalsIgnoreCase("true");
    }

    void setSoftTabs(Properties props, boolean enabled)
    {
        setProp(props, PROP_SOFT_TABS, enabled ? "true" : "false");
    }
    
    String getFontSize(User usr) 
    {
        return getProp(usr, PROP_FONT_SIZE, "").trim();
    }

    void setFontSize(Properties props, String sz)
    {
        setProp(props, PROP_FONT_SIZE, sz);
    }
    
    boolean isActiveLineHighlight(User usr) 
    {
        return getProp(usr, PROP_LINE_HIGHLIGHT, "true").trim().equalsIgnoreCase("true");
    }

    void setActiveLineHighlight(Properties props, boolean enabled)
    {
        setProp(props, PROP_LINE_HIGHLIGHT, enabled ? "true" : "false");
    }
    
    String getCustomEditorInit(User usr) 
    {
        return getProp(usr, PROP_EDITOR_INIT, "").trim();
    }
    
    void setCustomEditorInit(Properties props, String js)
    {
        if ((js == null) || js.equals("")) {
            js = " ";  // set to single space to avoid fall back to default 
        }
        setProp(props, PROP_EDITOR_INIT, js);
    }

    String getMode(User usr, String extKey) 
    {
        return getProp(usr, PROP_MODE + "." + extKey, "").trim();
    }
    
    void setMode(Properties props, String extKey, String mode) 
    {
        setProp(props, PROP_MODE + "." + extKey, mode);
    }
    
    String getTheme(User usr, String extKey) 
    {
        return getProp(usr, PROP_THEME + "." + extKey, "").trim();
    }
    
    void setTheme(Properties props, String extKey, String theme) 
    {
        setProp(props, PROP_THEME + "." + extKey, theme);
    }
    
    boolean isSyntaxCheck(User usr, String extKey)
    {
        return getProp(usr, PROP_SYNTAX_CHECK + "." + extKey, "true").trim().equals("true");
    }
    
    void setSyntaxCheck(Properties props, String extKey, boolean enabled)
    {
        setProp(props, PROP_SYNTAX_CHECK + "." + extKey, enabled ? "true" : "false");
    }
    
    boolean isWordWrap(User usr, String extKey)
    {
        return getProp(usr, PROP_WORD_WRAP + "." + extKey, "false").trim().equals("true");
    }
    
    void setWordWrap(Properties props, String extKey, boolean enabled)
    {
        setProp(props, PROP_WORD_WRAP + "." + extKey, enabled ? "true" : "false");
    }
    
    boolean isAutoComplete(User usr, String extKey)
    {
        return getProp(usr, PROP_AUTO_COMPLETE + "." + extKey, "true").trim().equals("true");
    }
    
    void setAutoComplete(Properties props, String extKey, boolean enabled)
    {
        setProp(props, PROP_AUTO_COMPLETE + "." + extKey, enabled ? "true" : "false");
    }
    
    String getEditBgColor(User usr, String extKey) 
    {
        return getProp(usr, PROP_EDIT_BGCOLOR + "." + extKey, "").trim();
    }

    void setEditBgColor(Properties props, String extKey, String col)
    {
        setProp(props, PROP_EDIT_BGCOLOR + "." + extKey, col);
    }
    
    String getViewBgColor(User usr, String extKey) 
    {
        return getProp(usr, PROP_VIEW_BGCOLOR + "." + extKey, "").trim();
    }

    void setViewBgColor(Properties props, String extKey, String col)
    {
        setProp(props, PROP_VIEW_BGCOLOR + "." + extKey, col);
    }
    
//    void clearExtensions()
//    {
//        Iterator<String> it = extensionKeys.iterator();
//        while (it.hasNext()) {
//            String extKey = it.next();
//            pluginProps.remove("mode." + extKey);
//            pluginProps.remove("theme." + extKey);
//            pluginProps.remove("syntax_check." + extKey);
//            pluginProps.remove("word_wrap." + extKey);
//            pluginProps.remove("auto_complete." + extKey);
//            pluginProps.remove("edit_bgcolor." + extKey);
//            pluginProps.remove("view_bgcolor." + extKey);
//        }
//    }
    
    /* --------------- Private methods ------------------- */

    private String getProp(User usr, String propName, String defaultValue)
    {
        String val = usr.getProperty(plugId + "." + propName);
        if ((val == null) || val.equals("")) {  // fall back to default
            val = defaultProps.getProperty(propName, defaultValue);
        }
        return val;
    }
    
    private void setProp(Properties props, String propName, String propValue)
    {
        if (propValue == null) {
            props.remove(propName);
        } else {
            props.setProperty(plugId + "." + propName, propValue);
        }
    }

    private void readAceModesAndThemes(WebPluginContext ctx)
    {
        long now = System.currentTimeMillis();
        // rescan folder after 5 seconds
        if ((aceModes == null) || (aceThemes == null) || ((now - lastListUpdate) > 1000 * 5)) {
            File dir = getAceInstallDir(ctx);
            String[] fnames = dir.list();
            if (fnames == null) {
                return;
            }
            ArrayList<String> themes = new ArrayList();
            ArrayList<String> modes = new ArrayList();
            final String JS_SUFFIX = ".js";
            final String MODE_PREFIX = "mode-";
            final String THEME_PREFIX = "theme-";
            for (String fn : fnames) {
                if (! fn.endsWith(JS_SUFFIX)) {
                    continue;
                }
                int end_pos = fn.length() - JS_SUFFIX.length();
                if (fn.startsWith(MODE_PREFIX)) {
                    modes.add(fn.substring(MODE_PREFIX.length(), end_pos));
                } else if (fn.startsWith(THEME_PREFIX)) {
                    themes.add(fn.substring(THEME_PREFIX.length(), end_pos));
                }
            }
            aceModes = modes.toArray(new String[modes.size()]);
            aceThemes = themes.toArray(new String[themes.size()]);
            lastListUpdate = now;
        }
    }
    
    private Window getConfigDialog(WebUserSession webSess)
    {
        Window dialog = null;
        if (configDialogId != null) {
            try {
                dialog = (Window) webSess.getDialog(configDialogId);
            } catch (Exception ex) {}  // dialog with given id does not yet exist
        }
        if (dialog == null) {
            configDialogId = webSess.addDialog("plugins/ace_text_highlight/ace_config_dialog.zul");
            dialog = (Window) webSess.getDialog(configDialogId);
        }
        return dialog;
    }
    
    private SortedMap<String, String> createExtMap(String line)
    {
        TreeMap<String, String> res = new TreeMap();
        if (line != null) {
            line = line.toLowerCase();
            StringTokenizer st = new StringTokenizer(line, ", ");
            while (st.hasMoreTokens()) {
                String extKey = st.nextToken();
                // extKey is either a single file extension or multiple file
                // extensions separated by a pipeline character (|).
                if (extKey.indexOf('|') < 0) {
                    res.put(extKey, extKey);
                } else {
                    for (String ext : extKey.split("[\\| ]")) {
                        if (! ext.equals("")) {
                            res.put(ext, extKey);
                        }
                    }
                }
            }
        }
        return res;
    }

    /**
     * Normalizes the file extension to lower case letters and removes any
     * dot at the beginning of the file extension.
     * This is required to allow file extension comparison using the normal
     * equals() implementation of java.lang.String.
     *
     * @param ext
     * @return 
     */
    private static String normalizeExt(String ext)
    {
        ext = ext.trim();
        if (ext.startsWith(".")) {
            ext = ext.substring(1).trim();
        }
        return ext.toLowerCase();
    }

    private File getPluginPropsFile(PluginContext ctx)
    {
        File f = ctx.getPluginDirectory();
        return new File(f, ACE_PROPS_FILENAME);
    }
    
    private synchronized void loadPluginProps(PluginContext ctx) 
    {
        File pfile = getPluginPropsFile(ctx);
        try {
            defaultProps = new Properties();
            // ClassLoader cl = PropertiesLoader.class.getClassLoader();
            // InputStream fin = cl.getResourceAsStream(inifilename);
            if (pfile.exists()) {
                InputStream fin = new FileInputStream(pfile);
                try {
                    defaultProps.load(fin);
                } finally {
                    fin.close();
                }
            }
        } catch (Exception ex) {
            defaultProps = null;
            Log.error("Could not load plugin properties: " + pfile);
            throw new RuntimeException(ex);
        }
    }

    private synchronized void savePluginProps(PluginContext ctx)
    {
        if (defaultProps == null) return; 

        File pfile = getPluginPropsFile(ctx);
        if (DocConstants.DEBUG) {
            Log.info("Saving plugin properties: " + pfile);
        }
        FileOutputStream fout = null;
        try {
            fout = new FileOutputStream(pfile);
            defaultProps.store(fout, "Plugin properties");
            fout.close();
        } catch (Exception ex) {
            Log.error("Could not save plugin properties: " + pfile);
            if (fout != null) try { fout.close(); } catch (Exception ex2) {}
            throw new RuntimeException(ex);
        }
    }

}