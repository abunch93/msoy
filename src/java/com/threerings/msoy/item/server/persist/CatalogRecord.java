//
// $Id$

package com.threerings.msoy.item.server.persist;

import java.sql.Timestamp;
import java.util.Date;

import com.samskivert.jdbc.depot.PersistentRecord;
import com.samskivert.jdbc.depot.annotation.Entity;
import com.samskivert.jdbc.depot.annotation.Id;
import com.samskivert.jdbc.depot.annotation.Table;
import com.samskivert.jdbc.depot.annotation.Transient;

import com.samskivert.util.StringUtil;

import com.threerings.io.Streamable;

import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.item.data.gwt.CatalogListing;

/**
 * Represents a catalog listing of an item.
 */
@Entity
@Table
public abstract class CatalogRecord<T extends ItemRecord> extends PersistentRecord
    implements Streamable
{
    // AUTO-GENERATED: FIELDS START
    /** The column identifier for the {@link #item} field. */
    public static final String ITEM = "item";

    /** The column identifier for the {@link #itemId} field. */
    public static final String ITEM_ID = "itemId";

    /** The column identifier for the {@link #listedDate} field. */
    public static final String LISTED_DATE = "listedDate";

    /** The column identifier for the {@link #flowCost} field. */
    public static final String FLOW_COST = "flowCost";

    /** The column identifier for the {@link #goldCost} field. */
    public static final String GOLD_COST = "goldCost";

    /** The column identifier for the {@link #rarity} field. */
    public static final String RARITY = "rarity";

    /** The column identifier for the {@link #purchases} field. */
    public static final String PURCHASES = "purchases";

    /** The column identifier for the {@link #returns} field. */
    public static final String RETURNS = "returns";

    /** The column identifier for the {@link #repriceCounter} field. */
    public static final String REPRICE_COUNTER = "repriceCounter";
    // AUTO-GENERATED: FIELDS END

    public static final int SCHEMA_VERSION = 4;

    /** A reference to the listed item. This value is not persisted. */
    @Transient
    public ItemRecord item;

    /** The ID of the listed item. */
    @Id
    public int itemId;

    /** The time this item was listed in the catalog. */
    public Timestamp listedDate;

    /** The amount of flow it costs to purchase a clone of this item. */
    public int flowCost;
    
    /** The amount of gold it costs to purchase a clone of this item. */
    public int goldCost;

    /** The rarity of this item; {@see Item#rarity}. */
    public int rarity;
    
    /** The number of times this item has been purchased. */
    public int purchases;
    
    /** The number of times this item has been returned. */
    public int returns;
    
    /** A somewhat opaque counter representing how badly this record needs to be repriced. */
    public int repriceCounter;

    public CatalogRecord ()
    {
        super();
    }

    protected CatalogRecord (CatalogListing listing)
    {
        super();

        item = ItemRecord.newRecord(listing.item);
        listedDate = new Timestamp(listing.listedDate.getTime());
    }

    public CatalogListing toListing ()
    {
        CatalogListing listing = new CatalogListing();
        listing.item = item.toItem();
        listing.listedDate = new Date(listedDate.getTime());
        // the name part of the MemberName is filled in by ItemManager
        listing.creator = new MemberName(null, item.creatorId);
        listing.flowCost = flowCost;
        listing.goldCost = goldCost;
        listing.rarity = rarity;
        return listing;
    }

    @Override
    public String toString ()
    {
        return StringUtil.fieldsToString(this);
    }
}
