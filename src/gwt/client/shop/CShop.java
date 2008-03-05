//
// $Id$

package client.shop;

import java.util.ArrayList;

import com.threerings.msoy.web.data.CatalogQuery;

import client.shell.Args;
import client.shell.CShell;

/**
 * Extends {@link CShell} and provides shopping-specific services.
 */
public class CShop extends CShell
{
    /** Messages used by the shopping interfaces. */
    public static ShopMessages msgs;

    /**
     * Composes arguments to browse the catalog.
     */
    public static String composeArgs (CatalogQuery query, String tag, String search, int creatorId)
    {
        query.tag = tag;
        query.search = search;
        query.creatorId = creatorId;
        return composeArgs(query, 0);
    }

    /**
     * Composes arguments to browse the catalog.
     */
    public static String composeArgs (CatalogQuery query, int page)
    {
        ArrayList args = new ArrayList();
        args.add(new Byte(query.itemType));
        args.add(new Byte(query.sortBy));
        if (query.tag != null) {
            args.add("t" + query.tag);
        } else if (query.search != null) {
            args.add("s" + query.search);
        } else if (query.creatorId != 0) {
            args.add("c" + query.creatorId);
        } else {
            args.add("");
        }
        if (page > 0) {
            args.add(new Integer(page));
        }
        return Args.compose(args);
    }

    /**
     * Parses args previously composed via {@link #composeArgs}.
     */
    public static CatalogQuery parseArgs (Args args)
    {
        CatalogQuery query = new CatalogQuery();
        query.itemType = (byte)args.get(0,  query.itemType);
        query.sortBy = (byte)args.get(1, query.sortBy);
        String action = args.get(2, "");
        if (action.startsWith("s")) {
            query.search = action.substring(1);
        } else if (action.startsWith("t")) {
            query.tag = action.substring(1);
        } else if (action.startsWith("c")) {
            try {
                query.creatorId = Integer.parseInt(action.substring(1));
            } catch (Exception e) {
                // oh well
            }
        }
        return query;
    }
}
