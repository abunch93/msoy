package com.threerings.msoy.item.client {

import mx.containers.VBox;

import mx.controls.Label;

import com.threerings.util.MediaContainer;
import com.threerings.util.Util;

import com.threerings.msoy.item.data.Item;

/**
 * Renders an item in our inventory.
 * The item should be set to the "data" property.
 */
public class ItemRenderer extends VBox
{
    override public function validateDisplayList () :void
    {
        super.validateDisplayList();

        var item :Item = (data as Item);
        if (!Util.equals(item, _item)) {
            _item = item;

            if (_item == null) {
                _container.shutdown();
                _label.text = "";

            } else {
                _container.setMedia(_item.getThumbnailPath());
                _label.text = _item.getInventoryDescrip();
            }
        }
    }

    override protected function measure () :void
    {
        measuredWidth = 300;
        measuredHeight = 300;
    }

    override protected function createChildren () :void
    {
        super.createChildren();

        var scrollBox :VBox = new VBox();
        scrollBox.maxWidth = 250;
        scrollBox.maxHeight = 200;

        addChild(scrollBox);
        scrollBox.addChild(_container = new MediaContainer());

        addChild(_label = new Label());
        _label.maxHeight = 50;
        _label.maxWidth = 250;
    }

    protected var _container :MediaContainer;
    protected var _label :Label;
    protected var _item :Item;
}
}
