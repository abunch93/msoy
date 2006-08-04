//
// $Id$

package client.inventory;

import java.util.ArrayList;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.msoy.item.data.Item;
import com.threerings.msoy.web.client.WebContext;

/**
 * Displays all items of a particular type in a player's inventory.
 */
public class ItemPanel extends VerticalPanel
{
    public ItemPanel (WebContext ctx, String type)
    {
        // setStyleName("inventory_item");
        _ctx = ctx;
        _type = type;

        // this will contain our items
        add(_contents = new FlowPanel());

        // this will allow us to create new items
        add(_create = new Button("Create new..."));
        _create.addClickListener(new ClickListener() {
            public void onClick (Widget widget) {
                createNewItem();
            }
        });
        add(_status = new Label(""));
    }

    protected void onLoad ()
    {
        // load the users inventory if we have no already
        if (_items == null) {
            _ctx.itemsvc.loadInventory(_ctx.creds, _type, new AsyncCallback() {
                public void onSuccess (Object result) {
                    _items = (ArrayList)result;
                    if (_items == null || _items.size() == 0) {
                        _contents.add(
                            new Label("You have no " + _type + " items."));
                    } else {
                        for (int ii = 0; ii < _items.size(); ii++) {
                            _contents.add(
                                new ItemThumbnail((Item)_items.get(ii)));
                        }
                    }
                }
                public void onFailure (Throwable caught) {
                    // TODO: if ServiceException, translate
                    add(new Label("Failed to load inventory."));
                }
            });
        }
    }

    protected void createNewItem ()
    {
        ItemEditor editor = null;
        Item item = null;
        if (_type.equals("PHOTO")) {
            editor = new PhotoEditor();
        } else if (_type.equals("DOCUMENT")) {
            editor = new DocumentEditor();
        } else if (_type.equals("FURNITURE")) {
            editor = new FurnitureEditor();
        }
        if (editor != null) {
            editor.init(_ctx, this);
            editor.setItem(editor.createBlankItem());
            remove(_create);
            insert(editor, 1);
        }
    }

    /**
     * Called by an active {@link ItemEditor} when it is ready to go away
     * (either the editing is done or the user canceled).
     *
     * @param item if the editor was creating a new item, the new item should
     * be passed to this method so that it can be added to the display.
     */
    protected void editComplete (ItemEditor editor, Item item)
    {
        remove(editor);
        insert(_create, 1);
        if (item != null) {
            _contents.add(new ItemThumbnail(item));
        }
    }

    /**
     * Displays a status message to the user, may be called by item editors.
     */
    protected void setStatus (String status)
    {
        _status.setText(status);
    }

    protected WebContext _ctx;

    protected FlowPanel _contents;
    protected Button _create;
    protected Label _status;

    protected String _type;
    protected ArrayList _items;
}
