//
// $Id$

package com.threerings.msoy.item.remix.client;

import java.awt.BorderLayout;

import javax.swing.JApplet;

import com.samskivert.util.Interval;
import com.samskivert.util.StringUtil;

public class RemixApplet extends JApplet
{
    @Override
    public void init ()
    {
        super.init();

        String mediaURL = StringUtil.decode(getParameter("media"));
        add(new RemixPanel(mediaURL, this), BorderLayout.CENTER);
    }

    @Override
    public String getAppletInfo ()
    {
        return "Whirled Remixer";
    }

    @Override
    public void destroy ()
    {
        super.destroy();

        // The RemixPanel uses Intervals, and we have to take special care of them in applets
        Interval.resetTimer();
    }
}
