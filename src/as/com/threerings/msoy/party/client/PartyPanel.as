//
// $Id$

package com.threerings.msoy.party.client {

import mx.controls.TextInput;
import mx.events.FlexEvent;

import com.threerings.presents.client.ConfirmAdapter;
import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;

import com.threerings.flex.CommandButton;

import com.threerings.msoy.ui.FloatingPanel;

import com.threerings.msoy.client.Msgs;
import com.threerings.msoy.client.Roster;
import com.threerings.msoy.data.MsoyCodes;

import com.threerings.msoy.world.client.WorldContext;

import com.threerings.msoy.chat.client.ReportingListener;

import com.threerings.msoy.party.data.PartyCodes;
import com.threerings.msoy.party.data.PartyObject;
import com.threerings.msoy.party.data.PartyPeep;

public class PartyPanel extends FloatingPanel
    implements AttributeChangeListener
{
    public function PartyPanel (ctx :WorldContext, partyObj :PartyObject)
    {
        super(ctx, Msgs.PARTY.get("t.party"));
        _wctx = ctx;
        showCloseButton = true;

        _partyObj = partyObj;
    }

    override public function close () :void
    {
        _partyObj.removeListener(_roster);
        _partyObj.removeListener(this);

        super.close();
    }

    override protected function didOpen () :void
    {
        super.didOpen();

        _roster.init(_partyObj.peeps.toArray());
        _partyObj.addListener(_roster);
        _partyObj.addListener(this);
    }

    override protected function createChildren () :void
    {
        super.createChildren();

        var isLeader :Boolean = (_partyObj.leaderId == _ctx.getMyName().getMemberId());

        _name = new TextInput();
        _name.styleName = "partyTitle";
        _name.maxChars = PartyCodes.MAX_NAME_LENGTH;
        _name.percentWidth = 100;
        _name.text = _partyObj.name;
        _name.editable = isLeader;
        _name.addEventListener(FlexEvent.ENTER, commitName);
        addChild(_name);

        _roster = new Roster(_ctx, PartyObject.PEEPS, PeepRenderer,
            PartyPeep.createSortByOrder(_partyObj));
        addChild(_roster);

        addChild(new CommandButton(Msgs.PARTY.get("b.leave"), _wctx.getPartyDirector().leaveParty));

        _status = new TextInput();
        _status.styleName = "partyStatus";
        _status.maxChars = PartyCodes.MAX_NAME_LENGTH;
        _status.percentWidth = 100;
        _status.text = _partyObj.status;
        _status.editable = isLeader;
        _status.addEventListener(FlexEvent.ENTER, commitStatus);
        addChild(_status);
    }

    public function attributeChanged (event :AttributeChangedEvent) :void
    {
        switch (event.getName()) {
            case PartyObject.STATUS:
                _status.text = String(event.getValue());
                break;

            case PartyObject.LEADER_ID:
                var isLeader :Boolean = (event.getValue() == _ctx.getMyName().getMemberId());
                _name.editable = isLeader;
                _status.editable = isLeader;
                break;
        }
    }

    protected function commitName (event :FlexEvent) :void
    {
        _wctx.getPartyDirector().updateNameOrStatus(_name.text, true);
    }

    protected function commitStatus (event :FlexEvent) :void
    {
        _wctx.getPartyDirector().updateNameOrStatus(_status.text, false);
    }

    protected var _wctx :WorldContext;

    protected var _partyObj :PartyObject;

    protected var _roster :Roster;
    protected var _name :TextInput;
    protected var _status :TextInput;
}

}
