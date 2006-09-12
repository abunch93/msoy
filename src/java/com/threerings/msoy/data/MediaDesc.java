//
// $Id$

package com.threerings.msoy.data;

import java.util.Arrays;

import com.samskivert.util.StringUtil;

import com.threerings.io.SimpleStreamableObject;

import com.threerings.msoy.item.data.Item;
import com.threerings.msoy.item.data.MediaItem;

/**
 * Contains information about a piece of media in the catalog.
 */
public class MediaDesc extends SimpleStreamableObject
{
    /** The hash used to identify a piece of media. */
    public byte[] hash;

    public byte mimeType;

    // more to come?

    /**
     * Create a media descriptor from the specified item.
     */
    public static MediaDesc fromItem (Item item)
    {
        MediaDesc data = new MediaDesc();
        if (item instanceof MediaItem) {
            MediaItem mitem = (MediaItem) item;
            data.hash = mitem.mediaHash;
            data.mimeType = mitem.mimeType;

        } else {
            // other kinds of items should have default representations
            // of some special media already in the system...
            // TODO
        }

        return data;
    }

/*
    public static MediaDesc fromDBString (String encoded)
    {
        if (encoded == null) {
            return null;
        }
        try {
            int colon = encoded.indexOf(':');
            if (colon == -1) {
                return new MediaDesc(Integer.parseInt(encoded));
            }

            MediaDesc data = new MediaDesc(-1);
            data.hash = StringUtil.unhexlate(encoded.substring(0, colon));
            data.mimeType =
                (byte) Integer.parseInt(encoded.substring(colon + 1));
            return data;

        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    public static String asDBString (MediaDesc data)
    {
        if (data == null) {
            return null;
        }
        if (data.id == -1) {
            return StringUtil.hexlate(data.hash) + ":" + data.mimeType;
        } else {
            return String.valueOf(data.id);
        }
    }
*/

    /** Suitable for unserialization. */
    public MediaDesc ()
    {
    }

    /**
     * TEMPORARY CONSTRUCTOR, for making it easy for me to
     * pre-initialize some media...
     */
    public MediaDesc (String filename)
    {
        hash = MediaItem.stringToHash(
            filename.substring(0, filename.indexOf('.')));
        mimeType = (byte) MediaItem.suffixToMimeType(filename);
    }

    public boolean equals (Object other)
    {
        if (other instanceof MediaDesc) {
            MediaDesc that = (MediaDesc) other;
            return (this.mimeType == that.mimeType) &&
                Arrays.equals(this.hash, that.hash);
        }
        return false;
    }

    // documentation inherited
    public int hashCode ()
    {
        int code = 0;
        for (int ii = Math.min(3, hash.length - 1); ii >= 0; ii--) {
            code <<= 8;
            code |= hash[ii];
        }
        return code;
    }
}
