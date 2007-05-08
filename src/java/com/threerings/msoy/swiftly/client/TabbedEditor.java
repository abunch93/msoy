//
// $Id$

package com.threerings.msoy.swiftly.client;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.threerings.msoy.swiftly.data.PathElement;
import com.threerings.msoy.swiftly.data.SwiftlyCodes;
import com.threerings.msoy.swiftly.util.SwiftlyContext;

import com.samskivert.util.HashIntMap;

import com.infosys.closeandmaxtabbedpane.CloseAndMaxTabbedPane;
import com.infosys.closeandmaxtabbedpane.CloseListener;

public class TabbedEditor extends CloseAndMaxTabbedPane
{
    public TabbedEditor (SwiftlyContext ctx, SwiftlyEditor editor)
    {
        super(false);
        _ctx = ctx;
        _editor = editor;

        addCloseListener(new CloseListener() {
           public void closeOperation(MouseEvent e) {
              closeTabAt(getOverTabIndex());
           }
        });
        setMaxIcon(false);
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    }

    /**
     * Adds a {@link TabbedEditorComponent} to the tabbed panel.
     * @param the {@link TabbedEditorComponent} to load into a new editor tab.
     */
    public void addEditorTab (TabbedEditorComponent tab, PathElement pathElement)
    {
        Component comp = tab.getComponent();
        // add the tab
        add(comp);
        // select the newly opened tab
        setSelectedComponent(comp);

        // assign the mnemonic
        assignTabKeys();

        // add the component to the tabList
        _tabList.put(pathElement.elementId, tab);

        // set the title
        updateTabTitleAt(pathElement);
    }

    /**
     * Selects the component editing the supplied {@link PathElement}.
     * @param the {@link PathElement} to check
     * @return the TabbedEditorComponent if open, null otherwise.
     */
    public TabbedEditorComponent selectTab (PathElement pathElement)
    {
        TabbedEditorComponent tab;
        if ((tab = _tabList.get(pathElement.elementId)) != null) {
            setSelectedComponent(tab.getComponent());
        }
        return tab;
    }

    /**
     * Update the title on a tab, using the supplied {@link PathElement} as the index.
     * @param pathElement the PathElement to use as an index into the tabList
     */
    public void updateTabTitleAt (PathElement pathElement)
    {
        Component tab = _tabList.get(pathElement.elementId).getComponent();
        if (tab != null) {
            setTitleAt(indexOfComponent(tab), pathElement.getName());
        }
    }

    /**
     * Closes the tab holding the provided PathElement. Does nothing if the element is not open.
     */
    public void closePathElementTab (PathElement pathElement)
    {
        TabbedEditorComponent tab = _tabList.get(pathElement.elementId);
        if (tab == null) {
            return;
        }
        closeTabAt(indexOfComponent(tab.getComponent()));
    }

    /**
     * Closes the current tab.
     */
    public void closeCurrentTab ()
    {
        closeTabAt(getSelectedIndex());
    }

    /**
     * Closes the tab at the given index.
     * @param tabIndex the tab title needing removal
     */
    public void closeTabAt (int tabIndex)
    {
        // Don't try to remove a tab if we have none.
        if (getTabCount() == 0) {
            // do nothing
            return;
        }

        TabbedEditorComponent tab = (TabbedEditorComponent)getComponentAt(tabIndex);
        PathElement pathElement = tab.getPathElement();
        remove(_tabList.get(pathElement.elementId).getComponent());
        _tabList.remove(pathElement.elementId);
        _editor.tabRemoved(tab);
        assignTabKeys();
    }

    /**
     * Creates and returns an action to close the current tab.
     */
    public AbstractAction createCloseCurrentTabAction ()
    {
        return new AbstractAction(_ctx.xlate(SwiftlyCodes.SWIFTLY_MSGS, "m.action.close_tab")) {
            // from AbstractAction
            public void actionPerformed (ActionEvent e) {
                closeCurrentTab();
            }
        };
    }

    protected void assignTabKeys ()
    {
        for (int tabIndex = 0; tabIndex < getTabCount(); tabIndex++) {
            // ALT+tabIndex selects this tab for the first 9 tabs
            if (tabIndex < 9) {
                setMnemonicAt(tabIndex, KeyEvent.VK_1 + tabIndex);
            }
        }
    }

    protected SwiftlyContext _ctx;
    protected SwiftlyEditor _editor;

    // maps the pathelement elementId to the component editing/viewing that pathelement
    protected HashIntMap<TabbedEditorComponent> _tabList = new HashIntMap<TabbedEditorComponent>();
}
