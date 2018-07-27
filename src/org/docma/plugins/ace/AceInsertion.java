/*
 * AceInsertion.java
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.SortedMap;
import org.docma.plugin.internals.ScriptInsertion;
import org.docma.plugin.internals.TextFileHandler;
import org.docma.plugin.User;
import org.docma.plugin.web.WebUserSession;

/**
 *
 * @author MP
 */
class AceInsertion implements ScriptInsertion
{
    public static final String META_TEMPLATE_FILENAME = "meta.tmpl";
    public static final String HEAD_TEMPLATE_FILENAME = "head.tmpl";
    public static final String BODY_END_TEMPLATE_FILENAME = "body_end.tmpl";
    
    private final String pluginId;
    private final String feature;
    private AceHighlightPlugin plugin;
    private String meta_template = "";
    private String head_template = "";
    private String body_end_template = "";
    
    AceInsertion(String pluginId, String feature, AceHighlightPlugin plugin, File plugDir) throws Exception
    {
        this.pluginId = pluginId;
        this.feature = feature;
        this.plugin = plugin;
        meta_template = readFileToString(new File(plugDir, META_TEMPLATE_FILENAME));
        head_template = readFileToString(new File(plugDir, HEAD_TEMPLATE_FILENAME));
        body_end_template = readFileToString(new File(plugDir, BODY_END_TEMPLATE_FILENAME));
    }

    /* ---------- Interface ScriptInsertion ------------ */
    
    @Override
    public String getPluginId() 
    {
        return pluginId;
    }

    @Override
    public String getFeature() 
    {
        return feature;
    }

    @Override
    public Set<String> getFileExtensions(WebUserSession userSess) 
    {
        User usr = userSess.getUser();
        SortedMap<String, String> extMap = plugin.getExtensionMap(usr);
        return extMap.keySet();
    }

    @Override
    public String getInsertion(WebUserSession userSess, String ext, String position) 
    {
        ext = normalizeExt(ext);
        User usr = userSess.getUser();
        SortedMap<String, String> extMap = plugin.getExtensionMap(usr);
        String extKey = extMap.get(ext);
        if (extKey == null) {  // no insertion exists for this extension
            if (extMap.containsKey("*")) {  // if wildcard setting exists
                extKey = "*";    // return insertion for wildcard setting
            } else {
                return "";       // no insertion exists
            }
        }
        if (position.equals(TextFileHandler.INSERT_HTML_META)) {
            return meta_template;
        } else if (position.equals(TextFileHandler.INSERT_HTML_HEAD)) {
            return head_template;
        } else if (position.equals(TextFileHandler.INSERT_BODY_END)) {
            return htmlBodyEnd(usr, extKey);
        } else if (position.equals(TextFileHandler.INSERT_JS_BEFORE_SAVE)) {
            return "syncEditForm();";
        } else if (position.equals(TextFileHandler.INSERT_JS_ENTER_EDIT)) {
            return "enterEditMode();";
        } else if (position.equals(TextFileHandler.INSERT_JS_ENTER_VIEW)) {
            return "enterViewMode();";
        } else if (position.equals(TextFileHandler.INSERT_TOOLBAR)) {
            String searchLabel = userSess.getLabel("ace_text_highlight.search_text");
            return "<a href=\"javascript:getEditTxtFrame().showTextSearch();\">" + searchLabel + "</a>";
        // } else if (position.equals(TextFileHandler.INSERT_BODY_START)) {            
        // } else if (position.equals(TextFileHandler.INSERT_JS_ON_LOAD)) {
        } else {
            return "";
        }
    }
    
    /* ---------- Private methods ------------ */

    private String htmlBodyEnd(User usr, String extKey)
    {
        String mode = plugin.getMode(usr, extKey);
        if (mode.equals("")) {
            mode = "ace/mode/text";
        } else if (! mode.startsWith("ace/mode/")) {
            mode = "ace/mode/" + mode;
        }
        String theme = plugin.getTheme(usr, extKey);
        if (theme.equals("")) {
            theme = plugin.getDefaultTheme(usr);
        }
        if ((theme.length() > 0) && !theme.startsWith("ace/theme/")) {
            theme = "ace/theme/" + theme;
        }
        String edit_bgcolor = plugin.getEditBgColor(usr, extKey);
        if (edit_bgcolor.equals("")) {
            edit_bgcolor = plugin.getDefaultEditBgColor(usr);
        }
        String view_bgcolor = plugin.getViewBgColor(usr, extKey);
        if (view_bgcolor.equals("")) {
            view_bgcolor = plugin.getDefaultViewBgColor(usr);
        }

        StringBuilder sb = new StringBuilder(body_end_template);
        strReplace(sb, "###theme###", theme);
        strReplace(sb, "###mode###", mode);
        strReplace(sb, "###syntax_check###", plugin.isSyntaxCheck(usr, extKey) ? "true" : "false");
        strReplace(sb, "###tab_size###", "" + plugin.getTabSize(usr));
        strReplace(sb, "###soft_tabs###", plugin.isSoftTabs(usr) ? "true" : "false");
        strReplace(sb, "###word_wrap###", plugin.isWordWrap(usr, extKey) ? "true" : "false");
        strReplace(sb, "###highlight_active_line###", plugin.isActiveLineHighlight(usr) ? "true" : "false");
        strReplace(sb, "###auto_complete###", plugin.isAutoComplete(usr, extKey) ? "true" : "false");
        strReplace(sb, "###font_size###", plugin.getFontSize(usr));
        strReplace(sb, "###edit_bgcolor###", edit_bgcolor);
        strReplace(sb, "###view_bgcolor###", view_bgcolor);
        strReplace(sb, "###custom_editor_init###", plugin.getCustomEditorInit(usr));
        return sb.toString();
    }

    private void strReplace(StringBuilder sb, String target, String replacement)
    {
        int search_pos = 0;
        do {
            int pos = sb.indexOf(target, search_pos);
            if (pos < 0) {
                return;
            }
            sb.replace(pos, pos + target.length(), replacement);
            search_pos = pos + replacement.length();
        } while (search_pos < sb.length());
    }
    
    /**
     * Normalizes the file extension to lower case letters and removes any
     * dot at the beginning of the file extension.
     * This is required to allow file extension comparison using the normal
     * equals() implementation of java.lang.String.
     *
     * @param ext  the file extension
     * @return  the normalized file extension
     */
    private static String normalizeExt(String ext)
    {
        ext = ext.trim();
        if (ext.startsWith(".")) {
            ext = ext.substring(1).trim();
        }
        return ext.toLowerCase();
    }

    public static String readStreamToString(InputStream in, String encoding) throws IOException
    {
        InputStreamReader reader = new InputStreamReader(in, encoding);
        StringBuilder outbuf = new StringBuilder();
        char[] buf = new char[16 * 1024];
        int cnt;
        while ((cnt = reader.read(buf)) >= 0) {
            outbuf.append(buf, 0, cnt);
        }
        return outbuf.toString();
    }

    public static String readFileToString(File f) throws IOException
    {
        FileInputStream fin = new FileInputStream(f);
        String s = readStreamToString(fin, "UTF-8");
        try { fin.close(); } catch (Exception ex) {}
        return s;
        // StringBuilder buf = new StringBuilder();
        // BufferedReader in = new BufferedReader(new FileReader(f));
        // String line;
        // while ((line = in.readLine()) != null) {
        //     buf.append(line);
        // }
        // return buf.toString();
    }


}
