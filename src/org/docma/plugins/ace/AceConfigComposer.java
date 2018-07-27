/*
 * AceConfigComposer.java
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

import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;

import org.docma.coreapi.DocConstants;
import org.docma.plugin.User;
import org.docma.plugin.web.WebPluginContext;
import org.docma.plugin.web.WebUserSession;
import org.docma.util.Log;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.*;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkex.zul.Colorbox;
import org.zkoss.zul.*;

/**
 *
 * @author MP
 */
public class AceConfigComposer extends SelectorComposer<Component> implements EventListener
{
    private static final String USER_DIALOG_ID = "UserDialog";
    private static final String OKAY_BUTTON_ID = "UserDialogOkayBtn";
    private static final String CANCEL_BUTTON_ID = "UserDialogCancelBtn";
    
    private static final String EXTROW_ = "ExtRow_";
    private static final String EXTCOMBOBOX_ = "ExtCombobox_";
    private static final String MODELISTBOX_ = "ModeListbox_";
    private static final String THEMELISTBOX_ = "ThemeListbox_";
    private static final String SYNTAXCHECKBOX_ = "SyntaxCheckbox_";
    private static final String COMPLETECHECKBOX_ = "CompleteCheckbox_";
    private static final String WRAPCHECKBOX_ = "WrapCheckbox_";
    private static final String BGCOLCHECKBOX_ = "BgcolCheckbox_";
    private static final String BGCOLAREA_ = "BgcolArea_";
    private static final String EDITCOLORBOX_ = "EditColorbox_";
    private static final String VIEWCOLORBOX_ = "ViewColorbox_";
    
    private static final String EXT_LIST_SEPARATORS = ",;. \t";

    // @Wire Window aceConfigDialog;
    // @Wire Toolbarbutton aceConfigHelpBtn;
    @Wire Component aceConfigContainer;
    @Wire Listbox aceConfigDefaultThemeBox;
    @Wire Checkbox aceConfigEditBgDefaultBox;
    @Wire Colorbox aceConfigEditBgColBox;
    // @Wire Combobox aceConfigEditBgColTextbox;
    @Wire Checkbox aceConfigViewBgDefaultBox;
    @Wire Colorbox aceConfigViewBgColBox;
    // @Wire Combobox aceConfigViewBgColTextbox;
    @Wire Doublespinner aceConfigFontSizeBox;
    @Wire Listbox aceConfigFontSizeUnit;
    @Wire Spinner aceConfigTabSizeBox;
    @Wire Checkbox aceConfigSoftTabsBox;
    @Wire Checkbox aceConfigLineHighlightBox;
    @Wire Grid aceConfigExtensionsGrid;
    @Wire Textbox aceConfigInitCodeBox;

    private AceHighlightPlugin plugin = null;
    private WebPluginContext plugCtx = null;
    private WebUserSession userSession = null;
    private String[] aceModes = new String[0];
    private String[] aceThemes = new String[0];
    private String[] txtExtensions = new String[0];
    private final SortedSet<String> extLabels = new TreeSet();   // combo items of selectable file extensions
    
    private boolean reloadGUI = true;  // Update GUI fields if tab is selected for the
                                       // first time after the dialog has been opened.

    private boolean saveOnOK = false;  // If tab has not been selected, then nothing 
                                       // needs to be saved.
    
    private int rowIdCounter = 0;

    void onSelectTab(AceHighlightPlugin plugin, WebPluginContext ctx, WebUserSession webSess)
    {
        this.plugin = plugin;
        this.plugCtx = ctx;
        this.userSession = webSess;
        
        Window dialog = (Window) webSess.getDialog(USER_DIALOG_ID);
        if (dialog != null) {
            dialog.setWidth("620px");
        }
        
        if (reloadGUI) { // tab has been selected for the first time after dialog has been opened
            updateGUIFromModel(); // initialize GUI fields
            reloadGUI = false;    // after GUI initialization, do not refresh 
                                  // GUI any more as long as dialog is opened
        }
        saveOnOK = true;  // User has selected tab and may edit fields. Therefore,
                          // fields need to be saved when user clicks "OK" button.

        if (dialog != null) {
            // Adapt dialog size to content of selected tab
            dialog.invalidate();  // fitDialogSize(); 
        }
    }

    public void onOkayClick()
    {
        if (DocConstants.DEBUG) {
            Log.info("AceConfigComposer.onOkayClick");
        }
        // checkInvalidInputs();  // throws exception if invalid input exists 
        if (saveOnOK) {
            updateModel();
        }
        // dialog is closed; assure that GUI is refreshed on next call of onSelectTab
        reloadGUI = true;
    }

    public void onCancelClick()
    {
        if (DocConstants.DEBUG) {
            Log.info("AceConfigComposer.onCancelClick");
        }
        // dialog is closed; assure that GUI is refreshed on next call of onSelectTab
        reloadGUI = true;
    }

    public void onDialogOpen()
    {
        if (DocConstants.DEBUG) {
            Log.info("AceConfigComposer.onDialogOpen");
        }
        reloadGUI = true;  // Assure that GUI is refreshed on next call of onSelectTab.
        saveOnOK = false;  // As long as tab is not selected, nothing needs to be saved.
    }
    
    public void onDialogClose()
    {
        if (DocConstants.DEBUG) {
            Log.info("AceConfigComposer.onDialogClose");
        }
        // dialog is closed; assure that GUI is refreshed on next call of onSelectTab
        reloadGUI = true;
    }

    @Listen("onCheck = #aceConfigEditBgDefaultBox")
    public void onCheckDefaultEditBg()
    {
        aceConfigEditBgColBox.setDisabled(aceConfigEditBgDefaultBox.isChecked());
    }

    @Listen("onCheck = #aceConfigViewBgDefaultBox")
    public void onCheckDefaultViewBg()
    {
        aceConfigViewBgColBox.setDisabled(aceConfigViewBgDefaultBox.isChecked());
    }

//    @Listen("onClick = #aceConfigHelpBtn")
//    public void onHelpClick()
//    {
//        String help_url = Labels.getLabel("ace_text_highlight.help_url");
//        openHelp(help_url);
//    }
//

//    @Listen("onChange = #aceConfigEditBgColBox")
//    public void onChangeEditBgColor()
//    {
//        aceConfigEditBgColTextbox.setValue(aceConfigEditBgColBox.getColor());
//    }
//
//    @Listen("onChange = #aceConfigEditBgColTextbox")
//    public void onChangeEditBgColorText()
//    {
//        String txt_col = aceConfigEditBgColTextbox.getValue().trim();
//        if (txt_col.equals("")) {
//            txt_col = "#FFFFFF";
//        }
//        boolean is_default = txt_col.equalsIgnoreCase("default");
//        aceConfigEditBgColBox.setDisabled(is_default);
//        if (! is_default) {
//            aceConfigEditBgColBox.setColor(txt_col);
//        }
//    }
//
//    @Listen("onChanging = #aceConfigEditBgColTextbox")
//    public void onChangingEditBgColorText(Event evt)
//    {
//        if (evt instanceof InputEvent) {
//            InputEvent ie = (InputEvent) evt;
//            String edit_val = ie.getValue();
//            edit_val = (edit_val == null) ? "" : edit_val.trim();
//            if (edit_val.startsWith("#") && (edit_val.length() == 7)) {
//                aceConfigEditBgColBox.setColor(edit_val);
//            }
//        }
//    }
//
//    @Listen("onChange = #aceConfigViewBgColBox")
//    public void onChangeViewBgColor()
//    {
//        aceConfigViewBgColTextbox.setValue(aceConfigViewBgColBox.getColor());
//    }
//
//    @Listen("onChange = #aceConfigViewBgColTextbox")
//    public void onChangeViewBgColorText()
//    {
//        String txt_col = aceConfigViewBgColTextbox.getValue().trim();
//        if (txt_col.equals("")) { 
//            txt_col = "#FFFFFF";
//        }
//        boolean is_default = txt_col.equalsIgnoreCase("default");
//        aceConfigViewBgColBox.setDisabled(is_default);
//        if (! is_default) {
//            aceConfigViewBgColBox.setColor(txt_col);
//        }
//    }
//
//    @Listen("onChanging = #aceConfigViewBgColTextbox")
//    public void onChangingViewBgColorText(Event evt)
//    {
//        if (evt instanceof InputEvent) {
//            InputEvent ie = (InputEvent) evt;
//            String edit_val = ie.getValue();
//            edit_val = (edit_val == null) ? "" : edit_val.trim();
//            if (edit_val.startsWith("#") && (edit_val.length() == 7)) {
//                aceConfigViewBgColBox.setColor(edit_val);
//            }
//        }
//    }

    @Override
    public void onEvent(Event evt) throws Exception 
    {
        Component tar = evt.getTarget();
        String tarId = tar.getId();
        String ename = evt.getName();
        // System.out.println("Event '" + evt.getName() + "' on target '" + tar.getId() + 
        //                    "'. Type: " + tar.getClass().getName());
        if (tarId.startsWith(EXTCOMBOBOX_)) {   // file extension combobox
            if (ename.equalsIgnoreCase("onBlur")) {
                // Remove row if no file extension has been entered and it is
                // not the last row. Append a new row if last row is no longer empty.
                removeEmptyRow();
                boolean added = appendEmptyRow();
                // if (added) {
                //     userTabbox.invalidate();  // resize dialog to fit size of grid
                // }
            } else if (ename.equalsIgnoreCase("onChange")) {
                // If user has entered one or more file extensions and no mode
                // is selected, then automatically select the mode with same  
                // name as the extension (if existent)
                String rowId = tarId.substring(EXTCOMBOBOX_.length());
                String modeBoxId = MODELISTBOX_ + rowId;
                Listbox modeBox = (Listbox) tar.getFellowIfAny(modeBoxId);
                if ((modeBox != null) && (modeBox.getSelectedIndex() < 0)) {  // no mode selected
                    Combobox extBox = (Combobox) tar;
                    String extList = extBox.getValue().trim();
                    for (String ext : extListToArray(extList)) {  // extensions, lowercase
                        String selMode = ext;  // default: use mode with same name as extension
                        if (ext.equals("txt")) { 
                            selMode = "text";
                        } else if (ext.equals("content")) { 
                            selMode = "html";
                        } else if (ext.equals("js")) {
                            selMode = "javascript";
                        }
                        if (selectListItem(modeBox, selMode)) {
                            break; // mode has been selected
                        }
                    }
                }
            }
        } else if (ename.equalsIgnoreCase("onCheck") && tarId.startsWith(BGCOLCHECKBOX_)) {
            // If user has checked default background color, then hide the
            // box that contains the color input fields.
            boolean isDefaultBg = ((Checkbox) tar).isChecked();
            String rowId = tarId.substring(BGCOLCHECKBOX_.length());
            Component box = tar.getFellowIfAny(BGCOLAREA_ + rowId);
            if (box != null) {
                box.setVisible(! isDefaultBg);
            }
            Colorbox colbox1 = (Colorbox) tar.getFellowIfAny(EDITCOLORBOX_ + rowId);
            Colorbox colbox2 = (Colorbox) tar.getFellowIfAny(VIEWCOLORBOX_ + rowId);
            if ((colbox1 != null) && (colbox2 != null)) {
                colbox1.setDisabled(isDefaultBg);
                colbox2.setDisabled(isDefaultBg);
            }
        }
    }

    /* --------------- Package local ------------------- */
    
    String getUserDialogId()
    {
        return USER_DIALOG_ID;
    }
    
    String getOkayButtonId()
    {
        return OKAY_BUTTON_ID;
    }

    String getCancelButtonId()
    {
        return CANCEL_BUTTON_ID;
    }

    /* --------------- Private methods ------------------- */

    private void updateGUIFromModel()
    {
        // Update required member fields
        aceModes = plugin.getAvailableModes(plugCtx);
        aceThemes = plugin.getAvailableThemes(plugCtx);
        txtExtensions = plugCtx.getApplicationContext().getTextFileExtensions();
        extLabels.clear();
        extLabels.add("*");   // wildcard (all extensions)
        extLabels.add("content");
        for (String ext : txtExtensions) {
            ext = ext.toLowerCase();
            if (ext.equals("htm") || ext.equals("html")) {
                ext = "htm, html";
            }
            extLabels.add(ext);
        }
        
        User usr = userSession.getUser();

        // Default theme
        String defaultTheme = plugin.getDefaultTheme(usr);
        if (aceConfigDefaultThemeBox.getItemCount() > 0) {
            aceConfigDefaultThemeBox.getItems().clear();
        }
        Listitem selItem = null;
        aceConfigDefaultThemeBox.appendItem("", "");
        for (String theme : aceThemes) {
            Listitem item = new Listitem(theme, theme);
            aceConfigDefaultThemeBox.appendChild(item);
            if (theme.equalsIgnoreCase(defaultTheme)) {
                selItem = item;
            }
        }
        aceConfigDefaultThemeBox.selectItem(selItem);  // if selItem is null, no item will be selected
        
        // Font size
        String fontSize = plugin.getFontSize(usr);
        setFontSize(fontSize);
        
        // Highlight active line
        aceConfigLineHighlightBox.setChecked(plugin.isActiveLineHighlight(usr));
        
        // Tab settings
        aceConfigTabSizeBox.setValue(plugin.getTabSize(usr));
        aceConfigSoftTabsBox.setChecked(plugin.isSoftTabs(usr));
        
        // Default background colors
        String edit_col = plugin.getDefaultEditBgColor(usr);
        aceConfigEditBgDefaultBox.setChecked(edit_col.equals(""));
        setColorBox(aceConfigEditBgColBox, edit_col);
        String view_col = plugin.getDefaultViewBgColor(usr);
        aceConfigViewBgDefaultBox.setChecked(view_col.equals(""));
        setColorBox(aceConfigViewBgColBox, view_col);

        // Custom initialization script
        aceConfigInitCodeBox.setValue(plugin.getCustomEditorInit(usr));
        
        // Clear/initialize grid rows 
        Rows gridrows = aceConfigExtensionsGrid.getRows();
        if (gridrows != null) {
            gridrows.getChildren().clear();
        } else {
            gridrows = new Rows();
            aceConfigExtensionsGrid.appendChild(gridrows);
        }
        
        // Fill extensions grid
        SortedSet<String> extKeys = plugin.getExtensionKeys(usr);
        for (String extKey : extKeys) {
            String mode = plugin.getMode(usr, extKey);
            String theme = plugin.getTheme(usr, extKey);
            boolean synCheck = plugin.isSyntaxCheck(usr, extKey);
            boolean autoComp = plugin.isAutoComplete(usr, extKey);
            boolean wordWrap = plugin.isWordWrap(usr, extKey);
            String editCol = plugin.getEditBgColor(usr, extKey);
            String viewCol = plugin.getViewBgColor(usr, extKey);
            Row r = new Row();
            buildExtRow(r, extKeyToList(extKey), mode, theme, 
                        synCheck, autoComp, wordWrap, editCol, viewCol);
            gridrows.appendChild(r);
        }
        
        // Add empty row
        Row r = new Row();
        buildNewRow(r, "");
        gridrows.appendChild(r);
    }

    private void fitDialogSize()
    {
        Component root = aceConfigContainer;
        while ((root != null) && !((root instanceof Tabbox) || (root instanceof Window))) {
            root = root.getParent();
        }
        if (root != null) {
            root.invalidate();
        }
    }

    private void setColorBox(Colorbox colBox, String value)
    {
        if (value.equals("")) {
            colBox.setColor("#FFFFFF");
            colBox.setDisabled(true);
        } else {
            colBox.setDisabled(false);
            try {
                colBox.setColor(value);
            } catch (Exception ex) {          // if invalid color code,
                colBox.setColor("#FFFFFF");   // fall back to white
            }
        }
    }
    
    private String nextRowId()
    {
        return String.valueOf(++rowIdCounter);
    }
    
    private void buildNewRow(Row r, String extList) 
    {
        buildExtRow(r, extList, null, null, true, true, false, "", "");
    }
    
    private void buildExtRow(Row r, String extList, 
                             String mode, String theme, 
                             boolean syntaxCheck, boolean autoComplete, boolean wordWrap,
                             String editColor, String viewColor)
    {
        String rowId = nextRowId();
        r.setId(EXTROW_ + rowId);
 
        //
        // First column: list of file extensions
        //
        Combobox cb = new Combobox();
        cb.setId(EXTCOMBOBOX_ + rowId);
        cb.setHflex("1");
        cb.setMaxlength(80);
        cb.setValue(extList);
        cb.addEventListener("onBlur", this);
        cb.addEventListener("onChange", this);
        for (String extLabel : extLabels) {
            cb.appendItem(extLabel);
        }
        r.appendChild(cb);
        
        //
        // Second column: mode / theme
        //
        Listbox modelist = new Listbox();
        modelist.setId(MODELISTBOX_ + rowId);
        modelist.setMold("select");
        modelist.setRows(1);
        Listitem selMode = null;
        for (String m : aceModes) {
            Listitem item = new Listitem(m, m);
            modelist.appendChild(item);
            if (m.equalsIgnoreCase(mode)) {
                selMode = item;
            }
        }
        modelist.selectItem(selMode);  // if selMode is null, no item will be selected
        
        Listbox themelist = new Listbox();
        themelist.setId(THEMELISTBOX_ + rowId);
        themelist.setMold("select");
        themelist.setRows(1);
        Listitem selTheme = new Listitem("", "");  // empty string means default theme
        themelist.appendChild(selTheme);           // add empty string as first item
        for (String th : aceThemes) {
            Listitem item = new Listitem(th, th);
            themelist.appendChild(item);
            if (th.equalsIgnoreCase(theme)) {
                selTheme = item;
            }
        }
        themelist.selectItem(selTheme);
        
        Vlayout v1 = new Vlayout();
        v1.appendChild(modelist);
        v1.appendChild(themelist);
        r.appendChild(v1);

        //
        // Third column: Options (syntax checks, auto complete, word wrap
        //
        Checkbox cb1 = new Checkbox(userSession.getLabel("ace_text_highlight.syntax_checks"));
        Checkbox cb2 = new Checkbox(userSession.getLabel("ace_text_highlight.auto_complete"));
        Checkbox cb3 = new Checkbox(userSession.getLabel("ace_text_highlight.word_wrap"));
        cb1.setId(SYNTAXCHECKBOX_ + rowId);
        cb2.setId(COMPLETECHECKBOX_ + rowId);
        cb3.setId(WRAPCHECKBOX_ + rowId);
        cb1.setStyle("white-space:nowrap;");
        cb2.setStyle("white-space:nowrap;");
        cb3.setStyle("white-space:nowrap;");
        cb1.setChecked(syntaxCheck);
        cb2.setChecked(autoComplete);
        cb3.setChecked(wordWrap);
        
        Vlayout v2 = new Vlayout();
        v2.appendChild(cb1);
        v2.appendChild(cb2);
        v2.appendChild(cb3);
        r.appendChild(v2);

        //
        // Fourth column: background color edit/view
        //
        Checkbox cb4 = new Checkbox(userSession.getLabel("ace_text_highlight.default_color"));
        cb4.setId(BGCOLCHECKBOX_ + rowId);
        cb4.setChecked("".equals(editColor));  // empty string means default background color
        cb4.addEventListener("onCheck", this);
        
        Label l1 = new Label(userSession.getLabel("ace_text_highlight.edit_bg_color"));
        l1.setStyle("font-size:8pt;  white-space:nowrap;");
        Label l2 = new Label(userSession.getLabel("ace_text_highlight.view_bg_color"));
        l2.setStyle("font-size:8pt;  white-space:nowrap;");
        Colorbox colbox1 = new Colorbox();
        colbox1.setId(EDITCOLORBOX_ + rowId);
        setColorBox(colbox1, editColor);
        Colorbox colbox2 = new Colorbox();
        colbox2.setId(VIEWCOLORBOX_ + rowId);
        setColorBox(colbox2, viewColor);

        Hbox hb = new Hbox();
        hb.setId(BGCOLAREA_ + rowId);
        hb.setSpacing("1px");
        hb.setPack("start");
        hb.setAlign("center");
        hb.appendChild(l1);
        hb.appendChild(colbox1);
        hb.appendChild(l2);
        hb.appendChild(colbox2);
        hb.setVisible(! cb4.isChecked());

        Vlayout v3 = new Vlayout();
        v3.appendChild(cb4);
        v3.appendChild(hb);
//        Hbox vb1 = createColorInput("ace_text_highlight.edit_bg_color");
//        Hbox vb2 = createColorInput("ace_text_highlight.view_bg_color");
//        Vlayout v3 = new Vlayout();
//        v3.appendChild(vb1);
//        v3.appendChild(vb2);
        r.appendChild(v3);
    }
    
    private String extKeyToList(String extKey)
    {
        return extKey.replace("|", ", ");
    }
    
    private String extListToKey(String extList)
    {
        StringBuilder sb = new StringBuilder();
        StringTokenizer st = new StringTokenizer(extList, EXT_LIST_SEPARATORS);
        while (st.hasMoreTokens()) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(st.nextToken());
        }
        return sb.toString();
    }
    
    private String[] extListToArray(String extList)
    {
        StringTokenizer st = new StringTokenizer(extList, EXT_LIST_SEPARATORS);
        String[] arr = new String[st.countTokens()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = st.nextToken().toLowerCase();
        }
        return arr;
    }

    /**
     * If the last row is not an empty row, then append an empty row.
     */
    private boolean appendEmptyRow()
    {
        Rows gridrows = aceConfigExtensionsGrid.getRows();
        if (gridrows != null) {
            Component comp = gridrows.getLastChild();
            if (comp instanceof Row) {
                Row r = (Row) comp;
                Component c = r.getFirstChild();
                if (c instanceof Combobox) {
                    String v = ((Combobox) c).getValue().trim();
                    if (v.length() > 0) {  // not an empty row
                        // Add empty row
                        Row emptyrow = new Row();
                        buildNewRow(emptyrow, "");
                        gridrows.appendChild(emptyrow);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Remove empty row, if it is not the last row.
     */
    private void removeEmptyRow()
    {
        Rows gridrows = aceConfigExtensionsGrid.getRows();
        if (gridrows != null) {
            List<Component> rowlist = gridrows.getChildren();
            for (int i = 0; i < rowlist.size() - 1; i++) {  // exclude the the last row
                Component comp = rowlist.get(i);
                if (comp instanceof Row) {
                    Component c = comp.getFirstChild();
                    if (c instanceof Combobox) {
                        String v = ((Combobox) c).getValue().trim();
                        if (v.equals("")) {
                            gridrows.removeChild(comp);
                            return;
                        }
                    }
                }
            }
        }
    }
    
    private synchronized void updateModel()
    {
        User usr = userSession.getUser();
        Properties props = new Properties();
        
        // Default theme
        String theme = getSelectedValue(aceConfigDefaultThemeBox);
        plugin.setDefaultTheme(props, theme);
        
        // Font size
        String fontSz = getFontSize(); 
        plugin.setFontSize(props, fontSz);
        
        // Highlight active line
        boolean isLineHighlight = aceConfigLineHighlightBox.isChecked();
        plugin.setActiveLineHighlight(props, isLineHighlight);
        
        // Tab settings
        int tabSz;
        try {
            tabSz = aceConfigTabSizeBox.intValue();
        } catch (Exception ex) {  // invalid value
            tabSz = 4;  // fall back to default
        }
        plugin.setTabSize(props, tabSz);
        plugin.setSoftTabs(props, aceConfigSoftTabsBox.isChecked());
        
        // Default background colors
        String editCol;
        if (aceConfigEditBgDefaultBox.isChecked()) {
            editCol = "";
        } else {
            editCol = aceConfigEditBgColBox.getColor();
        }
        plugin.setDefaultEditBgColor(props, editCol);
        String viewCol;
        if (aceConfigViewBgDefaultBox.isChecked()) {
            viewCol = "";
        } else {
            viewCol = aceConfigViewBgColBox.getColor();
        }
        plugin.setDefaultViewBgColor(props, viewCol);

        // Custom initialization script
        plugin.setCustomEditorInit(props, aceConfigInitCodeBox.getValue().trim());
        
        // File extensions settings
        SortedSet<String> extKeys = new TreeSet<String>();
        Rows gridrows = aceConfigExtensionsGrid.getRows();
        if (gridrows != null) {
            for (Component comp : gridrows.getChildren()) {
                String compId = comp.getId();
                if (compId.startsWith(EXTROW_)) {
                    String rowId = compId.substring(EXTROW_.length());
                    Combobox extBox = (Combobox) comp.getFellowIfAny(EXTCOMBOBOX_ + rowId);
                    String key = extListToKey(extBox.getValue().trim());
                    if (key.equals("")) {
                        continue;
                    }
                    extKeys.add(key);
                    
                    Listbox modeBox = (Listbox) comp.getFellowIfAny(MODELISTBOX_ + rowId);
                    Listbox themeBox = (Listbox) comp.getFellowIfAny(THEMELISTBOX_ + rowId);
                    Checkbox syntaxBox = (Checkbox) comp.getFellowIfAny(SYNTAXCHECKBOX_ + rowId);
                    Checkbox completeBox = (Checkbox) comp.getFellowIfAny(COMPLETECHECKBOX_ + rowId);
                    Checkbox wrapBox = (Checkbox) comp.getFellowIfAny(WRAPCHECKBOX_ + rowId);
                    Checkbox bgColBox = (Checkbox) comp.getFellowIfAny(BGCOLCHECKBOX_ + rowId);
                    Colorbox editColBox = (Colorbox) comp.getFellowIfAny(EDITCOLORBOX_ + rowId);
                    Colorbox viewColBox = (Colorbox) comp.getFellowIfAny(VIEWCOLORBOX_ + rowId);
                    
                    plugin.setMode(props, key, getSelectedValue(modeBox));
                    plugin.setTheme(props, key, getSelectedValue(themeBox));
                    plugin.setSyntaxCheck(props, key, syntaxBox.isChecked());
                    plugin.setAutoComplete(props, key, completeBox.isChecked());
                    plugin.setWordWrap(props, key, wrapBox.isChecked());
                    boolean defaultBg = bgColBox.isChecked();
                    plugin.setEditBgColor(props, key, defaultBg ? "" : editColBox.getColor());
                    plugin.setViewBgColor(props, key, defaultBg ? "" : viewColBox.getColor());
                }
            }
        }
        plugin.setExtensionKeys(props, extKeys);
        
        plugin.saveUserProps(usr, props);
    }

    private void openHelp(String path)
    {
        String client_action = "window.open('" + path + "', " +
          "'_blank', 'width=850,height=600,resizable=yes,scrollbars=yes,location=yes,menubar=yes,status=yes');";
        Clients.evalJavaScript(client_action);
    }

    private String getFontSize()
    {
        String size = aceConfigFontSizeBox.getText().trim();
        if (size.length() > 0) {
            String unit = getSelectedValue(aceConfigFontSizeUnit);
            size += unit.equals("") ? "pt" : unit;
        }
        return size;
    }
    
    private void setFontSize(String size)
    {
        if ((size == null) || size.equals("")) {
            aceConfigFontSizeBox.setText("");
            selectListItem(aceConfigFontSizeUnit, "pt");
            return;
        }
        String value;
        String unit;
        if (size.length() >= 2) {
            unit = size.substring(size.length() - 2);
            value = size.substring(0, size.length() - 2);
        } else {
            value = size;
            unit = "pt";
        }
        double doub;
        try {
            doub = Double.parseDouble(value);
        } catch (Exception ex) {
            doub = 1.0;
        }
        aceConfigFontSizeBox.setValue(doub);
        if (! selectListItem(aceConfigFontSizeUnit, unit)) {
            selectListItem(aceConfigFontSizeUnit, "pt");  //  unit = "pt";
        }
    }

    private String getSelectedValue(Listbox listbox)
    {
        Listitem item = listbox.getSelectedItem();
        if (item == null) {
            return "";
        }
        Object obj = item.getValue();
        return (obj == null) ? "" : obj.toString();
    }
    
    private boolean selectListItem(Listbox listbox, String itemvalue)
    {
        for (int i=0; i < listbox.getItemCount(); i++) {
            Listitem item = listbox.getItemAtIndex(i);
            Object val = item.getValue();
            if ((val != null) && val.toString().equalsIgnoreCase(itemvalue)) {
                item.setSelected(true);
                return true;
            }
        }
        return false;
    }

}
