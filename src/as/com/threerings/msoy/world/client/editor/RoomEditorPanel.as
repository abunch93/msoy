//
// $Id$

package com.threerings.msoy.world.client.editor {

import flash.display.Bitmap;
import flash.display.DisplayObject;
import flash.display.Graphics;
import flash.display.Shape;
import flash.events.IEventDispatcher;
import flash.events.Event;
import flash.events.MouseEvent;
import flash.geom.Point;
import flash.geom.Rectangle;

import mx.containers.HBox;
import mx.containers.TabNavigator;
import mx.containers.VBox;
import mx.controls.CheckBox;
import mx.controls.ComboBox;
import mx.controls.HRule;
import mx.controls.Label;
import mx.controls.Spacer;
import mx.core.UIComponent;
import mx.events.ListEvent;

import com.threerings.flex.CommandButton;
import com.threerings.flex.CommandCheckBox;
import com.threerings.msoy.ui.FloatingPanel;

import com.threerings.msoy.client.HeaderBar;
import com.threerings.msoy.client.Msgs;
import com.threerings.msoy.client.MsoyController;
import com.threerings.msoy.client.TopPanel;

import com.threerings.msoy.world.client.FurniSprite;
import com.threerings.msoy.world.client.WorldContext;
import com.threerings.msoy.world.data.FurniData;


/**
 * A separate room editing panel, which lets the player edit furniture inside the room.
 */
public class RoomEditorPanel extends FloatingPanel
{
    public function RoomEditorPanel (ctx :WorldContext, controller :RoomEditorController)
    {
        super(ctx, Msgs.EDITING.get("t.editor_title"));
        _controller = controller;

        styleName = "roomEditPanel";
        showCloseButton = true;
    }

    // @Override from FloatingPanel
    override public function open (
        modal :Boolean = false, parent :DisplayObject = null, avoid :DisplayObject = null) :void
    {
        super.open(modal, parent, avoid);

        this.x = stage.stageWidth - width - GAP;
        this.y = HeaderBar.HEIGHT + TopPanel.DECORATIVE_MARGIN_HEIGHT + GAP;
    }

    // @Override from FloatingPanel
    override public function close () :void
    {
        super.close();
        _controller.actionEditorClosed();
        (_ctx as WorldContext).getGameDirector().tutorialEvent("editorClosed");
    }

    /** Updates object data displayed on the editing panel. */
    public function updateDisplay (target :FurniSprite) :void
    {
        var data :FurniData = (target != null) ? target.getFurniData() : null;
        _details.updateDisplay(data);
        _action.updateDisplay(data);
        _room.updateDisplay(data);

        _custom.updateDisplay(target);
    }

    /** Updates the enabled status of the undo button (based on the size of the undo stack). */
    public function updateUndoStatus (enabled :Boolean) :void
    {
        _undoButton.enabled = enabled;
    }

    /** Updates the enabled status of the delete button (based on current selection). */
    public function updateDeleteStatus (enabled :Boolean) :void
    {
        if (_deleteButton != null) { // just in case this gets called during initialization...
            _deleteButton.enabled = enabled;
        }
    }

    /** Updates the name drop-down box with the selected item definitions. */
    public function updateNameList (defs :Array) :void
    {
        _namebox.dataProvider = defs;
    }

    /** Selects the specified item in the name list. */
    public function selectInNameList (def :Object) :void
    {
        if (def == null) {
            _namebox.selectedIndex = -1;
        } else {
            _namebox.selectedItem = def;
        }
    }

    public function setHomeButtonEnabled (enabled :Boolean) :void
    {
        _room.setHomeButtonEnabled(enabled);
    }

    /** Add or remove advanced panels from display. */
    public function displayAdvancedPanels (show :Boolean) :void
    {
        var alreadyDisplayed :Boolean = _contents.contains(_advancedPanels);

        if (show && ! alreadyDisplayed) {
            _contents.addChild(_advancedPanels);
        }

        if (! show && alreadyDisplayed) {
            _contents.removeChild(_advancedPanels);
        }
    }
    
    /** Handler for dealing with changes in the name selection box. */
    protected function nameListChanged (event :ListEvent) :void
    {
        if (_namebox.selectedItem != null) {
            _controller.findAndSetTarget(_namebox.selectedItem.data);
        }
    }
        
    // from superclasses
    override protected function createChildren () :void
    {
        super.createChildren();

        // container for room name
        var namebar :VBox = new VBox();
        namebar.styleName = "roomEditNameBar";
        namebar.percentWidth = 100;
        namebar.addChild(_room = new RoomPanel(_controller));
        addChild(namebar);

        // container for everything else
        _contents = new VBox();
        _contents.styleName = "roomEditContents";
        _contents.percentWidth = 100;
        addChild(_contents);
        
        // sub-container for object name and buttons
        var box :HBox = new HBox();
        box.styleName = "roomEditButtonBar";
        box.percentWidth = 100;
        _contents.addChild(box);
        
        _namebox = new ComboBox();
        _namebox.percentWidth = 100;
        _namebox.maxWidth = 250;
        _namebox.prompt = Msgs.EDITING.get("l.select_item");
        _namebox.addEventListener(ListEvent.CHANGE, nameListChanged);
        box.addChild(_namebox);

        _deleteButton = new CommandButton(null, _controller.actionDelete);
        _deleteButton.styleName = "roomEditButtonTrash3";
        _deleteButton.toolTip = Msgs.EDITING.get("i.delete_button");
        _deleteButton.enabled = false;
        box.addChild(_deleteButton);
        
        _undoButton = new CommandButton(null, _controller.actionUndo);
        _undoButton.styleName = "roomEditButtonUndo3";
        _undoButton.toolTip = Msgs.EDITING.get("i.undo_button");
        _undoButton.enabled = false;
        box.addChild(_undoButton);

        var spacer :VBox = new VBox();
        spacer.percentWidth = 100;
        spacer.height = 10;
        _contents.addChild(spacer);
        
        _contents.addChild(new CommandCheckBox(Msgs.EDITING.get("l.advanced_editing"),
            _controller.actionAdvancedEditing));

        // now create collapsing sections

        hr = new HRule();
        hr.percentWidth = 100;
        _contents.addChild(hr);

        c = new CollapsingContainer(Msgs.EDITING.get("t.item_custom"));
        c.setContents(_custom = new CustomPanel(c, hr));
        _contents.addChild(c);

        _advancedPanels = new VBox();
        _advancedPanels.styleName = "roomEditAdvanced";
        _advancedPanels.percentWidth = 100;
        _contents.addChild(_advancedPanels);
        
        var hr :HRule = new HRule();
        hr.percentWidth = 100;
        _advancedPanels.addChild(hr);

        var c :CollapsingContainer = new CollapsingContainer(Msgs.EDITING.get("t.item_prefs"));
        c.setContents(_details = new DetailsPanel(_controller));
        _advancedPanels.addChild(c);

        hr = new HRule();
        hr.percentWidth = 100;
        _advancedPanels.addChild(hr);

        c = new CollapsingContainer(Msgs.EDITING.get("t.item_action"));
        c.setContents(_action = new ActionPanel(_controller)); 
        _advancedPanels.addChild(c);
    }

    protected var _contents :VBox;

    protected var _deleteButton :CommandButton;
    protected var _undoButton :CommandButton;
    
    protected var _details :DetailsPanel;
    protected var _action :ActionPanel;
    protected var _custom :CustomPanel;
    protected var _advancedPanels :VBox;
    
    protected var _room :RoomPanel;
    protected var _namebox :ComboBox;
    protected var _controller :RoomEditorController;

    protected static const GAP :int = 10;
}
}
