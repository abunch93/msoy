//
// $Id$

package com.threerings.msoy.data {

import com.threerings.util.Enum;

/**
 * Enumerates the ways in which the flash client can be embedded. Traditionally this is either "on
 * whirled.com" or not. This class was added to also incorporate the notion of being embedded on
 * Facebook. The behavior in this scenario overlaps partially with traditional embeds and
 * partially with whirled.com. As much as possible, individual methods define the specific
 * categories of overlapping behavior.
 *
 * <p>Note that the gwt Embedding enumeration is a nested class in <code>client.frame.Frame</code>.
 * The value does not need to be serialized (so class names don't have to match). Furthermore, they
 * are not exactly analogous because gwt is never embedded independently like the flash client (it
 * does not make sense to include the <code>OTHER</code> value there).</p>
 */
public final class Embedding extends Enum
{
    /** Not embedded, i.e. the user is looking at whirled.com or a dev deployment. */
    public static const NONE :Embedding = new Embedding("NONE");

    /** Embedded on facebook, gwt is available but there are other limits. */
    public static const FACEBOOK :Embedding = new Embedding("FACEBOOK");

    /** Embedded elsewhere, no gwt interface. */
    public static const OTHER :Embedding = new Embedding("OTHER");

    finishedEnumerating(Embedding);

    /**
     * Gets the value of the enumeration with the given name.
     */
    public static function valueOf (name :String) :Embedding
    {
        return Enum.valueOf(Embedding, name) as Embedding;
    }

    /**
     * Gets an array of all enumerated values.
     */
    public static function values () :Array
    {
        return Enum.values(Embedding);
    }

    /** @private */
    public function Embedding (name :String)
    {
        super(name);
    }

    /**
     * Returns true if this embedding is expected to have access to the gwt interface.
     */
    public function hasGWT () :Boolean
    {
        return this != OTHER;
    }

    /**
     * Returns true if this embedding should upsell whirled (i.e. display some links to whirled).
     */
    public function shouldUpsellWhirled () :Boolean
    {
        return this == OTHER;
    }
}
}