//
// $Id$

package com.threerings.msoy.item.server.persist;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.samskivert.Log;
import com.samskivert.io.PersistenceException;

import com.samskivert.util.IntListUtil;
import com.samskivert.util.QuickSort;

import com.samskivert.jdbc.DatabaseLiaison;
import com.samskivert.jdbc.JDBCUtil;
import com.samskivert.jdbc.depot.CacheInvalidator;
import com.samskivert.jdbc.depot.DepotRepository;
import com.samskivert.jdbc.depot.EntityMigration;
import com.samskivert.jdbc.depot.Key;
import com.samskivert.jdbc.depot.PersistenceContext;
import com.samskivert.jdbc.depot.PersistentRecord;
import com.samskivert.jdbc.depot.annotation.Computed;
import com.samskivert.jdbc.depot.annotation.Entity;
import com.samskivert.jdbc.depot.annotation.FullTextIndex;
import com.samskivert.jdbc.depot.clause.FieldDefinition;
import com.samskivert.jdbc.depot.clause.FieldOverride;
import com.samskivert.jdbc.depot.clause.FromOverride;
import com.samskivert.jdbc.depot.clause.Join;
import com.samskivert.jdbc.depot.clause.Limit;
import com.samskivert.jdbc.depot.clause.OrderBy;
import com.samskivert.jdbc.depot.clause.QueryClause;
import com.samskivert.jdbc.depot.clause.Where;
import com.samskivert.jdbc.depot.expression.ColumnExp;
import com.samskivert.jdbc.depot.expression.FunctionExp;
import com.samskivert.jdbc.depot.expression.LiteralExp;
import com.samskivert.jdbc.depot.expression.SQLExpression;
import com.samskivert.jdbc.depot.operator.Arithmetic;
import com.samskivert.jdbc.depot.operator.Conditionals.*;
import com.samskivert.jdbc.depot.operator.Logic.*;
import com.samskivert.jdbc.depot.operator.SQLOperator;

import com.threerings.msoy.server.MsoyEventLogger;
import com.threerings.msoy.server.persist.TagHistoryRecord;
import com.threerings.msoy.server.persist.TagNameRecord;
import com.threerings.msoy.server.persist.TagRecord;
import com.threerings.msoy.server.persist.TagRepository;

import com.threerings.msoy.world.server.persist.MemoryRepository;

import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.gwt.CatalogListing;

import static com.threerings.msoy.Log.log;

/**
 * Manages a repository of digital items of a particular type.
 */
public abstract class ItemRepository<
    T extends ItemRecord,
    CLT extends CloneRecord<T>,
    CAT extends CatalogRecord<T>,
    RT extends RatingRecord<T>>
    extends DepotRepository
{
    @Computed
    @Entity
    public static class RatingAverageRecord extends PersistentRecord {
        @Computed(fieldDefinition="count(*)")
        public int count;
        @Computed(fieldDefinition="sum(" + RatingRecord.RATING + ")")
        public int sum;
    }

    /** The factor by which we split item cost into gold and flow. */
    public final static int FLOW_FOR_GOLD = 600;

    public ItemRepository (PersistenceContext ctx)
    {
        super(ctx);

        _tagRepo = new TagRepository(ctx) {
            protected TagRecord createTagRecord () {
                return ItemRepository.this.createTagRecord();
            }
            protected TagHistoryRecord createTagHistoryRecord () {
                return ItemRepository.this.createTagHistoryRecord();
            }
        };

        // rarity -> pricing; all pricing to manual
        _ctx.registerMigration(
            getCatalogClass(), new EntityMigration.Rename(8, "rarity", "pricing"));
        _ctx.registerMigration(getCatalogClass(), new EntityMigration(8) {
            public boolean runBeforeDefault () {
                return false;
            }
            public int invoke (Connection conn, DatabaseLiaison liaison) throws SQLException {
                String query = "update " + liaison.tableSQL(_tableName) +
                    " set " + liaison.columnSQL("pricing") + " = " + CatalogListing.PRICING_MANUAL;
                Statement stmt = conn.createStatement();
                try {
                    return stmt.executeUpdate(query);
                } finally {
                    JDBCUtil.close(stmt);
                }
            }
        });
        _ctx.registerMigration(
            getCatalogClass(), new EntityMigration.Drop(8, "repriceCounter"));
    }

    /**
     * Configures this repository with its item type and the memory repository.
     */
    public void init (byte itemType, MemoryRepository memRepo, MsoyEventLogger eventLog)
    {
        _itemType = itemType;
        _memRepo = memRepo;
        _eventLog = eventLog;
    }

    /**
     * Returns the item type constant for the type of item handled by this repository.
     */
    public byte getItemType ()
    {
        return _itemType;
    }

    /**
     * Converts a runtime item record to an initialized instance of our persistent item record
     * class.
     */
    public ItemRecord newItemRecord (Item item)
    {
        try {
            T record = getItemClass().newInstance();
            record.fromItem(item);
            return record;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the repository that manages tags for this item.
     */
    public TagRepository getTagRepository ()
    {
        return _tagRepo;
    }

    /**
     * Load an item, or a clone.
     */
    public T loadItem (int itemId)
        throws PersistenceException
    {
        // TODO: This will only work for the first two billion clones.
        return itemId > 0 ? loadOriginalItem(itemId) : loadClone(itemId);
    }

    /**
     * Loads an item with the specified identifier. Returns null if no item exists with that
     * identifier.
     */
    public T loadOriginalItem (int itemId)
        throws PersistenceException
    {
        return load(getItemClass(), itemId);
    }

    /**
     * Loads the clone with the given identifier. Returns null if no clone exists with that
     * identifier.
     */
    public T loadClone (int cloneId) throws PersistenceException
    {
        CLT cloneRecord = load(getCloneClass(), cloneId);
        if (cloneRecord == null) {
            return null;
        }

        T clone = loadOriginalItem(cloneRecord.originalItemId);
        if (clone == null) {
            throw new PersistenceException(
                "Clone's original does not exist [cloneId=" + cloneId +
                ", originalItemId=" + cloneRecord.originalItemId + "]");
        }
        clone.initFromClone(cloneRecord);
        return clone;
    }

    /**
     * Loads all original items owned by the specified member in the specified suite.
     */
    public List<T> loadOriginalItems (int ownerId, int suiteId)
        throws PersistenceException
    {
        Where where;
        if (suiteId == 0) {
            where = new Where(getItemColumn(ItemRecord.OWNER_ID), ownerId);
        } else {
            where = new Where(getItemColumn(ItemRecord.OWNER_ID), ownerId,
                              getItemColumn(SubItemRecord.SUITE_ID), suiteId);
        }
        return findAll(getItemClass(), where);
    }

    /**
     * Loads all original items with the specified suite.
     */
    public List<T> loadOriginalItemsBySuite (int suiteId)
        throws PersistenceException
    {
        return findAll(getItemClass(), new Where(getItemColumn(SubItemRecord.SUITE_ID), suiteId));
    }

    /**
     * Loads all cloned items owned by the specified member.
     */
    public List<T> loadClonedItems (int ownerId, int suiteId)
        throws PersistenceException
    {
        Where where;
        if (suiteId == 0) {
            where = new Where(getCloneColumn(CloneRecord.OWNER_ID), ownerId);
        } else {
            where = new Where(getCloneColumn(CloneRecord.OWNER_ID), ownerId,
                              getItemColumn(SubItemRecord.SUITE_ID), suiteId);
        }
        return loadClonedItems(where);
    }

    /**
     * Loads up to maxCount items from a user's inventory that were the most recently touched.
     */
    public List<T> loadRecentlyTouched (int ownerId, int maxCount)
        throws PersistenceException
    {
        // Since we don't know how many we'll find of each kind (cloned, orig), we load the max
        // from each.
        Limit limit = new Limit(0, maxCount);
        List<T> originals = findAll(
            getItemClass(),
            new Where(getItemColumn(ItemRecord.OWNER_ID), ownerId),
            OrderBy.descending(getItemColumn(ItemRecord.LAST_TOUCHED)),
            limit);
        List<T> clones = loadClonedItems(
            new Where(getCloneColumn(CloneRecord.OWNER_ID), ownerId),
            OrderBy.descending(getCloneColumn(CloneRecord.LAST_TOUCHED)), limit);
        int size = originals.size() + clones.size();

        ArrayList<T> list = new ArrayList<T>(size);
        list.addAll(originals);
        list.addAll(clones);

        // now, sort by their lastTouched time
        QuickSort.sort(list, new Comparator<T>() {
            public int compare (T o1, T o2) {
                return o2.lastTouched.compareTo(o1.lastTouched);
            }
        });

        // remove any items beyond maxCount
        for (int ii = size - 1; ii >= maxCount; ii--) {
            list.remove(ii);
        }

        return list;
    }

    /**
     * Loads the specified items. Omits missing items from results.
     */
    public List<T> loadItems (int[] itemIds)
        throws PersistenceException
    {
        if (itemIds.length == 0) {
            return Collections.emptyList();
        }
        Comparable[] idArr = IntListUtil.box(itemIds);
        Where inClause = new Where(new In(getItemClass(), ItemRecord.ITEM_ID, idArr));
        List<T> items = loadClonedItems(inClause);
        items.addAll(findAll(getItemClass(), inClause));
        return items;
    }

    /**
     * Loads all items who have a non-zero {@link ItemRecord#flagged} field, limited to a given
     * number of rows.
     */
    public List<T> loadFlaggedItems (int count)
        throws PersistenceException
    {
        return findAll(getItemClass(),
                       new Where(new GreaterThan(getItemColumn(ItemRecord.FLAGGED), 0)),
                       new Limit(0, count));
    }

    /**
     * Loads a single clone record by item id.
     */
    public CLT loadCloneRecord (int itemId)
        throws PersistenceException
    {
        return load(getCloneClass(), itemId);
    }

    /**
     * Loads all the raw clone records associated with a given original item id. This is
     * potentially a very large dataset.
     */
    public List<CLT> loadCloneRecords (int itemId)
        throws PersistenceException
    {
        return findAll(
            getCloneClass(),
            new Where(getCloneColumn(CloneRecord.ORIGINAL_ITEM_ID), itemId));
    }

    /**
     * Loads and returns all items (clones and originals) that are "in use" at the specified
     * location.
     */
    public List<T> loadItemsByLocation (int location)
        throws PersistenceException
    {
        List<T> items = loadClonedItems(
            new Where(getCloneColumn(CloneRecord.LOCATION), location));
        List<T> citems = findAll(
            getItemClass(),new Where(getItemColumn(ItemRecord.LOCATION), location));
        items.addAll(citems);
        return items;
    }

    /**
     * Mark the specified items as being used in the specified way.
     */
    public void markItemUsage (int[] itemIds, byte usageType, int location)
        throws PersistenceException
    {
        Class<T> iclass = getItemClass();
        Class<CLT> cclass = getCloneClass();

        for (int itemId : itemIds) {
            int result;
            if (itemId > 0) {
                result = updatePartial(
                    iclass, itemId, ItemRecord.USED, usageType, ItemRecord.LOCATION, location,
                    ItemRecord.LAST_TOUCHED, new Timestamp(System.currentTimeMillis()));
            } else {
                result = updatePartial(
                    cclass, itemId, ItemRecord.USED, usageType, ItemRecord.LOCATION, location,
                    ItemRecord.LAST_TOUCHED, new Timestamp(System.currentTimeMillis()));
            }
            // if the item didn't update, freak out.
            if (0 == result) {
                Log.warning("Unable to find item to mark usage [itemId=" + itemId +
                            ", usageType=" + usageType + ", location=" + location + "].");
            }
        }
    }

    /**
     * Find a single catalog entry randomly.
     */
    public CAT pickRandomCatalogEntry ()
        throws PersistenceException
    {
        CAT record = load(getCatalogClass(), new QueryClause[] {
            new Limit(0, 1),
            OrderBy.random()
        });

        if (record != null) {
            record.item = loadOriginalItem(record.listedItemId);
        }
        return record;
    }

    /**
     * Find a single random catalog entry that is tagged with *any* of the specified tags.
     */
    public CAT findRandomCatalogEntryByTags (String... tags)
        throws PersistenceException
    {
        // first find the tag record...
        List<TagNameRecord> tagRecords = getTagRepository().getTags(tags);
        int tagCount = tagRecords.size();
        if (tagCount == 0) {
            return null;
        }

        Integer[] tagIds = new Integer[tagCount];
        for (int ii = 0; ii < tagCount; ii++) {
            tagIds[ii] = tagRecords.get(ii).tagId;
        }

        List<CAT> records = findAll(getCatalogClass(),
            new Join(getCatalogClass(), CatalogRecord.LISTED_ITEM_ID,
                     getItemClass(), ItemRecord.ITEM_ID),
            new Limit(0, 1),
            OrderBy.random(),
            new Join(getCatalogClass(), CatalogRecord.LISTED_ITEM_ID,
                     getTagRepository().getTagClass(), TagRecord.TARGET_ID),
            new Where(new In(getTagColumn(TagRecord.TAG_ID), tagIds)));

        if (records.isEmpty()) {
            return null;
        }

        CAT record = records.get(0);
        record.item = loadOriginalItem(record.listedItemId);
        return record;
    }

    /**
     * Counts all items in the catalog that match the supplied query terms.
     */
    public int countListings (boolean mature, String search, int tag, int creator)
        throws PersistenceException
    {
        ArrayList<QueryClause> clauses = new ArrayList<QueryClause>();
        clauses.add(new FromOverride(getCatalogClass()));
        clauses.add(new Join(getCatalogClass(), CatalogRecord.LISTED_ITEM_ID,
                             getItemClass(), ItemRecord.ITEM_ID));

        // see if there's any where bits to turn into an actual where clause
        addSearchClause(clauses, mature, search, tag, creator);

        // finally fetch all the catalog records of interest
        ListingCountRecord crec = load(
            ListingCountRecord.class, clauses.toArray(new QueryClause[clauses.size()]));
        return crec.count;
    }

    /**
     * Loads all items in the catalog.
     *
     * TODO: This method currently fetches CatalogRecords through a join against ItemRecord,
     *       and then executes a second query against ItemRecord only. This really really has
     *       to be a single join in a sane universe, but that makes significant demands on the
     *       Depot code that we don't know how to handle yet (or possibly some fiddling with
     *       the Item vs Catalog class hierarchies).
     */
    public List<CAT> loadCatalog (byte sortBy, boolean mature, String search, int tag,
                                  int creator, int offset, int rows)
        throws PersistenceException
    {
        ArrayList<QueryClause> clauses = new ArrayList<QueryClause>();
        clauses.add(new Join(getCatalogClass(), CatalogRecord.LISTED_ITEM_ID,
                             getItemClass(), ItemRecord.ITEM_ID));
        clauses.add(new Limit(offset, rows));

        // sort out the primary and secondary order by clauses
        ArrayList<SQLExpression> obExprs = new ArrayList<SQLExpression>();
        ArrayList<OrderBy.Order> obOrders = new ArrayList<OrderBy.Order>();
        switch(sortBy) {
        case CatalogListing.SORT_BY_LIST_DATE:
            addOrderByListDate(obExprs, obOrders);
            addOrderByRating(obExprs, obOrders);
            break;
        case CatalogListing.SORT_BY_RATING:
            addOrderByRating(obExprs, obOrders);
            addOrderByPrice(obExprs, obOrders, OrderBy.Order.ASC);
            break;
        case CatalogListing.SORT_BY_PRICE_ASC:
            addOrderByPrice(obExprs, obOrders, OrderBy.Order.ASC);
            addOrderByRating(obExprs, obOrders);
            break;
        case CatalogListing.SORT_BY_PRICE_DESC:
            addOrderByPrice(obExprs, obOrders, OrderBy.Order.DESC);
            addOrderByRating(obExprs, obOrders);
            break;
        case CatalogListing.SORT_BY_PURCHASES:
            addOrderByPurchases(obExprs, obOrders);
            addOrderByRating(obExprs, obOrders);
            break;
        default:
            throw new IllegalArgumentException(
                "Sort method not implemented [sortBy=" + sortBy + "]");
        }
        clauses.add(new OrderBy(obExprs.toArray(new SQLExpression[obExprs.size()]),
                                obOrders.toArray(new OrderBy.Order[obOrders.size()])));

        // see if there's any where bits to turn into an actual where clause
        addSearchClause(clauses, mature, search, tag, creator);

        // finally fetch all the catalog records of interest
        List<CAT> records = findAll(
            getCatalogClass(), clauses.toArray(new QueryClause[clauses.size()]));
        if (records.size() == 0) {
            return records;
        }

        // construct an array of item ids we need to load
        Comparable[] idArr = new Integer[records.size()];
        int ii = 0;
        for (CatalogRecord record : records) {
            idArr[ii++] = record.listedItemId;
        }

        // load those items and map item ID's to items
        List<T> items = findAll(
            getItemClass(), new Where(new In(getItemClass(), ItemRecord.ITEM_ID, idArr)));
        Map<Integer, T> map = new HashMap<Integer, T>();
        for (T iRec : items) {
            map.put(iRec.itemId, iRec);
        }

        // finally populate the catalog records
        for (CatalogRecord<T> record : records) {
            record.item = map.get(record.listedItemId);
        }
        return records;
    }

    /**
     * Load a single catalog listing.
     */
    public CAT loadListing (int catalogId, boolean loadListedItem)
        throws PersistenceException
    {
        CAT record = load(getCatalogClass(), catalogId);
        if (record != null && loadListedItem) {
            record.item = load(getItemClass(), record.listedItemId);
        }
        return record;
    }

    /**
     * Update either the 'purchases' or the 'returns' field of a catalog listing, and figure out if
     * it's time to reprice it.
     */
    public void nudgeListing (int catalogId, boolean purchased)
        throws PersistenceException
    {
        CAT record = load(getCatalogClass(), catalogId);
        if (record == null) {
            return; // if the listing has been unlisted, we don't need to nudge it.
        }

        Map<String, SQLExpression> updates = new HashMap<String, SQLExpression>();
        if (purchased) {
            updates.put(CatalogRecord.PURCHASES,
                        new Arithmetic.Add(getCatalogColumn(CatalogRecord.PURCHASES), 1));

            int purchases = record.purchases + 1; // for below calculations
            switch (record.pricing) {
            case CatalogListing.PRICING_LIMITED_EDITION:
                if (purchases >= record.salesTarget) {
                    updates.put(CatalogRecord.PRICING,
                                new LiteralExp(""+CatalogListing.PRICING_HIDDEN));
                }
                break;

            case CatalogListing.PRICING_ESCALATE:
                if (purchases == record.salesTarget) {
                    updates.put(CatalogRecord.FLOW_COST,
                                new LiteralExp(""+CatalogListing.escalatePrice(record.flowCost)));
                    updates.put(CatalogRecord.GOLD_COST,
                                new LiteralExp(""+CatalogListing.escalatePrice(record.goldCost)));
                }
                break;
            }

        } else {
            updates.put(CatalogRecord.RETURNS,
                        new Arithmetic.Add(getCatalogColumn(CatalogRecord.RETURNS), 1));
        }

        // finally update the columns we actually modified
        updateLiteral(getCatalogClass(), record.catalogId, updates);
    }

    /**
     * Inserts the supplied item into the database. The {@link ItemRecord#itemId} and the
     * {@link ItemRecord#lastTouched) fields will be filled in as a result of this call.
     */
    public void insertOriginalItem (T item, boolean catalogListing)
        throws PersistenceException
    {
        if (item.itemId != 0) {
            throw new PersistenceException("Can't insert item with existing key: " + item);
        }
        item.lastTouched = new Timestamp(System.currentTimeMillis());
        insert(item);
    }

    /**
     * Updates the supplied item in the database. The {@link ItemRecord#lastTouched) field
     * will be filled in as a result of this call.
     */
    public void updateOriginalItem (T item)
        throws PersistenceException
    {
        item.lastTouched = new Timestamp(System.currentTimeMillis());
        update(item);
    }

    /**
     * Create a row in our catalog table corresponding to the given item record, which should
     * be of the immutable variety.
     */
    public CatalogRecord insertListing (
        ItemRecord listItem, int originalItemId, int pricing, int salesTarget,
        int flowCost, int goldCost, long listingTime)
        throws PersistenceException
    {
        if (listItem.ownerId != 0) {
            throw new PersistenceException(
                "Can't list item with owner [itemId=" + listItem.itemId + "]");
        }

        CAT record;
        try {
            record = getCatalogClass().newInstance();
        } catch (Exception e) {
            throw new PersistenceException(e);
        }
        record.item = listItem;
        record.listedItemId = listItem.itemId;
        record.originalItemId = originalItemId;
        record.listedDate = new Timestamp(listingTime);
        record.pricing = pricing;
        record.salesTarget = salesTarget;
        record.purchases = record.returns = 0;
        record.flowCost = flowCost;
        record.goldCost = goldCost;
        insert(record);

        // wire this listed item and its original up to the catalog record
        noteListing(record.listedItemId, record.catalogId);
        noteListing(originalItemId, record.catalogId);

        _eventLog.itemListedInCatalog(
            listItem.creatorId, _itemType, listItem.itemId,
            flowCost, goldCost, pricing, salesTarget);

        return record;
    }

    /**
     * Updates the pricing for the specified catalog listing.
     */
    public void updatePricing (int catalogId, int pricing, int salesTarget,
                               int flowCost, int goldCost, long updateTime)
        throws PersistenceException
    {
        updatePartial(getCatalogClass(), catalogId,
                      // TODO?: CatalogRecord.LISTED_DATE, new Timestamp(updateTime),
                      CatalogRecord.PRICING, pricing,
                      CatalogRecord.SALES_TARGET, salesTarget,
                      CatalogRecord.FLOW_COST, flowCost,
                      CatalogRecord.GOLD_COST, goldCost);
    }

    /**
     * Removes the listing for the specified item from the catalog, returns true if a listing was
     * found and removed, false otherwise.
     */
    public boolean removeListing (CatalogRecord listing)
        throws PersistenceException
    {
        // clear out the listing mappings for the original item
        if (listing.originalItemId != 0) {
            noteListing(listing.originalItemId, 0);
        }
        return delete(getCatalogClass(), listing.catalogId) > 0;
    }

    /**
     * Inserts an item clone into the database with the given owner and purchase data. Also fills
     * (@link CloneRecord#itemId) with the next available ID and {@link CloneRecord#ownerId)
     * with the new owner. Finally, updates {@link CloneRecord#lastTouched) and
     * {@link CloneRecord#purchaseTime).
     */
    public ItemRecord insertClone (ItemRecord parent, int newOwnerId, int flowPaid, int goldPaid)
        throws PersistenceException
    {
        CLT record;
        try {
            record = getCloneClass().newInstance();
        } catch (Exception e) {
            throw new PersistenceException(e);
        }
        record.initialize(parent, newOwnerId, flowPaid, goldPaid);
        insert(record);

        ItemRecord newClone = (ItemRecord) parent.clone();
        newClone.initFromClone(record);

        _eventLog.itemPurchased(newOwnerId, _itemType, newClone.itemId, flowPaid, goldPaid);
        return newClone;
    }

    /**
     * Deletes an item from the repository and all associated data (ratings, tags, tag history).
     * This method does not perform any checking to determine whether it is safe to delete the item
     * so do not call it unless you know the item is not listed in the catalog or otherwise in use.
     */
    public void deleteItem (final int itemId)
        throws PersistenceException
    {
        if (itemId < 0) {
            delete(getCloneClass(), itemId);

        } else {
            // delete the item in question
            delete(getItemClass(), itemId);

            // delete rating records for this item (and invalidate the cache properly)
            deleteAll(getRatingClass(),
                      new Where(getRatingColumn(RatingRecord.ITEM_ID), itemId),
                      new CacheInvalidator.TraverseWithFilter<RT>(getRatingClass()) {
                          public boolean testForEviction (Serializable key, RT record) {
                              return record != null && record.itemId == itemId;
                          }
                      });

            // delete tag records relating to this item
            _tagRepo.deleteTags(itemId);

            // delete any entity memory for this item as well
            _memRepo.deleteMemories(_itemType, itemId);
        }
    }

    /**
     * Returns the rating given to the specified item by the specified member.
     */
    public RatingRecord<T> getRating (int itemId, int memberId)
        throws PersistenceException
    {
        return load(getRatingClass(),
                    RatingRecord.ITEM_ID, itemId,
                    RatingRecord.MEMBER_ID, memberId);
    }

    /**
     * Insert/update a rating row, calculate the new rating and finally update the item's rating.
     */
    public float rateItem (int itemId, int memberId, byte rating)
        throws PersistenceException
    {
        // first create a new rating record
        RatingRecord<T> record;
        try {
            record = getRatingClass().newInstance();
        } catch (Exception e) {
            throw new PersistenceException(
                "Failed to create a new item rating record " +
                "[itemId=" + itemId + ", memberId=" + memberId + "]", e);
        }
        // populate and insert it
        record.itemId = itemId;
        record.memberId = memberId;
        record.rating = rating;
        store(record);

        RatingAverageRecord average =
            load(RatingAverageRecord.class,
                 new FromOverride(getRatingClass()),
                 new Where(getRatingColumn(RatingRecord.ITEM_ID), itemId));

        float newRating = (float) ((average.count == 0) ? 0.0 : average.sum/average.count);
        // and then smack the new value into the item using yummy depot code
        updatePartial(getItemClass(), itemId, ItemRecord.RATING, newRating,
                      ItemRecord.LAST_TOUCHED, new Timestamp(System.currentTimeMillis()));
        return newRating;
    }

    /**
     * Transfers rating records from one record to another. This is used when a catalog listing is
     * updated to migrate the players' individual rating records from the old prototype item to the
     * new one.
     *
     * <p> Note: this destabilizes the rating of the abandoned previous listing, but that rating is
     * meaningless anyway since the item is no longer in the catalog. Ratings should really be on
     * listings not items, but that's a giant fiasco we don't want to deal with.
     */
    public void reassignRatings (final int oldItemId, int newItemId)
        throws PersistenceException
    {
        // TODO: this cache eviction might be slow :)
        updatePartial(getRatingClass(), new Where(getRatingColumn(RatingRecord.ITEM_ID), oldItemId),
                      new CacheInvalidator.TraverseWithFilter<RT>(getRatingClass()) {
                          public boolean testForEviction (Serializable key, RT record) {
                              return (record.itemId == oldItemId);
                          }
                      }, RatingRecord.ITEM_ID, newItemId);
    }

    /**
     * Safely changes the owner of an item record with a sanity-check against race conditions.
     */
    public void updateOwnerId (ItemRecord item, int newOwnerId)
        throws PersistenceException
    {
        Where where;
        Key key;
        if (item.itemId < 0) {
            where = new Where(getCloneColumn(ItemRecord.ITEM_ID), item.itemId,
                              getCloneColumn(ItemRecord.OWNER_ID), item.ownerId);
            key = new Key<CLT>(getCloneClass(), CloneRecord.ITEM_ID, item.itemId);
        } else {
            where = new Where(getItemColumn(ItemRecord.ITEM_ID), item.itemId,
                              getItemColumn(ItemRecord.OWNER_ID), item.ownerId);
            key = new Key<T>(getItemClass(), ItemRecord.ITEM_ID, item.itemId);
        }
        int modifiedRows =  updatePartial(
            item.itemId < 0 ? getCloneClass() : getItemClass(), where, key,
            ItemRecord.OWNER_ID, newOwnerId,
            ItemRecord.LAST_TOUCHED, new Timestamp(System.currentTimeMillis()));
        if (modifiedRows == 0) {
            throw new PersistenceException("Failed to safely update ownerId [item=" + item +
                                           ", newOwnerId=" + newOwnerId + "]");
        }
    }

    /**
     * Notes that the specified original item is now associated with the specified catalog listed
     * item (which may be zero to clear out a listing link).
     */
    protected void noteListing (int originalItemId, int catalogId)
        throws PersistenceException
    {
        updatePartial(getItemClass(), originalItemId, ItemRecord.CATALOG_ID, catalogId);
    }

    /**
     * Performs the necessary join to load cloned items matching the supplied where clause.
     */
    protected List<T> loadClonedItems (Where where, QueryClause... clauses)
        throws PersistenceException
    {
        final int OUR_CLAUSE_COUNT = 9;
        QueryClause[] allClauses = new QueryClause[clauses.length + OUR_CLAUSE_COUNT];
        allClauses[0] = where;
        allClauses[1] = new Join(
            getItemClass(), ItemRecord.ITEM_ID, getCloneClass(), CloneRecord.ORIGINAL_ITEM_ID);
        allClauses[2] = new FieldDefinition(
            ItemRecord.SOURCE_ID, getItemClass(), ItemRecord.ITEM_ID);
        allClauses[3] = new FieldOverride(
            ItemRecord.ITEM_ID, getCloneClass(), CloneRecord.ITEM_ID);
        allClauses[4] = new FieldOverride(
            ItemRecord.OWNER_ID, getCloneClass(), CloneRecord.OWNER_ID);
        allClauses[5] = new FieldOverride(
            ItemRecord.LOCATION, getCloneClass(), CloneRecord.LOCATION);
        allClauses[6] = new FieldOverride(
            ItemRecord.USED, getCloneClass(), CloneRecord.USED);
        allClauses[7] = new FieldOverride(
            ItemRecord.LAST_TOUCHED, getCloneClass(), CloneRecord.LAST_TOUCHED);
        allClauses[8] = new FieldOverride(ItemRecord.CATALOG_ID, "0");
        System.arraycopy(clauses, 0, allClauses, OUR_CLAUSE_COUNT, clauses.length);

        return findAll(getItemClass(), allClauses);
    }

    /**
     * Helper function for {@link #countListings} and {@link #loadCatalog}.
     */
    protected void addSearchClause (ArrayList<QueryClause> clauses, boolean mature, String search,
                                    int tag, int creator)
        throws PersistenceException
    {
        ArrayList<SQLOperator> whereBits = new ArrayList<SQLOperator>();

        if (search != null && search.length() > 0) {
            whereBits.add(new FullTextMatch(getItemClass(), ItemRecord.FTS_ND, search));
        }

        if (tag > 0) {
            // join against TagRecord
            clauses.add(new Join(getCatalogClass(), CatalogRecord.LISTED_ITEM_ID,
                                 getTagRepository().getTagClass(), TagRecord.TARGET_ID));
            // and add a condition
            whereBits.add(new Equals(getTagColumn(TagRecord.TAG_ID), tag));
        }

        if (creator > 0) {
            whereBits.add(new Equals(getItemColumn(ItemRecord.CREATOR_ID), creator));
        }

        if (!mature) {
            // add a check to make sure ItemRecord.FLAG_MATURE is not set on any returned items
            whereBits.add(new Equals(getItemColumn(ItemRecord.MATURE), false));
        }

        whereBits.add(new Not(new Equals(getCatalogColumn(CatalogRecord.PRICING),
                                         CatalogListing.PRICING_HIDDEN)));
        clauses.add(new Where(new And(whereBits.toArray(new SQLOperator[whereBits.size()]))));
    }

    protected void addOrderByListDate (ArrayList<SQLExpression> exprs,
                                       ArrayList<OrderBy.Order> orders)
    {
        exprs.add(getCatalogColumn(CatalogRecord.LISTED_DATE));
        orders.add(OrderBy.Order.DESC);
    }

    protected void addOrderByRating (ArrayList<SQLExpression> exprs,
                                     ArrayList<OrderBy.Order> orders)
    {
        exprs.add(new FunctionExp("floor", getItemColumn(ItemRecord.RATING)));
        orders.add(OrderBy.Order.DESC);
    }

    protected void addOrderByPrice (ArrayList<SQLExpression> exprs, ArrayList<OrderBy.Order> orders,
                                    OrderBy.Order order)
    {
        exprs.add(new Arithmetic.Add(getCatalogColumn(CatalogRecord.FLOW_COST),
                                     new Arithmetic.Mul(getCatalogColumn(CatalogRecord.GOLD_COST),
                                                        FLOW_FOR_GOLD)));
        orders.add(order);
    }

    protected void addOrderByPurchases (ArrayList<SQLExpression> exprs,
                                        ArrayList<OrderBy.Order> orders)
    {
        // TODO: someday make an indexed column that represents (purchases-returns)
        exprs.add(getCatalogColumn(CatalogRecord.PURCHASES));
        orders.add(OrderBy.Order.DESC);
    }

    protected ColumnExp getItemColumn (String cname)
    {
        return new ColumnExp(getItemClass(), cname);
    }

    protected ColumnExp getCatalogColumn (String cname)
    {
        return new ColumnExp(getCatalogClass(), cname);
    }

    protected ColumnExp getCloneColumn (String cname)
    {
        return new ColumnExp(getCloneClass(), cname);
    }

    protected ColumnExp getRatingColumn (String cname)
    {
        return new ColumnExp(getRatingClass(), cname);
    }

    protected ColumnExp getTagColumn (String cname)
    {
        return new ColumnExp(getTagRepository().getTagClass(), cname);
    }

    @Override // from DepotRepository
    protected void getManagedRecords (Set<Class<? extends PersistentRecord>> classes)
    {
        classes.add(getItemClass());
        classes.add(getCloneClass());
        classes.add(getCatalogClass());
        classes.add(getRatingClass());
    }

    /**
     * Specific item repositories override this method and indicate the class of item on which they
     * operate.
     */
    protected abstract Class<T> getItemClass ();

    /**
     * Specific item repositories override this method and indicate their item's clone persistent
     * record class.
     */
    protected abstract Class<CLT> getCloneClass ();

    /**
     * Specific item repositories override this method and indicate their item's catalog persistent
     * record class.
     */
    protected abstract Class<CAT> getCatalogClass ();

    /**
     * Specific item repositories override this method and indicate their item's rating persistent
     * record class.
     */
    protected abstract Class<RT> getRatingClass ();

    /**
     * Specific item repositories override this method and indicate their item's tag persistent
     * record class.
     */
    protected abstract TagRecord createTagRecord ();

    /**
     * Specific item repositories override this method and indicate their item's tag history
     * persistent record class.
     */
    protected abstract TagHistoryRecord createTagHistoryRecord ();

    /** The byte type of our item. */
    protected byte _itemType;

    /** Used to manage our item tags. */
    protected TagRepository _tagRepo;

    /** We call into this to delete item memory if we're an item that has memory. */
    protected MemoryRepository _memRepo;

    /** Reference to the event logger. */
    protected MsoyEventLogger _eventLog;

    /** The minimum number of purchases before we'll start attenuating price based on returns. */
    protected static final int MIN_ATTEN_PURCHASES = 5;
}
