//
// $Id$

package com.threerings.msoy.world.client {

import com.threerings.whirled.client.SceneDirector;

import com.threerings.msoy.client.MsoyParameters;
import com.threerings.msoy.data.all.MemberName;

public class StudioContext extends WorldContext
{
    public function StudioContext (client :StudioClient)
    {
        super(client);

        var params :Object = MsoyParameters.get();
        _name = new MemberName(
            String(params["username"] || params["name"] || "Fester Bestertester"), 0);
        _w = Number(params["width"]);
        _h = Number(params["height"]);
    }

    override public function getWidth () :Number
    {
        return isNaN(_w) ? super.getWidth() : _w;
    }

    override public function getHeight () :Number
    {
        return isNaN(_h) ? super.getHeight() : _h;
    }

    override public function getMyName () :MemberName
    {
        return _name;
    }

    override protected function createWorldDirectors () :void
    {
        // suppress super
    }

    /** Our name in the studio. */
    protected var _name :MemberName;

    /** An overridden width for the studio. */
    protected var _w :Number;

    /** An overridden height for the studio */
    protected var _h :Number;
}
}
